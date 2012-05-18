/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.simulator;

import freenet.crypt.DummyRandomSource;
import freenet.node.MHProbe;
import freenet.node.Node;
import freenet.node.NodeStarter;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.PooledExecutor;
import freenet.support.io.FileUtil;

import java.io.File;
import java.text.NumberFormat;

/**
 * Create a mesh of nodes and let them sort out their locations.
 * 
 * Then present a user interface to run different types of probes from random nodes.
 */
public class RealNodeProbeTest extends RealNodeTest {

	static final int NUMBER_OF_NODES = 100;
	static final int DEGREE = 5;
	static final short MAX_HTL = (short) 5;
	static final boolean START_WITH_IDEAL_LOCATIONS = true;
	static final boolean FORCE_NEIGHBOUR_CONNECTIONS = true;
	static final boolean ENABLE_SWAPPING = false;
	static final boolean ENABLE_SWAP_QUEUEING = false;
	static final boolean ENABLE_FOAF = true;
	
	public static int DARKNET_PORT_BASE = RealNodeRequestInsertTest.DARKNET_PORT_END;
	public static final int DARKNET_PORT_END = DARKNET_PORT_BASE + NUMBER_OF_NODES;

	public static void main(String[] args) throws Exception {
		System.out.println("Probe test using real nodes:");
		System.out.println();
		String dir = "realNodeProbeTest";
		File wd = new File(dir);
		if(!FileUtil.removeAll(wd)) {
			System.err.println("Mass delete failed, test may not be accurate.");
			System.exit(EXIT_CANNOT_DELETE_OLD_DATA);
		}
		if (!wd.mkdir()) {
			System.err.println("Unabled to create test directory \"" + dir + "\".");
			return;
		}
		NodeStarter.globalTestInit(dir, false, LogLevel.ERROR, "", true);
		// Make the network reproducible so we can easily compare different routing options by specifying a seed.
		DummyRandomSource random = new DummyRandomSource(3142);
		Node[] nodes = new Node[NUMBER_OF_NODES];
		Logger.normal(RealNodeProbeTest.class, "Creating nodes...");
		Executor executor = new PooledExecutor();
		for(int i = 0; i < NUMBER_OF_NODES; i++) {
			System.err.println("Creating node " + i);
			nodes[i] = NodeStarter.createTestNode(DARKNET_PORT_BASE + i, 0, dir, true, MAX_HTL, 0 /* no dropped packets */, random, executor, 500 * NUMBER_OF_NODES, 65536, true, ENABLE_SWAPPING, false, false, false, ENABLE_SWAP_QUEUEING, true, 0, ENABLE_FOAF, false, true, false, null);
			Logger.normal(RealNodeProbeTest.class, "Created node " + i);
		}
		Logger.normal(RealNodeProbeTest.class, "Created " + NUMBER_OF_NODES + " nodes");
		// Now link them up
		makeKleinbergNetwork(nodes, START_WITH_IDEAL_LOCATIONS, DEGREE, FORCE_NEIGHBOUR_CONNECTIONS, random);

		Logger.normal(RealNodeProbeTest.class, "Added random links");

		for(int i = 0; i < NUMBER_OF_NODES; i++) {
			System.err.println("Starting node " + i);
			nodes[i].start(false);
		}

		waitForAllConnected(nodes);

		final NumberFormat nf = NumberFormat.getInstance();
		MHProbe.Listener print = new MHProbe.Listener() {
			@Override
			public void onTimeout() {
				System.out.println("Probe timed out.");
			}

			@Override
			public void onDisconnected() {
				System.out.println("Probe disconnected.");
			}

			@Override
			public void onIdentifier(long identifier) {
				System.out.println("Probe got identifier " + identifier + ".");
			}

			@Override
			public void onLinkLengths(double[] linkLengths) {
				System.out.print("Probe got link lengths: { ");
				for (Double length : linkLengths) System.out.print(length + " ");
				System.out.println("}.");
			}

			@Override
			public void onOutputBandwidth(long outputBandwidth) {
				System.out.println("Probe got bandwidth limit " + nf.format(outputBandwidth) +
				                   " bytes per second.");
			}

			@Override
			public void onBuild(int build) {
				System.out.println("Probe got build " + build + ".");
			}

			@Override
			public void onUptime(long session, double percent48hour) {
				System.out.print("Probe got session uptime " + nf.format(session) + " ms " +
				                 "and 48-hour " + nf.format(percent48hour) + "%.");
			}

			@Override
			public void onStoreSize(long storeSize) {
				System.out.println("Probe got store size " + nf.format(storeSize) + " bytes.");
			}
		};

		final MHProbe.ProbeType types[] = {
			MHProbe.ProbeType.BANDWIDTH,
			MHProbe.ProbeType.BUILD,
			MHProbe.ProbeType.IDENTIFIER,
			MHProbe.ProbeType.LINK_LENGTHS,
			MHProbe.ProbeType.STORE_SIZE,
			MHProbe.ProbeType.UPTIME
		};

		while (true) {
			System.out.println("0) BANDWIDTH");
			System.out.println("1) BUILD");
			System.out.println("2) IDENTIFIER");
			System.out.println("3) LINK_LENGTHS");
			System.out.println("4) STORE_SIZE");
			System.out.println("5) UPTIME");
			System.out.println("Anything else to exit.");
			System.out.println("Select: ");
			try {
				int selection = Integer.valueOf(System.console().readLine());
				int index = random.nextInt(NUMBER_OF_NODES);
				nodes[index].dispatcher.mhProbe.start(MAX_HTL, random.nextLong(), types[selection], print);
			} catch (Exception e) {
				//If a non-number is entered or one outside the bounds.
				System.out.print(e.toString());
				e.printStackTrace();
				//Return isn't enough to exit: the nodes are still in the background.
				System.exit(0);
			}
		}
	}
}
