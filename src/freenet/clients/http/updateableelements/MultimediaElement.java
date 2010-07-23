package freenet.clients.http.updateableelements;

import java.net.MalformedURLException;
import java.util.LinkedList;

import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyToadlet;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.HTMLNode;

public class MultimediaElement extends MediaElement {

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
		HTMLNode node = new HTMLNode("span", "class", "jsonly MultimediaElement");
		addChild(node);
		node.addChild(new HTMLNode("img", "src", "/imagecreator/?text=+"+FProxyToadlet.l10n("startmultimedia")));
		addChild("noscript").addChild(originalNode);
		init(false);
		fetchListener.onEvent();
	}

	@Override
	protected void subsequentStateDisplay(FProxyFetchResult result,
			HTMLNode node) {
		int total = result.requiredBlocks;
		int fetchedPercent = (int) (result.fetchedBlocks / (double) total * 100);
		node.addChild(originalNode);
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

	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.MULTIMEDIA_ELEMENT_UPDATER;
	}

	@Override
	protected void addCompleteElement(HTMLNode node) {
		node.addChild(tagName, "src", key.toString()).setContent("");
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
}
