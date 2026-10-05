package com.zerotrust.legacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Archived plaintext single-client echo server. Not exposed by Main or active
 * peer code.
 * <p>
 * Accepts a single client connection and echoes every received line prefixed
 * with {@code "Echo: "}. Retained for reference only.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see Client
 */
@Deprecated(forRemoval = false)
public class Server {
    private final ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Binds a server socket to the given port.
     *
     * @param port Port to listen on
     * @throws IOException if the port cannot be bound
     */
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    /**
     * Blocks until a client connects, then echoes every received line until
     * the connection is closed.
     * <p>
     * Note: this call does not return after the loop exits; use
     * {@link #close()} from another thread to terminate.
     * </p>
     *
     * @throws IOException if accepting the client or the I/O streams fails
     */
    public void start() throws IOException {
        clientSocket = serverSocket.accept();
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            out.println("Echo: " + inputLine);
        }
    }

    /**
     * Closes the client streams, client socket, and server socket.
     *
     * @throws IOException if a close operation fails
     */
    public void close() throws IOException {
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        if (clientSocket != null) {
            clientSocket.close();
        }
        serverSocket.close();
    }
}
