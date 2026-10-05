package com.zerotrust.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Owns the TCP listener, active socket, streams, and connection lifecycle. */
public final class PeerConnection {
    private final int localPort;
    private final ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final Consumer<Exception> errorHandler;
    private volatile Socket peerSocket;
    private volatile PrintWriter out;
    private volatile BufferedReader in;
    private volatile boolean closed;

    public PeerConnection(int localPort, ExecutorService executorService, Consumer<Exception> errorHandler)
            throws IOException {
        this.localPort = localPort;
        this.executorService = executorService;
        this.errorHandler = errorHandler;
        this.serverSocket = new ServerSocket(localPort);
    }

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

    public synchronized void sendLine(String line) throws IOException {
        if (!isReady()) {
            throw new IOException("Peer connection is not ready");
        }
        out.println(line);
        if (out.checkError()) {
            throw new IOException("Peer connection write failed");
        }
    }

    public String readLine() throws IOException {
        if (!isReady()) {
            throw new IOException("Peer connection is not ready");
        }
        return in.readLine();
    }

    public boolean isReady() {
        Socket socket = peerSocket;
        return !closed && socket != null && socket.isConnected() && !socket.isClosed() && out != null && in != null;
    }

    public boolean isConnected() {
        Socket socket = peerSocket;
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public int getLocalPort() {
        return localPort;
    }

    public void close() {
        closed = true;
        closeQuietly(peerSocket);
        closeQuietly(serverSocket);
        closeQuietly(in);
        if (out != null) {
            out.close();
        }
    }

    private void initializeStreams(Socket socket) throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void reportError(Exception exception) {
        if (!closed && errorHandler != null) {
            errorHandler.accept(exception);
        }
    }

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
