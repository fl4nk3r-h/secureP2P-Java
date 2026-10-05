package com.zerotrust.network;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns inbound reading, message buffering, decryption dispatch, and callbacks.
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

    public MessageListener(PeerConnection connection, SessionManager sessionManager, ExecutorService executorService) {
        this.connection = connection;
        this.sessionManager = sessionManager;
        this.executorService = executorService;
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        listenerThread = new Thread(this::readLoop, "PeerMessageListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void onMessageReceived(Consumer<String> callback) {
        messageHandler = callback;
    }

    public void onError(Consumer<Exception> callback) {
        errorHandler = callback;
    }

    public String pollMessage() {
        return messageQueue.poll();
    }

    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    public int getQueueSize() {
        return messageQueue.size();
    }

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

    private void readLoop() {
        try {
            String line;
            while (running && (line = connection.readLine()) != null) {
                messageQueue.put(line);
                executorService.execute(() -> processMessage(line));
            }
        } catch (Exception exception) {
            if (running) {
                reportError(exception);
            }
        }
    }

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

    private void reportError(Exception exception) {
        Consumer<Exception> callback = errorHandler;
        if (callback != null) {
            callback.accept(exception);
        }
    }
}
