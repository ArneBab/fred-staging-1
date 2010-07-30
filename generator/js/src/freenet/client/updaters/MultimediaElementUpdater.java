package freenet.client.updaters;

import java.util.HashMap;
import java.util.Map;

import freenet.client.l10n.L10n;
import freenet.client.tools.Base64;
import freenet.client.tools.FreenetRequest;
import freenet.client.tools.QueryParameter;
import freenet.client.UpdaterConstants;
import freenet.client.FreenetJs;

import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.Window;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.xml.client.XMLParser;
import com.google.gwt.xml.client.Node;
import com.google.gwt.xml.client.NodeList;
import com.google.gwt.xml.client.NamedNodeMap;
import com.google.gwt.xml.client.Element;

/**An Updater which aids in the retrieval of multimedia elements, such as audio and
 * video. A placeholder image is originally provided by the node with a class
 * containing the keyword <code>unFinalized</code>. The Updater creates an onClick
 * event for this image, which will send a request for data from the
 * {@link MultimediaElement}, which will respond with the entire original element. The
 * Updater will extract all the potential sources which <em>could</em> be fetched, and
 * will run them by the browser to see if they may be rendered. When/if it finds one
 * the browsers thinks is acceptable, it will notify the node to fetch that source.
 * It will then update the placeholder with a progress image. The elements
 * <code>&lt;video&gt;</code> and <code>&lt;audio&gt;</code> are supported.
 */
public class MultimediaElementUpdater extends ReplacerUpdater {

