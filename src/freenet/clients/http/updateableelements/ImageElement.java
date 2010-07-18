package freenet.clients.http.updateableelements;

import java.util.HashMap;
import java.util.Map;

import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyToadlet;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;

public class ImageElement extends MediaElement {

	public ImageElement(FProxyFetchTracker tracker, FreenetURI key,
			long maxSize, ToadletContext ctx, ParsedTag originalImg,
			boolean pushed) {
		super(tracker, key, maxSize, ctx, originalImg, pushed);
	}

	public static ImageElement createImageElement(FProxyFetchTracker tracker,FreenetURI key,long maxSize,ToadletContext ctx, boolean pushed){
		return createImageElement(tracker,key,maxSize,ctx,-1,-1, null, pushed);
	}

	public static ImageElement createImageElement(FProxyFetchTracker tracker,FreenetURI key,long maxSize,ToadletContext ctx,int width,int height, String name, boolean pushed){
		Map<String,String> attributes=new HashMap<String, String>();
		attributes.put("src", key.toString());
		if(width!=-1){
			attributes.put("width", String.valueOf(width));
		}
		if(height!=-1){
			attributes.put("height", String.valueOf(height));
		}
		if(name != null) {
			attributes.put("alt", name);
			attributes.put("title", name);
		}
		return new ImageElement(tracker,key,maxSize,ctx,new ParsedTag("img", attributes), pushed);
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.IMAGE_ELEMENT_UPDATER;
	}

	@Override
	protected void initialStateDisplay(HTMLNode node) {
		Map<String, String> attr = originalElement.getAttributesAsMap();
		String sizePart = new String();
		if (attr.containsKey("width") && attr.containsKey("height")) {
			sizePart = "&width=" + attr.get("width") + "&height=" + attr.get("height");
		}
		attr.put("src", "/imagecreator/?text=+"+FProxyToadlet.l10n("imageinitializing")+"+" + sizePart);
		node.addChild(makeHtmlNodeForParsedTag(new ParsedTag(originalElement, attr)));
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "fetchedBlocks", String.valueOf(0) });
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "requiredBlocks", String.valueOf(1) });
	}

	@Override
	protected void subsequentStateDisplay(FProxyFetchResult result, HTMLNode node) {
		int total = result.requiredBlocks;
		int fetchedPercent = (int) (result.fetchedBlocks / (double) total * 100);
		Map<String, String> attr = originalElement.getAttributesAsMap();
		String sizePart = new String();
		if (attr.containsKey("width") && attr.containsKey("height")) {
			sizePart = "&width=" + attr.get("width") + "&height=" + attr.get("height");
		}
		attr.put("src", "/imagecreator/?text=" + fetchedPercent + "%25" + sizePart);
		node.addChild(makeHtmlNodeForParsedTag(new ParsedTag(originalElement, attr)));
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "fetchedBlocks", String.valueOf(result.fetchedBlocks) });
		node.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "requiredBlocks", String.valueOf(result.requiredBlocks) });
	}

}
