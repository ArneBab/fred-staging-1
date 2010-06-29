/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.crypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import freenet.crypt.ciphers.Rijndael;

/**
 * Control mechanism for the Periodic Cipher Feed Back mode.  This is
 * a CFB variant used apparently by a number of programs, including PGP. 
 * Thanks to Hal for suggesting it.
 * 
 * http://www.streamsec.com/pcfb1.pdf
 * 
 * NOTE: This is identical to CFB if block size = key size. As of Freenet 0.7, 
 * we use it with block size = key size.
 *
 * @author Scott
 */
public class PCFBMode {
    
    protected BlockCipher c;
    protected byte[] feedback_register;
    protected int registerPointer;
    
    public static PCFBMode create(BlockCipher c) {
    	if(c instanceof Rijndael)
    		return new RijndaelPCFBMode((Rijndael)c);
    	return new PCFBMode(c);
    }
    
    public static PCFBMode create(BlockCipher c, byte[] iv) {
    	if(c instanceof Rijndael)
    		return new RijndaelPCFBMode((Rijndael)c, iv);
    	return new PCFBMode(c, iv);
    }
    
    protected PCFBMode(BlockCipher c) {
        this.c = c;
        feedback_register = new byte[c.getBlockSize() >> 3];
        registerPointer = feedback_register.length;
    }

    protected PCFBMode(BlockCipher c, byte[] iv) {
        this(c);
        System.arraycopy(iv, 0, feedback_register, 0, feedback_register.length);
    }

    /**
     * Resets the PCFBMode to an initial IV
     */
    public final void reset(byte[] iv) {
        System.arraycopy(iv, 0, feedback_register, 0, feedback_register.length);
        registerPointer = feedback_register.length;
    }
    
    /**
     * Resets the PCFBMode to an initial IV
     * @param iv The buffer containing the IV.
     * @param offset The offset to start reading the IV at.
     */
    public final void reset(byte[] iv, int offset) {
        System.arraycopy(iv, offset, feedback_register, 0, feedback_register.length);
        registerPointer = feedback_register.length;
    }

    /**
     * Writes the initialization vector to the stream.  Though the IV
     * is transmitted in the clear, this gives the attacker no additional 
     * information because the registerPointer is set so that the encrypted
     * buffer is empty.  This causes an immediate encryption of the IV,
     * thus invalidating any information that the attacker had.
     */
    public void writeIV(RandomSource rs, OutputStream out) throws IOException {
        rs.nextBytes(feedback_register);
        out.write(feedback_register);
    }
    
    /**
     * Reads the initialization vector from the given stream.  
     */
    public void readIV(InputStream in) throws IOException {
        //for (int i=0; i<feedback_register.length; i++) {
        //    feedback_register[i]=(byte)in.read();
        //}
        Util.readFully(in, feedback_register);
    }

    /**
     * returns the length of the IV
     */
    public int lengthIV() {
        return feedback_register.length;
    }

    /**
     * Deciphers one byte of data, by XOR'ing the ciphertext byte with
     * one byte from the encrypted buffer.  Then places the received
     * byte in the feedback register.  If no bytes are available in 
     * the encrypted buffer, the feedback register is encrypted, providing
     * block_size/8 new bytes for decryption
     */
    //public synchronized int decipher(int b) {
    public int decipher(int b) {
        if (registerPointer == feedback_register.length) refillBuffer();
        int rv = (feedback_register[registerPointer] ^ (byte) b) & 0xff;
        feedback_register[registerPointer++] = (byte) b;
        return rv;
    }

    /**
     * NOTE: As a side effect, this will decrypt the data in the array.
     */
    //public synchronized byte[] blockDecipher(byte[] buf, int off, int len) {
    public byte[] blockDecipher(byte[] buf, int off, int len) {
        while (len > 0) {
            if (registerPointer == feedback_register.length) refillBuffer();
            int n = Math.min(len, feedback_register.length - registerPointer);
            for (int i=off; i<off+n; ++i) {
                byte b = buf[i];
                buf[i] ^= feedback_register[registerPointer];
                feedback_register[registerPointer++] = b;
            }
            off += n;
            len -= n;
        }
        return buf;
    }

    /**
     * Enciphers one byte of data, by XOR'ing the plaintext byte with
     * one byte from the encrypted buffer.  Then places the enciphered 
     * byte in the feedback register.  If no bytes are available in 
     * the encrypted buffer, the feedback register is encrypted, providing
     * block_size/8 new bytes for encryption
     */
    //public synchronized int encipher(int b) {
    public int encipher(int b) {
        if (registerPointer == feedback_register.length) refillBuffer();
        feedback_register[registerPointer] ^= (byte) b;
        return feedback_register[registerPointer++] & 0xff;
    }

    /**
     * NOTE: As a sideeffect, this will encrypt the data in the array.
     */
    //public synchronized byte[] blockEncipher(byte[] buf, int off, int len) {
    public byte[] blockEncipher(byte[] buf, int off, int len) {
        while (len > 0) {
            if (registerPointer == feedback_register.length) refillBuffer();
            int n = Math.min(len, feedback_register.length - registerPointer);
            for (int i=off; i<off+n; ++i)
                buf[i] = (feedback_register[registerPointer++] ^= buf[i]);
            off += n;
            len -= n;
        }
        return buf;
    }
        
    // Refills the encrypted buffer with data.
    //private synchronized void refillBuffer() {
    protected void refillBuffer() {
        // Encrypt feedback into result
        c.encipher(feedback_register, feedback_register);

        registerPointer=0;
    }
}
