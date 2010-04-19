/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client;

import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.Native8Code;
import com.onionnetworks.fec.PureCode;

import freenet.support.LRUHashtable;
import freenet.support.Logger;

/**
 * FECCodec implementation using the onion code.
 */
public class StandardOnionFECCodec extends FECCodec {
	// REDFLAG: How big is one of these?
	private static int MAX_CACHED_CODECS = 8;

	static boolean noNative;

	private static final LRUHashtable<MyKey, StandardOnionFECCodec> recentlyUsedCodecs = new LRUHashtable<MyKey, StandardOnionFECCodec>();

	private static class MyKey {
		/** Number of input blocks */
		int k;
		/** Number of output blocks, including input blocks */
		int n;

		public MyKey(int n, int k) {
			this.n = n;
			this.k = k;
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof MyKey) {
				MyKey key = (MyKey)o;
				return (key.n == n) && (key.k == k);
			} else return false;
		}

		@Override
		public int hashCode() {
			return (n << 16) + k;
		}
	}

	public synchronized static FECCodec getInstance(int dataBlocks, int checkBlocks) {
		MyKey key = new MyKey(dataBlocks, checkBlocks + dataBlocks);
		StandardOnionFECCodec codec = recentlyUsedCodecs.get(key);
		if(codec != null) {
			recentlyUsedCodecs.push(key, codec);
			return codec;
		}
		codec = new StandardOnionFECCodec(dataBlocks, checkBlocks + dataBlocks);
		recentlyUsedCodecs.push(key, codec);
		while(recentlyUsedCodecs.size() > MAX_CACHED_CODECS) {
			recentlyUsedCodecs.popKey();
		}
		return codec;
	}

	public StandardOnionFECCodec(int k, int n) {
		super(k, n);

		loadFEC();

		logMINOR = Logger.shouldLog(Logger.MINOR, this);
	}

	@Override
	protected void loadFEC() {
		synchronized(this) {
			if(fec != null) return;
		}
		FECCode fec2 = null;
		if(!noNative) {
			try {
				fec2 = new Native8Code(k,n);
				Logger.minor(this, "Loaded native FEC.");

			} catch (Throwable t) {
				if(!noNative) {
					System.err.println("Failed to load native FEC: "+t);
					t.printStackTrace();
				}
				Logger.error(this, "Failed to load native FEC: "+t+" (k="+k+" n="+n+ ')', t);

				if(t instanceof UnsatisfiedLinkError)
					noNative = true;
			}
		}

		if (fec2 != null){
			synchronized(this) {
			fec = fec2;
			}
		} else 	{
			fec2 = new PureCode(k,n);
			synchronized(this) {
				fec = fec2;
			}
		}

		// revert to below if above causes JVM crashes
		// Worst performance, but decode crashes
		// fec = new PureCode(k,n);
		// Crashes are caused by bugs which cause to use 320/128 etc. - n > 256, k < 256.
	}

	@Override
	public int countCheckBlocks() {
		return n-k;
	}

	@Override
	public String toString() {
		return super.toString()+":n="+n+",k="+k;
	}

	@Override
	public short getAlgorithm() {
		return Metadata.SPLITFILE_ONION_STANDARD;
	}
}
