package freenet.client.filter;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.updateableelements.ImageElement;
import freenet.clients.http.updateableelements.MultimediaElement;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLEncoder;
import freenet.support.HTMLNode;

/** This TagReplacerCallback adds pushing support for freesites, and replaces their img's to pushed ones */
public class PushingTagReplacerCallback implements TagReplacerCallback {

	/** The FProxyFetchTracker */
	private FProxyFetchTracker	tracker;
	/** The maxSize used for fetching */
	private long				maxSize;
	/** The current ToadletContext */
	private ToadletContext		ctx;
	/** HTML nodes that may have children elements should be stored here*/
	private HTMLNode flowContent = null;
	private HTMLNode currentElement = null;

	/**
	 * Constructor
	 * 
	 * @param tracker
	 *            - The FProxyFetchTracker
	 * @param maxSize
	 *            - The maxSize used for fetching
	 * @param ctx
	 *            - The current ToadletContext
	 */
	public PushingTagReplacerCallback(FProxyFetchTracker tracker, long maxSize, ToadletContext ctx) {
		this.tracker = tracker;
		this.maxSize = maxSize;
		this.ctx = ctx;
	}

	/**
	 * Returns the javascript code that initializes the l10n on the client side. It must be inserted to the page.
	 * 
	 * @return The javascript code that needs to be inserted in order to l10n work
	 */
	public static String getClientSideLocalizationScript() {
		StringBuilder l10nBuilder = new StringBuilder("var l10n={\n");
		boolean isNamePresentAtLeastOnce=false;
		for (String key : NodeL10n.getBase().getAllNamesWithPrefix("fproxy.push")) {
			l10nBuilder.append(key.substring("fproxy.push".length() + 1) + ": \"" + HTMLEncoder.encode(NodeL10n.getBase().getString(key)) + "\",\n");
			isNamePresentAtLeastOnce=true;
		}
		String l10n = isNamePresentAtLeastOnce?l10nBuilder.substring(0, l10nBuilder.length() - 2):l10nBuilder.toString();
		l10n = l10n.concat("\n};");
		return l10n;
	}

	public String processTag(ParsedTag pt, URIProcessor uriProcessor) {
		// If javascript or pushing is disabled, then it won't need pushing
		if (ctx.getContainer().isFProxyJavascriptEnabled() && ctx.getContainer().isFProxyWebPushingEnabled()) {
			if (pt.element.toLowerCase().compareTo("img") == 0) {
				// Img's needs to be replaced with pushed ImageElement's
				for (int i = 0; i < pt.unparsedAttrs.length; i++) {
					String attr = pt.unparsedAttrs[i];
					String name = attr.substring(0, attr.indexOf("="));
					String value = attr.substring(attr.indexOf("=") + 2, attr.length() - 1);
					if (name.compareTo("src") == 0) {
						String src;
						try {
							// We need absolute URI
							src = uriProcessor.makeURIAbsolute(uriProcessor.processURI(value, null, false, false));
						} catch (CommentException ce) {
							return null;
						} catch (URISyntaxException use) {
							return null;
						}
						if (src.startsWith("/")) {
							src = src.substring(1);
						}
						try {
							// Create the ImageElement
							return new ImageElement(tracker, new FreenetURI(src), maxSize, ctx, pt, true).generate();
						} catch (MalformedURLException mue) {
							return null;
						}
					}
				}
			} else if(pt.element.toLowerCase().compareTo("video") == 0 || pt.element.toLowerCase().compareTo("audio") == 0) {
				if(!pt.startSlash) { 
					for (int i = 0; i < pt.unparsedAttrs.length; i++) {
						String attr = pt.unparsedAttrs[i];
						String name = attr.substring(0, attr.indexOf("="));
						String value = attr.substring(attr.indexOf("=") + 2, attr.length() - 1);
						if (name.compareTo("src") == 0) {
							String src;
							try {
								// We need absolute URI
								src = uriProcessor.makeURIAbsolute(uriProcessor.processURI(value, null, false, false));
							} catch (CommentException ce) {
								return null;
							} catch (URISyntaxException use) {
								return null;
							}
							if (src.startsWith("/")) {
								src = src.substring(1);
							}
							pt.unparsedAttrs[i] = name+"=\""+src+"\"";
						}
					}
					flowContent = currentElement = pt.toHTMLNode();
					return "";
				} else {
					String result = new MultimediaElement(tracker, ctx, flowContent).generate();
					flowContent = currentElement = null;
					return result;
				}
			} else if(pt.element.toLowerCase().compareTo("source") == 0 && flowContent != null) {
				for (int i = 0; i < pt.unparsedAttrs.length; i++) {
					String attr = pt.unparsedAttrs[i];
					String name = attr.substring(0, attr.indexOf("="));
					String value = attr.substring(attr.indexOf("=") + 2, attr.length() - 1);
					if (name.compareTo("src") == 0) {
						String src;
						try {
							// We need absolute URI
							src = uriProcessor.makeURIAbsolute(uriProcessor.processURI(value, null, false, false));
						} catch (CommentException ce) {
							return null;
						} catch (URISyntaxException use) {
							return null;
						}
						if (src.startsWith("/")) {
							src = src.substring(1);
						}
						pt.unparsedAttrs[i] = name+"=\""+src+"\"";
					}
				}
				currentElement = pt.toHTMLNode();
				flowContent.addChild(currentElement);
				return "";
			} else if (pt.element.toLowerCase().compareTo("body") == 0 && pt.startSlash==true) {
				// After the <body>, we need to insert the requestId and the l10n script
				return "".concat(/*new XmlAlertElement(ctx).generate()*/"".concat("<input id=\"requestId\" type=\"hidden\" value=\"" + ctx.getUniqueId() + "\" name=\"requestId\"/>")).concat("<script type=\"text/javascript\" language=\"javascript\">".concat(getClientSideLocalizationScript()).concat("</script>")).concat("</body>");
			} else if (pt.element.toLowerCase().compareTo("head") == 0) {
				// After the <head>, we need to add GWT support
				return "<head><script type=\"text/javascript\" language=\"javascript\" src=\"/static/freenetjs/freenetjs.nocache.js\"></script><noscript><style> .jsonly {display:none;}</style></noscript><link href=\"/static/reset.css\" rel=\"stylesheet\" type=\"text/css\" />";
			}
		}
		return null;
	}

	public boolean inFlowContent() {
		return (flowContent != null && currentElement != null);
	}

	public String processText(String text, String type) {
		if(inFlowContent()) {
			currentElement.setContent(text);
			return "";
		}
		return text;
	}

}
