/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.crypt;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;

import net.i2p.util.NativeBigInteger;
import freenet.node.FSParseException;
import freenet.node.NodeStarter;
import freenet.support.Base64;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

@SuppressWarnings("deprecation")
public final class CryptSignature{
    public static final SigType preferredSignature = SigType.ECDSAP256;
    private static final String verifyError = "CryptSignature inalized in verify only mode. "
            + "Can not sign in this mode";
    private boolean verifyOnly;

    /* variables for ECDSA signatures */
    private final SigType type;
    private KeyPair keys;
    private Signature sign;
    private Signature verify;

    /* Variables for DSA signatures */
    /** Length of signature parameters R and S */
    private static final int SIGNATURE_PARAMETER_LENGTH = 32;
    private static final DSAGroup dsaGroup = Global.DSAgroupBigA;
    private static final SecureRandom random = NodeStarter.getGlobalSecureRandom();
    private final Hash sha256 = new Hash(HashType.SHA256);
    private DSAPrivateKey dsaPrivK;
    private DSAPublicKey dsaPubK;

    /**
     * Creates an instance of CryptSignature and generates a key pair for the 
     * algorithm specified by type
     * @param type The Signing algorithm used 
     */
    public CryptSignature(SigType type){
        this.type = type;
        if(type.name()=="DSA"){
            dsaPrivK = new DSAPrivateKey(dsaGroup, random);
            dsaPubK = new DSAPublicKey(dsaGroup, dsaPrivK);
        }
        else {
            try {
                keys = KeyGenUtils.genKeyPair(type.keyType);

                sign = type.get();
                sign.initSign(keys.getPrivate());
                verify = type.get();
                verify.initVerify(keys.getPublic());
            } catch (UnsupportedTypeException e) {
                Logger.error(CryptSignature.class, "Internal error; please report:", e);
            } catch (InvalidKeyException e) {
                Logger.error(CryptSignature.class, "Internal error; please report:", e);
            }
        }
        verifyOnly = false;
    }

    /**
     * Will initialize CryptSignature to be able to verify a message was signed with 
     * the public key.
     * @param type Type of Signature algorithm used
     * @param publicKey The public key that can be used to verify signatures
     * @throws CryptFormatException
     * @throws InvalidKeyException
     */
    public CryptSignature(SigType type, byte[] publicKey) throws CryptFormatException, 
    InvalidKeyException{
        this.type = type;
        verifyOnly = true;
        if(type.name()=="DSA"){
            dsaPrivK = null;
            dsaPubK = DSAPublicKey.create(publicKey);
        }
        else{
            try {
                keys = KeyGenUtils.getPublicKeyPair(type.keyType, publicKey);
                verify = type.get();
                verify.initVerify(keys.getPublic());
            } catch (UnsupportedTypeException e) {
                Logger.error(CryptSignature.class, "Internal error; please report:", e);
            }
        }
    }
    
    /**
     * Will initialize CryptSignature to be able to verify a message was signed with 
     * the public key.
     * @param type Type of Signature algorithm used
     * @param publicKey The public key that can be used to verify signatures
     * @throws CryptFormatException
     * @throws InvalidKeyException
     */
    public CryptSignature(SigType type, ByteBuffer publicKey) throws CryptFormatException, 
    InvalidKeyException{
        this(type, publicKey.array());
    }

    public CryptSignature(SigType type, KeyPair pair) throws InvalidKeyException{
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        this.type = type;
        this.keys = pair;
        verifyOnly = (pair.getPrivate() == null);

        verify = type.get();
        verify.initVerify(keys.getPublic());
        if(!verifyOnly){
            sign = type.get();
            sign.initSign(keys.getPrivate());
        }
    }

    public CryptSignature(SigType type, PublicKey pub, PrivateKey pri) throws InvalidKeyException{
        this(type, KeyGenUtils.getKeyPair(pub, pri));
    }

