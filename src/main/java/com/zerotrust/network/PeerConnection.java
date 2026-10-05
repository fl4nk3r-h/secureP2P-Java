package com.zerotrust.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Owns the TCP listener, active socket, streams, and connection lifecycle.
 * <p>
 * Provides both listener mode ({@link #acceptAsync(Consumer)}) and client mode
 * ({@link #connectAsync(String, int, Consumer)}). Line-based framing is exposed
 * through {@link #sendLine(String)} and {@link #readLine()}.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see AsyncPeer
 */
public final class PeerConnection {
    private final int localPort;
    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final Consumer<Exception> errorHandler;
    private volatile Socket peerSocket;
    private volatile PrintWriter out;
    private volatile BufferedReader in;
    private volatile boolean closed;

    /**
     * Binds a server socket to the given local port.
     *
     * @param localPort        Port to listen on
     * @param executorService   Executor used to run async accept/connect tasks
     * @param errorHandler      Callback for connection-level failures
     * @throws IOException if the port cannot be bound
     */
    public PeerConnection(int localPort, ExecutorService executorService, Consumer<Exception> errorHandler)
            throws IOException {
        this.localPort = localPort;
        this.executorService = executorService;
        this.errorHandler = errorHandler;
        this.serverSocket = new ServerSocket(localPort);
    }

    /**
     * Asynchronously accepts the next incoming connection and initializes its
     * I/O streams.
     *
     * @param callback Invoked with this connection once the socket is accepted
     *                 and streams are ready
     */
    public void acceptAsync(Consumer<PeerConnection> callback) {
        executorService.execute(() -> {
            try {
                peerSocket = serverSocket.accept();
                initializeStreams(peerSocket);
                if (callback != null) {
                    callback.accept(this);
                }
            } catch (Exception exception) {
                reportError(exception);
            }
        });
    }

    /**
     * Asynchronously opens a connection to a remote peer and initializes its
     * I/O streams.
     *
     * @param address   Host name or address of the remote peer
     * @param port      Port of the remote peer
     * @param callback  Invoked with this connection once the socket is
     *                 connected and streams are ready
     */
    public void connectAsync(String address, int port, Consumer<PeerConnection> callback) {
        executorService.execute(() -> {
            try {
                peerSocket = new Socket(address, port);
                initializeStreams(peerSocket);
                if (callback != null) {
                    callback.accept(this);
                }
            } catch (Exception exception) {
                reportError(exception);
            }
        });
    }

    /**
     * Waits up to the given timeout for the connection to become fully ready.
     *
     * @param timeoutMillis Maximum time in milliseconds to poll
     * @return {@code true} if the connection became ready in time
     * @throws InterruptedException if interrupted while waiting
     * @see #isReady()
     */
    public boolean waitUntilReady(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (isReady()) {
                return true;
            }
            Thread.sleep(25);
        }
        return isReady();
    }

    /**
     * Writes a line followed by a newline to the remote peer, flushing the
     * stream.
     *
     * @param line Line to send
     * @throws IOException if the connection is not ready or the write fails
     */
    public synchronized void sendLine(String line) throws IOException {
        if (!isReady()) {
            throw new IOException("Peer connection is not ready");
        }
        out.println(line);
        if (out.checkError()) {
            throw new IOException("Peer connection write failed");
        }
    }

    /**
     * Reads one line of text from the remote peer.
     *
     * @return The line, or {@code null} on end of stream
     * @throws IOException if the connection is not ready or a read error occurs
     */
    public String readLine() throws IOException {
        if (!isReady()) {
            throw new IOException("Peer connection is not ready");
        }
        return in.readLine();
    }

    /**
     * @return {@code true} if the socket is connected and both I/O streams are
     *         initialized and the connection has not been closed
     */
    public boolean isReady() {
        Socket socket = peerSocket;
        return !closed && socket != null && socket.isConnected() && !socket.isClosed() && out != null && in != null;
    }

    /**
     * @return {@code true} if the socket is currently connected and open
     */
    public boolean isConnected() {
        Socket socket = peerSocket;
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * @return The local port this connection's server socket is bound to
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * Closes the peer socket, server socket, and I/O streams.
     * <p>
     * Best-effort: individual close failures are swallowed.
     * </p>
     */
    public void close() {
        closed = true;
        closeQuietly(peerSocket);
        closeQuietly(serverSocket);
        closeQuietly(in);
        if (out != null) {
            out.close();
        }
    }

    /**
     * Initializes the buffered reader and auto-flushing printer for a socket.
     *
     * @param socket The connected socket to attach streams to
     * @throws IOException if the stream constructors fail
     */
    private void initializeStreams(Socket socket) throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Forwards an error to the registered handler unless the connection is
     * already closed.
     *
     * @param exception The error that occurred
     */
    private void reportError(Exception exception) {
        if (!closed && errorHandler != null) {
            errorHandler.accept(exception);
        }
    }

    /**
     * Closes a resource, ignoring any close failure.
     *
     * @param closeable Resource to close; may be {@code null}
     */
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Closing is best effort during shutdown.
            }
        }
    }
}
