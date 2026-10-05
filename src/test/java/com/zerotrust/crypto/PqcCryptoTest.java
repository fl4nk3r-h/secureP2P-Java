package com.zerotrust.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Tests for the v2 post-quantum crypto primitives.
 * <p>
 * Covers AES-256-GCM round trips, AAD binding, nonce freshness, and
 * ML-KEM-768 encapsulation/decapsulation consistency.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see AeadCrypto
 * @see MlKemKeyExchange
 */
class PqcCryptoTest {
    /**
     * Verifies an AES-GCM round trip succeeds with matching associated data.
     *
     * @throws Exception if encryption or decryption fails
     */
    @Test
    void aesGcmRoundTripBindsAssociatedData() throws Exception {
        byte[] key = new byte[AeadCrypto.AES_KEY_BYTES];
        byte[] plaintext = "authenticated payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] aad = "v2:message:1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encrypted = AeadCrypto.encrypt(plaintext, key, aad);
        byte[] decrypted = AeadCrypto.decrypt(encrypted, key, aad);

        assertArrayEquals(plaintext, decrypted);
        assertFalse(Arrays.equals(plaintext, encrypted));
    }

    /**
     * Verifies each encryption uses a fresh nonce and that modifying the
     * ciphertext or AAD causes decryption to fail.
     *
     * @throws Exception if encryption fails
     */
    @Test
    void aesGcmUsesFreshNonceAndRejectsModifiedCiphertext() throws Exception {
        byte[] key = new byte[AeadCrypto.AES_KEY_BYTES];
        byte[] plaintext = "same plaintext".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] aad = "header".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] first = AeadCrypto.encrypt(plaintext, key, aad);
        byte[] second = AeadCrypto.encrypt(plaintext, key, aad);
        assertNotEquals(Arrays.toString(first), Arrays.toString(second));

        second[second.length - 1] ^= 1;
        assertThrows(Exception.class, () -> AeadCrypto.decrypt(second, key, aad));
        assertThrows(Exception.class,
                () -> AeadCrypto.decrypt(first, key, "wrong-header".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /**
     * Verifies that ML-KEM-768 encapsulation and decapsulation produce the
     * same shared secret.
     *
     * @throws Exception if key generation or KEM operations fail
     */
    @Test
    void mlKem768ProducesMatchingSharedSecret() throws Exception {
        KeyPair recipient = MlKemKeyExchange.generateKeyPair();

        MlKemKeyExchange.Encapsulation encapsulation = MlKemKeyExchange.encapsulate(recipient.getPublic().getEncoded());
        byte[] decapsulated = MlKemKeyExchange.decapsulate(
                recipient.getPrivate().getEncoded(), encapsulation.ciphertext());

        assertArrayEquals(encapsulation.sharedSecret(), decapsulated);
    }
}
