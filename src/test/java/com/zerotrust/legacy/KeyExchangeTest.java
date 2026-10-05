package com.zerotrust.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Archived tests for the pre-v2 DH {@link KeyExchange}.
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see KeyExchange
 */
@Deprecated(forRemoval = false)
class KeyExchangeTest {
    /**
     * Verifies that both parties derive the same shared secret.
     *
     * @throws Exception if key generation or the agreement fails
     */
    @Test
    void generateSharedSecret() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();

        assertEquals(alice.getSharedSecretString(bob.getPublicKeyString()),
                bob.getSharedSecretString(alice.getPublicKeyString()));
    }

    /**
     * Verifies that a public key is exported as a non-blank Base64 string.
     *
     * @throws Exception if key generation fails
     */
    @Test
    void getPublicKeyString() throws Exception {
        String publicKey = new KeyExchange().getPublicKeyString();
        assertNotNull(publicKey);
        assertFalse(publicKey.isBlank());
    }

    /**
     * Verifies that the shared-secret string is produced for a valid peer key.
     *
     * @throws Exception if key generation or the agreement fails
     */
    @Test
    void getSharedSecretString() throws Exception {
        KeyExchange alice = new KeyExchange();
        KeyExchange bob = new KeyExchange();
        assertNotNull(alice.getSharedSecretString(bob.getPublicKeyString()));
    }
}
