package com.zerotrust.legacy;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.KeyAgreement;

/**
 * Archived pre-v2 finite-field DH exchange. Not used by the active peer
 * protocol.
 * <p>
 * Implements a 1024-bit Diffie-Hellman key agreement retained for reference.
 * Both parties exchange Base64 public keys and derive the same shared secret.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see com.zerotrust.crypto.MlKemKeyExchange
 */
@Deprecated(forRemoval = false)
public class KeyExchange {
    private KeyPair keyPair;
    private KeyAgreement keyAgreement;

    /**
     * Generates a 1024-bit DH key pair and initializes the key agreement.
     *
     * @throws Exception if DH key generation or initialization fails
     */
    public KeyExchange() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(1024);
        keyPair = keyPairGenerator.generateKeyPair();
        keyAgreement = KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
    }

    /**
     * @return Base64-encoded representation of the local public key
     */
    public String getPublicKeyString() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Computes the DH shared secret with the given remote public key.
     *
     * @param otherPublicKeyString Base64-encoded remote DH public key
     * @return The raw shared secret bytes
     * @throws Exception if key decoding or the agreement phase fails
     */
    public byte[] generateSharedSecret(String otherPublicKeyString) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(otherPublicKeyString);
        PublicKey otherPublicKey = KeyFactory.getInstance("DH")
                .generatePublic(new X509EncodedKeySpec(decodedKey));
        keyAgreement.doPhase(otherPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * Computes the DH shared secret and encodes it for transmission.
     *
     * @param otherPublicKeyString Base64-encoded remote DH public key
     * @return Base64-encoded shared secret
     * @throws Exception if key decoding or the agreement phase fails
     */
    public String getSharedSecretString(String otherPublicKeyString) throws Exception {
        return Base64.getEncoder().encodeToString(generateSharedSecret(otherPublicKeyString));
    }
}
