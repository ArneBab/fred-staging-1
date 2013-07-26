/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.clients.http.bookmark;

import java.net.MalformedURLException;

import freenet.keys.FreenetURI;
import freenet.keys.USK;
import freenet.l10n.NodeL10n;
import freenet.node.FSParseException;
import freenet.node.NodeClientCore;
import freenet.node.useralerts.AbstractUserAlert;
import freenet.node.useralerts.UserAlert;
import freenet.node.useralerts.UserAlertManager;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

public class BookmarkItem extends Bookmark {

	/** Whether we are logging at MINOR. */
	private static volatile boolean logMINOR;

	static {
		Logger.registerClass(BookmarkItem.class);
	}

	private final BookmarkUpdatedUserAlert alert;

	private final UserAlertManager alerts;

	protected String desc;

	protected String shortDescription;

	private FreenetURI key;

	private boolean updated;

	private boolean hasAnActivelink = false;

	public BookmarkItem(FreenetURI k, String n, String d, String s, boolean hasAnActivelink, UserAlertManager uam)
			throws MalformedURLException {

		this.key = k;
		setName(n);
		this.desc = d;
		this.shortDescription = s;
		this.hasAnActivelink = hasAnActivelink;
		this.alerts = uam;
		alert = new BookmarkUpdatedUserAlert();
		assert (key != null);
	}

	public BookmarkItem(SimpleFieldSet sfs, UserAlertManager uam) throws FSParseException, MalformedURLException {
		setName(sfs.get("Name"));
		this.desc = sfs.get("Description");
		if (desc == null) {
			desc = "";
		}
		this.shortDescription = sfs.get("ShortDescription");
		if (shortDescription == null) {
			shortDescription = "";
		}
		this.hasAnActivelink = sfs.getBoolean("hasAnActivelink");
		this.key = new FreenetURI(sfs.get("URI"));
		this.alerts = uam;
		this.alert = new BookmarkUpdatedUserAlert();
	}

	public String getDescription() {
		if (desc == null) {
			return "";
		}
		if (desc.toLowerCase().startsWith("l10n:")) {
			return NodeL10n.getBase().getString("Bookmarks.Defaults.Description." + desc.substring("l10n:".length()));
		}
		return desc;
	}

	public String getShortDescription() {
		if (shortDescription == null) {
			return "";
		}
		if (shortDescription.toLowerCase().startsWith("l10n:")) {
			return NodeL10n.getBase().getString("Bookmarks.Defaults.ShortDescription." + shortDescription.substring("l10n:".length()));
		}
		return shortDescription;
	}

	public synchronized FreenetURI getURI() {
		return key;
	}

	public String getKey() {
		return key.toString();
	}

	public synchronized String getKeyType() {
		return key.getKeyType();
	}

	public USK getUSK() throws MalformedURLException {
		return USK.create(key);
	}

	public boolean hasAnActivelink() {
		return hasAnActivelink;
	}

	public synchronized void update(FreenetURI uri, boolean hasAnActivelink, String description, String shortDescription) {
		this.key = uri;
		this.desc = description;
		this.shortDescription = shortDescription;
		this.hasAnActivelink = hasAnActivelink;
		if (!key.isUSK()) {
			disableBookmark();
		}
	}

	/** @return True if we updated the edition */
	public synchronized boolean setEdition(long ed, NodeClientCore node) {
		if (key.getSuggestedEdition() >= ed) {
			if (logMINOR) {
				Logger.minor(this, "Edition " + ed + " is too old, not updating " + key);
			}
			return false;
		}
		key = key.setSuggestedEdition(ed);
		enableBookmark();
		return true;
	}

	@Override
	public SimpleFieldSet getSimpleFieldSet() {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putSingle("Name", getName());
		sfs.putSingle("Description", desc);
		sfs.putSingle("ShortDescription", shortDescription);
		sfs.put("hasAnActivelink", hasAnActivelink);
		sfs.putSingle("URI", key.toString());
		return sfs;
	}

	@Override
	public String toString() {
		return getName() + "###" + (this.desc != null ? this.desc : "") + "###" + this.hasAnActivelink + "###" + this.key.toString();
	}

	@Override
	public int hashCode() {
		int hash = super.hashCode();
		hash = 31 * hash + this.key.setSuggestedEdition(0).hashCode();
		hash = 31 * hash + (this.hasAnActivelink ? 1 : 0);
		hash = 31 * hash + (this.desc != null ? this.desc.hashCode() : 0);
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof BookmarkItem) {
			BookmarkItem b = (BookmarkItem) o;
			if (!super.equals(o)) {
				return false;
			}
			if (!b.key.equals(key)) {
				if ("USK".equals(b.key.getKeyType())) {
					if (!b.key.setSuggestedEdition(key.getSuggestedEdition()).equals(key)) {
						return false;
					}
				} else {
					return false;
				}
			}
			if (b.alerts != alerts) {
				return false;
			} // Belongs to a different node???
			if (b.hasAnActivelink != hasAnActivelink) {
				return false;
			}
			if (b.desc.equals(desc)) {
				return true;
			}
			if (b.desc == null || desc == null) {
				return false;
			}
			if (!b.desc.equals(desc)) {
				return false;
			}
			return true;
		} else {
			return false;
		}
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("BookmarkItem." + key);
	}

	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("BookmarkItem." + key, new String[] { pattern }, new String[] { value });
	}

	private String l10n(String key, String[] patterns, String[] values) {
		return NodeL10n.getBase().getString("BookmarkItem." + key, patterns, values);
	}

	private synchronized void enableBookmark() {
		if (updated) {
			return;
		}
		assert (key.isUSK());
		updated = true;
		alerts.register(alert);
	}

	private synchronized void disableBookmark() {
		updated = false;
		alerts.unregister(alert);
	}

	private class BookmarkUpdatedUserAlert extends AbstractUserAlert {

		public BookmarkUpdatedUserAlert() {
			super(true, null, null, null, null, UserAlert.MINOR, false, null, true, null);
		}

		@Override
		public String getTitle() {
			return l10n("bookmarkUpdatedTitle", "name", getName());
		}

		@Override
		public String getText() {
			return l10n("bookmarkUpdated", new String[] { "name", "edition" },
							   new String[] { getName(), Long.toString(key.getSuggestedEdition()) });
		}

		@Override
		public String getShortText() {
			return l10n("bookmarkUpdatedShort", "name", getName());
		}

		@Override
		public HTMLNode getHTMLText() {
			HTMLNode n = new HTMLNode("div");
			NodeL10n.getBase().addL10nSubstitution(n, "BookmarkItem.bookmarkUpdatedWithLink", new String[] { "link", "name", "edition" },
														  new HTMLNode[] { HTMLNode.link("/" + key), HTMLNode.text(getName()), HTMLNode.text(key.getSuggestedEdition()) });
			return n;
		}

		@Override
		public String dismissButtonText() {
			return l10n("deleteBookmarkUpdateNotification");
		}

		@Override
		public boolean isValid() {
			synchronized (BookmarkItem.this) {
				return updated;
			}
		}

		@Override
		public boolean isEventNotification() {
			return true;
		}

		@Override
		public void isValid(boolean validity) {
			if (validity) {
				return;
			}
			disableBookmark();
		}

		@Override
		public void onDismiss() {
			disableBookmark();
		}

	}

}
