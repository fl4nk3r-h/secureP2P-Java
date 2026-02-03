package com.zerotrust.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Simple TCP Client to connect to the Server
 * 
 * @author Aritra Chakraborty
 * @version 1.0
 */
public class Client {
    // public static void main(String[] args) {
    //     try {
    //         Client client = new Client("localhost", 12345);
    //         client.sendMessage("Hello, Server!");
    //         String response = client.receiveMessage();
    //         System.out.println("Server response: " + response);
    //         client.close();
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    // }
    private Socket socket;
    private PrintWriter out;

    private BufferedReader in;

    /**
     * Creates a Client that connects to the specified address and port.
     * 
     * @param address
     * @param port
     * @throws IOException
     */
    public Client(String address, int port) throws IOException {
        socket = new Socket(address, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Sends a message to the server.
     * 
     * @param message
     */
    public void sendMessage(String message) {
        out.println(message);
    }

    /**
     * Receives a message from the server.
     * 
     * @return The message received from the server.
     * @throws IOException
     */
    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    /**
     * Closes the client socket and associated streams.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
}
