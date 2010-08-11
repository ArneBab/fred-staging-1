package freenet.clients.http.updateableelements;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyToadlet;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;

/** A pushed video or audio element.
 * <p>Initially this class will present a placeholder img tag. When data is requested,
 * without a key being finalized, the original content will be pushed. The key that
 * will be fetched by this element is specified by a call to
 * <code>PushQueueFetchToadlet</code>. After this has happened, progress updates will
 * be provided by this class until fetch completion.</p>
 * <p>All of this is necessary because the browser will have multiple multimedia
 * sources it may choose to download from. These may or may not be in a format
 * compatible with the browser's rendering systems. Furthermore, multimedia files can
 * be very large. As browsers tend to try to download the first couple of frames, we
 * must take care that the fetch is delayed until expressly requested by the user.</p>
 * <p>This class supports <code>&lt;audio&gt;</code> and <code>&lt;video&gt;</code></p>
 * */
public class MultimediaElement extends MediaElement implements LazyFetchingElement {

	static {
		Logger.registerClass(MultimediaElement.class);
	}

	private static volatile boolean logMINOR;
	String tagName;
	HTMLNode originalNode;
	LinkedList<FreenetURI> keys = new LinkedList<FreenetURI>();
	String mimeType;

	public MultimediaElement(FProxyFetchTracker tracker, ToadletContext ctx,
			HTMLNode flowElement) {
		super(tracker, null, Long.MAX_VALUE, ctx, false);
		tagName = flowElement.getFirstTag();
		originalNode = flowElement;
		originalNode.setContent("");
		LinkedList<HTMLNode> nodes = new LinkedList<HTMLNode>(originalNode.getChildren());
		nodes.add(originalNode);
		for(HTMLNode node : nodes) {
			if(logMINOR) Logger.minor(this, "Processing potential source element: "+node.generate());
			String src = node.getAttribute("src");
			if(src != null) {
				String character = src.contains("?") ? "&" : "?";
				String srcWithQuery = src+character+"noprogress&max-size="+Long.MAX_VALUE;
				node.addAttribute("src", srcWithQuery);
				try {
					if (src.startsWith("/")) src = src.substring(1);
					if(src.contains("?")) src = src.substring(0, src.indexOf("?"));
					keys.add(new FreenetURI(src));
				} catch (MalformedURLException e) {
					Logger.error(this, e.toString(), e);
				}
			}
			else break;
		}
		if(logMINOR) Logger.minor(this, "Multimedia element populated: \""+originalNode.generateChildren()+"\" Keys: "+keys.toString());

		String sizePart = new String();
		Map<String, String> attrs = originalNode.getAttributes();
		HashMap<String, String> newAttrs = new HashMap<String, String>();
		if(attrs.containsKey("width") && attrs.containsKey("height")) {
			if(attrs.containsKey("width")) newAttrs.put("width", attrs.get("width"));
			if(attrs.containsKey("height")) newAttrs.put("height", attrs.get("height"));
		} else {
			//These defaults come from the HTML5 spec.
			newAttrs.put("width", "300");
			newAttrs.put("height", "150");
		}
		sizePart = "&width=" + newAttrs.get("width") + "&height=" + newAttrs.get("height");
		newAttrs.put("src", "/imagecreator/?text=+"+FProxyToadlet.l10n("startmultimedia")+sizePart);

		HTMLNode node = new HTMLNode("span", "class", "jsonly MultimediaElement unFinalized");
		addChild(node);
		node.addChild(new HTMLNode("img", newAttrs));
		addChild("noscript").addChild(originalNode);
		init(false);
		fetchListener.onEvent();
	}

	@Override
	protected void subsequentStateDisplay(FProxyFetchResult result,
			HTMLNode node) {
		int total = result.requiredBlocks;
		int fetchedPercent = (int) (result.fetchedBlocks / (double) total * 100);
		String sizePart = new String();
		Map<String, String> attrs = originalNode.getAttributes();
		HashMap<String, String> newAttrs = new HashMap<String, String>();
		if(attrs.containsKey("width") && attrs.containsKey("height")) {
			if(attrs.containsKey("width")) newAttrs.put("width", attrs.get("width"));
			if(attrs.containsKey("height")) newAttrs.put("height", attrs.get("height"));
		} else {
			newAttrs.put("width", "300");
			newAttrs.put("height", "150");
		}
		sizePart = "&width=" + newAttrs.get("width") + "&height=" + newAttrs.get("height");
		newAttrs.put("src", "/imagecreator/?text="+fetchedPercent+"%25"+sizePart);

		node.addChild(new HTMLNode("img", newAttrs));
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "fetchedBlocks", String.valueOf(result.fetchedBlocks) });
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "requiredBlocks", String.valueOf(result.requiredBlocks) });
	}

	@Override
	public void updateState() {
		if(key == null) {
			children.clear();
			HTMLNode node = new HTMLNode("span", "class", "jsonly MultimediaElement");
			node.addChild(originalNode);
			addChild(node);
		}
		else super.updateState();
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.MULTIMEDIA_ELEMENT_UPDATER;
	}

	@Override
	protected void addCompleteElement(HTMLNode node) {
		String src = key.toString();
		if(!src.startsWith("/")) src = "/"+src;
		String character = src.contains("?") ? "&" : "?";
		String extras = character+"noprogress&max-size="+Long.MAX_VALUE;
		if(mimeType != null) extras += "&type="+mimeType;
		src += extras;
		HashMap<String, String> attrs = new HashMap<String, String>(originalNode.getAttributes());
		attrs.put("src", src);
		node.addChild(new HTMLNode(tagName, attrs, ""));
	}

	@Override
	public String toString() {
		return "MultimediaElement[key:" + key + ",maxSize:" + maxSize + ",updaterId:" + getUpdaterId(null) + "]";
	}

	@Override
	public String getUpdaterId(String requestId) {
		StringBuffer buf = new StringBuffer();
		for(FreenetURI destination : keys) {
			buf.append(destination.toString()+",");
		}
		return Base64.encodeStandard(("element[URIs:" + buf.toString() + ",random:" + randomNumber + "]").getBytes());
		}

	public void finalizeTarget(FreenetURI key, String type) {
		if(logMINOR) Logger.minor(this, "Finalizing element with key "+key);
		this.key = key;
		this.mimeType = type;
		startFetch();
	}
}
