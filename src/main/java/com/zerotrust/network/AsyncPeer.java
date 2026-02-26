package com.zerotrust.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import com.zerotrust.crypto.CryptoUtils;
import com.zerotrust.crypto.KeyExchange;

/**
 * Asynchronous Peer-to-Peer Communication Implementation
 * 
 * Features:
 * - Non-blocking connection handling
 * - Callback-based message reception
 * - Thread pool for concurrent operations
 * - Message queue for buffering
 * - Event-driven architecture
 * 
 * Status: Stable
 * Location: com.zerotrust.network
 * 
 * @author fl4nk3r
 * @version 1.0
 */
public class AsyncPeer {
    private String peerId;
    private String remotePeerId;
    private int port;
    private ServerSocket serverSocket;
    private Socket peerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private SecretKey encryptionKey;
    private KeyExchange keyExchange;

    // Thread management
    private ExecutorService executorService;
    private Thread listenerThread;
    private volatile boolean running = false;

    // Callbacks
    private Consumer<AsyncPeer> onConnected;
    private Consumer<String> onMessageReceived;
    private Consumer<Exception> onError;
    private Consumer<Boolean> onSendComplete;

    // Message queue for async processing
    private BlockingQueue<String> messageQueue;

    /**
     * Creates an AsyncPeer that listens on the specified port.
     * 
     * @param peerId Unique identifier for this peer
     * @param port   Port to listen on
     * @throws Exception if initialization fails
     */
    public AsyncPeer(String peerId, int port) throws Exception {
        this.peerId = peerId;
        this.port = port;
        this.keyExchange = new KeyExchange();
        this.serverSocket = new ServerSocket(port);
        this.executorService = Executors.newFixedThreadPool(4);
        this.messageQueue = new LinkedBlockingQueue<>();
        System.out.println("AsyncPeer " + peerId + " created on port " + port);
    }

    /**
     * Exchanges peer identifiers over the active socket connection.
     * <p>
     * Both peers should call this method after connection establishment and before
     * key exchange. It sends the local peerId and reads the remote peerId.
     * </p>
     *
     * @return The remote peer's identifier
     * @throws IOException if I/O streams are not ready or exchange fails
     */
    public String exchangePeerId() throws IOException {
        if (out == null || in == null) {
            throw new IOException("I/O streams not initialized. Cannot exchange peerId.");
        }

        out.println("PEER_ID:" + peerId);
        String line = in.readLine();

        if (line == null || !line.startsWith("PEER_ID:")) {
            throw new IOException("Invalid peerId exchange message: " + line);
        }

        remotePeerId = line.substring("PEER_ID:".length());
        return remotePeerId;
    }

