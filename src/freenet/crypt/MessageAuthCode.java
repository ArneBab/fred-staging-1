package freenet.crypt;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Poly1305KeyGenerator;
import org.bouncycastle.util.Arrays;

public class MessageAuthCode {
	private static final MACType defaultType = PreferredAlgorithms.preferredMAC;
	private Mac mac;
	private SecretKey key;
	private IvParameterSpec iv;
	
	public MessageAuthCode() throws NoSuchAlgorithmException{
		this(defaultType);
	}
	
	public MessageAuthCode(MACType type) throws NoSuchAlgorithmException {
		this(type, KeyGenerator.getInstance(defaultType.mac).generateKey());
	}
	
	public MessageAuthCode(MACType type, byte[] cryptoKey) {
		this(type, new SecretKeySpec(cryptoKey, type.mac));	
	}
	
	public MessageAuthCode(MACType type, SecretKey cryptoKey) {
		try {
			mac = type.get();
			key = cryptoKey;
			if(type.ivlen != -1){;
				checkPoly1305Key(key.getEncoded());
				iv = new IvParameterSpec(new byte[type.ivlen]);//FIXME actually gen IV
				mac.init(key, iv);
			}
			else{
				mac.init(key);
			}
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public MessageAuthCode(byte[] key, byte[] iv){
		this(new SecretKeySpec(key, defaultType.mac), iv);
	}
	
	public MessageAuthCode(byte[] key, IvParameterSpec iv){
		this(new SecretKeySpec(key, defaultType.mac), iv);
	}
	
	public MessageAuthCode(SecretKey key, byte[] iv){
		this(key, new IvParameterSpec(iv));
	}
	
	public MessageAuthCode(SecretKey key, IvParameterSpec iv){
		try{
			mac = defaultType.get();
			checkPoly1305Key(key.getEncoded());
			this.key = key;
			this.iv = iv;
			mac.init(key, this.iv);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void checkPoly1305Key(byte[] encodedKey){
		try{
			Poly1305KeyGenerator.checkKey(encodedKey);
		} catch (IllegalArgumentException e){
			//FIXME log error
			//fail("Generated key for algo " + mac.getAlgorithm() + " does not match required Poly1305 format.");
		}
	}
	
	public void addByte(byte input){
		mac.update(input);
	}
	
	public void addBytes(byte[]... input){
		for(byte[] b: input){
			mac.update(b);
		}
	}
	
	public void addBytes(ByteBuffer input){
		mac.update(input);
	}
	
	public void addBytes(byte[] input, int offset, int len){
		mac.update(input, offset, len);
	}
	
	public byte[] getMAC(){
		return mac.doFinal();
	}
	
	public byte[] getMAC(byte[]... input){
		addBytes(input);
		return mac.doFinal();
	}
	
	public boolean verify(byte[] otherMac){
		return Arrays.areEqual(getMAC(), otherMac);
	}
	
	public boolean verify(byte[] mac1, byte[] mac2){
		return Arrays.areEqual(mac1, mac2);
	}
	
	public boolean verifyData(byte[] otherMac, byte[]... data){
		return Arrays.areEqual(getMAC(data), otherMac);
	}
	
	public SecretKey getKey(){
		return key;
	}
	
	public byte[] getEncodedKey(){
		return key.getEncoded();
	}
	
	public byte[] getIV() {
		return iv.getIV();
	}
	
	public IvParameterSpec getIVSpec(){
		return iv;
	}

}