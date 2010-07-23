package freenet.client.updaters;

import freenet.client.tools.Base64;
import freenet.client.tools.FreenetRequest;
import freenet.client.tools.QueryParameter;
import freenet.client.UpdaterConstants;
import freenet.client.FreenetJs;


import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.Window;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;

public class MultimediaElementUpdater extends ReplacerUpdater {

	@Override
	public void updated(String elementId, String content) {
		FreenetJs.log("Updating Multimedia element");
		final String localElementId = elementId;
		Element element = RootPanel.get(elementId).getElement().getFirstChildElement().getFirstChildElement();
		if(element.getTagName() == "IMG") Image.wrap(element).addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				FreenetRequest.sendRequest(UpdaterConstants.dataPath, new QueryParameter[] { new QueryParameter("requestId", FreenetJs.requestId),
						new QueryParameter("elementId", localElementId) }, new RequestCallback(){
					@Override
					public void onResponseReceived(Request request, Response response) {
						String content = Base64.decode(response.getText().split("[:]")[2]);
						FreenetJs.log("Replacing Multimedia element "+localElementId+" with"+content);
						RootPanel.get(localElementId).getElement().setInnerHTML(content);
					}
					@Override
					public void onError(Request request, Throwable exception) {
						FreenetJs.log("ERROR! Unable to retrieve intial Multimedia element state");
					}

				});
			}
		});
		else super.updated(elementId, content);
	}

	
}
