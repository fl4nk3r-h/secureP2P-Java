package com.zerotrust.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

@Deprecated(forRemoval = false)
class KeyExchangeTest {
    @Test
    void generateSharedSecret() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();

        assertEquals(alice.getSharedSecretString(bob.getPublicKeyString()),
                bob.getSharedSecretString(alice.getPublicKeyString()));
    }

    @Test
    void getPublicKeyString() throws Exception {
        String publicKey = new KeyExchange().getPublicKeyString();
        assertNotNull(publicKey);
        assertFalse(publicKey.isBlank());
    }

    @Test
    void getSharedSecretString() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();
        assertNotNull(alice.getSharedSecretString(bob.getPublicKeyString()));
    }
}
