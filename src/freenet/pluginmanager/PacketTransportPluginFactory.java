package freenet.pluginmanager;

import java.net.UnknownHostException;
import java.util.Set;

import freenet.io.comm.IOStatisticCollector;
import freenet.node.TransportManager.TransportMode;

/**
 * A plugin must implement this interface and must register an instance with the transportManager.
 * It will be used when needed.
 * @author chetan
 *
 */
public interface PacketTransportPluginFactory {
	
	/**
	 * The plugin instance and this method must return the same value. 
	 * Else we throw an an exception and call invalidTransportCallback method.
	 * @return The transport name of the plugin.
	 */
	public String getTransportName();
	
	/**
	 * Get a list of operative modes
	 */
	public Set<TransportMode> getOperationalModes();
	
	/**
	 * Method to initialise and start the plugin. It must create its own thread for listening.
     * FredPlugin should help with that.
	 * @param pluginAddress If plugin is configurable then the pluginAddress is used to bind
	 * @param collector If plugin supports sharing statistics, then the object will be used
	 * @param startTime 
	 * @return The plugin that can be be used.
	 */
	public PacketTransportPlugin makeTransportPlugin(TransportMode transportMode, PluginAddress pluginAddress, IOStatisticCollector collector, long startupTime) throws TransportInitException;
	
	/**
	 * The plugin instance was faulty. We leave it to the plugin to fix the issue and register again if it wants to.
	 * The transport is not being used, even though the plugin is loaded.
	 */
	public void invalidTransportCallback(FaultyTransportPluginException e);
	
	/**
	 * This method must convert an address from the noderef to the type PluginAddress.
	 * 
	 * @param address
	 * @return
	 * @throws UnknownHostException
	 */
	public PluginAddress toPluginAddress(String address) throws MalformedPluginAddressException;

}
