package com.zerotrust.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class KeyExchangeTest {
    @Test
    void testGenerateSharedSecret() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();

        String aliceSecret = alice.getSharedSecretString(bob.getPublicKeyString());
        String bobSecret = bob.getSharedSecretString(alice.getPublicKeyString());

        assertNotNull(aliceSecret);
        assertNotNull(bobSecret);
        assertEquals(aliceSecret, bobSecret);
    }

    @Test
    void testGetPublicKeyString() throws Exception {
        KeyExchange exchange = new KeyExchange();
        String publicKey = exchange.getPublicKeyString();
        assertNotNull(publicKey);
        assertFalse(publicKey.isBlank());
    }

    @Test
    void testGetSharedSecretString() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();

        String sharedSecret = alice.getSharedSecretString(bob.getPublicKeyString());
        assertNotNull(sharedSecret);
        assertFalse(sharedSecret.isBlank());
    }
}
