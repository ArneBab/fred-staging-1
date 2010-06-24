/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client.filter;

import freenet.client.filter.HTMLFilter.ParsedTag;

/** <code>FilterCallback</code> which does nothing to the data it parses.
 * This class is ran when inserts are being filtered, as inserts
 * do not need to worry about charsets or web-pushing. This callback
 * therefore should not be ran against data which is going to be
 * passed directly to a browser, without further decompression.*/
public class InsertFilterCallback implements FilterCallback{

	public String onBaseHref(String baseHref) {
		return baseHref;
	}

	public void onText(String s, String type) {
		//Do nothing
	}

	public String processForm(String method, String action){
		return action;
	}

	public String processTag(ParsedTag pt) {
		return null;
	}

	public String processURI(String uri, String overrideType)
			throws CommentException {
		return uri;
	}

	public String processURI(String uri, String overrideType,
			boolean noRelative, boolean inline) throws CommentException {
		return uri;
	}
	

}
