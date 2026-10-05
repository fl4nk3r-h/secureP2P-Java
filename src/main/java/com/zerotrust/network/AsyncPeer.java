package com.zerotrust.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Facade for peer connection, session, and inbound message delivery modules.
 */
public class AsyncPeer {
    private final String peerId;
    private final int port;
    private final ExecutorService executorService;
    private final PeerConnection peerConnection;
    private final SessionManager sessionManager;
    private final MessageListener messageListener;
    private volatile Consumer<Exception> onError;
    private volatile Consumer<Boolean> onSendComplete;

    public AsyncPeer(String peerId, int port) throws Exception {
        this.peerId = peerId;
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(4);
        this.peerConnection = new PeerConnection(port, executorService, this::handleError);
        this.sessionManager = new SessionManager(peerId, peerConnection, executorService);
        this.messageListener = new MessageListener(peerConnection, sessionManager, executorService);
        this.messageListener.onError(this::handleError);
    }

    public void acceptConnectionAsync(Consumer<AsyncPeer> callback) {
        peerConnection.acceptAsync(connection -> {
            if (callback != null) {
                callback.accept(this);
            }
        });
    }

    public void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback) {
        peerConnection.connectAsync(address, port, connection -> {
            if (callback != null) {
                callback.accept(this);
            }
        });
    }

    public String exchangePeerId() throws IOException {
        return sessionManager.exchangePeerId();
    }

    public void performKeyExchangeAsync(Runnable onComplete) {
        sessionManager.performKeyExchangeAsync(() -> {
            messageListener.start();
            if (onComplete != null) {
                onComplete.run();
            }
        }, this::handleError);
    }

    public void onMessageReceived(Consumer<String> callback) {
        messageListener.onMessageReceived(callback);
    }

    public void onError(Consumer<Exception> callback) {
        this.onError = callback;
        messageListener.onError(this::handleError);
    }

    public void onSendComplete(Consumer<Boolean> callback) {
        this.onSendComplete = callback;
    }

    public void sendMessageAsync(String message) {
        sendMessageAsync(message, onSendComplete);
    }

    public void sendMessageAsync(String message, Consumer<Boolean> callback) {
        sessionManager.sendMessageAsync(message, callback);
    }

    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageListener.pollMessage(timeout, unit);
    }

    public String pollMessage() {
        return messageListener.pollMessage();
    }

    public boolean isConnected() {
        return peerConnection.isConnected();
    }

    public boolean isEncrypted() {
        return sessionManager.isEncrypted();
    }

    public void close() {
        peerConnection.close();
        messageListener.close();
        sessionManager.close();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public String getPeerId() {
        return peerId;
    }

    public String getRemotePeerId() {
        return sessionManager.getRemotePeerId();
    }

    public int getPort() {
        return port;
    }

    public int getQueueSize() {
        return messageListener.getQueueSize();
    }

    private void handleError(Exception exception) {
        Consumer<Exception> callback = onError;
        if (callback != null) {
            callback.accept(exception);
        } else {
            System.err.println("AsyncPeer " + peerId + " error: " + exception.getMessage());
        }
    }
}
