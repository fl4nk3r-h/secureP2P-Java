package com.zerotrust.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Facade for peer connection, session, and inbound message delivery modules.
 * <p>
 * An {@code AsyncPeer} ties together a {@link PeerConnection} (TCP transport),
 * a {@link SessionManager} (peer identity exchange and ML-KEM/AES-GCM session
 * state), and a {@link MessageListener} (inbound message dispatch). All blocking
 * operations are asynchronous with callback- or error-based completion.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see PeerConnection
 * @see SessionManager
 * @see MessageListener
 */
public class AsyncPeer {
    private final String peerId;
    private final int port;
    private final ExecutorService executorService;
    private final PeerConnection peerConnection;
    private final SessionManager sessionManager;
    private final MessageListener messageListener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Consumer<Exception> onError;
    private volatile Consumer<Boolean> onSendComplete;

    /**
     * Creates a new peer and its supporting connection, session, and listener
     * modules.
     * <p>
     * Binds the TCP listener to the given port and generates the local ML-KEM
     * key pair.
     * </p>
     *
     * @param peerId  Identifier of this peer, exchanged with the remote peer
     * @param port    Local port to bind the server socket to
     * @throws Exception if the socket or session initialization fails
     */
    public AsyncPeer(String peerId, int port) throws Exception {
        this.peerId = peerId;
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(4);
        this.peerConnection = new PeerConnection(port, executorService, this::handleError);
        this.sessionManager = new SessionManager(peerId, peerConnection, executorService);
        this.messageListener = new MessageListener(peerConnection, sessionManager, executorService);
        this.messageListener.onError(this::handleError);
    }

    /**
     * Asynchronously waits for an incoming connection.
     *
     * @param callback Invoked with this peer once the connection is accepted
     */
    public void acceptConnectionAsync(Consumer<AsyncPeer> callback) {
        peerConnection.acceptAsync(connection -> {
            if (callback != null) {
                callback.accept(this);
            }
        });
    }

    /**
     * Asynchronously connects to a remote peer.
     *
     * @param address   Host name or address of the remote peer
     * @param port      Port of the remote peer
     * @param callback  Invoked with this peer once the connection is established
     */
    public void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback) {
        peerConnection.connectAsync(address, port, connection -> {
            if (callback != null) {
                callback.accept(this);
            }
        });
    }

    /**
     * Exchanges peer identifiers with the remote peer using a blocking
     * {@code PEER_ID:} handshake.
     *
     * @return The remote peer's identifier
     * @throws IOException if the connection is not ready or the handshake frame
     *                     is invalid
     */
    public String exchangePeerId() throws IOException {
        return sessionManager.exchangePeerId();
    }

    /**
     * Performs the asynchronous ML-KEM key exchange and starts the message
     * listener on success.
     *
     * @param onComplete Invoked on the executor after the session key is
     *                  established and the listener has started
     */
    public void performKeyExchangeAsync(Runnable onComplete) {
        sessionManager.performKeyExchangeAsync(() -> {
            messageListener.start();
            if (onComplete != null) {
                onComplete.run();
            }
        }, this::handleError);
    }

    /**
     * Registers a callback for decrypted incoming messages.
     *
     * @param callback Invoked with each decrypted message payload
     */
    public void onMessageReceived(Consumer<String> callback) {
        messageListener.onMessageReceived(callback);
    }

    /**
     * Registers a callback for asynchronous errors from the connection or
     * message listener.
     *
     * @param callback Invoked with the first failure that occurs
     */
    public void onError(Consumer<Exception> callback) {
        this.onError = callback;
        messageListener.onError(this::handleError);
    }

    /**
     * Registers a callback reporting send success for
     * {@link #sendMessageAsync(String)}.
     *
     * @param callback Invoked with {@code true} on success, {@code false} on
     *                 failure
     */
    public void onSendComplete(Consumer<Boolean> callback) {
        this.onSendComplete = callback;
    }

    /**
     * Asynchronously sends a message using the registered send-complete
     * callback.
     *
     * @param message Plaintext message to send; must not be sent before the
     *                session is established
     */
    public void sendMessageAsync(String message) {
        sendMessageAsync(message, onSendComplete);
    }

    /**
     * Asynchronously encrypts and sends a message.
     *
     * @param message   Plaintext message to send
     * @param callback  Invoked with {@code true} on success, {@code false} on
     *                  failure
     */
    public void sendMessageAsync(String message, Consumer<Boolean> callback) {
        sessionManager.sendMessageAsync(message, callback);
    }

    /**
     * Waits up to the given timeout for a queued incoming message.
     *
     * @param timeout Maximum time to wait
     * @param unit    Time unit of the timeout argument
     * @return The next queued message, or {@code null} if the timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageListener.pollMessage(timeout, unit);
    }

    /**
     * Returns the next queued incoming message without waiting.
     *
     * @return The next queued message, or {@code null} if the queue is empty
     */
    public String pollMessage() {
        return messageListener.pollMessage();
    }

    /**
     * @return {@code true} if the underlying socket is connected and open
     */
    public boolean isConnected() {
        return peerConnection.isConnected();
    }

    /**
     * Waits up to the given timeout for the connection to become fully ready
     * (socket plus streams initialized).
     *
     * @param timeoutMillis Maximum time in milliseconds to wait
     * @return {@code true} if the connection became ready in time
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForConnectionReady(long timeoutMillis) throws InterruptedException {
        return peerConnection.waitUntilReady(timeoutMillis);
    }

    /**
     * @return {@code true} if the key exchange completed and an AES session key
     *         is established
     */
    public boolean isEncrypted() {
        return sessionManager.isEncrypted();
    }

    /**
     * Closes the connection, listener, and session, then shuts down the peer's
     * executor.
     * <p>
     * Idempotent: subsequent calls are ignored.
     * </p>
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
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

    /**
     * @return The identifier of this peer
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * @return The remote peer's identifier, or {@code null} before the identity
     *         exchange completes
     */
    public String getRemotePeerId() {
        return sessionManager.getRemotePeerId();
    }

    /**
     * @return The local port this peer is bound to
     */
    public int getPort() {
        return port;
    }

    /**
     * @return The number of buffered incoming messages waiting in the queue
     */
    public int getQueueSize() {
        return messageListener.getQueueSize();
    }

    /**
     * Routes an internal error to the registered error callback, or to
     * {@code System.err} when no callback is registered.
     *
     * @param exception The error that occurred
     */
    private void handleError(Exception exception) {
        Consumer<Exception> callback = onError;
        if (callback != null) {
            callback.accept(exception);
        } else {
            System.err.println("AsyncPeer " + peerId + " error: " + exception.getMessage());
        }
    }
}