    /**
     * Initialize the CryptSignature object: from an SFS generated by asFieldSet()
     * @param sfs The SimpleFieldSet that contains the public and private key pair
     * @param type
     * @throws FSParseException
     */
    public CryptSignature(SigType type, SimpleFieldSet sfs) throws FSParseException{
        this.type = type;
        verifyOnly = false;
        try {
            if(type.equals(SigType.DSA)){
                dsaPrivK = DSAPrivateKey.create(sfs.subset("dsaPrivKey"), dsaGroup);
                dsaPubK = DSAPublicKey.create(sfs.subset("dsaPubKey"), dsaGroup);
            }
            else{
                byte[] pub = null;
                byte[] pri = null;
                pub = Base64.decode(sfs.get("pub"));
                pri = Base64.decode(sfs.get("pri"));

                keys = KeyGenUtils.getKeyPair(type.keyType, pub, pri);

                sign = type.get();
                sign.initSign(keys.getPrivate());
                verify = type.get();
                verify.initVerify(keys.getPublic());
            }
        }  catch (Exception e) {
            throw new FSParseException(e);
        }
    }

    /**
     * Creates an instance of CryptSignature using the global DSAGroup and
     * the passed in key pair
     * @param priv DSA private key
     * @param pub DSA public key
     */
    public CryptSignature(DSAPublicKey pub, DSAPrivateKey priv){
        type = SigType.DSA;
        dsaPrivK = priv;
        dsaPubK = pub;
        verifyOnly = false;
    }

    public CryptSignature(DSAPublicKey pub){
        type = SigType.DSA;
        dsaPubK = pub;
        verifyOnly = true;
    }

