/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.crypt;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import freenet.crypt.ciphers.Rijndael;
import freenet.support.Logger;

/**
 * CryptBitSet will encrypt and decrypt both byte[]s and BitSets with a specified
 * algorithm, key, and also an iv if the algorithm requires one. 
 * @author unixninja92
*/
public class CryptBitSet {
	public final static CryptBitSetType defaultType = CryptBitSetType.ChaCha;
	private CryptBitSetType type;
	private SecretKey key;

	//Used for AES and ChaCha ciphers
	private Cipher cipher;
	
	//These variables are used with Rijndael ciphers
	private BlockCipher blockCipher;
	private PCFBMode pcfb;
	private byte[] iv;
	private int ivLength;
	
	/**
	 * Creates an instance of CryptBitSet that will be able to encrypt and decrypt 
	 * sets of bytes using the algorithm type with the specified key.
	 * @param type The symmetric algorithm, mode, and key and block size to use
	 * @param key The key that will be used for encryption
	 */
	public CryptBitSet(CryptBitSetType type, SecretKey key){
		this.type = type;

		try {
			if(type.cipherName == "AES"){
				cipher = Cipher.getInstance(type.algName, PreferredAlgorithms.aesCTRProvider);
				this.key = key;
			} else if(type.cipherName == "Rijndael"){
				blockCipher = new Rijndael(type.keyType.keySize, type.blockSize);
				blockCipher.initialize(key.getEncoded());
				if(type == CryptBitSetType.RijndaelPCFB){
					ivLength = type.blockSize >> 3;
					pcfb = PCFBMode.create(blockCipher, genIV());
				}
			}
		}  catch (GeneralSecurityException e) {
			Logger.error(CryptBitSet.class, "Unexpected error; please report:", e);
		} catch (UnsupportedCipherException e) {
			Logger.error(CryptBitSet.class, "Unexpected error; please report:", e);
		} 
	}
	
	public CryptBitSet(CryptBitSetType type, byte[] key){
		this(type, KeyUtils.getSecretKey(key, type.keyType));
	}
	
	
	/**
	 * Creates an instance of CryptBitSet that will be able to encrypt and decrypt 
	 * sets of bytes using the algorithm type with the specified key and iv. Should 
	 * only be used with RijndaelPCFB
	 * @param type
	 * @param key
	 * @param iv
	 * @throws UnsupportedTypeException 
	 */
	public CryptBitSet(CryptBitSetType type, SecretKey key, byte[] iv, int offset) throws UnsupportedTypeException {
		this.type = type;
		this.key = key;
		ivLength = type.blockSize >> 3;
		System.arraycopy(iv, offset, this.iv, 0, ivLength);
		try{
			if(type == CryptBitSetType.RijndaelPCFB){
				blockCipher = new Rijndael(type.keyType.keySize, type.blockSize);
				blockCipher.initialize(key.getEncoded());
				pcfb = PCFBMode.create(blockCipher, iv);
			}
			else {
				throw new UnsupportedTypeException(type);
			}
		} catch (UnsupportedCipherException e) {
			Logger.error(CryptBitSet.class, "Unexpected error; please report:", e);
		}
	}
	
	public CryptBitSet(CryptBitSetType type, SecretKey key, byte[] iv) throws UnsupportedTypeException{
		this(type, key, iv, 0);
	}
	
	public CryptBitSet(CryptBitSetType type, byte[] key, byte[] iv, int offset) throws UnsupportedTypeException {
		this(type, KeyUtils.getSecretKey(key, type.keyType), iv, offset);
	}
	
	public CryptBitSet(CryptBitSetType type, byte[] key, byte[] iv) throws UnsupportedTypeException {
		this(type, key, iv, 0);
	}

