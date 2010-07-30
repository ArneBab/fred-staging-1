package freenet.client.filter;

import freenet.client.filter.HTMLFilter.ParsedTag;

/** This callback can be registered to a HTMLContentFilter, that provides callback for all tags it processes. It can modify tags. */
public interface TagReplacerCallback {
	/**
	 * Processes a tag, and return a replacement
	 * 
	 * @param pt
	 *            - The tag that is processed
	 * @param uriProcessor
	 *            - The URIProcessor that helps with URI transformations
	 * @return the replacement for the tag, or null if not needed
	 */
	public String processTag(ParsedTag pt, URIProcessor uriProcessor);

	/** Reveals whether a the callback is processing an element which is a member of
	 * the 'Flow Content' category. Such elements may have text content and zero or
	 * more child elements.
	 * See {@link http://dev.w3.org/html5/spec/Overview.html#flow-content} for details.
	 * @return whether the callback is processing Flow Content
	 */
	public boolean inFlowContent();

	/** Processes text and returns a replacement. This is probably only useful when
	 * the TagReplacerCallback needs to extract text content from a flow element.
	 * If the text does not need to be changed it will be returned unmodified. It may
	 * be removed, in which case an empty String is returned.
	 * @param text Text to process
	 * @param type Type of tag that is being processed
	 * @return Processed text
	 */
	public String processText(String text, String type);
}
