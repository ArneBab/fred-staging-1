package freenet.client.updaters;

import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.Window;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;

import freenet.client.FreenetJs;

public class MultimediaElementUpdater extends ReplacerUpdater {

	@Override
	public void updated(String elementId, String content) {
		Element element = RootPanel.get(elementId).getElement().getFirstChildElement().getFirstChildElement();
		FreenetJs.log("Updating MultimediaUpdater"+element.getTagName());
		if(element.getTagName() == "IMG") Image.wrap(element).addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				Window.alert("Multimedia element clicked");
			}
		});
	}
}
