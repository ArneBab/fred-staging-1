package freenet.node;

import java.util.Random;

import freenet.io.comm.Message;
import freenet.io.comm.Peer.LocalAddressException;
import freenet.io.comm.PeerContext;

/** Base interface for PeerNode, for purposes of the transport layer. Will be overridden
 * for unit tests to simplify testing. 
 * @author toad
 */
interface BasePeerNode extends PeerContext {

	SessionKey getCurrentKeyTracker();

	SessionKey getPreviousKeyTracker();

	SessionKey getUnverifiedKeyTracker();

	void receivedPacket(boolean dontLog, boolean dataPacket);

	void verified(SessionKey s);

	void startRekeying();

	void maybeRekey();

	void reportIncomingPacket(byte[] buf, int offset, int length, long now);

	void reportOutgoingPacket(byte[] data, int offset, int length, long now);
	
	void processDecryptedMessage(byte[] data, int offset, int length, int overhead);

	void reportPing(long rt);

	double averagePingTime();

	void wakeUpSender();

	int getMaxPacketSize();

	PeerMessageQueue getMessageQueue();

	boolean shouldPadDataPackets();

	void sendEncryptedPacket(byte[] data) throws LocalAddressException;

	void sentPacket();

	boolean shouldThrottle();

	void sentThrottledBytes(int length);

	void onNotificationOnlyPacketSent(int length);

	void resentBytes(int bytesToResend);

	Random paddingGen();

	void handleMessage(Message msg);

	/** Make a load stats message.
	 * @param realtime True for the realtime load stats, false for the bulk load stats.
	 * @param highPriority If true, boost the priority so it gets sent fast.
	 */
	MessageItem makeLoadStats(boolean realtime, boolean highPriority);
	
	boolean grabSendLoadStatsASAP(boolean realtime);

	/** Set the load stats to be sent asap. E.g. if we grabbed it and can't actually 
	 * execute the send for some reason. */
	void setSendLoadStatsASAP(boolean realtime);

}
