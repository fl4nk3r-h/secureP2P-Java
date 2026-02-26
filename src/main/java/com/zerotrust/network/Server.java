package com.zerotrust.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple TCP Server that echoes received messages
 * 
 * @author fl4nk3r
 * @version 1.0
 */
public class Server {
    // public static void main(String[] args) {
    //     try {
    //         Server server = new Server(12345);
    //         server.start();
    //         server.close();
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    // }
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;

    private BufferedReader in;

    /**
     * Creates a Server that listens on the specified port.
     * 
     * @param port
     * @throws IOException
     */
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
    }

    /**
     * Starts the server to accept a client connection and echo messages.
     * 
     * @throws IOException
     */
    public void start() throws IOException {
        clientSocket = serverSocket.accept();
        System.out.println("Client connected: " + clientSocket.getInetAddress());
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            System.out.println("Received: " + inputLine);
            out.println("Echo: " + inputLine);
        }
    }

    /**
     * Closes the server and client sockets.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        in.close();
        out.close();
        clientSocket.close();
        serverSocket.close();
    }
}
