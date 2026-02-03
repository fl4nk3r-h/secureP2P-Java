package com.zerotrust.crypto;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for AES encryption and decryption
 * Status: Stable
 * Location: com.zerotrust.crypto
 * 
 * @author Aritra Chakraborty
 * @version 1.0
 */
public class CryptoUtils {
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Decrypt data with AES
     * 
     * @param encryptedData Base64 encoded ciphertext
     * @param key           SecretKey for AES
     * @return Decrypted plain text
     * @throws Exception if decryption fails
     */
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedData);
    }

    /**
     * Generate a random AES key
     * 
     * @throws Exception if key generation fails
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Convert a shared secret string to a SecretKey using SHA-256 hashing.
     * <p>
     * This method uses SHA-256 to hash the input, which always produces a 32-byte
     * (256-bit) output, suitable for AES-256 encryption. This ensures consistent
     * key derivation regardless of the input length.
     * </p>
     * 
     * @param keyString The shared secret string (can be any length)
     * @return SecretKey object with 256-bit AES key
     * @throws Exception if hashing fails
     */
    public static SecretKey getKeyFromString(String keyString) throws Exception {
        // Hash the shared secret using SHA-256 to get consistent 32-byte key
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(keyString.getBytes("UTF-8"));

        // Create AES key from the hash (always 32 bytes = 256 bits)
        return new SecretKeySpec(hash, "AES");
    }
}
