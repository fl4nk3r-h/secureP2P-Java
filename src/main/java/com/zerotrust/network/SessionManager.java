package com.zerotrust.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import com.zerotrust.crypto.CryptoUtils;
import com.zerotrust.crypto.KeyExchange;

/** Owns peer identity exchange and the current session encryption state. */
public final class SessionManager {
    private final String localPeerId;
    private final PeerConnection connection;
    private final ExecutorService executorService;
    private final KeyExchange keyExchange;
    private volatile String remotePeerId;
    private volatile SecretKey encryptionKey;

    public SessionManager(String localPeerId, PeerConnection connection, ExecutorService executorService)
            throws Exception {
        this.localPeerId = localPeerId;
        this.connection = connection;
        this.executorService = executorService;
        this.keyExchange = new KeyExchange();
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
                connection.sendLine(keyExchange.getPublicKeyString());
                String peerPublicKey = connection.readLine();
                if (peerPublicKey == null || peerPublicKey.isBlank()) {
                    throw new IOException("Peer public key was empty");
                }
                String sharedSecret = keyExchange.getSharedSecretString(peerPublicKey);
                encryptionKey = CryptoUtils.getKeyFromString(sharedSecret);
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
                connection.sendLine(CryptoUtils.encrypt(message, encryptionKey));
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
        return CryptoUtils.decrypt(encryptedMessage, encryptionKey);
    }

    public boolean isEncrypted() {
        return encryptionKey != null;
    }

    public String getLocalPeerId() {
        return localPeerId;
    }

    public String getRemotePeerId() {
        return remotePeerId;
    }

    public void close() {
        encryptionKey = null;
    }
}
