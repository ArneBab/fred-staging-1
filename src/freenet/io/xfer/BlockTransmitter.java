/*
 * Dijjer - A Peer to Peer HTTP Cache
 * Copyright (C) 2004,2005 Change.Tv, Inc
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package freenet.io.xfer;

import java.util.LinkedList;

import freenet.io.comm.AsyncMessageFilterCallback;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.DisconnectedException;
import freenet.io.comm.Message;
import freenet.io.comm.MessageCore;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.io.comm.PeerContext;
import freenet.io.comm.PeerRestartedException;
import freenet.io.comm.RetrievalException;
import freenet.io.comm.SlowAsyncMessageFilterCallback;
import freenet.node.PrioRunnable;
import freenet.node.SyncSendWaitedTooLongException;
import freenet.node.Ticker;
import freenet.support.BitArray;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.TimeUtil;
import freenet.support.Logger.LogLevel;
import freenet.support.io.NativeThread;
import freenet.support.math.MedianMeanRunningAverage;

/**
 * @author ian
 *
 * Given a PartiallyReceivedBlock retransmit to another node (to be received by BlockReceiver).
 * Since a PRB can be concurrently transmitted to many peers NOWHERE in this class is prb.abort() to be called.
 * 
 * Transmits can be cancelled, we will send a sendAborted. However, the receiver cannot 
 * cancel an incoming block transfer, because that would allow bad things. See the comments
 * on BlockReceiver.
 */
public class BlockTransmitter {

	private static volatile boolean logMINOR;

	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	public static final int SEND_TIMEOUT = 60000;
	public static final int PING_EVERY = 8;
	
	final MessageCore _usm;
	final PeerContext _destination;
	private boolean _sendComplete;
	final long _uid;
	final PartiallyReceivedBlock _prb;
	private LinkedList<Integer> _unsent;
	private MyRunnable _senderThread = new MyRunnable();
	private BitArray _sentPackets;
	final PacketThrottle throttle;
	private long timeAllSent = -1;
	final ByteCounter _ctr;
	final int PACKET_SIZE;
	private final Ticker _ticker;
	
	class MyRunnable implements PrioRunnable {
		
		private boolean running = false;
		
		public void run() {
			synchronized(this) {
				if(running) return;
				running = true;
			}
			try {
				while(true) {
					int packetNo = -1;
					synchronized(_senderThread) {
						if(_sendComplete) return;
						if(_unsent.size() == 0) packetNo = -1;
						else
							packetNo = _unsent.removeFirst();
					}
					if(packetNo == -1) {
						schedule(10*1000);
						return;
					} else {
						if(!innerRun(packetNo)) return;
					}
				}
			} finally {
				synchronized(this) {
					running = false;
				}
			}
		}
		
		public void schedule(long delay) {
			if(_sendComplete) return;
			_ticker.queueTimedJob(this, "BlockTransmitter block sender for "+_uid+" to "+_destination, delay, false, false);
		}

		/** @return True . */
		private boolean innerRun(int packetNo) {
			int totalPackets;
			try {
				_destination.sendThrottledMessage(DMT.createPacketTransmit(_uid, packetNo, _sentPackets, _prb.getPacket(packetNo)), _prb._packetSize, _ctr, SEND_TIMEOUT, false, null);
				totalPackets=_prb.getNumPackets();
			} catch (PeerRestartedException e) {
				Logger.normal(this, "Terminating send due to peer restart: "+e);
				synchronized(_senderThread) {
					_sendComplete = true;
				}
				return false;
			} catch (NotConnectedException e) {
				Logger.normal(this, "Terminating send: "+e);
				synchronized(_senderThread) {
					_sendComplete = true;
				}
				return false;
			} catch (AbortedException e) {
				Logger.normal(this, "Terminating send due to abort: "+e);
				synchronized(_senderThread) {
					_sendComplete = true;
				}
				return false;
			} catch (WaitedTooLongException e) {
				Logger.normal(this, "Waited too long to send packet, aborting");
				synchronized(_senderThread) {
					_sendComplete = true;
				}
				return false;
			} catch (SyncSendWaitedTooLongException e) {
				// Impossible
				synchronized(_senderThread) {
					_sendComplete = true;
				}
				Logger.error(this, "Impossible: Caught "+e, e);
				return false;
			}
			synchronized (_senderThread) {
				_sentPackets.setBit(packetNo, true);
				if(_unsent.size() == 0 && getNumSent() == totalPackets) {
					//No unsent packets, no unreceived packets
					sendAllSentNotification();
					timeAllSent = System.currentTimeMillis();
					if(logMINOR)
						Logger.minor(this, "Sent all blocks, none unsent");
					_senderThread.notifyAll();
				}
			}
			return true;
		}