    /**
     * Add the passed in byte to the bytes that will be signed.
     * @param input Byte to be signed
     * @throws UnsupportedTypeException 
     */
    private void addByte(Signature sig, byte input) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            sig.update(input);
        } catch (SignatureException e) {
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
    }

    /**
     * Add the passed in byte[] to the bytes that will be signed.
     * @param input Byte[] to be signed
     */
    private void addBytes(Signature sig, byte[]... input) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            for(byte[] b: input){
                sig.update(b);
            }
        } catch (SignatureException e) {
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
    }

    /**
     * Add the specified portion of the passed in byte[] to the bytes that 
     * will be signed.
     * @param data Byte[] to be signed
     * @param offset Where to start reading bytes for signature
     * @param length How many bytes after offset to use for signature
     */
    private void addBytes(Signature sig, byte[] data, int offset, int length) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            sig.update(data, offset, length);
        } catch (SignatureException e) {
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
    }

    /**
     * Adds the ByteBuffer to the bytes that will be signed.
     * @param input ByteBuffer to be signed
     */
    private void addBytes(Signature sig, ByteBuffer input) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            sig.update(input);
        } catch (SignatureException e) {
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
    }

    public void addByteToSign(byte input) {
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        addByte(sign, input);
    }

    public void addBytesToSign(byte[]... input) {
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        addBytes(sign, input);
    }

    public void addBytesToSign(byte[] input, int offset, int length) {
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        addBytes(sign, input, offset, length);
    }

    public void addBytesToSign(ByteBuffer input) {
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        addBytes(sign, input);
    }

    public void addByteToVerify(byte input) {
        addByte(verify, input);
    }

    public void addBytesToVerify(byte[]... input) {
        addBytes(verify, input);
    }

    public void addBytesToVerify(byte[] input, int offset, int length) {
        addBytes(verify, input, offset, length);
    }

    public void addBytesToVerify(ByteBuffer input) {
        addBytes(verify, input);
    }

    public ByteBuffer sign(){
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        byte[] result = null;
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            while(true) {
                result = sign.sign();
                if(result.length <= type.maxSigSize)
                    break;
                else
                    Logger.error(this, "DER encoded signature used "+result.length+" bytes, more "
                            + "than expected "+type.maxSigSize+" - re-signing...");
            }
        } catch (SignatureException e) {
            e.printStackTrace();
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
        return ByteBuffer.wrap(result);
    }

    /**
     * Signs the byte[]s passed in
     * @param data byte[]s to be signed
     * @return The signature of the signed data
     */
    public ByteBuffer sign(byte[]... data) {
        if(verifyOnly){
            throw new IllegalStateException(verifyError);
        }
        byte[] result = null;
        if(type == SigType.DSA){
            try {
                DSASignature sig;
                sig = signToDSASignature(data);
                result = new byte[SIGNATURE_PARAMETER_LENGTH*2];
                System.arraycopy(sig.getRBytes(SIGNATURE_PARAMETER_LENGTH), 0, result, 0, 
                        SIGNATURE_PARAMETER_LENGTH);
                System.arraycopy(sig.getSBytes(SIGNATURE_PARAMETER_LENGTH), 0, result, 
                        SIGNATURE_PARAMETER_LENGTH, SIGNATURE_PARAMETER_LENGTH);
            } catch (UnsupportedTypeException e) {
                Logger.error(CryptSignature.class, "Internal error; please report:", e);
            }
        }
        else{
            addBytesToSign(data);
            result = sign().array();
        }
        return ByteBuffer.wrap(result);
    }
    
    /**
     * Signs the ByteBuffer passed in
     * @param data ByteBuffer to be signed
     * @return The signature of the signed data
     */
    public ByteBuffer sign(ByteBuffer data) {
        return sign(data.array());
    }

    /**
     * Signs byte[]s to DSASignature
     * @param data Byte[]s to sign
     * @return Returns the DSASignature of the data
     * @throws UnsupportedTypeException 
     */
    public DSASignature signToDSASignature(byte[]... data) {
        if(type != SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        return signToDSASignature(new NativeBigInteger(1, sha256.genHash(data)));
    }
    
    /**
     * Signs ByteBuffer to DSASignature
     * @param data ByteBuffer to sign
     * @return Returns the DSASignature of the data
     * @throws UnsupportedTypeException 
     */
    public DSASignature signToDSASignature(ByteBuffer data) {
        return signToDSASignature(data.array());
    }

    /**
     * Signs data m to DSASignature
     * @param m BitIteger to be signed
     * @return Returns the DSASignature of m
     * @throws UnsupportedTypeException 
     */
    public DSASignature signToDSASignature(BigInteger m) {
        if(type != SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        DSASignature result = null;
        if(!verifyOnly){
            result = DSA.sign(dsaGroup, dsaPrivK, m, random);
        } else{
            throw new IllegalStateException(verifyError);
        }
        return result;
    }

    /**
     * Sign data and return a fixed size signature. The data does not need to be hashed, the 
     * signing code will handle that for us, using an algorithm appropriate for the keysize.
     * @return A zero padded DER signature (maxSigSize). Space Inefficient but constant-size.
     */
    public byte[] signToNetworkFormat(byte[]... data) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        byte[] plainsig = sign(data).array();
        if(!verifyOnly){
            int targetLength = type.maxSigSize;

            if(plainsig.length != targetLength) {
                byte[] newData = new byte[targetLength];
                if(plainsig.length < targetLength) {
                    System.arraycopy(plainsig, 0, newData, 0, plainsig.length);
                } else {
                    throw new IllegalStateException("Too long!");
                }
                plainsig = newData;
            }
        } else{
            throw new IllegalStateException(verifyError);
        }
        return plainsig;
    }
    
    /**
     * Sign data and return a fixed size signature. The data does not need to be hashed, the 
     * signing code will handle that for us, using an algorithm appropriate for the keysize.
     * @return A zero padded DER signature (maxSigSize). Space Inefficient but constant-size.
     */
    public byte[] signToNetworkFormat(ByteBuffer data) {
        return signToNetworkFormat(data.array());
    }

    /**
     * Verifies that a signature is signed by the public key the class was 
     * instantiated with
     * @param signature The signature to verify
     * @return If the signature is valid it returns true, otherwise it returns false.
     */
    public boolean verify(byte[] signature, int offset, int len) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        try {
            return verify.verify(signature, offset, len);
        } catch (SignatureException e) {
            Logger.error(CryptSignature.class, "Internal error; please report:", e);
        }
        return false;
    }

    public boolean verify(byte[] sig) {
        return verify(sig, 0, sig.length);
    }
    
    public boolean verify(ByteBuffer sig) {
        return verify(sig.array());
    }

    /**
     * Verifies that the Signature of the byte[] data matches the signature passed in
     * @param signature Signature to be verified
     * @param data Data to be signed
     * @return True if the signature matches the signature generated for the passed in 
     * data, otherwise false
     */
    public boolean verifyData(byte[] signature, byte[]... data){
        if(type == SigType.DSA) {
            int x = 0;
            byte[] bufR = new byte[SIGNATURE_PARAMETER_LENGTH];
            byte[] bufS = new byte[SIGNATURE_PARAMETER_LENGTH];

            System.arraycopy(signature, x, bufR, 0, SIGNATURE_PARAMETER_LENGTH);
            x+=SIGNATURE_PARAMETER_LENGTH;
            System.arraycopy(signature, x, bufS, 0, SIGNATURE_PARAMETER_LENGTH);

            NativeBigInteger r = new NativeBigInteger(1, bufR);
            NativeBigInteger s = new NativeBigInteger(1, bufS);
            try {
                return verifyData(r, s, data);
            } catch (UnsupportedTypeException e) {
                Logger.error(CryptSignature.class, "Internal error; please report:", e);
            }
        }
        else {
            addBytesToVerify(data);
            return verify(signature);
        }
        return false; 
    }
    
    public boolean verifyData(ByteBuffer signature, ByteBuffer data){
        return verifyData(signature.array(), data.array());
    }

    /**
     * Verifies that the signature of m matches the signature passed in
     * @param sig Signature to be verified
     * @param m Data to be signed
     * @return True if the signature matches the signature generated for the passed in 
     * data, otherwise false
     */
    public boolean verifyData(DSASignature sig, BigInteger m) {
        if(type != SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        return DSA.verify(dsaPubK, sig, m, false);
    }

    public boolean verifyData(BigInteger r, BigInteger s, BigInteger m) {
        return verifyData(new DSASignature(r, s), m);
    }

    public boolean verifyData(DSASignature sig, byte[]... data) {
        return verifyData(sig, new NativeBigInteger(1, sha256.genHash(data)));
    }
    
    public boolean verifyData(DSASignature sig, ByteBuffer data) {
        return verifyData(sig, data.array());
    }

    public boolean verifyData(BigInteger r, BigInteger s, byte[]... data) {
        return verifyData(r, s, new NativeBigInteger(1, sha256.genHash(data)));
    }
    
    public boolean verifyData(BigInteger r, BigInteger s, ByteBuffer data) {
        return verifyData(r, s, data.array());
    }

    public ECPublicKey getPublicKey() throws UnsupportedTypeException{
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        return (ECPublicKey) keys.getPublic();
    }

    /**
     * Returns an SFS containing:
     *  - the private key
     *  - the public key
     *  - the name of the curve in use
     *  
     *  It should only be used in NodeCrypto
     * @param includePrivate - include the (secret) private key
     * @return SimpleFieldSet
     */
    public SimpleFieldSet asFieldSet(boolean includePrivate) {
        if(type == SigType.DSA){
            throw new UnsupportedTypeException(type);
        }
        SimpleFieldSet fs = new SimpleFieldSet(true);
        SimpleFieldSet fsCurve = new SimpleFieldSet(true);
        fsCurve.putSingle("pub", Base64.encode(keys.getPublic().getEncoded()));
        if(includePrivate)
            fsCurve.putSingle("pri", Base64.encode(keys.getPrivate().getEncoded()));
        fs.put(type.name(), fsCurve);
        return fs;
    }
}