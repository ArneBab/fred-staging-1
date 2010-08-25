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

import java.util.HashMap;
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
import freenet.io.comm.RetrievalException;
import freenet.io.comm.SlowAsyncMessageFilterCallback;
import freenet.node.Ticker;
import freenet.support.BitArray;
import freenet.support.Buffer;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.io.NativeThread;
import freenet.support.math.MedianMeanRunningAverage;

/**
 * IMPORTANT: Incoming block transfers cannot be cancelled. They can timeout, if there is
 * more than RECEIPT_TIMEOUT * MAX_CONSECUTIVE_MISSING_PACKET_REPORTS (currently 2 
 * minutes) between received blocks, but there is no means (no DMT message) to tell a 
 * sender that we are no longer interested in this block. This is actually an important
 * security feature. If we had implemented a receiveAborted message, then either:
 * 
 * a) Receive cancels propagate: Attackers can probe for data and then cancel the 
 * transfer, enabling censorship attacks i.e. find out where it is without propagating it, 
 * and then take action against that location. IMHO this is not as strong as it sounds 
 * because on darknet it is worthless and on opennet you are relying on path folding to 
 * get the next hop, which only happens after a successful transfer anyway. The other 
 * issue is inserts, e.g. accept an insert and then return RNF, and then send a cancel, 
 * a naive implementation of receive propagation would cancel the whole fabric including
 * all the transfers to other nodes. Finally, there is still a bandwidth multiplier even
 * if cancels propagate, because nodes closer to the traffic source will have transferred
 * more blocks, unless cancels are ignored by all but the origin node (which is bad for 
 * security as it gives us a timing).
 * OR
 * b) Receive cancels don't propagate: Attackers can start transfers downstream and then
 * cancel the receive on their side, making for a nice little DoS attack. However this is
 * limited by load limiting - the victim still has the transfers running, so it won't 
 * accept any more, so the attacker needs to e.g. constantly connect to new nodes (e.g. 
 * via announcement) to make much of an impact.
 * 
 * FIXME RECONSIDER! Given that we don't do path folding until after a successful 
 * transfer we probably should allow receive cancels and let them propagate. This might
 * allow a lesser DoS by just repeatedly requesting keys that a particular node has and
 * then cancelling, but on the other hand we could punish nodes if they start and then
 * cancel too many times. And there might be some interesting applications e.g. find all
 * the blocks in a file and only transfer if we have enough to reconstruct. On the other
 * hand, even here we have a bandwidth amplifier effect - if a receive cancel takes effect 
 * immediately, and is propagated, downstream will have moved several blocks. Better to 
 * have explicit support for bundles and have them not start transferring at all until we
 * have decided we have enough data and we really want it all?
 * 
 * FIXME Unfortunately the above is not true. sendAborted is used in *both* directions.
 * We don't cancel the prb, but we do stop sending in BlockTransmitter if we receive one.
 * But it still doesn't work even as intended: RequestSender.killTurtle() calls prb.abort,
 * but BlockReceiver assumes it is always responsible for aborts, so doesn't do anything
 * with that information. So when we cancel a turtle, we don't send a sendAborted, we just
 * log a "Caught in receive - probably a bug as receive sets it".
 * 
 * Hmmm... the old code, before the threadless refactoring, *did* send a sendAborted in 
 * the finally block.
 * 
 * @author ian
 */
public class BlockReceiver implements AsyncMessageFilterCallback {

	/*
	 * RECEIPT_TIMEOUT must be less than 60 seconds because BlockTransmitter times out after not
	 * hearing from us in 60 seconds. Without contact from the transmitter, we will try sending
	 * at most MAX_CONSECUTIVE_MISSING_PACKET_REPORTS every RECEIPT_TIMEOUT to recover.
	 */
	public static final int RECEIPT_TIMEOUT = 30000;
	// TODO: This should be proportional to the calculated round-trip-time, not a constant
	public static final int MAX_ROUND_TRIP_TIME = RECEIPT_TIMEOUT;
	public static final int MAX_CONSECUTIVE_MISSING_PACKET_REPORTS = 4;
	public static final int MAX_SEND_INTERVAL = 500;
	public static final int CLEANUP_TIMEOUT = 5000;
	// After 15 seconds, the receive is overdue and will cause backoff.
	public static final int TOO_LONG_TIMEOUT = 15000;
	PartiallyReceivedBlock _prb;
	PeerContext _sender;
	long _uid;
	MessageCore _usm;
	/** packet : Integer -> reportTime : Long * */
	HashMap<Integer, Long> _recentlyReportedMissingPackets = new HashMap<Integer, Long>();
	ByteCounter _ctr;
	Ticker _ticker;
	boolean sentAborted;
	private MessageFilter discardFilter;
	private long discardEndTime;
	private boolean senderAborted;
//	private final boolean _doTooLong;

	boolean logMINOR=Logger.shouldLog(LogLevel.MINOR, this);
	