		public int getPriority() {
			return NativeThread.HIGH_PRIORITY;
		}
		
	}
	
	public BlockTransmitter(MessageCore usm, Ticker ticker, PeerContext destination, long uid, PartiallyReceivedBlock source, ByteCounter ctr) {
		_ticker = ticker;
		_usm = usm;
		_destination = destination;
		_uid = uid;
		_prb = source;
		_ctr = ctr;
		if(_ctr == null) throw new NullPointerException();
		PACKET_SIZE = DMT.packetTransmitSize(_prb._packetSize, _prb._packets)
			+ destination.getOutgoingMangler().fullHeadersLengthOneMessage();
		try {
			_sentPackets = new BitArray(_prb.getNumPackets());
		} catch (AbortedException e) {
			Logger.error(this, "Aborted during setup");
			// Will throw on running
		}
		throttle = _destination.getThrottle();
	}

	public void abortSend(int reason, String desc) throws NotConnectedException {
		synchronized(this) {
			if(_sendComplete) return;
			_sendComplete = true;
		}
		sendAborted(reason, desc);
	}
	
	public void sendAborted(int reason, String desc) throws NotConnectedException {
		_usm.send(_destination, DMT.createSendAborted(_uid, reason, desc), _ctr);
	}
	
	private void sendAllSentNotification() {
		try {
			_usm.send(_destination, DMT.createAllSent(_uid), _ctr);
		} catch (NotConnectedException e) {
			Logger.normal(this, "disconnected for allSent()");
		}
	}
	
	public interface BlockTransmitterCompletion {
		
		public void blockTransferFinished(boolean success);
		
	}
	
	public class NullBlockTransmitterCompletion implements BlockTransmitterCompletion {

		public void blockTransferFinished(boolean success) {
			// Ignore
		}
		
	}
	
	private final NullBlockTransmitterCompletion nullCompletion = new NullBlockTransmitterCompletion();
	
	private PartiallyReceivedBlock.PacketReceivedListener myListener = null;
	
	private AsyncMessageFilterCallback notificationWaiter;
		
	public void sendAsync() {
		sendAsync(nullCompletion);
	}
	