	/**
	 * Encrypts or decrypts a specified section of input.
	 * @param mode Sets mode to either encrypt or decrypt. 0 for decryption,
	 * 1 for encryption.
	 * @param input The byte[] to be processes(either encrypted or decrypted)
	 * @param offset The position in the byte[] to start processing at
	 * @param len How many more bytes to process in the array past offset
	 * @return Depending on the value of mode will either return an encrypted
	 * or decrypted version of the selected portion of the byte[] input
	 */
	private byte[] processesBytes(int mode, byte[] input, int offset, int len){
		try {
			if(type.cipherName == "Rijndael"){
				if(mode == 0){
					switch(type){
					case RijndaelPCFB:
						return pcfb.blockDecipher(input, offset, len);
					case RijndaelECB:
					case RijndaelECB128:
						byte[] actualInput = extractSmallerArray(input, offset, len);
						byte[] result = new byte[len];
						blockCipher.decipher(actualInput, result);
						return result;
					case RijndaelCTR:
						break;
					}
				}
				else{
					switch(type){
					case RijndaelPCFB:
						return pcfb.blockEncipher(input, offset, len);
					case RijndaelECB:
					case RijndaelECB128:
						byte[] actualInput = extractSmallerArray(input, offset, len);
						byte[] result = new byte[len];
						blockCipher.encipher(actualInput, result);
						return result;
					case RijndaelCTR:
						break;
					}
				}
			}
			else if(type.cipherName == "AES"){
				cipher.init(mode, key);
				return cipher.doFinal(input, offset, len);
			}
		} catch (GeneralSecurityException e) {
			Logger.error(CryptBitSet.class, "Unexpected error; please report:", e);
		} 
		return null;
	}
	
	/**
	 * Encrypts the specified section of input
	 * @param input The bytes to be encrypted
	 * @param offset The position of input to start encrypting at
	 * @param len The number of bytes after offset to encrypt
	 * @return Returns byte[] input with the specified section encrypted
	 */
	public byte[] encrypt(byte[] input, int offset, int len){
		return processesBytes(1, input, offset, len);
	}
	
	/**
	 * Encrypts the entire byte[] input
	 * @param input The byte[] to be encrypted
	 * @return The encrypted byte[]
	 */
	public byte[] encrypt(byte[] input){
		return encrypt(input, 0, input.length);
	}
	
	/**
	 * Encrypts the BitSet input
	 * @param input The BitSet to encrypt
	 * @return The encrypted BitSet
	 */
	public BitSet encrypt(BitSet input){
		return BitSet.valueOf(encrypt(input.toByteArray()));
	}
	
	/**
	 * Decrypts the specified section of input
	 * @param input The bytes to be decrypted
	 * @param offset The position of input to start decrypting at
	 * @param len The number of bytes after offset to decrypt
	 * @return Returns byte[] input with the specified section decrypted
	 */
	public byte[] decrypt(byte[] input, int offset, int len){
		return processesBytes(0, input, offset, len);
	}
	
	/**
	 * Decrypts the entire byte[] input
	 * @param input The byte[] to be decrypted
	 * @return The decrypted byte[]
	 */
	public byte[] decrypt(byte[] input){
		return decrypt(input, 0, input.length);
	}
	
	/**
	 * Decrypts the BitSet input
	 * @param input The BitSet to decrypt
	 * @return The decrypted BitSet
	 */
	public BitSet decrypt(BitSet input){
		return BitSet.valueOf(decrypt(input.toByteArray()));
	}
	
	public void setIV(byte[] iv){
		this.iv = iv;
	}
	
	public byte[] genIV(){
		byte[] newIV = new byte[ivLength];
		PreferredAlgorithms.random.nextBytes(newIV);
		this.iv = newIV;
		return newIV;
	}
	
	public byte[] getIV(){
		return iv;
	}
	
	private byte[] extractSmallerArray(byte[] input, int offset, int len){
		if(input.length == len){
			return input;
		}
		else{
			byte[] result = new byte[len];
			System.arraycopy(input, offset, result, 0, len);
			return result;
		}
	}
}
