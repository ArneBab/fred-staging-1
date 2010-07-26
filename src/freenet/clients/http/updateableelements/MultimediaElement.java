package freenet.clients.http.updateableelements;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyToadlet;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.HTMLNode;

public class MultimediaElement extends MediaElement implements LazyFetchingElement {

	String tagName;
	HTMLNode originalNode;
	LinkedList<FreenetURI> keys = new LinkedList<FreenetURI>();

	public MultimediaElement(FProxyFetchTracker tracker, ToadletContext ctx,
			LinkedList<ParsedTag> blockElement) {
		super(tracker, null, 0, ctx, false);
		tagName = blockElement.getFirst().element;
		originalNode = makeHtmlNodeForParsedTag(blockElement.getFirst());
		originalNode.setContent("");
		if(originalNode.getAttribute("src") != null) originalNode.addAttribute("src", originalNode.getAttribute("src").concat("?max-size=0"));
		for(ParsedTag tag : blockElement) {
			HTMLNode source = makeHtmlNodeForParsedTag(tag);
			String src = source.getAttribute("src");
			if(src != null) {
				if(tag.element == "source") originalNode.addChild(source).addAttribute("src", originalNode.getAttribute("src").concat("?max-size=0"));
				try {
					keys.add(new FreenetURI(src));
				} catch (MalformedURLException e) {
					//Do nothing
				}
			}
			else break;
		}

		String sizePart = new String();
		Map<String, String> attrs = originalNode.getAttributes();
		HashMap<String, String> newAttrs = new HashMap<String, String>();
		if(attrs.containsKey("width")) newAttrs.put("width", attrs.get("width"));
		if(attrs.containsKey("height")) newAttrs.put("height", attrs.get("height"));
		if (attrs.containsKey("width") && attrs.containsKey("height")) {
			sizePart = "&width=" + attrs.get("width") + "&height=" + attrs.get("height");
		}
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
		if(attrs.containsKey("width")) newAttrs.put("width", attrs.get("width"));
		if(attrs.containsKey("height")) newAttrs.put("height", attrs.get("height"));
		newAttrs.put("src", "/imagecreator/?text="+fetchedPercent+"%25"+sizePart);
		if (attrs.containsKey("width") && attrs.containsKey("height")) {
			sizePart = "&width=" + attrs.get("width") + "&height=" + attrs.get("height");
		}
	
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
		String src = key.toString()+"?max-size=0";
		if(!src.startsWith("/")) src = "/"+src;
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

	public void finalizeTarget(FreenetURI key) {
		this.key = key;
		startFetch();
	}
}
