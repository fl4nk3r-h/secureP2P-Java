package com.zerotrust.legacy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

@Deprecated(forRemoval = false)
class CryptoUtilsTest {
    @Test
    void decryptRejectsInvalidBase64() {
        assertThrows(Exception.class, () -> CryptoUtils.decrypt("not-base64", CryptoUtils.generateKey()));
    }

    @Test
    void encryptRejectsNullPlaintext() {
        assertThrows(Exception.class, () -> CryptoUtils.encrypt(null, CryptoUtils.generateKey()));
    }

    @Test
    void generateKeyReturnsAes256Key() throws Exception {
        assertNotNull(CryptoUtils.generateKey());
        assertEquals(32, CryptoUtils.generateKey().getEncoded().length);
    }

    @Test
    void sharedSecretDerivationIsDeterministic() throws Exception {
        SecretKey first = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey second = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey different = CryptoUtils.getKeyFromString("different-secret-value");

        assertArrayEquals(first.getEncoded(), second.getEncoded());
        assertNotEquals(new String(first.getEncoded()), new String(different.getEncoded()));
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        SecretKey key = CryptoUtils.generateKey();
        String ciphertext = CryptoUtils.encrypt("legacy payload", key);
        assertEquals("legacy payload", CryptoUtils.decrypt(ciphertext, key));
    }
}
