package com.zerotrust.legacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Archived plaintext TCP echo client. Not exposed by Main or active peer code.
 * <p>
 * Connects to an echo {@link Server} and exchanges plaintext lines. Retained
 * for reference only.
 * </p>
 *
 * @author fl4nk3r-h
 * @version 2.0.0
 * @see Server
 */
@Deprecated(forRemoval = false)
public class Client {
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;

    /**
     * Connects to the echo server at the given address and port.
     *
     * @param address Host name or address of the server
     * @param port    Port of the server
     * @throws IOException if the connection cannot be established
     */
    public Client(String address, int port) throws IOException {
        socket = new Socket(address, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Sends a message line to the server.
     *
     * @param message Plaintext message to send
     */
    public void sendMessage(String message) {
        out.println(message);
    }

    /**
     * Reads one echoed line from the server.
     *
     * @return The received line, or {@code null} on end of stream
     * @throws IOException if a read error occurs
     */
    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    /**
     * Closes the streams and the socket.
     *
     * @throws IOException if a close operation fails
     */
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
}
