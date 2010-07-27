package freenet.client.updaters;

import java.util.HashMap;
import java.util.Map;

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

public class MultimediaElementUpdater extends ReplacerUpdater {

	@Override
	public void updated(String elementId, String content) {
		final String localElementId = elementId;
		final com.google.gwt.dom.client.Element parentElement = RootPanel.get(elementId).getElement().getFirstChildElement();
		final com.google.gwt.dom.client.Element element = parentElement.getFirstChildElement();
		if(parentElement.getClassName().contains("unFinalized")) Image.wrap(element).addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				element.setAttribute("src", "/imagecreator/?text=0%25");
				parentElement.setClassName("jsonly MultimediaElement");
				FreenetJs.log("Unfinalized multimedia element detected");
				FreenetRequest.sendRequest(UpdaterConstants.dataPath, new QueryParameter[] { new QueryParameter("requestId", FreenetJs.requestId),
						new QueryParameter("elementId", localElementId) }, new RequestCallback(){
					@Override
					public void onResponseReceived(Request request, Response response) {
						String tagName;
						HashMap<String, String> potentialFiles = new HashMap<String, String>();
						String content = Base64.decode(response.getText().split("[:]")[2]);
						FreenetJs.log("Processing element "+localElementId+" which looks like "+content);
						Node multimedia = null;
						//There should only be one multimedia element retrieved
						multimedia = XMLParser.parse(content).getDocumentElement().getElementsByTagName("audio").item(0);
						if(multimedia == null) multimedia = XMLParser.parse(content).getDocumentElement().getElementsByTagName("video").item(0);
						if(multimedia == null) {
							FreenetJs.log("ERROR: Unable to find a usable element");
							return;
						}
						tagName = multimedia.getNodeName();
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

						for(Map.Entry<String, String> entry : potentialFiles.entrySet()) {
							potentialFiles.remove(entry.getKey());
							FreenetJs.log("Checking whether browser can process "+entry.getKey());
							if(isPotentiallyValid(tagName, entry.getKey(), entry.getValue())) {
								fetch(localElementId, entry.getKey());
								break;
							}
						}
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
	private native boolean supportsElement(String tagName) /*-{
		return $doc.createElement(tagName).play ? true : false;
	}-*/;

	private native boolean isPotentiallyValid(String tagName, String src, String codec) /*-{
		var node = $doc.createElement(tagName);
		var extension = src.substring(src.lastIndexOf(".")+1, src.lastIndexOf("?"));
		var mime;
		@freenet.client.FreenetJs::log(Ljava/lang/String;)("File has an extension of "+extension);
		switch(extension) {
			case "ogg":
				mime='application/ogg;';
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
