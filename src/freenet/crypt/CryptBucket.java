/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.crypt;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.engines.AESLightEngine;

import com.db4o.ObjectContainer;

import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.io.ArrayBucket;
import freenet.support.io.ArrayBucketFactory;

/**
 * CryptBucket is a bucket filter that encrypts all data going to the underlying
 * bucket and decrypts all data read from the underlying bucket. This is done using
 * AEAD.
 * Warning: 
 * Avoid using Closer.close() on InputStream's opened on this Bucket. The MAC is only 
 * checked when the end of the bucket is reached, which may be in read() or may 
 * be in close().
 */
public final class CryptBucket implements Bucket {
	private static final CryptBucketType defaultType = PreferredAlgorithms.preferredCryptBucketAlg;
	private static final SecureRandom rand = PreferredAlgorithms.sRandom;
	private final CryptBucketType type;
	private final Bucket underlying;
    private SecretKey key;
    private boolean readOnly;
    
    private FilterOutputStream outStream;
    
    private final int OVERHEAD = AEADOutputStream.AES_OVERHEAD;
    
    public CryptBucket(Bucket underlying, byte[] key){
    	this(defaultType, underlying, key);
    }
    
    public CryptBucket(CryptBucketType type, Bucket underlying, byte[] key){
    	this(type, underlying, KeyUtils.getSecretKey(key, type.keyType), false);
    }
    
    public CryptBucket(CryptBucketType type, Bucket underlying){
    	this(type, underlying, KeyUtils.genSecretKey(type.keyType), false);
    }
    
    /**
     * Creates instance of CryptBucket using the algorithm type with the specified key
     * to decrypt the underlying bucket and encrypt it as well if it is not readOnly
     * @param type What kind of cipher and mode to use for encryption
     * @param underlying The bucket that will be storing the encrypted data
     * @param key The key that will be used for encryption
     * @param readOnly Sets if the bucket will be read-only 
     */
    public CryptBucket(CryptBucketType type, Bucket underlying, SecretKey key, boolean readOnly) {
    	this.type = type;
        this.underlying = underlying;
        this.key = key;
        this.readOnly = readOnly;
    }
    
    /**
     * Decrypts the data in the underlying bucket.
     * @return Returns the unencrypted data in a byte[]
     */
    public final byte[] decrypt(){
    	byte[] plain = new byte[(int) size()];
    	try {
    		FilterInputStream is = genInputStream();
			is.read(plain);
			is.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return plain;
    }
    
    /**
     * Checks if an output/encryption stream has been generated yet.
     * If one hasen't then it generates one. 
     * @throws IOException
     */
    private final void checkOutStream() throws IOException{
    	if(!readOnly){
    		if(outStream == null){
    			outStream = genOutputStream();
    		}
    	}
    	else{
    		throw new IOException("Read only");
    	}
    }
    
    /**
     * Adds a byte to be encrypted into the underlying bucket
     * @param input Byte to be encrypted
     * @throws IOException
     */
    public final void addByte(byte input) throws IOException{
    	checkOutStream();
    	try {
    		outStream.write(input);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    /**
     * Adds byte[]s to be encrypted into the underlying bucket
     * @param input Any number of byte[]s to be encrypted
     * @throws IOException
     */
    public final void addBytes(byte[]... input) throws IOException{
    	checkOutStream();
    	try {
			for(byte[] b: input){
				outStream.write(b);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    /**
     * Adds a selection of a byte[] to be encrypted into the underlying bucket
     * @param input The byte[] to encrypt
     * @param offset Where in the byte[] to start encrypting
     * @param len How many bytes after offset to encrypt and send to underlying bucket
     * @throws IOException
     */
    public final void addBytes(byte[] input, int offset, int len) throws IOException{
    	checkOutStream();
    	try {
    		outStream.write(input, offset, len);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    /**
     * 
     * @throws IOException
     */
    public final void encrypt() throws IOException{
    	if(outStream == null){
    		throw new IOException();
    	}
    	checkOutStream();
    	outStream.close();
    	outStream = null;
    }
    
    public final void encrypt(byte[]... input) throws IOException{
    	addBytes(input);
    	encrypt();
    }
    
	@Override
    public OutputStream getOutputStream() throws IOException {
    	return genOutputStream();
    }
	
	private final FilterOutputStream genOutputStream() throws IOException {
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
				key.getEncoded(), nonce, new AESFastEngine(), 
				new AESLightEngine(), isOld);
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return genInputStream();
	}
	
	private final FilterInputStream genInputStream() throws IOException {return new AEADInputStream(underlying.getInputStream(), 
        			key.getEncoded(), new AESFastEngine(), new AESLightEngine(), 
        			type.equals(CryptBucketType.AEADAESOCBDraft00));
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
	public final synchronized void setReadOnly() {
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
	public final Bucket createShadow() {
        Bucket undershadow = underlying.createShadow();
        CryptBucket ret = new CryptBucket(undershadow, key.getEncoded());
        ret.setReadOnly();
		return ret;
	}
	
	public byte[] toByteArray(){
		return ((ArrayBucket)underlying).toByteArray();
	}
}