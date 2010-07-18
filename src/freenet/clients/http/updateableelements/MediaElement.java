package freenet.clients.http.updateableelements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import freenet.client.FetchException;
import freenet.client.filter.HTMLFilter.ParsedTag;
import freenet.clients.http.FProxyFetchInProgress;
import freenet.clients.http.FProxyFetchResult;
import freenet.clients.http.FProxyFetchTracker;
import freenet.clients.http.FProxyFetchWaiter;
import freenet.clients.http.SimpleToadletServer;
import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;

/** A pushed image, the progress is shown with the ImageCreatorToadlet */
public abstract class MediaElement extends BaseUpdateableElement {

	private static volatile boolean	logMINOR;

	static {
		Logger.registerClass(MediaElement.class);
	}

	/** The tracker that the Fetcher can be acquired */
	public FProxyFetchTracker		tracker;
	/** The original URI */
	public final FreenetURI			origKey;
	/** The URI of the download this progress bar shows */
	public FreenetURI				key;
	/** The maxSize */
	public long						maxSize;
	/** The FetchListener that gets notified when the download progresses */
	private NotifierFetchListener	fetchListener;

	protected ParsedTag				originalElement;

	// FIXME get this from global weakFastRandom ???
	private final int				randomNumber	= new Random().nextInt();

	private boolean					wasError		= false;

	public MediaElement(FProxyFetchTracker tracker, FreenetURI key, long maxSize, ToadletContext ctx, ParsedTag originalElement, boolean pushed) {
		super("span", ctx);
		long now = System.currentTimeMillis();
		if (logMINOR) {
			Logger.minor(this, "MediaElement creating for uri:" + key);
		}
		this.originalElement = originalElement;
		this.tracker = tracker;
		this.key = this.origKey = key;
		this.maxSize = maxSize;
		init(pushed);
		if(!pushed) return;
		// Creates and registers the FetchListener
		fetchListener = new NotifierFetchListener(((SimpleToadletServer) ctx.getContainer()).pushDataManager, this);
		((SimpleToadletServer) ctx.getContainer()).getTicker().queueTimedJob(new Runnable() {
			public void run() {
				try {
					FProxyFetchWaiter waiter = MediaElement.this.tracker.makeFetcher(MediaElement.this.key, MediaElement.this.maxSize, null);
					MediaElement.this.tracker.getFetchInProgress(MediaElement.this.key, MediaElement.this.maxSize, null).addListener(fetchListener);
					MediaElement.this.tracker.getFetchInProgress(MediaElement.this.key, MediaElement.this.maxSize, null).close(waiter);
				} catch (FetchException fe) {
					if (fe.newURI != null) {
						try {
							MediaElement.this.key = fe.newURI;
							FProxyFetchWaiter waiter = MediaElement.this.tracker.makeFetcher(MediaElement.this.key, MediaElement.this.maxSize, null);
							MediaElement.this.tracker.getFetchInProgress(MediaElement.this.key, MediaElement.this.maxSize, null).addListener(fetchListener);
							MediaElement.this.tracker.getFetchInProgress(MediaElement.this.key, MediaElement.this.maxSize, null).close(waiter);
						} catch (FetchException fe2) {
							wasError = true;
						}
					}
				}
				fetchListener.onEvent();
			}
		}, 0);

		if (logMINOR) {
			Logger.minor(this, "MediaElement creating finished in:" + (System.currentTimeMillis() - now) + " ms");
		}
	}

	@Override
	public void dispose() {
		if (logMINOR) {
			Logger.minor(this, "Disposing MediaElement");
		}
		FProxyFetchInProgress progress = tracker.getFetchInProgress(key, maxSize, null);
		if (progress != null) {
			progress.removeListener(fetchListener);
			if (logMINOR) {
				Logger.minor(this, "canCancel():" + progress.canCancel());
			}
			progress.requestImmediateCancel();
			if (progress.canCancel()) {
				tracker.run();
			}
		}
	}

	@Override
	public String getUpdaterId(String requestId) {
		return getId(origKey, randomNumber);
	}

	public static String getId(FreenetURI uri, int randomNumber) {
		return Base64.encodeStandard(("image[URI:" + uri.toString() + ",random:" + randomNumber + "]").getBytes());
	}

	@Override
	public void updateState(boolean initial) {
		if (logMINOR) {
			Logger.minor(this, "Updating MediaElement for url:" + key + (origKey == key ? (" originally " + origKey) : ""));
		}
		children.clear();
		HTMLNode whenJsEnabled = new HTMLNode("span", "class", "jsonly "+this.getClass());
		addChild(whenJsEnabled);
		// When js disabled
		addChild("noscript").addChild(makeHtmlNodeForParsedTag(originalElement));
		if (initial) {
			if (wasError) {
				whenJsEnabled.addChild(makeHtmlNodeForParsedTag(originalElement));
			} else {
				initialStateDisplay(whenJsEnabled);
			}
		} else {
			FProxyFetchResult fr = null;
			FProxyFetchWaiter waiter = null;
			try {
				try {
					waiter = tracker.makeFetcher(key, maxSize, null);
					fr = waiter.getResultFast();
				} catch (FetchException fe) {
					whenJsEnabled.addChild("div", "error");
				}
				if (fr == null) {
					whenJsEnabled.addChild("div", "No fetcher found");
				} else {
					if (fr.isFinished() && fr.hasData()) {
						if (logMINOR) Logger.minor(this, "MediaElement is completed");
						whenJsEnabled.addChild(makeHtmlNodeForParsedTag(originalElement));
					} else if (fr.failed != null) {
						if (logMINOR) Logger.minor(this, "MediaElement is errorous");
						whenJsEnabled.addChild(makeHtmlNodeForParsedTag(originalElement));
					} else {
						if (logMINOR) Logger.minor(this, "MediaElement is still in progress");
						subsequentStateDisplay(fr, whenJsEnabled);
					}
				}
			} finally {
				if (waiter != null) {
					tracker.getFetchInProgress(key, maxSize, null).close(waiter);
				}
				if (fr != null) {
					tracker.getFetchInProgress(key, maxSize, null).close(fr);
				}
			}
		}
	}

	protected abstract void initialStateDisplay(HTMLNode node);

	protected abstract void subsequentStateDisplay(FProxyFetchResult result, HTMLNode node);

	// FIXME move this to some global utilities class.
	protected HTMLNode makeHtmlNodeForParsedTag(ParsedTag pt) {
		List<String> attributeNames = new ArrayList<String>();
		List<String> attributeValues = new ArrayList<String>();
		for (Entry<String, String> att : pt.getAttributesAsMap().entrySet()) {
			attributeNames.add(att.getKey());
			attributeValues.add(att.getValue());
		}
		return new HTMLNode(pt.element, attributeNames.toArray(new String[] {}), attributeValues.toArray(new String[] {}));
	}

	@Override
	public String toString() {
		return "MediaElement[key:" + key + ",maxSize:" + maxSize + ",originalElement:" + originalElement + ",updaterId:" + getUpdaterId(null) + "]";
	}

}
