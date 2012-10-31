package freenet.crypt;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

import freenet.support.Logger;

public class ECDH {

    public final Curves curve;
    private final KeyPair key;
    
    public enum Curves {
        // rfc5903 or rfc6460: it's NIST's random/prime curves : suite B
        // Order matters. Append to the list, do not re-order.
        P256("secp256r1", "AES128", 91, 32),
        P384("secp384r1", "AES192", 120, 48),
        P521("secp521r1", "AES256", 158, 66);
        
        public final ECGenParameterSpec spec;
        private KeyPairGenerator keygenCached;
        /** The symmetric algorithm associated with the curve (use that, nothing else!) */
        public final String defaultKeyAlgorithm;
        /** Expected size of a pubkey */
        public final int modulusSize;
        /** Expected size of the derived secret (in bytes) */
        public final int derivedSecretSize;
        
        private Curves(String name, String defaultKeyAlg, int modulusSize, int derivedSecretSize) {
            this.spec = new ECGenParameterSpec(name);
            this.defaultKeyAlgorithm = defaultKeyAlg;
            this.modulusSize = modulusSize;
            this.derivedSecretSize = derivedSecretSize;
        }
        
        private synchronized KeyPairGenerator getKeyPairGenerator(SecureRandom random) {
        	if(keygenCached != null) return keygenCached;
            KeyPairGenerator kg = null;
            try {
                kg = KeyPairGenerator.getInstance("ECDH");
                kg.initialize(spec, random);
            } catch (NoSuchAlgorithmException e) {
                Logger.error(ECDH.class, "NoSuchAlgorithmException : "+e.getMessage(),e);
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                Logger.error(ECDH.class, "InvalidAlgorithmParameterException : "+e.getMessage(),e);
                e.printStackTrace();
            }
            keygenCached = kg;
            return kg;
        }
        
        public synchronized KeyPair generateKeyPair(SecureRandom random) {
            return getKeyPairGenerator(random).generateKeyPair();
        }
        
        public String toString() {
            return spec.getName();
        }
    }
    
    /**
     * Initialize the ECDH Exchange: this will draw some entropy
     * @param curve The ECC curve to use. Equivalent to a group for DH.
     * @param random The random number generator to use. Not used if we've
     * already been called.
     */
    public ECDH(Curves curve, SecureRandom random) {
        this.curve = curve;
        this.key = curve.generateKeyPair(random);
    }
    
    /**
     * Completes the ECDH exchange: this is CPU intensive
     * @param pubkey
     * @return a SecretKey or null if it fails
     * 
     * **THE OUTPUT SHOULD ALWAYS GO THROUGH A KDF**
     */
    public SecretKey getAgreedSecret(ECPublicKey pubkey) {
        SecretKey key = null;
        try {
            key = getAgreedSecret(pubkey, curve.defaultKeyAlgorithm);
        } catch (InvalidKeyException e) {
            Logger.error(this, "InvalidKeyException : "+e.getMessage(),e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            Logger.error(this, "NoSuchAlgorithmException : "+e.getMessage(),e);
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            Logger.error(this, "InvalidAlgorithmParameterException : "+e.getMessage(),e);
            e.printStackTrace();
        }
        return key;
    }
    
    protected SecretKey getAgreedSecret(PublicKey pubkey, String algorithm) throws InvalidKeyException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyAgreement ka = null;
        ka = KeyAgreement.getInstance("ECDH");
        ka.init(key.getPrivate(), curve.spec);
        ka.doPhase(pubkey, true);
        
        return ka.generateSecret(algorithm);        
    }
    
    public ECPublicKey getPublicKey() {
        return (ECPublicKey) key.getPublic();
    }
    
    /**
     * Returns an ECPublicKey from bytes obtained using ECPublicKey.getEncoded()
     * @param data
     * @return ECPublicKey or null if it fails
     */
    public static ECPublicKey getPublicKey(byte[] data) {
        ECPublicKey remotePublicKey = null;
        try {
            X509EncodedKeySpec ks = new X509EncodedKeySpec(data);
            KeyFactory kf = KeyFactory.getInstance("ECDH");
            remotePublicKey = (ECPublicKey)kf.generatePublic(ks);
            
        } catch (NoSuchAlgorithmException e) {
            Logger.error(ECDH.class, "NoSuchAlgorithmException : "+e.getMessage(),e);
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            Logger.error(ECDH.class, "InvalidKeySpecException : "+e.getMessage(), e);
            e.printStackTrace();
        }
        
        return remotePublicKey;
    }
}
