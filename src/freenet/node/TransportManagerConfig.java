package freenet.node;

import java.util.HashMap;

import freenet.node.TransportManager.TransportMode;
import freenet.pluginmanager.TransportPluginConfigurationException;
import freenet.pluginmanager.TransportPluginException;

/**
 * TransportManagerConfig keeps a track of all addresses and ports fred binds to.
 * For now its sole purpose is to track transport plugin bindings in the addressMap field.
 * @author chetan
 *
 */
public class TransportManagerConfig {
	
	public final TransportMode transportMode;
	
	public HashMap<String, String> addressMap = new HashMap<String, String> ();
	
	public HashMap<String, Boolean> enabledTransports = new HashMap<String, Boolean> ();
	
	public TransportManagerConfig(TransportMode transportMode) {
		this.transportMode = transportMode;
	}
	
	public void addTransportAddress(String transportName, String pluginAddress){
		addressMap.put(transportName, pluginAddress);
	}
	
	public String getTransportAddress(String transportName) throws TransportPluginConfigurationException{
		return addressMap.get(transportName);
	}

}
