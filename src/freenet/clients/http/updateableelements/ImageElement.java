package freenet.clients.http.updateableelements;

import java.util.Map;

import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyToadlet;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.HTMLNode;

public class ImageElement extends MediaElement {

	final ParsedTag originalElement;
	public ImageElement(FProxyFetchTracker tracker, FreenetURI key,
			long maxSize, ToadletContext ctx, ParsedTag originalElement,
			boolean pushed) {
		super(tracker, key, maxSize, ctx, pushed);
		this.originalElement = originalElement;
		HTMLNode node = new HTMLNode("span", "class", "jsonly ImageElement");
		addChild(node);
		if(wasError) node.addChild(makeHtmlNodeForParsedTag(this.originalElement));
		else {
			Map<String, String> attr = ImageElement.this.originalElement.getAttributesAsMap();
			String sizePart = new String();
			if (attr.containsKey("width") && attr.containsKey("height")) {
				sizePart = "&width=" + attr.get("width") + "&height=" + attr.get("height");
			}
			attr.put("src", "/imagecreator/?text=+"+FProxyToadlet.l10n("imageinitializing")+"+" + sizePart);
			node.addChild(makeHtmlNodeForParsedTag(new ParsedTag(ImageElement.this.originalElement, attr)));
			node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "fetchedBlocks", String.valueOf(0) });
			node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "requiredBlocks", String.valueOf(1) });
			addChild("noscript").addChild(makeHtmlNodeForParsedTag(ImageElement.this.originalElement));
			init(pushed);
		}
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.IMAGE_ELEMENT_UPDATER;
	}

	@Override
	protected void subsequentStateDisplay(FProxyFetchResult result, HTMLNode node) {
		int total = result.requiredBlocks;
		int fetchedPercent = (int) (result.fetchedBlocks / (double) total * 100);
		Map<String, String> attr = ImageElement.this.originalElement.getAttributesAsMap();
		String sizePart = new String();
		if (attr.containsKey("width") && attr.containsKey("height")) {
			sizePart = "&width=" + attr.get("width") + "&height=" + attr.get("height");
		}
		attr.put("src", "/imagecreator/?text=" + fetchedPercent + "%25" + sizePart);
		node.addChild(makeHtmlNodeForParsedTag(new ParsedTag(ImageElement.this.originalElement, attr)));
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "fetchedBlocks", String.valueOf(result.fetchedBlocks) });
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "requiredBlocks", String.valueOf(result.requiredBlocks) });
	}

	@Override
	protected void addCompleteElement(HTMLNode node) {
		node.addChild(makeHtmlNodeForParsedTag(ImageElement.this.originalElement));
	}

	@Override
	public String getUpdaterId(String requestId) {
		return getId(origKey, randomNumber);
	}

	public static String getId(FreenetURI uri, int randomNumber) {
		return Base64.encodeStandard(("image[URI:" + uri.toString() + ",random:" + randomNumber + "]").getBytes());
	}

	@Override
	public String toString() {
		return "MediaElement[key:" + key + ",maxSize:" + maxSize + ",originalElement:" + ImageElement.this.originalElement + ",updaterId:" + getUpdaterId(null) + "]";
	}
}
