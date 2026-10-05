package com.zerotrust.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jcajce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.util.PrivateKeyFactory;
import org.bouncycastle.pqc.crypto.util.PublicKeyFactory;

/** ML-KEM-768 key generation and KEM operations backed by Bouncy Castle. */
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

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-KEM", PROVIDER);
        generator.initialize(PARAMETER_SPEC, new SecureRandom());
        return generator.generateKeyPair();
    }

    public static Encapsulation encapsulate(byte[] encodedPublicKey) throws Exception {
        AsymmetricKeyParameter publicKey = PublicKeyFactory.createKey(encodedPublicKey);
        SecretWithEncapsulation result = new MLKEMGenerator(new SecureRandom()).generateEncapsulated(publicKey);
        try {
            return new Encapsulation(result.getEncapsulation(), result.getSecret());
        } finally {
            result.destroy();
        }
    }

    public static byte[] decapsulate(byte[] encodedPrivateKey, byte[] encapsulation) throws Exception {
        AsymmetricKeyParameter privateKey = PrivateKeyFactory.createKey(encodedPrivateKey);
        return new MLKEMExtractor(privateKey).extractSecret(encapsulation);
    }

    public static byte[] copy(byte[] value) {
        return Arrays.copyOf(value, value.length);
    }

    public record Encapsulation(byte[] ciphertext, byte[] sharedSecret) {
        public Encapsulation {
            ciphertext = copy(ciphertext);
            sharedSecret = copy(sharedSecret);
        }

        @Override
        public byte[] ciphertext() {
            return copy(ciphertext);
        }

        @Override
        public byte[] sharedSecret() {
            return copy(sharedSecret);
        }
    }
}
