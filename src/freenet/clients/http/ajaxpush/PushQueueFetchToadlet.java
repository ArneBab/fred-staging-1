package freenet.clients.http.ajaxpush;

import java.io.IOException;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.RedirectException;
import freenet.clients.http.SimpleToadletServer;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.updateableelements.UpdaterConstants;
import freenet.keys.FreenetURI;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class PushQueueFetchToadlet extends Toadlet {

	private static volatile boolean	logMINOR;

	static {
		Logger.registerClass(PushQueueFetchToadlet.class);
	}

	protected PushQueueFetchToadlet(HighLevelSimpleClient client) {
		super(client);
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		String requestId = req.getParam("requestId");
		String elementId = req.getParam("elementId");
		FreenetURI key = new FreenetURI(req.getParam("key"));
		if (logMINOR) Logger.minor(this, "Retrieving key: "+key+" For element: "+elementId+" In request: "+requestId);
		((SimpleToadletServer) ctx.getContainer()).pushDataManager.setFinalizedKey(requestId, elementId, key);

		writeTemporaryRedirect(ctx, null, "/"+key);
	}

	@Override
	public String path() {
		return UpdaterConstants.queuePath;
	}

}
