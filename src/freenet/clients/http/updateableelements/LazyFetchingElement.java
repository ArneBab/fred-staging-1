package freenet.clients.http.updateableelements;

import freenet.keys.FreenetURI;

/** Represents pushing elements which do not initially know which key needs to be
 * fetched. This is created by the <code>PushingTagReplacerCallback</code>,
 * as with other elements. Later, a call made to
 * {@link finalizeTarget(FreenetURI key)} sets the key which shall be fetched, and
 * starts the fetch.
 */
public interface LazyFetchingElement {

	/**Sets the key which will be fetched by the instance.*/
	void finalizeTarget(FreenetURI key, String type);
}