	@Override
	public void updated(String elementId, String content) {
		final String localElementId = elementId;
		final com.google.gwt.dom.client.Element parentElement = RootPanel.get(elementId).getElement().getFirstChildElement();
		final com.google.gwt.dom.client.Element element = parentElement.getFirstChildElement();
		//Begin the fetch process when the placeholder is clicked
		if(parentElement.getClassName().contains("unFinalized")) Image.wrap(element).addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				element.setAttribute("src", "/imagecreator/?text=0%25");
				parentElement.setClassName("jsonly MultimediaElement");
				FreenetJs.log("Unfinalized multimedia element detected");
				//Request the original element
				FreenetRequest.sendRequest(UpdaterConstants.dataPath, new QueryParameter[] { new QueryParameter("requestId", FreenetJs.requestId),
						new QueryParameter("elementId", localElementId) }, new RequestCallback(){
					@Override
					public void onResponseReceived(Request request, Response response) {
						String tagName;
						HashMap<String, String> potentialFiles = new HashMap<String, String>();
						String content = Base64.decode(response.getText().split("[:]")[2]);
						FreenetJs.log("Processing element "+localElementId+" which looks like "+content);
						Node multimedia = null;
						/*There should only be one multimedia element retrieved
						 * Check whether it is an <audio> or <video> element. If it
						 * isn't, then it isn't supported by this Updater.
						 */
						multimedia = XMLParser.parse(content).getDocumentElement().getElementsByTagName("audio").item(0);
						if(multimedia == null) multimedia = XMLParser.parse(content).getDocumentElement().getElementsByTagName("video").item(0);
						if(multimedia == null) {
							FreenetJs.log("ERROR: Unable to find a usable element");
							element.setAttribute("src", "/imagecreator/?text="+L10n.get("multimediaFailure"));
							element.setAttribute("alt", L10n.get("multimediaFailureUnknownElement"));
							element.setAttribute("title", L10n.get("multimediaFailureUnknownElement"));
							return;
						}
						tagName = multimedia.getNodeName();
						FreenetJs.log("Checking whether browser supports "+tagName+" elements");
						if(!supportsElement(tagName)) {
							FreenetJs.log("ERROR: Browser does not support "+tagName);
							element.setAttribute("src", "/imagecreator/?text="+L10n.get("multimediaFailure"));
							element.setAttribute("alt", L10n.get("multimediaFailureUnsupportedElement"));
							element.setAttribute("title", L10n.get("multimediaFailureUnsupportedElement"));
							return;
						}
						/*Figure out how many sources exist. These can come from
						 * either an 'src' attribute on the element, or from one or
						 * more child <source> elements.
						 * Place all the sources into potentialFiles.
						 */
						if(((Element)multimedia).hasAttribute("src")) {
							FreenetJs.log("Dealing with one src attribute");
							potentialFiles.put(((Element)multimedia).getAttribute("src"), ((Element)multimedia).getAttribute("codec"));
						}
						else {
							NodeList multimediaChildren = multimedia.getChildNodes();
							FreenetJs.log("Found "+multimediaChildren.getLength()+" potential source elements");
							for(int i = 0; i < multimediaChildren.getLength(); i++) {
								Node childNode = multimediaChildren.item(i);
								if(childNode.getNodeName().toLowerCase() == "source") {
									NamedNodeMap attributes = childNode.getAttributes();
									Node srcNode = attributes.getNamedItem("src");
									Node codecNode = attributes.getNamedItem("codec");
									String src = srcNode != null ? srcNode.getNodeValue() : null;
									String codec = codecNode != null ? codecNode.getNodeValue() : null;
									potentialFiles.put(src, codec);
								}
							}
						}
						FreenetJs.log("There are "+potentialFiles.size()+" possible source elements");

						//Check each source for compatibility with the browser
						for(Map.Entry<String, String> entry : potentialFiles.entrySet()) {
							potentialFiles.remove(entry.getKey());
							FreenetJs.log("Checking whether browser can process "+entry.getKey());
							if(isPotentiallyValid(tagName, entry.getKey(), entry.getValue())) {
								//Fetch the supported element
								fetch(localElementId, entry.getKey());
								return;
							}
						}
						FreenetJs.log("ERROR: No compatible multimedia source was found.");
						element.setAttribute("src", "/imagecreator/?text="+L10n.get("multimediaFailure"));
						element.setAttribute("alt", L10n.get("multimediaFailureUnsupportedSources"));
						element.setAttribute("title", L10n.get("multimediaFailureUnsupportedSources"));
					}
					@Override
					public void onError(Request request, Throwable exception) {
						FreenetJs.log("ERROR! Unable to retrieve intial Multimedia element state");
					}

				});
			}
		});
		else {
			FreenetJs.log("Updating an already finalized multimedia element");
			super.updated(elementId, content);
		}
	}

	/**
	 * Instructs the node to fetch a particular source for this element.
	 * @param elementId id of the element whose source has been finalized
	 * @param src the key to fetch
	 */
	private void fetch(String elementId, String src) {
		if(src.startsWith("/")) src = src.substring(1);
		src = src.substring(0, src.lastIndexOf("?"));
		FreenetJs.log("Queuing multimedia element "+src);
		FreenetRequest.sendRequest(UpdaterConstants.queuePath, new QueryParameter[] { new QueryParameter("requestId", FreenetJs.requestId),
				new QueryParameter("elementId", elementId), new QueryParameter("key", src) }, new RequestCallback() {
			@Override
			public void onResponseReceived(Request request, Response response) {
				FreenetJs.log("Fetch queued");
			}
			@Override
			public void onError(Request request, Throwable exception) {
				FreenetJs.log("ERROR! Unable to retrieve intial Multimedia element state");
			}
		});
	}

	/**Checks whether the browser is capable of rendering the element in question*/
	private native boolean supportsElement(String tagName) /*-{
		return $doc.createElement(tagName).play ? true : false;
	}-*/;

	/**Queries the browser to figure out whether a given file might be playable. Checks
	 * whether the browser supports the mimetype(guessed from the filename), and codec,
	 * if given.
	 * @param tagName The type of tag. Generally should be <code>&ltaudio&rt</code> or
	 * <code>&lt;video&gt;</code>.
	 * @param src The complete key
	 * @param codec The codec, defined in the element
	 * @return
	 */
	private native boolean isPotentiallyValid(String tagName, String src, String codec) /*-{
		var node = $doc.createElement(tagName);
		var extension = src.substring(src.lastIndexOf(".")+1, src.lastIndexOf("?"));
		var mime;
		@freenet.client.FreenetJs::log(Ljava/lang/String;)("File has an extension of "+extension);
		switch(extension) {
			case "ogx":
				mime='application/ogg';
				break;
			case "ogg":
				mime='audio/ogg'
				break;
			case "oga":
				mime='audio/ogg'
				break;
			case "ogv":
				mime='video/ogg'
				break;
			case "webm":
				mime='video/webm';
				break;
			case "mp3":
				mime='audio/mpeg3'
				break;
			case "wav":
				mime='audio/wav'
				break;
			case "avi":
				mime='video/x-msvideo';
				break;
			default:
				mime = 'unknown/unsupported;';
		}
		if(codec == undefined) codec = "";
		@freenet.client.FreenetJs::log(Ljava/lang/String;)("Checking if browser can play "+mime+codec);
		if(node.canPlayType(mime+codec) == "probably" || node.canPlayType(mime+codec) == "maybe") return true;
		return false;
	}-*/;
}