	public void sendAsync(final BlockTransmitterCompletion callback) {

		final long startTime = System.currentTimeMillis();
		
		synchronized(_prb) {
			try {
				_unsent = _prb.addListener(myListener = new PartiallyReceivedBlock.PacketReceivedListener() {;

					public void packetReceived(int packetNo) {
						synchronized(_senderThread) {
							_unsent.addLast(packetNo);
							timeAllSent = -1;
							_sentPackets.setBit(packetNo, false);
							_senderThread.schedule(0);
						}
					}

					public void receiveAborted(int reason, String description) {
					}
				});
			} catch (AbortedException e) {
				Logger.normal(this, "AbortedException in BlockTransfer.send():"+e);
				try {
					String desc=_prb.getAbortDescription();
					if (desc.indexOf("Upstream")<0)
						desc="Upstream transfer failed: "+desc;
					sendAborted(_prb.getAbortReason(), desc);
				} catch (NotConnectedException gone) {
					//ignore
				}
				callback.blockTransferFinished(false);
				return;
			}
		}
		_senderThread.schedule(0);
		
		notificationWaiter = new SlowAsyncMessageFilterCallback() {

			private boolean completed = false;
			
			public void onMatched(Message msg) {
				boolean complete = false;
				synchronized(_senderThread) {
					complete = _sendComplete;
				}
				if(complete) {
					complete(false);
					return;
				}
				if(logMINOR) Logger.minor(this, "Got "+msg);
				if (msg.getSpec().equals(DMT.missingPacketNotification)) {
					LinkedList<Integer> missing = (LinkedList<Integer>) msg.getObject(DMT.MISSING);
					for (int packetNo :missing) {
						try {
							if (_prb.isReceived(packetNo)) {
								synchronized(_senderThread) {
									if (_unsent.contains(packetNo)) {
										Logger.minor(this, "already to transmit packet #"+packetNo);
									} else {
									_unsent.addFirst(packetNo);
									timeAllSent=-1;
									_sentPackets.setBit(packetNo, false);
									_senderThread.schedule(0);
									}
								}
							} else {
								// To be expected if the transfer is slow, since we send missingPacketNotification on a timeout.
								if(logMINOR)
									Logger.minor(this, "receiver requested block #"+packetNo+" which is not received");
							}
						} catch (AbortedException e) {
							Logger.normal(this, "AbortedException in BlockTransfer.send():"+e);
							try {
								String desc=_prb.getAbortDescription();
								if (desc.indexOf("Upstream")<0)
									desc="Upstream transfer failed: "+desc;
								sendAborted(_prb.getAbortReason(), desc);
							} catch (NotConnectedException gone) {
								//ignore
							}
							complete(false);
							return;
						}
					}
				} else if (msg.getSpec().equals(DMT.allReceived)) {
					long endTime = System.currentTimeMillis();
					if(logMINOR) {
						long transferTime = (endTime - startTime);
						synchronized(avgTimeTaken) {
							avgTimeTaken.report(transferTime);
							Logger.minor(this, "Block send took "+transferTime+" : "+avgTimeTaken);
						}
					}
					complete(true);
					return;
				} else {
					Logger.error(this, "Transmitter received unknown message type: "+msg.getSpec().getName());
				}
				try {
					waitNotification();
				} catch (DisconnectedException e) {
					onDisconnect(null);
				}
			}

			public boolean shouldTimeout() {
				return false;
			}

			public void onTimeout() {
				long now = System.currentTimeMillis();
				//SEND_TIMEOUT (one minute) after all packets have been transmitted, terminate the send.
				try {
					if((timeAllSent > 0) && ((now - timeAllSent) > SEND_TIMEOUT) &&
							(getNumSent() == _prb.getNumPackets())) {
						String timeString=TimeUtil.formatTime((now - timeAllSent), 2, true);
						Logger.error(this, "Terminating send "+_uid+" to "+_destination+" from "+_destination.getSocketHandler()+" as we haven't heard from receiver in "+timeString+ '.');
						sendAborted(RetrievalException.RECEIVER_DIED, "Haven't heard from you (receiver) in "+timeString);
						complete(false);
					} else {
						if(logMINOR) Logger.minor(this, "Ignoring timeout: timeAllSent="+timeAllSent+" ("+(System.currentTimeMillis() - timeAllSent)+"), getNumSent="+getNumSent()+ '/' +_prb.getNumPackets());
						try {
							waitNotification();
						} catch (DisconnectedException e) {
							onDisconnect(null);
						}
					}
				} catch (AbortedException e) {
					Logger.normal(this, "AbortedException in BlockTransfer.send():"+e);
					try {
						String desc=_prb.getAbortDescription();
						if (desc.indexOf("Upstream")<0)
							desc="Upstream transfer failed: "+desc;
						sendAborted(_prb.getAbortReason(), desc);
					} catch (NotConnectedException gone) {
						//ignore
					}
					complete(false);
				} catch (NotConnectedException e) {
					onDisconnect(null);
				}
			}

			public void onDisconnect(PeerContext ctx) {
				//most likely from sending an abort()
				Logger.normal(this, "Not connected waiting for response in sender "+this);
				complete(false);
			}

			public void onRestarted(PeerContext ctx) {
				//most likely from sending an abort()
				Logger.normal(this, "Sender restarted waiting for response in sender "+this);
				complete(false);
			}

			private void complete(boolean b) {
				synchronized(this) {
					if(completed) return;
					completed = true;
				}
				synchronized(_senderThread) {
					_sendComplete = true;
					_senderThread.notifyAll();
				}
				if (myListener!=null)
					_prb.removeListener(myListener);
				callback.blockTransferFinished(b);
			}

			public int getPriority() {
				return NativeThread.NORM_PRIORITY;
			}
			
		};
		
		try {
			waitNotification();
		} catch (DisconnectedException e) {
			synchronized(_senderThread) {
				_sendComplete = true;
				_senderThread.notifyAll();
			}
			callback.blockTransferFinished(false);
		}
		
	}
	
	protected void waitNotification() throws DisconnectedException {
		MessageFilter mfMissingPacketNotification = MessageFilter.create().setType(DMT.missingPacketNotification).setField(DMT.UID, _uid).setTimeout(SEND_TIMEOUT).setSource(_destination);
		MessageFilter mfAllReceived = MessageFilter.create().setType(DMT.allReceived).setField(DMT.UID, _uid).setTimeout(SEND_TIMEOUT).setSource(_destination);
		MessageFilter mf = mfMissingPacketNotification.or(mfAllReceived);
		_usm.addAsyncFilter(mf, notificationWaiter, _ctr); // FIXME use _ctr for byte counting!
	}

	private static MedianMeanRunningAverage avgTimeTaken = new MedianMeanRunningAverage();
	
	public int getNumSent() {
		int ret = 0;
		for (int x=0; x<_sentPackets.getSize(); x++) {
			if (_sentPackets.bitAt(x)) {
				ret++;
			}
		}
		return ret;
	}

	public PeerContext getDestination() {
		return _destination;
	}
	
	@Override
	public String toString() {
		return "BlockTransmitter for "+_uid+" to "+_destination.shortToString();
	}
}
