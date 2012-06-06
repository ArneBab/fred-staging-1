package freenet.node;

import freenet.config.SubConfig;
import freenet.io.comm.AsyncMessageFilterCallback;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.DisconnectedException;
import freenet.io.comm.Message;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.io.comm.PeerContext;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Handles starting, routing, and responding to Metropolis-Hastings corrected probes.
 *
 * Possible future additions to these probes' results include:
 * <ul>
 * <li>Checking whether a key is present in the datastore, either only at the endpoint or each node along the way.</li>
 * <li>Success rates for remote requests by HTL; perhaps over some larger amount of time than the past hour.</li>
 * </ul>
 */
public class MHProbe implements ByteCounter {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;
	private static volatile boolean logWARNING;

	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logWARNING = Logger.shouldLog(Logger.LogLevel.WARNING, this);
				logMINOR = Logger.shouldLog(Logger.LogLevel.MINOR, this);
				logDEBUG = Logger.shouldLog(Logger.LogLevel.DEBUG, this);
			}
		});
	}

	private final static String sourceDisconnect = "Previous step in probe chain no longer connected.";

	public static final byte MAX_HTL = 50;

	/**
	 * Probability of HTL decrement at HTL = 1.
	 */
	public static final double DECREMENT_PROBABILITY = 0.2;

	/**
	 * In ms, per HTL above HTL = 1.
	 */
	public static final int TIMEOUT_PER_HTL = 3000;

	/**
	 * In ms, to account for probabilistic decrement at HTL = 1.
	 */
	public static final int TIMEOUT_HTL1 = (int)(TIMEOUT_PER_HTL / DECREMENT_PROBABILITY);

	/**
	 * Minute in milliseconds.
	 */
	private static final long MINUTE = 60 * 1000;

	/**
	 * Maximum number of accepted probes in the last minute.
	 */
	public static final byte MAX_ACCEPTED = 5;

	private class SynchronizedCounter {
		private byte c = 0;

		public synchronized void increment() { c++; }
		public synchronized void decrement() { c--; }
		public synchronized byte value() { return c; }
	}

	/**
	 * Number of accepted probes in the last minute.
	 */
	private final SynchronizedCounter accepted;

	private final Node node;

	private final Timer timer;

	public enum ProbeError {
		/**
		 * The node being waited on to provide a response disconnected.
		 */
		DISCONNECTED,
		/**
		 * A node cannot accept the request because its probe DoS protection has tripped.
		 */
		OVERLOAD,
		/**
		 * Timed out while waiting for a response.
		 */
		TIMEOUT,
		/**
		 * Only used locally, not sent over the network. The local node did not recognize the error used.
		 * This should always be specified along with the description string containing the remote error.
		 */
		UNKNOWN,
		/**
		 * A remote node did not recognize the requested probe type. For locally started probes it will not be
		 * a ProbeError but a ProtocolError.
		 */
		UNRECOGNIZED_TYPE
	}

	public enum ProbeType {
		BANDWIDTH,
		BUILD,
		IDENTIFIER,
		LINK_LENGTHS,
		STORE_SIZE,
		UPTIME_48H,
		UPTIME_7D
	}

	/**
	 * Listener for the different types of probe results.
	 */
	public interface Listener {
		/**
		 * An error occurred.
		 @param error type: What error occurred. Can be one of MHProbe.ProbeError.
		 * @param description description: Arbitrary string, Defined if the error type is UNKNOWN,
		 *                                 Contains the unrecognized error from the message.
		 */
		void onError(ProbeError error, String description);

		/**
		 * Endpoint opted not to respond with the requested information.
		 */
		void onRefused();

		/**
		 * Output bandwidth limit result.
		 * @param outputBandwidth endpoint's reported output bandwidth limit in KiB per second.
		 */
		void onOutputBandwidth(long outputBandwidth);

		/**
		 * Build result.
		 * @param build endpoint's reported build / main version.
		 */
		void onBuild(int build);

		/**
		 * Identifier result.
		 * @param identifier identifier given by endpoint.
		 * @param uptimePercentage quantized noisy 7-day uptime percentage
		 */
		void onIdentifier(long identifier, long uptimePercentage);

		/**
		 * Link length result.
		 * @param linkLengths endpoint's reported link lengths.
		 */
		void onLinkLengths(double[] linkLengths);

		/**
		 * Store size result.
		 * @param storeSize endpoint's reported store size in GiB.
		 */
		void onStoreSize(long storeSize);

		/**
		 * Uptime result.
		 * @param uptimePercentage endpoint's reported percentage uptime in the last requested period; either
		 *                         48 hour or 7 days.
		 */
		void onUptime(double uptimePercentage);
	}

	/**
	 * Applies random noise proportional to the input value.
	 * @param input Value to apply noise to.
	 * @return Value +/- Gaussian percentage.
	 */
	private double randomNoise(double input) {
		return input + (node.random.nextGaussian() * 0.01 * input);
	}

	/**
	 * Applies random noise proportional to the input value.
	 * @param input Value to apply noise to.
	 * @return Value +/- Gaussian percentage.
	 */
	private long randomNoise(long input) {
		return input + Math.round(node.random.nextGaussian() * 0.01 * input);
	}

	/**
	 * Counts as probe request transfer.
	 * @param bytes Bytes received.
	 */
	@Override
	public void sentBytes(int bytes) {
		node.nodeStats.probeRequestCtr.sentBytes(bytes);
	}

	/**
	 * Counts as probe request transfer.
	 * @param bytes Bytes received.
	 */
	@Override
	public void receivedBytes(int bytes) {
		node.nodeStats.probeRequestCtr.receivedBytes(bytes);
	}

	/**
	 * No payload in probes.
	 * @param bytes Ignored.
	 */
	@Override
	public void sentPayload(int bytes) {}

	public MHProbe(Node node) {
		this.node = node;
		this.accepted = new SynchronizedCounter();
		this.timer = new Timer(true);
	}

	/**
	 * Sends an outgoing probe request.
	 * @param htl htl for this outgoing probe: should be [1, MAX_HTL]
	 * @param listener Something which implements MHProbe.Listener and will be called with results.
	 * @see MHProbe.Listener
	 */
	public void start(final byte htl, final long uid, final ProbeType type, final Listener listener) {
		Message request = DMT.createMHProbeRequest(htl, uid, type);
		request(request, null, new ResultListener(listener));
	}

	/**
	 * Same as its three-argument namesake, but responds to results by passing them on to source.
	 * @param message probe request, (possibly made by DMT.createMHProbeRequest) containing HTL
	 * @param source node from which the probe request was received. Used to relay back results.
	 */
	public void request(Message message, PeerNode source) {
		AsyncMessageFilterCallback cb;
		try {
			cb = new ResultRelay(source, message.getLong(DMT.UID));
		} catch (IllegalArgumentException e) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Received probe request from null source.", e);
			return;
		}
		request(message, source, cb);
	}

	/**
	 * Processes an incoming probe request.
	 * If the probe has a positive HTL, routes with MH correction and probabilistically decrements HTL.
	 * If the probe comes to have an HTL of zero: (an incoming HTL of zero is taken to be one.)
	 * Returns (as node settings allow) exactly one of:
	 * <ul>
	 *         <li>unique identifier and integer 7-day uptime percentage</li>
	 *         <li>uptime: 48-hour percentage or 7-day percentage</li>
	 *         <li>output bandwidth</li>
	 *         <li>store size</li>
	 *         <li>link lengths</li>
	 *         <li>build number</li>
	 * </ul>
	 *
	 * @param message probe request, containing HTL
	 * @param source node from which the probe request was received. Used to relay back results. If null, it is
	 *               considered to have been sent from the local node.
	 * @param callback callback for probe response
	 */
	public void request(final Message message, final PeerNode source, final AsyncMessageFilterCallback callback) {
		final Long uid = message.getLong(DMT.UID);
		if (accepted.value() >= MAX_ACCEPTED) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Already accepted maximum number of probes; rejecting incoming.");
			try {
				Message overload = DMT.createMHProbeError(uid, ProbeError.OVERLOAD);
				//Locally sent message.
				if (source == null) {
					callback.onMatched(overload);
				} else {
					source.sendAsync(overload, null, this);
				}
			} catch (NotConnectedException e) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Source of excess probe no longer connected.", e);
			} catch (NullPointerException e) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Source of excess probe no longer connected.", e);
			}
			return;
		}
		ProbeType type;
		try {
			type = ProbeType.valueOf(message.getString(DMT.TYPE));
			if (logDEBUG) Logger.debug(MHProbe.class, "Probe type is " + type.name() + ".");
		} catch (IllegalArgumentException e) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Invalid probe type \"" + message.getString(DMT.TYPE) + "\".", e);
			try {
				Message unrecognized = DMT.createMHProbeError(uid, ProbeError.UNRECOGNIZED_TYPE);
				//Locally sent message.
				if (source == null) {
					callback.onMatched(unrecognized);
				} else {
					source.sendAsync(unrecognized, null, this);
				}
			} catch (NotConnectedException f) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Source of unrecognized result type is no longer connected.", f);
			} catch (NullPointerException f) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Source of unrecognized result type is no longer connected.", f);
			}
			return;
		}
		byte htl = message.getByte(DMT.HTL);
		if (htl < 1) {
			if (logWARNING) Logger.warning(MHProbe.class, "Received out-of-bounds HTL of " + htl + "; interpreting as 1.");
			htl = 1;
		} else if (htl > MAX_HTL) {
			if (logWARNING) Logger.warning(MHProbe.class, "Received out-of-bounds HTL of " + htl + "; interpreting as " + MAX_HTL + ".");
			htl = MAX_HTL;
		}
		accepted.increment();
		//One-minute window on acceptance; free up this probe's slot in 60 seconds.
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				accepted.decrement();
			}
		}, MINUTE);

		/*
		 * Route to a peer, using Metropolis-Hastings correction and ignoring backoff to get a more uniform
		 * endpoint distribution. HTL is decremented before routing so that it's possible to respondlocally.
		 */
		htl = probabilisticDecrement(htl);
		if (htl == 0) {
			respond(type, uid, source, callback);
		} else {
			route(message, type, uid, htl, source, callback);
		}
	}

	/**
	 * Attempts to route the message to a peer. If HTL is decremented to zero before this is possible, responds.
	 */
	private void route(final Message message, final ProbeType type, final long uid, byte htl,
	                   final PeerNode source, final AsyncMessageFilterCallback callback) {
		//Degree of the local node.
		int degree = degree();
		PeerNode candidate;
		//Loop until HTL runs out, in which case return a result, or the probe is relayed on to a peer.
		for (; htl > 0; htl = probabilisticDecrement(htl)) {
			//Can't handle a probe request if not connected to any peers.
			if (degree == 0) {
				if (logMINOR) Logger.minor(MHProbe.class, "Aborting received probe request because there are no connections.");
				/*
				 * If this is a locally-started request, not a relayed one, give an error.
				 * Otherwise, in this case there's nowhere to send the error.
				 */
				//TODO: Is it safe to manually call callback methods like this?
				if (callback instanceof ResultListener) callback.onDisconnect(null);
				return;
			}
			try {
				candidate = node.peers.myPeers[node.random.nextInt(degree)];
			} catch (IndexOutOfBoundsException e) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Peer count changed during candidate search.", e);
				degree = degree();
				continue;
			}
			//acceptProbability is the MH correction.
			double acceptProbability;
			try {
				acceptProbability = (double)degree / candidate.getDegree();
			} catch (ArithmeticException e) {
				/*
				 * Candidate's degree is zero: its peer locations are unknown.
				 * Cannot do M-H correction; fall back to random walk.
				 */
				if (logDEBUG) Logger.debug(MHProbe.class, "Peer (" + candidate.userToString() +
					") has no FOAF data.", e);
				acceptProbability = 1.0;
			} catch (NullPointerException e) {
				//Candidate's peer location array is null. See above for reasoning.
				if (logDEBUG) Logger.debug(MHProbe.class, "Peer (" + candidate.userToString() +
					") has no FOAF data.", e);
				acceptProbability = 1.0;
			}
			if (logDEBUG) Logger.debug(MHProbe.class, "acceptProbability is " + acceptProbability);
			if (node.random.nextDouble() < acceptProbability) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Accepted candidate.");
				if (candidate.isConnected()) {
					final int timeout = (htl - 1) * TIMEOUT_PER_HTL + TIMEOUT_HTL1;
					//Filter for response to this probe with requested result type.
					final MessageFilter filter = MessageFilter.create().setSource(candidate).setField(DMT.UID, uid).setTimeout(timeout);
					switch (type) {
						case BANDWIDTH: filter.setType(DMT.MHProbeBandwidth); break;
						case BUILD: filter.setType(DMT.MHProbeBuild); break;
						case IDENTIFIER: filter.setType(DMT.MHProbeIdentifier); break;
						case LINK_LENGTHS: filter.setType(DMT.MHProbeLinkLengths); break;
						case STORE_SIZE: filter.setType(DMT.MHProbeStoreSize); break;
						case UPTIME_48H:
						case UPTIME_7D: filter.setType(DMT.MHProbeUptime); break;
					}
					//Refusal or an error should also be listened for so it can be relayed.
					filter.or(MessageFilter.create().setSource(candidate).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.MHProbeRefused)
						.or(MessageFilter.create().setSource(candidate).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.MHProbeError)));
					message.set(DMT.HTL, htl);
					try {
						node.usm.addAsyncFilter(filter, callback, this);
						if (logDEBUG) Logger.debug(MHProbe.class, "Sending.");
						candidate.sendAsync(message, null, this);
						return;
					} catch (NotConnectedException e) {
						if (logDEBUG) Logger.debug(MHProbe.class, "Peer became disconnected between check and send attempt.", e);
					} catch (DisconnectedException e) {
						if (logDEBUG) Logger.debug(MHProbe.class, "Peer became disconnected while attempting to add filter.", e);
						callback.onDisconnect(candidate);
					} catch (NullPointerException e) {
						if (logDEBUG) Logger.debug(MHProbe.class, "Peer became disconnected between check and send attempt.", e);
					}
				}
			}
		}
		/*
		 * HTL has been decremented to zero; return a result.
		 */
		respond(type, uid, source, callback);
	}

	/**
	 * Depending on node settings, sends a message to source containing either a refusal or the requested result.
	 */
	private void respond(final ProbeType type, final long uid, final PeerNode source,
	                     final AsyncMessageFilterCallback callback) {
		Message result;

		if (!respondTo(type)) {
			result = DMT.createMHProbeRefused(uid);
		} else {
			switch (type) {
				case BANDWIDTH:
					//1,024 (2^10) bytes per KiB
					result = DMT.createMHProbeBandwidth(uid, randomNoise(Math.round((double)node.config.get("node").getInt("outputBandwidthLimit")/1024)));
					break;
				case BUILD:
					result = DMT.createMHProbeBuild(uid, node.nodeUpdater.getMainVersion());
					break;
				case IDENTIFIER:
					//7-day uptime with random noise, then quantized.
					result = DMT.createMHProbeIdentifier(uid,
					                                     node.config.get("node").getLong("identifier"),
					                                     Math.round(randomNoise(100*node.uptime.getUptimeWeek())));
					break;
				case LINK_LENGTHS:
					double[] linkLengths = new double[degree()];
					int i = 0;
					for (PeerNode peer : node.peers.connectedPeers) {
						linkLengths[i++] = randomNoise(Math.min(Math.abs(peer.getLocation() - node.peers.node.getLocation()),
							1.0 - Math.abs(peer.getLocation() - node.peers.node.getLocation())));
					}
					result = DMT.createMHProbeLinkLengths(uid, linkLengths);
					break;
				case STORE_SIZE:
					//1,073,741,824 bytes (2^30) per GiB
					result = DMT.createMHProbeStoreSize(uid, randomNoise(Math.round((double)node.config.get("node").getLong("storeSize")/1073741824)));
					break;
				case UPTIME_48H:
					result = DMT.createMHProbeUptime(uid, randomNoise(100*node.uptime.getUptime()));
					break;
				case UPTIME_7D:
					result = DMT.createMHProbeUptime(uid, randomNoise(100*node.uptime.getUptimeWeek()));
					break;
				default:
					if (logDEBUG) Logger.debug(MHProbe.class, "Response for probe result type \"" + type + "\" is not implemented.");
					return;
			}
		}
		//Returning result to probe sent locally.
		if (source == null) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Returning locally sent probe.");
			callback.onMatched(result);
			return;
		}
		try {
			if (logDEBUG) Logger.debug(MHProbe.class, "Sending response to probe.");
			source.sendAsync(result, null, this);
		} catch (NotConnectedException e) {
			if (logDEBUG) Logger.debug(MHProbe.class, sourceDisconnect, e);
		} catch (NullPointerException e) {
			if (logDEBUG) Logger.debug(MHProbe.class, sourceDisconnect, e);
		}
	}

	private boolean respondTo(ProbeType type) {
		final SubConfig nc = node.config.get("node");
		switch (type){
		case BANDWIDTH: return nc.getBoolean("probeBandwidth");
		case BUILD: return nc.getBoolean("probeBuild");
		case IDENTIFIER: return nc.getBoolean("probeIdentifier");
		case LINK_LENGTHS: return nc.getBoolean("probeLinkLengths");
		case STORE_SIZE: return nc.getBoolean("probeStoreSize");
		case UPTIME_48H:
		case UPTIME_7D: return nc.getBoolean("probeUptime");
		default:
			//There a valid ProbeType value that is not present here.
			if (logDEBUG) Logger.debug(MHProbe.class, "Probe type \"" + type.name() + "\" does not check" +
			                                          "if a response is allowed by the user; refusing.");
			return false;
		}
	}

	/**
	 * Decrements 20% of the time at HTL 1; otherwise always. This is to protect the responding node, whereas the
	 * anonymity of the node which initiated the request is not a concern.
	 * @param htl current HTL
	 * @return new HTL
	 */
	private byte probabilisticDecrement(byte htl) {
		assert(htl > 0);
		if (htl == 1) {
			if (node.random.nextDouble() < DECREMENT_PROBABILITY) return 0;
			return 1;
		}
		return (byte)(htl - 1);
	}

	/**
	 * @return number of peers the local node is connected to.
	 */
	private int degree() {
		return node.peers.connectedPeers.length;
	}

	/**
	 * Filter listener which determines the type of result and calls the appropriate probe listener method.
	 * This is used for returning probe results via FCP. It is for probes started by this node.
	 */
	private class ResultListener implements AsyncMessageFilterCallback {

		private final Listener listener;

		/**
		 * @param listener to call appropriate methods for events such as matched messages or timeout.
		 */
		public ResultListener(Listener listener) {
			this.listener = listener;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			listener.onError(ProbeError.DISCONNECTED, null);
		}

		/**
		 * Parses provided message and calls appropriate MHProbe.Listener method for the type of result.
		 * @param message Probe result.
		 */
		@Override
		public void onMatched(Message message) {
			if(logDEBUG) Logger.debug(MHProbe.class, "Matched " + message.getSpec().getName());
			if (message.getSpec().equals(DMT.MHProbeBandwidth)) {
				listener.onOutputBandwidth(message.getLong(DMT.OUTPUT_BANDWIDTH_UPPER_LIMIT));
			} else if (message.getSpec().equals(DMT.MHProbeBuild)) {
				listener.onBuild(message.getInt(DMT.BUILD));
			} else if (message.getSpec().equals(DMT.MHProbeIdentifier)) {
				listener.onIdentifier(message.getLong(DMT.IDENTIFIER), message.getLong(DMT.UPTIME_PERCENT));
			} else if (message.getSpec().equals(DMT.MHProbeLinkLengths)) {
				listener.onLinkLengths(message.getDoubleArray(DMT.LINK_LENGTHS));
			} else if (message.getSpec().equals(DMT.MHProbeStoreSize)) {
				listener.onStoreSize(message.getLong(DMT.STORE_SIZE));
			} else if (message.getSpec().equals(DMT.MHProbeUptime)) {
				listener.onUptime(message.getDouble(DMT.UPTIME_PERCENT));
			} else if (message.getSpec().equals(DMT.MHProbeError)) {
				final String errorType = message.getString(DMT.TYPE);
				try {
					if (errorType.equals(ProbeError.UNKNOWN.name()) && logWARNING) {
						Logger.warning(MHProbe.class, "Unexpectedly received local error \"" +
							errorType + "\" from remote node.");
					}
					listener.onError(ProbeError.valueOf(errorType), null);
				} catch (IllegalArgumentException e) {
					listener.onError(ProbeError.UNKNOWN, errorType);
					if (logDEBUG) Logger.debug(MHProbe.class, "Unknown error type \"" + errorType +
						"\".");
				}
			} else if (message.getSpec().equals(DMT.MHProbeRefused)) {
				listener.onRefused();
			}  else {
				if (logDEBUG) Logger.debug(MHProbe.class, "Unknown probe result set " + message.getSpec().getName());
			}
		}

		@Override
		public void onRestarted(PeerContext context) {}

		@Override
		public void onTimeout() {
			listener.onError(ProbeError.TIMEOUT, null);
		}

		@Override
		public boolean shouldTimeout() {
			return false;
		}
	}

	/**
	 * Filter listener which relays messages (intended to be responses to the probe) to the node (intended to be
	 * that from which the probe request was received) given during construction. Used for received probe requests.
	 */
	private class ResultRelay implements AsyncMessageFilterCallback {

		private final PeerNode source;
		private final Long uid;

		/**
		 * @param source peer from which the request was received and to which send the response.
		 * @throws IllegalArgumentException if source is null.
		 */
		public ResultRelay(PeerNode source, Long uid) throws IllegalArgumentException {
			if (source == null) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Probe " + uid + " source is null.");
				//No way to relay results back.
				throw new IllegalArgumentException(sourceDisconnect);
			}
			this.source = source;
			this.uid = uid;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Next node in chain disconnected.");
			try {
				source.sendAsync(DMT.createMHProbeError(uid, ProbeError.DISCONNECTED), null, MHProbe.this);
			} catch (NotConnectedException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			} catch (NullPointerException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			}
		}

		/**
		 * Sends an incoming probe response to the originator.
		 * @param message probe response.
		 */
		@Override
		public void onMatched(Message message) {
			if (source == null) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect);
				return;
			}

			//TODO: If result is a tracer request, can add local results to it here.
			if (logDEBUG) Logger.debug(MHProbe.class, "Relaying " + message.getSpec().getName() + " back" +
			                                          " to " + source.userToString());
			try {
				source.sendAsync(message, null, MHProbe.this);
			} catch (NotConnectedException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			} catch (NullPointerException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			}
		}

		@Override
		public void onRestarted(PeerContext context) {}

		@Override
		public void onTimeout() {
			if(logDEBUG) Logger.debug(MHProbe.class, "Relay timed out.");
			try {
				source.sendAsync(DMT.createMHProbeError(uid, ProbeError.TIMEOUT), null, MHProbe.this);
			} catch (NotConnectedException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			} catch (NullPointerException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect, e);
			}
		}

		@Override
		public boolean shouldTimeout() {
			return false;
		}
	}
}
