package freenet.clients.http;

import java.io.IOException;
import java.net.URI;

import freenet.clients.http.PageMaker.RenderParameters;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Toadlet for "Freenet is starting up" page.
 */
public class StartupToadlet extends Toadlet {

        private StaticToadlet staticToadlet;
        private volatile boolean isPRNGReady = false;

        public StartupToadlet(StaticToadlet staticToadlet) {
                super(null);
                this.staticToadlet = staticToadlet;
        }

        public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
                // If we don't disconnect we will have pipelining issues
                ctx.forceDisconnect();

                String path = uri.getPath();
                if(path.startsWith(StaticToadlet.ROOT_URL) && staticToadlet != null)
                        staticToadlet.handleMethodGET(uri, req, ctx);
                else {
                        String desc = NodeL10n.getBase().getString("StartupToadlet.title");
                        PageNode page = ctx.getPageMaker().getPageNode(desc, ctx, new RenderParameters().renderStatus(false).renderNavigationLinks(false).renderModeSwitch(false));
                        HTMLNode pageNode = page.outer;
                        HTMLNode headNode = page.headNode;
                        headNode.addChild("meta", new String[]{"http-equiv", "content"}, new String[]{"refresh", "20; url="});
                        HTMLNode contentNode = page.content;

                        //Is this information 'actionable'? If not, could we put it inside the 'console'
                        //if(!isPRNGReady) {
                        //	HTMLNode prngInfoboxContent = ctx.getPageMaker().getInfobox("infobox-error", NodeL10n.getBase().getString("StartupToadlet.entropyErrorTitle"), contentNode, null, true);
                        //	prngInfoboxContent.addChild("#", NodeL10n.getBase().getString("StartupToadlet.entropyErrorContent"));
                        //}


                        //HTMLNode container =  contentNode.addChild("div","class","container");
                        HTMLNode row =  contentNode.addChild("div","class","row");
                        HTMLNode span12 = row.addChild("div", new String[] {"class","id"}, new String[] {"span12 text-center","startupContainer"});
                        span12.addChild("img",new String[] {"src","id"},new String[] {"/static/freenet-logo-blue.png","freenetLogoBlue"});
                        span12.addChild("h2","FREENET");
                        //FIXME: Fix the progress bar width to indicate the real progress
                        HTMLNode progressDiv = span12.addChild("div",new String[]{"class","style"},new String[]{"progress progress-striped active span5","float: none; margin: 0 auto"});
                        progressDiv.addChild("div",new String[]{"class","style"},new String[]{"bar","width: 100%;"});
                        HTMLNode isStartingUp = span12.addChild("h4",NodeL10n.getBase().getString("StartupToadlet.isStartingUp"));

                        HTMLNode wrapperLogDiv = span12.addChild("div",new String[]{"class","id"},new String[]{"well","wrapperLogDiv"});
                        WelcomeToadlet.maybeDisplayWrapperLogfile(ctx, wrapperLogDiv);

                        //TODO: send a Retry-After header ?
                        writeHTMLReply(ctx, 503, desc, pageNode.generate());
                }
        }

        public void setIsPRNGReady() {
                isPRNGReady = true;
        }

        @Override
        public String path() {
                return "/";
        }
}
