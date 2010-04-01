package freenet.clients.http;

import freenet.support.HTMLNode;

public class PageNode extends InfoboxNode {
	
	public final HTMLNode headNode;
	public final HTMLNode bodyNode;

	PageNode(HTMLNode page, HTMLNode head, HTMLNode content, HTMLNode body) {
		super(page, content);
		this.headNode = head;
		this.bodyNode = body;
	}
	
	/**
	 * Adds a custom style sheet to the header of the page.
	 *
	 * @param customStyleSheet
	 *            The URL of the custom style sheet
	 */
	public void addCustomStyleSheet(String customStyleSheet) {
		headNode.addChild("link", new String[] { "rel", "href", "type", "media" }, new String[] { "stylesheet", customStyleSheet, "text/css", "screen" });
	}

}
