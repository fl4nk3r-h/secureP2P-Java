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
 */
@Deprecated(forRemoval = false)
public class KeyExchange {
    private KeyPair keyPair;
    private KeyAgreement keyAgreement;

    public KeyExchange() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(1024);
        keyPair = keyPairGenerator.generateKeyPair();
        keyAgreement = KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
    }

    public String getPublicKeyString() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public byte[] generateSharedSecret(String otherPublicKeyString) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(otherPublicKeyString);
        PublicKey otherPublicKey = KeyFactory.getInstance("DH")
                .generatePublic(new X509EncodedKeySpec(decodedKey));
        keyAgreement.doPhase(otherPublicKey, true);
        return keyAgreement.generateSecret();
    }

    public String getSharedSecretString(String otherPublicKeyString) throws Exception {
        return Base64.getEncoder().encodeToString(generateSharedSecret(otherPublicKeyString));
    }
}
