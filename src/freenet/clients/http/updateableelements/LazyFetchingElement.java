package freenet.clients.http.updateableelements;

import freenet.keys.FreenetURI;

public interface LazyFetchingElement {

	void finalizeTarget(FreenetURI key);
}
