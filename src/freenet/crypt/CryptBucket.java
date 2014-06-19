/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.crypt;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.util.Arrays;

import com.db4o.ObjectContainer;

import freenet.support.api.Bucket;

public final class CryptBucket implements Bucket {
	private static final CryptBucketType defaultType = PreferredAlgorithms.preferredCryptBucketAlg;
	private static final SecureRandom rand = PreferredAlgorithms.sRandom;
	private final CryptBucketType type;
	private final Bucket underlying;
    private SecretKey key;
    private boolean readOnly;
    
//    private FilterInputStream is;
//    private FilterOutputStream os;
    
    private final int OVERHEAD = AEADOutputStream.AES_OVERHEAD;
    
    public CryptBucket(Bucket underlying, byte[] key){
    	this(defaultType, underlying, key);
    }
    
    public CryptBucket(CryptBucketType type, Bucket underlying, byte[] key) {
    	this.type = type;
        this.underlying = underlying;
        this.key = new SecretKeySpec(key, "AES");
    }
    
    public final byte[] decrypt(){
    	byte[] plain = new byte[(int) size()];
    	try {
    		InputStream is = getInputStream();
			is.read(plain);
			is.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return plain;
    }
    
    public final void encrypt(byte[]... input){
    	try {
			OutputStream os = getOutputStream();
			for(byte[] b: input){
				os.write(b);
			}
			os.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	@Override
    public OutputStream getOutputStream() throws IOException {
    	return genOutputStream();
    }
	private final OutputStream genOutputStream() throws IOException {
		if(type.equals(CryptBucketType.AEADAESOCB) || type.equals(CryptBucketType.AEADAESOCBDraft00)){
			boolean isOld = type.equals(CryptBucketType.AEADAESOCBDraft00);

			byte[] nonce;
			if(isOld){
				nonce = new byte[16];
			}else{
				nonce = new byte[15];
			}
			rand.nextBytes(nonce);
			nonce[0] &= 0x7F;

			return new AEADOutputStream(underlying.getOutputStream(), 
					key.getEncoded(), nonce, new AESEngine(), 
					new AESLightEngine(), isOld);
		}
        throw new IOException();
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return genInputStream();
	}
	
	private final InputStream genInputStream() throws IOException {
        if(type.equals(CryptBucketType.AEADAESOCB) || type.equals(CryptBucketType.AEADAESOCBDraft00)){
        	return new AEADInputStream(underlying.getInputStream(), 
        			key.getEncoded(), new AESEngine(), new AESLightEngine(), 
        			type.equals(CryptBucketType.AEADAESOCBDraft00));
        }
        throw new IOException();
	}
	
	@Override
	public String getName() {
		return type.name();
	}
	
	@Override
	public final long size() {
        return underlying.size() - OVERHEAD;
	}
	
	@Override
	public final synchronized boolean isReadOnly() {
		return readOnly;
	}
	
	@Override
	public synchronized void setReadOnly() {
		this.readOnly = true;
	}
	
	@Override
	public final void free() {
        underlying.free();
	}
	
	@Override
	public final void storeTo(ObjectContainer container) {
		underlying.storeTo(container);
        container.store(this);
	}
	
	@Override
	public final void removeFrom(ObjectContainer container) {
		underlying.removeFrom(container);
        container.delete(this);
	}
	
	@Override
	public Bucket createShadow() {
        Bucket undershadow = underlying.createShadow();
        CryptBucket ret = new CryptBucket(undershadow, key.getEncoded());
        ret.setReadOnly();
		return ret;
	}
}