package com.zerotrust.crypto;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class CryptoUtilsTest {
    @Test
    void testDecrypt() {
        assertThrows(Exception.class, () -> CryptoUtils.decrypt("not-base64", CryptoUtils.generateKey()));
    }

    @Test
    void testEncrypt() {
        assertThrows(Exception.class, () -> CryptoUtils.encrypt(null, CryptoUtils.generateKey()));
    }

    @Test
    void testGenerateKey() throws Exception {
        assertNotNull(CryptoUtils.generateKey());
        assertEquals(32, CryptoUtils.generateKey().getEncoded().length);
    }

    @Test
    void testGetKeyFromString() throws Exception {
        SecretKey key1 = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey key2 = CryptoUtils.getKeyFromString("shared-secret-value");
        SecretKey key3 = CryptoUtils.getKeyFromString("different-secret-value");

        assertNotNull(key1);
        assertArrayEquals(key1.getEncoded(), key2.getEncoded());
        assertNotEquals(new String(key1.getEncoded()), new String(key3.getEncoded()));
        assertEquals(32, key1.getEncoded().length);
        assertEquals(32, key3.getEncoded().length);
    }

    @Test
    void testEncryptDecryptRoundTrip() throws Exception {
        SecretKey key = CryptoUtils.generateKey();
        String plaintext = "ZeroTrust message payload";
        String ciphertext = CryptoUtils.encrypt(plaintext, key);
        String decrypted = CryptoUtils.decrypt(ciphertext, key);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void testDecryptWithWrongKeyFails() throws Exception {
        SecretKey key1 = CryptoUtils.generateKey();
        SecretKey key2 = CryptoUtils.generateKey();
        String plaintext = "Confidential data";
        String ciphertext = CryptoUtils.encrypt(plaintext, key1);

        assertThrows(Exception.class, () -> CryptoUtils.decrypt(ciphertext, key2));
    }
}
