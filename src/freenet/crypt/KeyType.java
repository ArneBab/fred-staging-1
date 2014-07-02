package freenet.crypt;

public enum KeyType {
	Rijndael128("Rijndael", 128),
	Rijndael256("Rijndael", 256),
	AES128("AES", 128),
	AES256("AES", 256),
	HMACSHA1("HMACSHA1"), 
	HMACSHA256("HMACSHA256"),
	POLY1305("POLY1305-AES"),
	ChaCha("CHACHA", 128);
	
	public final String alg;
	public final int keySize;
	
	KeyType(String alg){
		this.alg = alg;
		this.keySize = -1;
	}
	
	KeyType(String alg, int keySize){
		this.alg = alg;
		this.keySize = keySize;
	}
}
