package freenet.clients.http.ajaxpush;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.RedirectException;
import freenet.clients.http.SimpleToadletServer;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.updateableelements.BaseUpdateableElement;
import freenet.clients.http.updateableelements.UpdaterConstants;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class PushQueueFetchToadlet extends Toadlet {

	private static volatile boolean	logMINOR;

	static {
		Logger.registerClass(PushQueueFetchToadlet.class);
	}

	public PushQueueFetchToadlet(HighLevelSimpleClient client) {
		super(client);
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if(logMINOR) Logger.minor(this, "Queueing a fetch...");
		String requestId = req.getParam("requestId");
		String elementId = req.getParam("elementId");
		elementId = elementId.replace(" ", "+");// This is needed, because BASE64 has '+', but it is a HTML escape for ' '
		FreenetURI key = null;
		try{
			key = new FreenetURI(req.getParam("key"));
		} catch(MalformedURLException e) {
			Logger.error(this, "Invalid key scheduled to be fetched for element "+elementId, e);
			throw e;
		}
		String mimeType = null;
		if(req.isParameterSet("type")) mimeType = req.getParam("type");
		if(logMINOR) Logger.minor(this, "Retrieving key: "+key+"Of type"+mimeType+" For element: "+elementId+" In request: "+requestId);
		((SimpleToadletServer) ctx.getContainer()).pushDataManager.setFinalizedKey(requestId, elementId, key, mimeType);
		BaseUpdateableElement node = ((SimpleToadletServer) ctx.getContainer()).pushDataManager.getRenderedElement(requestId, elementId);

		writeHTMLReply(ctx, 200, "OK", UpdaterConstants.SUCCESS + ":" + Base64.encodeStandard(node.getUpdaterType().getBytes()) + ":" + Base64.encodeStandard(node.generateChildren().getBytes()));
	}

	@Override
	public String path() {
		return UpdaterConstants.queuePath;
	}

}