	public BlockReceiver(MessageCore usm, PeerContext sender, long uid, PartiallyReceivedBlock prb, ByteCounter ctr, Ticker ticker, boolean doTooLong) {
		_sender = sender;
		_prb = prb;
		_uid = uid;
		_usm = usm;
		_ctr = ctr;
		_ticker = ticker;
//		_doTooLong = doTooLong;
	}

	public void sendAborted(int reason, String desc) throws NotConnectedException {
		_usm.send(_sender, DMT.createSendAborted(_uid, reason, desc), _ctr);
		sentAborted=true;
	}
	
	public interface BlockReceiverCompletion {
		
		public void blockReceived(byte[] buf);
		
		public void blockReceiveFailed(RetrievalException e);
		
	}
	
	private int consecutiveMissingPacketReports = 0;
	
	private BlockReceiverCompletion callback;
	
	private long startTime;
	
	private AsyncMessageFilterCallback notificationWaiter = new SlowAsyncMessageFilterCallback() {

		private boolean completed;
		
		public void onMatched(Message m1) {
            if(logMINOR)
            	Logger.minor(this, "Received "+m1);
            if ((m1 != null) && m1.getSpec().equals(DMT.sendAborted)) {
				String desc=m1.getString(DMT.DESCRIPTION);
				if (desc.indexOf("Upstream")<0)
					desc="Upstream transmit error: "+desc;
				_prb.abort(m1.getInt(DMT.REASON), desc);
				synchronized(BlockReceiver.this) {
					senderAborted = true;
				}
				complete(new RetrievalException(m1.getInt(DMT.REASON), desc));
				return;
			}
			if ((m1 != null) && (m1.getSpec().equals(DMT.packetTransmit))) {
				consecutiveMissingPacketReports = 0;
				// packetTransmit received
				int packetNo = m1.getInt(DMT.PACKET_NO);
				BitArray sent = (BitArray) m1.getObject(DMT.SENT);
				Buffer data = (Buffer) m1.getObject(DMT.DATA);
				LinkedList<Integer> missing = new LinkedList<Integer>();
				try {
					_prb.addPacket(packetNo, data);
					// Remove it from rrmp if its in there
					_recentlyReportedMissingPackets.remove(packetNo);
					// Check that we have what the sender thinks we have
					for (int x = 0; x < sent.getSize(); x++) {
						if (sent.bitAt(x) && !_prb.isReceived(x)) {
							// Sender thinks we have a block which we don't, but have we already
							// re-requested it recently?
							Long resendTime = _recentlyReportedMissingPackets.get(x);
							if ((resendTime == null) || (System.currentTimeMillis() > resendTime.longValue())) {
								// Make a note of the earliest time we should resend this, based on the number of other
								// packets we are already waiting for
								long resendWait = System.currentTimeMillis()
								+ (MAX_ROUND_TRIP_TIME + (_recentlyReportedMissingPackets.size() * MAX_SEND_INTERVAL));
								_recentlyReportedMissingPackets.put(x, resendWait);
								missing.add(x);
							}
						}
					}
				} catch (AbortedException e) {
					// We didn't cause it?!
					Logger.error(this, "Caught in receive - probably a bug as receive sets it: "+e, e);
					complete(new RetrievalException(RetrievalException.UNKNOWN, "Aborted?"));
					return;
				}
				if(logMINOR)
					Logger.minor(this, "Missing: "+missing.size());
				if (missing.size() > 0) {
					Message mn = DMT.createMissingPacketNotification(_uid, missing);
					try {
						_usm.send(_sender, mn, _ctr);
					} catch (NotConnectedException e) {
						onDisconnect(null);
						return;
					}
					consecutiveMissingPacketReports++;
					if (missing.size() > 50) {
						Logger.normal(this, "Excessive packet loss : "+mn);
					}
				}
			}
			if(m1.getSpec().equals(DMT.allSent)) {
				onTimeout();
				return;
			}
			try {
				if(_prb.allReceived()) {
					_usm.send(_sender, DMT.createAllReceived(_uid), _ctr);
					discardEndTime=System.currentTimeMillis()+CLEANUP_TIMEOUT;
					discardFilter=relevantMessages();
					maybeResetDiscardFilter();
					long endTime = System.currentTimeMillis();
					long transferTime = (endTime - startTime);
					if(logMINOR) {
						synchronized(avgTimeTaken) {
							avgTimeTaken.report(transferTime);
							Logger.minor(this, "Block transfer took "+transferTime+"ms - average is "+avgTimeTaken);
						}
					}
					complete(_prb.getBlock());
				}
			} catch (AbortedException e1) {
				// We didn't cause it?!
				Logger.error(this, "Caught in receive - probably a bug as receive sets it: "+e1, e1);
				complete(new RetrievalException(RetrievalException.UNKNOWN, "Aborted?"));
			} catch (NotConnectedException e1) {
				complete(new RetrievalException(RetrievalException.SENDER_DISCONNECTED));
			}
			try {
				waitNotification();
			} catch (DisconnectedException e) {
				onDisconnect(null);
				return;
			}
		}

		private void complete(RetrievalException retrievalException) {
			synchronized(this) {
				if(completed) {
					if(logMINOR) Logger.minor(this, "Already completed");
					return;
				}
				completed = true;
			}
			_prb.abort(retrievalException.getReason(), retrievalException.toString());
			callback.blockReceiveFailed(retrievalException);
		}

		private void complete(byte[] ret) {
			synchronized(this) {
				if(completed) {
					if(logMINOR) Logger.minor(this, "Already completed");
					return;
				}
				completed = true;
			}
			callback.blockReceived(ret);
		}

		public boolean shouldTimeout() {
			return false;
		}

		public void onTimeout() {
			synchronized(this) {
				if(completed) return;
			}
			LinkedList<Integer> missing = new LinkedList<Integer>();
			try {
				if(_prb.allReceived()) return;
				if (consecutiveMissingPacketReports >= MAX_CONSECUTIVE_MISSING_PACKET_REPORTS) {
					_prb.abort(RetrievalException.SENDER_DIED, "Sender unresponsive to resend requests");
					complete(new RetrievalException(RetrievalException.SENDER_DIED,
							"Sender unresponsive to resend requests"));
					return;
				}
				for (int x = 0; x < _prb.getNumPackets(); x++) {
					if (!_prb.isReceived(x)) {
						missing.add(x);
					}
				}
			} catch (AbortedException e) {
				// We didn't cause it?!
				Logger.error(this, "Caught in receive - probably a bug as receive sets it: "+e, e);
				complete(new RetrievalException(RetrievalException.UNKNOWN, "Aborted?"));
				return;
			}
			Message mn = DMT.createMissingPacketNotification(_uid, missing);
			try {
				_usm.send(_sender, mn, _ctr);
			} catch (NotConnectedException e) {
				onDisconnect(null);
				return;
			}
			consecutiveMissingPacketReports++;
			if (missing.size() > 50) {
				Logger.normal(this, "Sending large missingPacketNotification due to packet receiver timeout after "+RECEIPT_TIMEOUT+"ms");
			}
			try {
				waitNotification();
			} catch (DisconnectedException e) {
				onDisconnect(null);
				return;
			}
		}

		public void onDisconnect(PeerContext ctx) {
			complete(new RetrievalException(RetrievalException.SENDER_DISCONNECTED));
		}

		public void onRestarted(PeerContext ctx) {
			complete(new RetrievalException(RetrievalException.SENDER_DISCONNECTED));
		}

		public int getPriority() {
			return NativeThread.NORM_PRIORITY;
		}
		
	};
	
