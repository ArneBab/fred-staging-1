package freenet.node;

import freenet.io.comm.Peer;
import freenet.pluginmanager.StreamTransportPlugin;

public class PeerStreamConnection extends PeerConnection {
	
	/** The transport this connection is using. */
	protected StreamTransportPlugin transportPlugin;
	
	/** Mangler to handle connections for different transports */
	protected OutgoingStreamMangler streamMangler;
	
	/** The object that runs this connection. Analogous to NewPacketFormat and PacketSender */
	protected StreamConnectionFormat streamConnection;
	
	public PeerStreamConnection(PeerNode pn, StreamTransportPlugin transportPlugin, OutgoingStreamMangler streamMangler, StreamConnectionFormat streamConnection, Peer detectedPeer){
		super(transportPlugin.transportName, pn);
		this.transportPlugin = transportPlugin;
		this.streamMangler = streamMangler;
		this.streamConnection = streamConnection;
		this.detectedPeer = detectedPeer;
	}
	

}