    /**
     * The `acceptConnectionAsync` method asynchronously accepts incoming
     * connections and executes a
     * callback function upon successful connection.
     * 
     * @param callback The `callback` parameter in the `acceptConnectionAsync`
     *                 method is a `Consumer`
     *                 functional interface that accepts an `AsyncPeer` object as
     *                 input. This callback function is
     *                 executed asynchronously when a connection is accepted by the
     *                 `AsyncPeer` instance.
     */
    public void acceptConnectionAsync(Consumer<AsyncPeer> callback) {
        onConnected = callback;
        executorService.execute(() -> {
            try {
                System.out.println("AsyncPeer " + peerId + " listening for connections...");
                peerSocket = serverSocket.accept();
                out = new PrintWriter(peerSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));

                System.out.println("AsyncPeer " + peerId + " accepted connection from " +
                        peerSocket.getInetAddress());

                if (onConnected != null) {
                    onConnected.accept(this);
                }
            } catch (IOException e) {
                handleError(e);
            }
        });
    }

    /**
     * The `connectToPeerAsync` method establishes an asynchronous connection to a
     * peer using the
     * provided address and port, invoking a callback upon successful connection or
     * handling any
     * encountered IOException.
     * 
     * @param address  The `address` parameter in the `connectToPeerAsync` method
     *                 represents the IP
     *                 address or hostname of the peer to which the asynchronous
     *                 connection will be established. This
     *                 is the network location where the peer is listening for
     *                 incoming connections.
     * @param port     The `port` parameter in the `connectToPeerAsync` method
     *                 represents the port number
     *                 on which the peer will attempt to connect to the specified
     *                 address. Ports are used to uniquely
     *                 identify different network services running on the same host.
     *                 Common port numbers include 80 for
     *                 HTTP, 443 for HTTPS,
     * @param callback The `callback` parameter in the `connectToPeerAsync` method
     *                 is a `Consumer`
     *                 functional interface that accepts an `AsyncPeer` object as an
     *                 argument. This callback function
     *                 is invoked once the asynchronous connection to the peer is
     *                 established successfully.
     */
    public void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback) {
        onConnected = callback;
        executorService.execute(() -> {
            try {
                System.out.println("AsyncPeer " + peerId + " connecting to " + address + ":" + port);
                peerSocket = new Socket(address, port);
                out = new PrintWriter(peerSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));

                System.out.println("AsyncPeer " + peerId + " connected to " + address + ":" + port);

                if (onConnected != null) {
                    onConnected.accept(this);
                }
            } catch (IOException e) {
                handleError(e);
            }
        });
    }

    /**
     * The `performKeyExchangeAsync` method asynchronously performs a key exchange
     * process with a peer,
     * computes a shared secret, sets up encryption key, starts message listening,
     * and executes a
     * callback upon completion or error.
     * 
     * @param onComplete The `onComplete` parameter in the `performKeyExchangeAsync`
     *                   method is a
     *                   `Runnable` object that represents a block of code that can
     *                   be executed after the key exchange
     *                   process is completed asynchronously. It allows you to
     *                   specify additional actions or tasks to be
     *                   performed once the key exchange is finished. In
     */
    public void performKeyExchangeAsync(Runnable onComplete) {
        executorService.execute(() -> {
            try {
                System.out.println("AsyncPeer " + peerId + " starting key exchange...");

                // Send public key
                String myPublicKey = keyExchange.getPublicKeyString();
                out.println(myPublicKey);

                // Receive public key
                String peerPublicKey = in.readLine();

                // Compute shared secret
                String sharedSecret = keyExchange.getSharedSecretString(peerPublicKey);

                // Derive AES key from shared secret using SHA-256
                // No need to check length or take substring - SHA-256 handles any input
                encryptionKey = CryptoUtils.getKeyFromString(sharedSecret);

                System.out.println("AsyncPeer " + peerId + " key exchange completed");

                // Start listening for messages in background
                startMessageListener();

                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception e) {
                handleError(e);
            }
        });
    }

    /**
     * The `onMessageReceived` function sets a callback for when a message is
     * received.
     * 
     * @param callback The `callback` parameter in the `onMessageReceived` method is
     *                 a `Consumer`
     *                 functional interface that takes a `String` as input. This
     *                 parameter is used to set a callback
     *                 function that will be called when a message is received.
     */
    public void onMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    /**
     * The `onError` function in Java sets a callback function to handle exceptions.
     * 
     * @param callback The `callback` parameter in the `onError` method is a
     *                 `Consumer` functional
     *                 interface that takes an `Exception` as input. This parameter
     *                 allows you to specify a callback
     *                 function that will be executed when an error occurs.
     */
    public void onError(Consumer<Exception> callback) {
        this.onError = callback;
    }

    /**
     * The `onSendComplete` function in Java sets a callback function to be executed
     * when a send
     * operation is complete.
     * 
     * @param callback The `callback` parameter in the `onSendComplete` method is a
     *                 `Consumer`
     *                 functional interface that takes a `Boolean` as input. This
     *                 callback function will be invoked
     *                 when the sending operation is complete.
     */
    public void onSendComplete(Consumer<Boolean> callback) {
        this.onSendComplete = callback;
    }

    /**
     * The function `sendMessageAsync` sends a message asynchronously with an
     * optional callback.
     * 
     * @param message The `message` parameter in the `sendMessageAsync` method is a
     *                string that
     *                represents the message you want to send asynchronously.
     */
    public void sendMessageAsync(String message) {
        sendMessageAsync(message, null);
    }

    /**
     * The `sendMessageAsync` method sends a message asynchronously, encrypting it
     * if an encryption key
     * is provided, and invokes a callback with a boolean parameter indicating
     * success or failure.
     * 
     * @param message  The `message` parameter in the `sendMessageAsync` method is
     *                 the text message that
     *                 you want to send asynchronously. This message will be
     *                 processed, encrypted (if an encryption key
     *                 is provided), and then sent to the output stream.
     * @param callback The `callback` parameter in the `sendMessageAsync` method is
     *                 a
     *                 `Consumer<Boolean>` functional interface. This parameter
     *                 allows you to pass a callback function
     *                 that accepts a `Boolean` value. The callback function will be
     *                 executed after the message is sent
     *                 asynchronously. If the message is sent successfully, the
     *                 callback
     */
    public void sendMessageAsync(String message, Consumer<Boolean> callback) {
        executorService.execute(() -> {
            try {
                String toSend = message;
                if (encryptionKey != null) {
                    toSend = CryptoUtils.encrypt(message, encryptionKey);
                }

                out.println(toSend);
                System.out.println("AsyncPeer " + peerId + " sent message: " +
                        (message.length() > 20 ? message.substring(0, 20) + "..." : message));

                if (callback != null) {
                    callback.accept(true);
                }
            } catch (Exception e) {
                handleError(e);
                if (callback != null) {
                    callback.accept(false);
                }
            }
        });
    }

    /**
     * The `pollMessage` function retrieves and removes the head of the message
     * queue, waiting up to
     * the specified timeout for an element to become available.
     * 
     * @param timeout The `timeout` parameter specifies the maximum time to wait for
     *                a message to be
     *                available in the message queue before returning `null`.
     * @param unit    The `unit` parameter in the `pollMessage` method specifies the
     *                time unit for the
     *                timeout value. It is used to indicate the unit of time for the
     *                timeout duration, such as
     *                seconds, milliseconds, minutes, etc. This allows you to
     *                specify the timeout duration in a
     *                specific time unit that is
     * @return The method `pollMessage` returns a message from the message queue
     *         with the specified
     *         timeout and time unit.
     */
    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * The `pollMessage` function returns and removes the first message from a
     * message queue.
     * 
     * @return The `pollMessage` method is returning the message at the front of the
     *         message queue.
     */
    public String pollMessage() {
        return messageQueue.poll();
    }

    /**
     * The `isConnected` function in Java checks if a peer socket is not null and is
     * connected.
     * 
     * @return The method `isConnected()` returns a boolean value indicating whether
     *         the `peerSocket`
     *         is not null and is connected.
     */
    public boolean isConnected() {
        return peerSocket != null && peerSocket.isConnected();
    }

    /**
     * The function `isEncrypted()` returns true if an encryption key is set,
     * indicating that the data
     * is encrypted.
     * 
     * @return The method isEncrypted() is returning a boolean value indicating
     *         whether the
     *         encryptionKey is not null. If the encryptionKey is not null, the
     *         method will return true,
     *         indicating that the data is encrypted. If the encryptionKey is null,
     *         the method will return
     *         false, indicating that the data is not encrypted.
     */
    public boolean isEncrypted() {
        return encryptionKey != null;
    }

    /**
     * The close method shuts down various resources and services associated with an
     * AsyncPeer instance
     * in Java. Gracefully drains executor service before closing I/O streams.
     */
    public void close() {
        running = false;

        // Close I/O streams to unblock listener thread
        try {
            if (peerSocket != null && !peerSocket.isClosed())
                peerSocket.close();
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException e) {
            // Socket already closed
        }

        // Wait for listener thread to finish (it will exit on closed socket)
        try {
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.join(2000); // Wait up to 2 seconds
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Close buffered streams after listener exits
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
        } catch (IOException e) {
            // Already closed
        }

        // Shutdown executor service and wait for pending tasks to complete
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        System.out.println("AsyncPeer " + peerId + " closed");
    }

    /**
     * The function `getPeerId()` returns the peer ID as a String.
     * 
     * @return The `peerId` variable is being returned.
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Returns the remote peer's identifier after a successful exchange.
     *
     * @return The remote peerId, or null if not exchanged yet
     */
    public String getRemotePeerId() {
        return remotePeerId;
    }

    /**
     * The function `getPort()` returns the port number.
     * 
     * @return The `port` variable is being returned.
     */
    public int getPort() {
        return port;
    }

    /**
     * The function `getQueueSize` returns the size of the message queue.
     * 
     * @return The method `getQueueSize` returns the size of the message queue.
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * The `startMessageListener` method creates a new thread to listen for incoming
     * messages,
     * processes them asynchronously, and handles any errors that occur.
     */
    private void startMessageListener() {
        if (running)
            return;
        running = true;

        listenerThread = new Thread(() -> {
            System.out.println("AsyncPeer " + peerId + " message listener started");
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    messageQueue.put(line);
                    processMessageAsync(line);
                }
            } catch (IOException | InterruptedException e) {
                if (running) {
                    handleError(e);
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * The `processMessageAsync` method asynchronously decrypts an encrypted message
     * using a specified
     * encryption key and then invokes a callback function with the decrypted
     * message.
     * 
     * @param encryptedMessage The `processMessageAsync` method is designed to
     *                         asynchronously process
     *                         an encrypted message. The method uses an
     *                         `executorService` to execute the processing logic in
     *                         a
     *                         separate thread. Here's a breakdown of the steps
     *                         performed in the method:
     */
    private void processMessageAsync(String encryptedMessage) {
        executorService.execute(() -> {
            try {
                String decrypted = encryptedMessage;
                if (encryptionKey != null) {
                    decrypted = CryptoUtils.decrypt(encryptedMessage, encryptionKey);
                }

                if (onMessageReceived != null) {
                    onMessageReceived.accept(decrypted);
                }
            } catch (Exception e) {
                handleError(e);
            }
        });
    }

    /**
     * Waits for the connection to be ready (I/O streams initialized) with a
     * timeout.
     * <p>
     * This method polls the connection state to ensure that both input and output
     * streams are properly initialized before proceeding with operations like key
     * exchange. This prevents race conditions when using asynchronous connection
     * methods.
     * </p>
     * 
     * @param timeoutMillis Maximum time to wait in milliseconds
     * @return true if connection is ready, false if timeout occurred
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public boolean waitForConnectionReady(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long elapsed = 0;

        while (elapsed < timeoutMillis) {
            // Check if both I/O streams are initialized
            if (out != null && in != null && peerSocket != null && peerSocket.isConnected()) {
                return true;
            }

            // Wait a bit before checking again
            Thread.sleep(50);
            elapsed = System.currentTimeMillis() - startTime;
        }

        return false;
    }

    /**
     * The handleError function logs an error message, prints the stack trace, and
     * invokes a consumer
     * function if it is not null.
     * 
     * @param e The parameter "e" in the handleError method is an Exception object,
     *          which represents an
     *          error or exceptional condition that has occurred during the
     *          execution of the code.
     */
    private void handleError(Exception e) {
        System.err.println("AsyncPeer " + peerId + " error: " + e.getMessage());
        e.printStackTrace();
        if (onError != null) {
            onError.accept(e);
        }
    }
}
