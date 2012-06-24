package freenet.io.comm;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import freenet.node.Node;
import freenet.node.TransportManager.TransportMode;
import freenet.pluginmanager.FaultyTransportPluginException;
import freenet.pluginmanager.MalformedPluginAddressException;
import freenet.pluginmanager.PacketTransportPlugin;
import freenet.pluginmanager.PacketTransportPluginFactory;
import freenet.pluginmanager.PluginAddress;
import freenet.pluginmanager.TransportInitException;

public class UDPSocketPluginFactory implements PacketTransportPluginFactory {
	
	private final Node node;
	
	public UDPSocketPluginFactory(Node node){
		this.node = node;
	}
	
	/**
	 * Convert a simple string address into a plugin address
	 */
	@Override
	public PluginAddress toPluginAddress(String address) throws MalformedPluginAddressException {
		PluginAddress pluginAddress;
		int offset = address.lastIndexOf(':');
		if(offset < 0) 
			throw new MalformedPluginAddressException("Could not construct object");
		String stringAddress = address.substring(0, offset);
		InetAddress inetAddress;
		int portNumber;
		try {
			inetAddress = InetAddress.getByName(stringAddress);
			portNumber = Integer.parseInt(address.substring(offset + 1));
            if(portNumber < 0 || portNumber > 65535)
            	throw new MalformedPluginAddressException("Could not construct object");
        } catch (NumberFormatException e) {
            throw new MalformedPluginAddressException("Could not construct object");
        } catch (UnknownHostException e) {
        	throw new MalformedPluginAddressException("Could not construct object");
		}
		
		pluginAddress = new PluginAddressImpl(inetAddress, portNumber);
		return pluginAddress;
	}

	@Override
	public String getTransportName() {
		return Node.defaultPacketTransportName;
	}

	@Override
	public PacketTransportPlugin makeTransportPlugin(TransportMode transportMode, PluginAddress pluginAddress, IOStatisticCollector collector, long startupTime) throws TransportInitException {
		PluginAddressImpl address = (PluginAddressImpl) pluginAddress;
		String title = "UDP " + (transportMode == TransportMode.opennet ? "Opennet " : "Darknet ") + "port " + address.portNumber;
		try {
			return new UdpSocketHandler(transportMode, address.portNumber, address.inetAddress, node, startupTime, title, node.collector);
		} catch (SocketException e) {
			return null;	
		}
	}

	@Override
	public void invalidTransportCallback(FaultyTransportPluginException e) {
		//Do nothing
	}

	@Override
	public Set<TransportMode> getOperationalModes() {
		HashSet<TransportMode> h = new HashSet<TransportMode>();
		h.add(TransportMode.darknet);
		h.add(TransportMode.opennet);
		return h;
	}

}

/**
 * Simple implementation of PluginAddress for the TransportPlugin type
 * @author chetan
 *
 */
class PluginAddressImpl implements PluginAddress{
	InetAddress inetAddress;
	int portNumber;
	
	PluginAddressImpl(InetAddress inetAddress, int portNumber){
		this.inetAddress = inetAddress;
		this.portNumber = portNumber;
	}
	
	@Override
	public String toStringAddress() {
		return (inetAddress.getHostAddress() + ":" + portNumber);
	}
}