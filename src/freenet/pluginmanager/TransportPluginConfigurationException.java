package freenet.pluginmanager;

/**
 * Thrown when a configuration for a plugin was absent in the TransportManagerConfig and was tried to be accessed.
 * @author chetan
 *
 */
public class TransportPluginConfigurationException extends Exception{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final String errorMessage;
	
	public TransportPluginConfigurationException(String errorMessage){
		super(errorMessage);
		this.errorMessage = errorMessage;
	}
	
}
