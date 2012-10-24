package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * This step allows the user to choose between darknet and opennet, explaining each briefly.
 */
public class OPENNET implements Step {

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		HTMLNode contentNode = helper.getPageContent(WizardL10n.l10n("opennetChoicePageTitle"));
		InfoboxWidget infoboxContent = contentNode.addInfobox(InfoboxWidget.Type.NORMAL,
			WizardL10n.l10n("opennetChoiceTitle"));

		infoboxContent.body.addChild(new BlockText(WizardL10n.l10n("opennetChoiceIntroduction")));

		HTMLNode form = helper.addFormChild(infoboxContent.body, ".", "opennetForm", false);

		HTMLNode p = form.addChild(new BlockText());
		HTMLNode input = p.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "radio", "opennet", "false" });
		input.addB(WizardL10n.l10n("opennetChoiceConnectFriends") + ":");
		p.addLineBreak();
		p.addI(WizardL10n.l10n("opennetChoicePro"));
		p.addText(": " + WizardL10n.l10n("opennetChoiceConnectFriendsPRO") + "¹");
		p.addLineBreak();
		p.addI(WizardL10n.l10n("opennetChoiceCon"));
		p.addText(": " + WizardL10n.l10n("opennetChoiceConnectFriendsCON", "minfriends", "5"));

		p = form.addChild(new BlockText());
		input = p.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "radio", "opennet", "true" });
		input.addB(WizardL10n.l10n("opennetChoiceConnectStrangers") + ":");
		p.addLineBreak();
		p.addI(WizardL10n.l10n("opennetChoicePro"));
		p.addText(": " + WizardL10n.l10n("opennetChoiceConnectStrangersPRO"));
		p.addLineBreak();
		p.addI(WizardL10n.l10n("opennetChoiceCon"));
		p.addText(": " + WizardL10n.l10n("opennetChoiceConnectStrangersCON"));

		form.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "back", NodeL10n.getBase().getString("Toadlet.back")});
		form.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "next", NodeL10n.getBase().getString("Toadlet.next")});

		Box foot = new Box(HTMLClass.TOGGLEABLE);
		infoboxContent.addChild(foot);
		foot.addI("¹: " + WizardL10n.l10n("opennetChoiceHowSafeIsFreenetToggle"));
		Box footHidden = foot.addBox(HTMLClass.HIDDEN);
		OutputList footList = footHidden.addList(OutputList.Type.ORDERED, HTMLClass.NULL);
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetStupid"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetFriends") + "²");
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetTrustworthy"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetNoSuspect"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetChangeID"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetSSK"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetOS"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetBigPriv"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetDistant"));
		footList.addItem(WizardL10n.l10n("opennetChoiceHowSafeIsFreenetBugs"));
		HTMLNode foot2 = footHidden.addBlockText();
		foot2.addText("²: " + WizardL10n.l10n("opennetChoiceHowSafeIsFreenetFoot2"));
	}

	/**
	 * Doesn't make any changes, just passes result on to SECURITY_NETWORK.
	 * @param request Checked for "opennet" value.
	 */
	@Override
	public String postStep(HTTPRequest request) {
		if (request.isPartSet("opennet")) {
			return FirstTimeWizardToadlet.WIZARD_STEP.SECURITY_NETWORK+"&opennet="+
			        request.getPartAsStringFailsafe("opennet", 5);
		} else {
			//Nothing selected when "next" clicked. Display choice again.
			return FirstTimeWizardToadlet.WIZARD_STEP.OPENNET.name();
		}
	}
}
