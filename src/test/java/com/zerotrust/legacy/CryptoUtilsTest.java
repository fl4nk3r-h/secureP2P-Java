package com.zerotrust.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

/**
 * Archived tests for the pre-v2 {@link CryptoUtils} AES helper.
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see CryptoUtils
 */
@Deprecated(forRemoval = false)
class CryptoUtilsTest {
    /**
     * Verifies that decrypting invalid Base64 fails.
     */
    @Test
    void decryptRejectsInvalidBase64() {
        assertThrows(Exception.class, () -> CryptoUtils.decrypt("not-base64", CryptoUtils.generateKey()));
    }

    /**
     * Verifies that encrypting a null plaintext fails.
     */
    @Test
    void encryptRejectsNullPlaintext() {
        assertThrows(Exception.class, () -> CryptoUtils.encrypt(null, CryptoUtils.generateKey()));
    }

    /**
     * Verifies that generated keys are 256-bit AES keys.
     *
     * @throws Exception if key generation fails
     */
    @Test
    void generateKeyReturnsAes256Key() throws Exception {
        assertNotNull(CryptoUtils.generateKey());
        assertEquals(32, CryptoUtils.generateKey().getEncoded().length);
    }

    /**
     * Verifies that string-to-key derivation is deterministic and distinct per
     * input.
     *
     * @throws Exception if the digest fails
     */
    @Test
    void sharedSecretDerivationIsDeterministic() throws Exception {
        SecretKey first = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey second = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey different = CryptoUtils.getKeyFromString("different-secret-value");

        assertArrayEquals(first.getEncoded(), second.getEncoded());
        assertNotEquals(new String(first.getEncoded()), new String(different.getEncoded()));
    }

    /**
     * Verifies an encrypt/decrypt round trip recovers the original plaintext.
     *
     * @throws Exception if encryption or decryption fails
     */
    @Test
    void encryptDecryptRoundTrip() throws Exception {
        SecretKey key = CryptoUtils.generateKey();
        String ciphertext = CryptoUtils.encrypt("legacy payload", key);
        assertEquals("legacy payload", CryptoUtils.decrypt(ciphertext, key));
    }
}
