package com.zerotrust.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.zerotrust.crypto.AeadCrypto;
import com.zerotrust.crypto.MlKemKeyExchange;

/** Owns peer identity exchange and the ML-KEM/AES-GCM session state. */
public final class SessionManager {
    private static final String KEM_FRAME_PREFIX = "PQC_V2:ML-KEM-768:";
    private static final String CIPHERTEXT_FRAME_PREFIX = "PQC_CT:";
    private static final byte[] MESSAGE_AAD = "securep2p-v2:message".getBytes(StandardCharsets.UTF_8);

    private final String localPeerId;
    private final PeerConnection connection;
    private final ExecutorService executorService;
    private final KeyPair kemKeyPair;
    private volatile String remotePeerId;
    private volatile byte[] sessionKey;

    public SessionManager(String localPeerId, PeerConnection connection, ExecutorService executorService)
            throws Exception {
        this.localPeerId = localPeerId;
        this.connection = connection;
        this.executorService = executorService;
        this.kemKeyPair = MlKemKeyExchange.generateKeyPair();
    }

    public String exchangePeerId() throws IOException {
        connection.sendLine("PEER_ID:" + localPeerId);
        String line = connection.readLine();
        if (line == null || !line.startsWith("PEER_ID:")) {
            throw new IOException("Invalid peerId exchange message: " + line);
        }
        remotePeerId = line.substring("PEER_ID:".length());
        return remotePeerId;
    }

    public void performKeyExchangeAsync(Runnable onComplete, Consumer<Exception> onError) {
        executorService.execute(() -> {
            try {
                connection.sendLine(KEM_FRAME_PREFIX + Base64.getEncoder().encodeToString(
                        kemKeyPair.getPublic().getEncoded()));
                String peerKeyFrame = connection.readLine();
                if (peerKeyFrame == null || !peerKeyFrame.startsWith(KEM_FRAME_PREFIX)) {
                    throw new IOException("Invalid ML-KEM public key frame");
                }

                byte[] peerPublicKey = Base64.getDecoder().decode(
                        peerKeyFrame.substring(KEM_FRAME_PREFIX.length()));
                MlKemKeyExchange.Encapsulation outgoing = MlKemKeyExchange.encapsulate(peerPublicKey);
                connection.sendLine(CIPHERTEXT_FRAME_PREFIX
                        + Base64.getEncoder().encodeToString(outgoing.ciphertext()));

                String incomingFrame = connection.readLine();
                if (incomingFrame == null || !incomingFrame.startsWith(CIPHERTEXT_FRAME_PREFIX)) {
                    throw new IOException("Invalid ML-KEM ciphertext frame");
                }
                byte[] incomingCiphertext = Base64.getDecoder().decode(
                        incomingFrame.substring(CIPHERTEXT_FRAME_PREFIX.length()));
                byte[] incomingSecret = MlKemKeyExchange.decapsulate(
                        kemKeyPair.getPrivate().getEncoded(), incomingCiphertext);
                sessionKey = deriveSessionKey(outgoing.sharedSecret(), incomingSecret);
                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception exception) {
                if (onError != null) {
                    onError.accept(exception);
                }
            }
        });
    }

    public void sendMessageAsync(String message, Consumer<Boolean> callback) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                if (!isEncrypted()) {
                    throw new IllegalStateException("Session is not established");
                }
                byte[] plaintext = message.getBytes(StandardCharsets.UTF_8);
                byte[] encrypted = AeadCrypto.encrypt(plaintext, sessionKey, MESSAGE_AAD);
                connection.sendLine(Base64.getEncoder().encodeToString(encrypted));
                success = true;
            } catch (Exception exception) {
                if (callback != null) {
                    callback.accept(false);
                }
            }
            if (success && callback != null) {
                callback.accept(true);
            }
        });
    }

    public String decrypt(String encryptedMessage) throws Exception {
        if (!isEncrypted()) {
            throw new IllegalStateException("Session is not established");
        }
        byte[] encrypted = Base64.getDecoder().decode(encryptedMessage);
        byte[] plaintext = AeadCrypto.decrypt(encrypted, sessionKey, MESSAGE_AAD);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public boolean isEncrypted() {
        return sessionKey != null;
    }

    public String getLocalPeerId() {
        return localPeerId;
    }

    public String getRemotePeerId() {
        return remotePeerId;
    }

    public void close() {
        sessionKey = null;
    }

    private byte[] deriveSessionKey(byte[] outgoingSecret, byte[] incomingSecret) throws Exception {
        if (remotePeerId == null || remotePeerId.isBlank()) {
            throw new IllegalStateException("Peer identity exchange is required before key exchange");
        }
        byte[] first = localPeerId.compareTo(remotePeerId) < 0 ? outgoingSecret : incomingSecret;
        byte[] second = localPeerId.compareTo(remotePeerId) < 0 ? incomingSecret : outgoingSecret;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("securep2p-v2:ml-kem-768:session".getBytes(StandardCharsets.UTF_8));
        digest.update(first);
        digest.update(second);
        return digest.digest();
    }
}