	private void waitNotification() throws DisconnectedException {
		_usm.addAsyncFilter(relevantMessages(), notificationWaiter, _ctr);
	}
	
	private MessageFilter relevantMessages() {
		MessageFilter mfPacketTransmit = MessageFilter.create().setTimeout(RECEIPT_TIMEOUT).setType(DMT.packetTransmit).setField(DMT.UID, _uid).setSource(_sender);
		MessageFilter mfAllSent = MessageFilter.create().setTimeout(RECEIPT_TIMEOUT).setType(DMT.allSent).setField(DMT.UID, _uid).setSource(_sender);
		MessageFilter mfSendAborted = MessageFilter.create().setTimeout(RECEIPT_TIMEOUT).setType(DMT.sendAborted).setField(DMT.UID, _uid).setSource(_sender);
		return mfPacketTransmit.or(mfAllSent.or(mfSendAborted));
	}

	public void receive(BlockReceiverCompletion callback) {
		startTime = System.currentTimeMillis();
		this.callback = callback;
		try {
			waitNotification();
		} catch (DisconnectedException e) {
			RetrievalException retrievalException = new RetrievalException(RetrievalException.SENDER_DISCONNECTED);
			_prb.abort(retrievalException.getReason(), retrievalException.toString());
			callback.blockReceiveFailed(retrievalException);
		}
	}
	
	private static MedianMeanRunningAverage avgTimeTaken = new MedianMeanRunningAverage();
	
	private void maybeResetDiscardFilter() {
		long timeleft=discardEndTime-System.currentTimeMillis();
		if (timeleft>0) {
			try {
				discardFilter.setTimeout((int)timeleft);
				_usm.addAsyncFilter(discardFilter, this, _ctr);
			} catch (DisconnectedException e) {
				//ignore
			}
		}
	}
	
	/**
	 * Used to discard leftover messages, usually just packetTransmit and allSent.
	 * allSent, is quite common, as the receive() routine usually quits immeadiately on receiving all packets.
	 * packetTransmit is less common, when receive() requested what it thought was a missing packet, only reordered.
	 */
	public void onMatched(Message m) {
		if (logMINOR)
			Logger.minor(this, "discarding message post-receive: "+m);
		maybeResetDiscardFilter();												   
	}
	
	public boolean shouldTimeout() {
		return false;
	}
	
	public void onTimeout() {
		//ignore
	}

	public void onDisconnect(PeerContext ctx) {
		// Ignore
	}

	public void onRestarted(PeerContext ctx) {
		// Ignore
	}

	public synchronized boolean senderAborted() {
		return senderAborted;
	}
}
