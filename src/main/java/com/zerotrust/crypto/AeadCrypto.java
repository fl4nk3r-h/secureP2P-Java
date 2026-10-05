package com.zerotrust.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Explicit AES-256-GCM encryption for authenticated message payloads.
 * <p>
 * Produces self-contained {@code nonce || ciphertext || tag} values suitable
 * for transmission over the secure P2P wire protocol. Decryption fails on any
 * ciphertext or associated-data tampering.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see javax.crypto.Cipher
 * @see MlKemKeyExchange
 */
public final class AeadCrypto {
    /** Length in bytes of an AES-256 key. */
    public static final int AES_KEY_BYTES = 32;
    /** Length in bytes of a GCM nonce. */
    public static final int GCM_NONCE_BYTES = 12;
    /** Authentication tag size in bits for AES-GCM. */
    public static final int GCM_TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AeadCrypto() {
    }

    /**
     * Encrypts plaintext with AES-256-GCM using a freshly generated random nonce.
     *
     * @param plaintext      Data to encrypt; must not be {@code null}
     * @param key            32-byte AES-256 key
     * @param associatedData Optional additional authenticated data (AAD); may be
     *                       {@code null} or empty
     * @return Self-contained value containing the nonce followed by the
     *         ciphertext and authentication tag
     * @throws GeneralSecurityException if the underlying cipher fails
     * @throws IllegalArgumentException if {@code plaintext} is {@code null} or the
     *                                  key is not exactly 32 bytes
     * @see #decrypt(byte[], byte[], byte[])
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key, byte[] associatedData)
            throws GeneralSecurityException {
        validateKey(key);
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }

        byte[] nonce = new byte[GCM_NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, key, nonce, associatedData);
        byte[] ciphertext = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(nonce.length + ciphertext.length)
                .put(nonce)
                .put(ciphertext)
                .array();
    }

    /**
     * Decrypts a value produced by {@link #encrypt(byte[], byte[], byte[])}.
     *
     * @param encrypted      Self-contained {@code nonce || ciphertext || tag} value
     * @param key            32-byte AES-256 key used for encryption
     * @param associatedData  AAD exactly as supplied during encryption; may be
     *                       {@code null} or empty
     * @return The recovered plaintext
     * @throws GeneralSecurityException if authentication or decryption fails
     * @throws IllegalArgumentException if the payload is null, too short, or the
     *                                  key is not exactly 32 bytes
     */
    public static byte[] decrypt(byte[] encrypted, byte[] key, byte[] associatedData)
            throws GeneralSecurityException {
        validateKey(key);
        if (encrypted == null || encrypted.length <= GCM_NONCE_BYTES) {
            throw new IllegalArgumentException("encrypted payload is too short");
        }

        byte[] nonce = new byte[GCM_NONCE_BYTES];
        byte[] ciphertext = new byte[encrypted.length - GCM_NONCE_BYTES];
        System.arraycopy(encrypted, 0, nonce, 0, nonce.length);
        System.arraycopy(encrypted, nonce.length, ciphertext, 0, ciphertext.length);
        Cipher cipher = newCipher(Cipher.DECRYPT_MODE, key, nonce, associatedData);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Creates and initializes an AES/GCM/NoPadding cipher.
     *
     * @param mode            Cipher mode ({@code ENCRYPT_MODE} or {@code DECRYPT_MODE})
     * @param key             32-byte AES-256 key
     * @param nonce           12-byte GCM nonce
     * @param associatedData  Optional AAD; ignored when {@code null} or empty
     * @return The initialized cipher
     * @throws GeneralSecurityException if the cipher cannot be instantiated
     */
    private static Cipher newCipher(int mode, byte[] key, byte[] nonce, byte[] associatedData)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        return cipher;
    }

    /**
     * Validates that the supplied key is a non-null 32-byte AES-256 key.
     *
     * @param key Key to validate
     * @throws IllegalArgumentException if the key is {@code null} or not 32 bytes
     */
    private static void validateKey(byte[] key) {
        if (key == null || key.length != AES_KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must contain exactly 32 bytes");
        }
    }
}
