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

/**
 * Owns peer identity exchange and the ML-KEM/AES-GCM session state.
 * <p>
 * Implements the v2 wire protocol:
 * </p>
 * <ul>
 * <li>{@code PEER_ID:<id>} - identity handshake</li>
 * <li>{@code PQC_V2:ML-KEM-768:<base64 key>} - public key frame</li>
 * <li>{@code PQC_CT:<base64 ciphertext>} - KEM ciphertext frame</li>
 * </ul>
 * <p>
 * After both sides complete the KEM exchange, both derive the same 32-byte
 * session key (deterministic ordering by peer id, SHA-256) used for AES-256-GCM
 * message encryption.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see MlKemKeyExchange
 * @see AeadCrypto
 */
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

    /**
     * Creates a session for the local peer and generates its ML-KEM key pair.
     *
     * @param localPeerId     Identifier of this peer
     * @param connection      Connected transport used to exchange frames
     * @param executorService Executor running asynchronous session tasks
     * @throws Exception if ML-KEM key pair generation fails
     */
    public SessionManager(String localPeerId, PeerConnection connection, ExecutorService executorService)
            throws Exception {
        this.localPeerId = localPeerId;
        this.connection = connection;
        this.executorService = executorService;
        this.kemKeyPair = MlKemKeyExchange.generateKeyPair();
    }

    /**
     * Performs the blocking peer identity handshake.
     *
     * @return The remote peer's identifier
     * @throws IOException if the connection is not ready or the peer sends an
     *                     invalid {@code PEER_ID:} frame
     */
    public String exchangePeerId() throws IOException {
        connection.sendLine("PEER_ID:" + localPeerId);
        String line = connection.readLine();
        if (line == null || !line.startsWith("PEER_ID:")) {
            throw new IOException("Invalid peerId exchange message: " + line);
        }
        remotePeerId = line.substring("PEER_ID:".length());
        return remotePeerId;
    }

    /**
     * Asynchronously runs the ML-KEM key exchange: publishes the local public
     * key, encapsulates against the remote key, publishes the ciphertext,
     * decapsulates the remote ciphertext, and derives the session key.
     *
     * @param onComplete Invoked after the session key is established
     * @param onError    Invoked with the cause when the exchange fails
     */
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

    /**
     * Asynchronously encrypts and sends a message over the established session.
     *
     * @param message  Plaintext message to send
     * @param callback Invoked with {@code true} on success, {@code false} on
     *                 failure
     */
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

    /**
     * Decrypts a Base64-encoded wire message using the session key.
     *
     * @param encryptedMessage Base64-encoded AES-GCM payload
     * @return The decoded plaintext message
     * @throws Exception if the session is not established or decryption fails
     */
    public String decrypt(String encryptedMessage) throws Exception {
        if (!isEncrypted()) {
            throw new IllegalStateException("Session is not established");
        }
        byte[] encrypted = Base64.getDecoder().decode(encryptedMessage);
        byte[] plaintext = AeadCrypto.decrypt(encrypted, sessionKey, MESSAGE_AAD);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * @return {@code true} if the key exchange has completed and a session key
     *         is available
     */
    public boolean isEncrypted() {
        return sessionKey != null;
    }

    /**
     * @return The identifier of the local peer
     */
    public String getLocalPeerId() {
        return localPeerId;
    }

    /**
     * @return The remote peer's identifier, or {@code null} before the identity
     *         exchange completes
     */
    public String getRemotePeerId() {
        return remotePeerId;
    }

    /**
     * Discards the session key, ending the secure session.
     */
    public void close() {
        sessionKey = null;
    }

    /**
     * Derives the 32-byte session key from both KEM secrets.
     * <p>
     * Both peers order the secrets by peer-id comparison so the SHA-256 input
     * is identical on both sides.
     * </p>
     *
     * @param outgoingSecret Shared secret from the local encapsulation
     * @param incomingSecret Shared secret recovered from the remote ciphertext
     * @return The 32-byte derived session key
     * @throws Exception if the peer identity was not exchanged or the digest
     *                  is unavailable
     */
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
