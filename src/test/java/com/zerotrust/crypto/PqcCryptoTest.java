package com.zerotrust.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PqcCryptoTest {
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
        assertThrows(Exception.class, () -> AeadCrypto.decrypt(first, key, "wrong-header".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void mlKem768ProducesMatchingSharedSecret() throws Exception {
        KeyPair recipient = MlKemKeyExchange.generateKeyPair();

        MlKemKeyExchange.Encapsulation encapsulation =
                MlKemKeyExchange.encapsulate(recipient.getPublic().getEncoded());
        byte[] decapsulated = MlKemKeyExchange.decapsulate(
                recipient.getPrivate().getEncoded(), encapsulation.ciphertext());

        assertArrayEquals(encapsulation.sharedSecret(), decapsulated);
    }
}
