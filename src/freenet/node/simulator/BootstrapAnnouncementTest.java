package freenet.node.simulator;

import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;

import freenet.crypt.RandomSource;
import freenet.node.Node;
import freenet.node.NodeInitException;
import freenet.node.NodeStarter;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.PooledExecutor;
import freenet.support.TimeUtil;
import freenet.support.Logger.LogLevel;
import freenet.support.LoggerHook.InvalidThresholdException;
import freenet.support.SimpleFieldSet;
import freenet.support.io.FileUtil;

/**
 * Creates a few test nodes (and one test seed node) and sees if a test-new-node can
 * successfully bootstrop into this micro-network.
 */
public class BootstrapAnnouncementTest {
	
	public static int EXIT_NO_SEEDNODES = 257;
	public static int EXIT_FAILED_TARGET = 258;
	public static int EXIT_THREW_SOMETHING = 259;
	
	public static int DARKNET_PORT = 5006;
	public static int OPENNET_PORT = 8007;
	
	/**
	 * @param args
	 * @throws InvalidThresholdException 
	 * @throws NodeInitException 
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws InvalidThresholdException, NodeInitException, InterruptedException, IOException {
		Node seedNode = null;
		try {
			boolean noSwaps=false;
			String ipOverride = "127.0.0.1";
			if(args.length > 0)
				ipOverride = args[0];
			String testName="announce-test";
			File dir = new File(testName);
			FileUtil.removeAll(dir);
			dir.mkdir();
			RandomSource random = NodeStarter.globalTestInit(dir.getPath(), false, LogLevel.ERROR, "", false);
			// Create one node
			Executor executor = new PooledExecutor();
			seedNode = NodeStarter.createTestNode(DARKNET_PORT, OPENNET_PORT, testName, false, Node.DEFAULT_MAX_HTL, 0, random, executor, 1000, 5*1024*1024, true, true, true, true, true, true, true, 12*1024, false, false, false, false, ipOverride);
			
			//Logger.getChain().setThreshold(LogLevel.ERROR); // kill logging
			
			long startTime = System.currentTimeMillis();
			
			// Start it
			seedNode.start(noSwaps);
			//make it a seed node
			seedNode.setAcceptSeedConnections(true);
			
			//connect a few test nodes to it, about half opennet-enabled
			Node last=null;
			for (int i=1; i<20; i++) {
				//boolean useOpennet=(i%2==0);
				boolean useOpennet=true;
				Node innerNode=NodeStarter.createTestNode(DARKNET_PORT+i, (useOpennet?OPENNET_PORT+i:-1), testName, false, Node.DEFAULT_MAX_HTL, 0, random, executor, 1000, 5*1024*1024, true, true, true, true, true, true, true, 12*1024, false, false, false, false, ipOverride);
				RealNodeTest.connect(seedNode, innerNode);
				if (last!=null)
					RealNodeTest.connect(last, innerNode);
				innerNode.start(noSwaps);
				last=innerNode;
			}
			
			//write the seedNode ref to the magic filename...
			SimpleFieldSet seedRef=seedNode.exportOpennetPublicFieldSet();
			File innerDir = new File(dir, Integer.toString(DARKNET_PORT-1));
			innerDir.mkdir();
			FileWriter out=new FileWriter(new File(innerDir, "seednodes.fref"));
			seedRef.writeTo(out); 
			out.close();
			
			//Logger.getChain().setThreshold(LogLevel.MINOR); // log a bunch
			
			//start one last node (without any connections), connectToSeedNodes=true
			Node lonely=NodeStarter.createTestNode(DARKNET_PORT-1, OPENNET_PORT-1, testName, false, Node.DEFAULT_MAX_HTL, 0, random, executor, 1000, 5*1024*1024, true, true, true, true, true, true, true, 12*1024, false, true, false, false, ipOverride);
			lonely.start(noSwaps);
			
			//see if it will connect to the seed node & the rest...
			Node node=lonely;
			int seconds = 0;
			int targetReachedAt=-1;
			int targetPeers=3;
			
			while(seconds < 600) {
				Thread.sleep(1000);

				int seeds = node.peers.countSeednodes();
				int seedConns = node.peers.getConnectedSeedServerPeersVector(null).size();
				int opennetPeers = node.peers.countValidPeers();
				int opennetConns = node.peers.countConnectedOpennetPeers();
				System.err.println(""+seconds+" : seeds: "+seeds+", connected: "+seedConns
								   +" opennet peers: "+opennetPeers+", connected: "+opennetConns);
				seconds++;
				if(targetReachedAt>0) {
					if (seconds-targetReachedAt>5) {
						System.out.println("Run-off time complete...");
						System.exit(0);
					}
				} else
				if(opennetConns >= targetPeers) {
					long timeTaken = System.currentTimeMillis()-startTime;
					System.out.println("Completed bootstrap ("+targetPeers+" peers) in "+timeTaken+"ms ("+TimeUtil.formatTime(timeTaken)+")");
					targetReachedAt=seconds;
				}
			}
			System.err.println("Failed to reach target peers count "+targetPeers+" in 5 minutes.");
			node.park();
			System.exit(EXIT_FAILED_TARGET);
	    } catch (Throwable t) {
	    	System.err.println("CAUGHT: "+t);
	    	t.printStackTrace();
	    	try {
	    		if(seedNode != null)
	    			seedNode.park();
	    	} catch (Throwable t1) {};
	    	System.exit(EXIT_THREW_SOMETHING);
	    }
	}
}
