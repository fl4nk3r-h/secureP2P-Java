package com.zerotrust.network;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns inbound reading, message buffering, decryption dispatch, and callbacks.
 * <p>
 * A daemon listener thread reads wire lines from the
 * {@link PeerConnection}, buffers them, and dispatches each to the executor
 * where it is decrypted via the {@link SessionManager} and handed to the
 * registered message handler.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see PeerConnection
 * @see SessionManager
 */
public final class MessageListener {
    private final PeerConnection connection;
    private final SessionManager sessionManager;
    private final ExecutorService executorService;
    private final BlockingQueue<String> messageQueue;
    private volatile boolean running;
    private volatile Thread listenerThread;
    private volatile Consumer<String> messageHandler;
    private volatile Consumer<Exception> errorHandler;

    /**
     * Creates a message listener bound to the given transport and session.
     *
     * @param connection       Connected transport to read wire lines from
     * @param sessionManager    Session used to decrypt incoming messages
     * @param executorService   Executor running message-processing tasks
     */
    public MessageListener(PeerConnection connection, SessionManager sessionManager, ExecutorService executorService) {
        this.connection = connection;
        this.sessionManager = sessionManager;
        this.executorService = executorService;
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Starts the inbound listener thread.
     * <p>
     * Idempotent: a no-op if the listener is already running.
     * </p>
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        listenerThread = new Thread(this::readLoop, "PeerMessageListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Registers the callback for decrypted incoming messages.
     *
     * @param callback Invoked with each decrypted message payload
     */
    public void onMessageReceived(Consumer<String> callback) {
        messageHandler = callback;
    }

    /**
     * Registers the callback for inbound processing errors.
     *
     * @param callback Invoked with the first failure that occurs
     */
    public void onError(Consumer<Exception> callback) {
        errorHandler = callback;
    }

    /**
     * Returns the next buffered wire message without waiting.
     *
     * @return The next buffered line, or {@code null} if the queue is empty
     */
    public String pollMessage() {
        return messageQueue.poll();
    }

    /**
     * Waits up to the given timeout for the next buffered wire message.
     *
     * @param timeout Maximum time to wait
     * @param unit    Time unit of the timeout argument
     * @return The next buffered line, or {@code null} if the timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * @return The number of wire messages currently buffered
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * Stops the listener thread, waiting up to two seconds for it to exit.
     * <p>
     * Safe to call from the listener thread itself.
     * </p>
     */
    public synchronized void close() {
        running = false;
        Thread thread = listenerThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(2000);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        listenerThread = null;
    }

    /**
     * Continuously reads wire lines until the listener is stopped or the
     * connection closes, buffering and dispatching each line.
     */
    private void readLoop() {
        try {
            String line;
            while (running && (line = connection.readLine()) != null) {
                messageQueue.put(line);
                String wireMessage = line;
                executorService.execute(() -> processMessage(wireMessage));
            }
        } catch (Exception exception) {
            if (running) {
                reportError(exception);
            }
        }
    }

    /**
     * Decrypts a wire message and delivers it to the message handler.
     *
     * @param wireMessage Base64-encoded encrypted payload to process
     */
    private void processMessage(String wireMessage) {
        try {
            Consumer<String> callback = messageHandler;
            if (callback != null) {
                callback.accept(sessionManager.decrypt(wireMessage));
            }
        } catch (Exception exception) {
            reportError(exception);
        }
    }

    /**
     * Forwards an error to the registered error handler, if any.
     *
     * @param exception The error that occurred
     */
    private void reportError(Exception exception) {
        Consumer<Exception> callback = errorHandler;
        if (callback != null) {
            callback.accept(exception);
        }
    }
}
