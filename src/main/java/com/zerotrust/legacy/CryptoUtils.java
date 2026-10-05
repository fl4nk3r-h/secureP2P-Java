package com.zerotrust.legacy;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Archived pre-v2 AES helper. Not used by the active peer protocol.
 * <p>
 * Provides legacy AES/CBC (default JCE "AES" transformation) Base64 encryption
 * and key utilities. Retained for reference; the v2 protocol uses
 * {@link com.zerotrust.crypto.AeadCrypto} instead.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see com.zerotrust.crypto.AeadCrypto
 */
@Deprecated(forRemoval = false)
public class CryptoUtils {
    /**
     * Encrypts a string and encodes the result as Base64.
     *
     * @param data  Plaintext to encrypt; must not be {@code null}
     * @param key   AES secret key
     * @return Base64-encoded ciphertext
     * @throws Exception if encryption fails
     */
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
    }

    /**
     * Decodes Base64 ciphertext and decrypts it.
     *
     * @param encryptedData Base64-encoded ciphertext
     * @param key           AES secret key used for encryption
     * @return The recovered plaintext
     * @throws Exception if Base64 decoding or decryption fails
     */
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
    }

    /**
     * Generates a new 256-bit AES key.
     *
     * @return The generated AES {@link SecretKey}
     * @throws Exception if key generation fails
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, new SecureRandom());
        return keyGenerator.generateKey();
    }

    /**
     * Derives a deterministic AES key by hashing the given string with SHA-256.
     *
     * @param keyString Secret string to hash
     * @return AES {@link SecretKey} over the 32-byte SHA-256 digest
     * @throws Exception if the digest or key construction fails
     */
    public static SecretKey getKeyFromString(String keyString) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(keyString.getBytes("UTF-8"));
        return new SecretKeySpec(hash, "AES");
    }
}
