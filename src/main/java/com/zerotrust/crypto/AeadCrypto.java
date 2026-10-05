package com.zerotrust.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Explicit AES-256-GCM encryption for authenticated message payloads. */
public final class AeadCrypto {
    public static final int AES_KEY_BYTES = 32;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AeadCrypto() {
    }

    /**
     * Returns a self-contained value containing nonce followed by ciphertext and
     * tag.
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

    private static Cipher newCipher(int mode, byte[] key, byte[] nonce, byte[] associatedData)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        return cipher;
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != AES_KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must contain exactly 32 bytes");
        }
    }
}
