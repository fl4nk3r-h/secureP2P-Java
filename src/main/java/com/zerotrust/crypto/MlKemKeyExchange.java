package com.zerotrust.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.util.PrivateKeyFactory;
import org.bouncycastle.pqc.crypto.util.PublicKeyFactory;

/**
 * ML-KEM-768 key generation and KEM operations backed by Bouncy Castle.
 * <p>
 * Provides post-quantum key encapsulation for the v2 peer protocol: a sender
 * encapsulates a shared secret against a recipient's public key and the
 * recipient decapsulates it with their private key.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see AeadCrypto
 * @see org.bouncycastle.jcajce.spec.MLKEMParameterSpec
 */
public final class MlKemKeyExchange {
    private static final String PROVIDER = "BC";
    private static final MLKEMParameterSpec PARAMETER_SPEC = MLKEMParameterSpec.ml_kem_768;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private MlKemKeyExchange() {
    }

    /**
     * Generates an ML-KEM-768 key pair using the Bouncy Castle provider.
     *
     * @return A new ML-KEM-768 {@link KeyPair}
     * @throws GeneralSecurityException if key generation fails
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-KEM", PROVIDER);
        generator.initialize(PARAMETER_SPEC, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * Encapsulates a fresh shared secret against the given ML-KEM public key.
     *
     * @param encodedPublicKey  X.509/DER encoded recipient public key
     * @return The encapsulation (ciphertext to send) and the sender-side shared
     *         secret
     * @throws Exception if key parsing or encapsulation fails
     */
    public static Encapsulation encapsulate(byte[] encodedPublicKey) throws Exception {
        AsymmetricKeyParameter publicKey = PublicKeyFactory.createKey(encodedPublicKey);
        SecretWithEncapsulation result = new MLKEMGenerator(new SecureRandom()).generateEncapsulated(publicKey);
        try {
            return new Encapsulation(result.getEncapsulation(), result.getSecret());
        } finally {
            result.destroy();
        }
    }

    /**
     * Decapsulates an ML-KEM ciphertext using the given private key, recovering
     * the shared secret.
     *
     * @param encodedPrivateKey  X.509/DER encoded local ML-KEM private key
     * @param encapsulation      Ciphertext previously produced by
     *                          {@link #encapsulate(byte[])}
     * @return The shared secret corresponding to the encapsulation
     * @throws Exception if key parsing or decapsulation fails
     */
    public static byte[] decapsulate(byte[] encodedPrivateKey, byte[] encapsulation) throws Exception {
        AsymmetricKeyParameter privateKey = PrivateKeyFactory.createKey(encodedPrivateKey);
        return new MLKEMExtractor((MLKEMPrivateKeyParameters) privateKey).extractSecret(encapsulation);
    }

    /**
     * Returns a defensive copy of the supplied byte array.
     *
     * @param value Array to copy; must not be {@code null}
     * @return A new array containing the same bytes
     */
    public static byte[] copy(byte[] value) {
        return Arrays.copyOf(value, value.length);
    }

    /**
     * Immutable result of an ML-KEM encapsulation: the ciphertext to transmit
     * to the recipient and the sender-side shared secret.
     * <p>
     * Accessors return defensive copies so callers cannot mutate internal state.
     * </p>
     *
     * @author fl4nk3r-h
     * @version 2.0.0
     * @param ciphertext    Encapsulation ciphertext to send to the recipient
     * @param sharedSecret  Sender-side shared secret recovered from decapsulation
     */
    public record Encapsulation(byte[] ciphertext, byte[] sharedSecret) {
        public Encapsulation {
            ciphertext = copy(ciphertext);
            sharedSecret = copy(sharedSecret);
        }

        /**
         * @return A defensive copy of the encapsulation ciphertext
         */
        @Override
        public byte[] ciphertext() {
            return copy(ciphertext);
        }

        /**
         * @return A defensive copy of the sender-side shared secret
         */
        @Override
        public byte[] sharedSecret() {
            return copy(sharedSecret);
        }
    }
}
