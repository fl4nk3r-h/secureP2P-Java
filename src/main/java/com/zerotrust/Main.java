package com.zerotrust;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.zerotrust.network.AsyncPeer;

/**
 * Main entry point for the Secure P2P Communication System.
 * <p>
 * This application provides interactive peer-to-peer chat mode.
 * </p>
 * 
 * @author fl4nk3r-h
 * @version 2.0.0
 */
public class Main {

    private static final long CONNECTION_TIMEOUT_MILLIS = 15000;
    private static final long KEY_EXCHANGE_TIMEOUT_MILLIS = 15000;

    /**
     * Flag to control the running state of the interactive chat.
     * Used to coordinate graceful shutdown across multiple threads.
     */
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Main entry point of the application.
     * <p>
     * Parses command-line arguments to determine the operational mode and
     * initializes the interactive peer component.
     * </p>
     * 
     * @param args Command-line arguments:
     *             <ul>
     *             <li>args[0]: Mode ("peer" or "interactive")</li>
     *             <li>args[1+]: Additional mode-specific parameters</li>
     *             </ul>
     */
    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                String mode = args[0];

                if (mode.equals("peer") || mode.equals("interactive")) {
                    startInteractivePeerMode(args);

                } else {
                    printUsage();
                    System.out.println("Invalid mode: " + mode);
                }
            } else {
                System.out.println("Secure P2P Communication System");
                System.out.println("Mode not specified. Use one of the following:");
                printUsage();
            }
        } catch (Exception e) {
            System.err.println("Error in Main: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Starts the interactive P2P peer communication mode.
     * <p>
     * This mode supports simultaneous bidirectional message exchange with a
     * command-based
     * interface. The peer can either listen for incoming connections or connect to
     * another peer.
     * Communications use the currently configured session implementation after
     * key exchange.
     * </p>
     * 
     * @param args Command-line arguments:
     *             <ul>
     *             <li>args[1]: Peer name (default: auto-generated)</li>
     *             <li>args[2]: Local port (default: 9000)</li>
     *             <li>args[3]: Mode - "listen" or "connect" (default:
     *             "listen")</li>
     *             <li>args[4]: Remote host for connect mode (default:
     *             "localhost")</li>
     *             <li>args[5]: Remote port for connect mode (default: 9000)</li>
     *             </ul>
     * @throws Exception if connection, key exchange, or I/O operations fail
     */
    private static void startInteractivePeerMode(String[] args) throws Exception {
        // Parse command-line arguments with defaults
        String peerName = args.length > 1 ? args[1] : "AsyncPeer-" + (System.nanoTime() % 10000);
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 9000;
        String mode = args.length > 3 ? args[3] : "listen";

        // Display welcome banner
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║ Secure P2P Interactive Chat System  ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        AsyncPeer peer = new AsyncPeer(peerName, port);
        System.out.println(" AsyncPeer: " + peerName + " initialized on port " + port);

        try {
            AtomicReference<Exception> asyncFailure = new AtomicReference<>();

            if (mode.equals("listen")) {
                // Listening mode: wait for incoming connection
                System.out.println(" Listening for incoming connections on port " + port + "...");
                awaitConnection(peer, asyncFailure, true, null, 0);
                System.out.println(" Connection accepted and ready!");

                // Exchange peer identifiers
                System.out.println(" Exchanging peer identifiers...");
                String remotePeerId = peer.exchangePeerId();
                System.out.println(" Remote peer identified: " + remotePeerId);

                registerMessageDisplay(peer, peerName, remotePeerId);

                // Perform secure key exchange
                System.out.println(" Performing key exchange...");
                awaitKeyExchange(peer, asyncFailure);
                System.out.println(" Key exchange completed!\n");

                // Start interactive chat session
                startInteractiveChat(peer, peerName, remotePeerId, true);

            } else if (mode.equals("connect")) {
                // Connect mode: initiate connection to remote peer
                String remoteHost = args.length > 4 ? args[4] : "localhost";
                int remotePort = args.length > 5 ? Integer.parseInt(args[5]) : 9000;

                System.out.println(" Connecting to " + remoteHost + ":" + remotePort + "...");
                awaitConnection(peer, asyncFailure, false, remoteHost, remotePort);
                System.out.println(" Connected and ready!");

                // Exchange peer identifiers
                System.out.println(" Exchanging peer identifiers...");
                String remotePeerId = peer.exchangePeerId();
                System.out.println(" Remote peer identified: " + remotePeerId);

                registerMessageDisplay(peer, peerName, remotePeerId);

                // Perform secure key exchange
                System.out.println(" Performing key exchange...");
                awaitKeyExchange(peer, asyncFailure);
                System.out.println(" Key exchange completed!\n");

                // Start interactive chat session
                startInteractiveChat(peer, peerName, remotePeerId, true);
            } else {
                System.out.println(" Invalid mode. Use 'listen' or 'connect'");
            }

        } finally {
            peer.close();
            System.out.println("\n Connection closed.");
        }
    }

    private static void awaitConnection(AsyncPeer peer, AtomicReference<Exception> asyncFailure,
            boolean listener, String remoteHost, int remotePort) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        peer.onError(exception -> {
            asyncFailure.compareAndSet(null, exception);
            connected.countDown();
        });
        Consumer<AsyncPeer> callback = ignored -> connected.countDown();
        if (listener) {
            peer.acceptConnectionAsync(callback);
        } else {
            peer.connectToPeerAsync(remoteHost, remotePort, callback);
        }

        if (!connected.await(CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            Exception failure = asyncFailure.get();
            if (failure != null) {
                throw new IOException("Connection failed: " + failure.getMessage(), failure);
            }
            throw new IOException("Connection failed to initialize within timeout period");
        }
        Exception failure = asyncFailure.get();
        if (failure != null) {
            throw new IOException("Connection failed: " + failure.getMessage(), failure);
        }
    }

    private static void awaitKeyExchange(AsyncPeer peer, AtomicReference<Exception> asyncFailure) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        peer.onError(exception -> {
            asyncFailure.compareAndSet(null, exception);
            completed.countDown();
        });
        peer.performKeyExchangeAsync(completed::countDown);
        if (!completed.await(KEY_EXCHANGE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            Exception failure = asyncFailure.get();
            if (failure != null) {
                throw new IOException("Key exchange failed: " + failure.getMessage(), failure);
            }
            throw new IOException("Key exchange did not complete within timeout period");
        }
        Exception failure = asyncFailure.get();
        if (failure != null) {
            throw new IOException("Key exchange failed: " + failure.getMessage(), failure);
        }
    }

    private static void registerMessageDisplay(AsyncPeer peer, String peerName, String remotePeerId) {
        String localDisplayName = (peerName != null && !peerName.isBlank()) ? peerName : peer.getPeerId();
        String remoteDisplayName = (remotePeerId != null && !remotePeerId.isBlank()) ? remotePeerId : "Remote";
        peer.onMessageReceived(message -> {
            System.out.println("\n " + remoteDisplayName + ": " + message);
            System.out.print(localDisplayName + "> ");
        });
    }

    /**
     * Starts the interactive chat loop with simultaneous send and receive
     * capabilities.
     * <p>
     * This method creates two threads:
     * <ul>
     * <li><b>Receive Thread:</b> Continuously polls for incoming messages</li>
     * <li><b>Main Thread:</b> Handles user input and sends messages</li>
     * </ul>
     * The chat supports various commands (e.g., /help, /quit, /status) for enhanced
     * user interaction.
     * </p>
     * 
     * @param peer         The AsyncPeer instance managing the connection
     * @param peerName     The local peer's display name
     * @param remotePeerId The remote peer's identifier
     * @param isListener   True if this peer initiated listening, false if it
     *                     connected
     * @throws Exception if message send/receive or I/O operations fail
     */
    private static void startInteractiveChat(AsyncPeer peer, String peerName, String remotePeerId, boolean isListener)
            throws Exception {
        isRunning.set(true);
        String localDisplayName = (peerName != null && !peerName.isBlank()) ? peerName : peer.getPeerId();
        String remoteDisplayName = (remotePeerId != null && !remotePeerId.isBlank()) ? remotePeerId : "Remote";

        // Main send thread
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(" Type messages to send (commands: /help, /quit):\n");
        System.out.print(localDisplayName + "> ");

        try {
            while (isRunning.get()) {
                String input = reader.readLine();

                if (input == null) {
                    break;
                }

                input = input.trim();

                // Handle commands
                if (input.startsWith("/")) {
                    handleCommand(input, localDisplayName, remoteDisplayName);
                } else if (!input.isEmpty()) {
                    try {
                        peer.sendMessageAsync(input);
                        System.out.print(localDisplayName + "> ");
                    } catch (Exception e) {
                        System.out.println(" Error sending message: " + e.getMessage());
                        System.out.print(localDisplayName + "> ");
                    }
                } else {
                    System.out.print(localDisplayName + "> ");
                }
            }
        } finally {
            isRunning.set(false);
            reader.close();
        }
    }

    /**
     * Handles user commands during the interactive chat session.
     * <p>
     * Supported commands:
     * <ul>
     * <li><b>/help:</b> Displays all available commands</li>
     * <li><b>/quit, /exit, /close:</b> Closes the connection and exits chat</li>
     * <li><b>/status:</b> Shows current connection status and encryption
     * details</li>
     * <li><b>/clear:</b> Clears the terminal screen</li>
     * </ul>
     * </p>
     * 
     * @param command      The command string entered by the user (must start with
     *                     '/')
     * @param peerName     The local peer's display name
     * @param remotePeerId The remote peer's identifier
     */
    private static void handleCommand(String command, String peerName, String remotePeerId) {
        switch (command.toLowerCase()) {
            case "/quit":
            case "/bye":
            case "/exit":
            case "/close":
                System.out.println("\n Closing connection...");
                isRunning.set(false);
                break;

            case "/help":
                System.out.println("\n Available Commands:");
                System.out.println("  /help      - Show this help message");
                System.out.println("  /quit      - Close connection and exit");
                System.out.println("  /bye       - Same as /quit");
                System.out.println("  /exit      - Same as /quit");
                System.out.println("  /close     - Same as /quit");
                System.out.println("  /status    - Show connection status");
                System.out.println("  /clear     - Clear screen");
                System.out.println();
                System.out.print(peerName + "> ");
                break;

            case "/status":
                System.out.println("\n Connection Status:");
                System.out.println("  Local AsyncPeer: " + peerName);
                System.out.println("  Remote AsyncPeer: " + remotePeerId);
                System.out.println("  Status: CONNECTED & PAIRED");
                System.out.println("  Encryption: legacy session encryption (v2 migration in progress)");
                System.out.println();
                System.out.print(peerName + "> ");
                break;

            case "/clear":
                System.out.print("\033[H\033[2J");
                System.out.flush();
                System.out.print(peerName + "> ");
                break;

            default:
                System.out.println(" Unknown command: " + command);
                System.out.println("   Type '/help' for available commands");
                System.out.print(peerName + "> ");
        }
    }

    /**
     * Prints comprehensive usage instructions for all operational modes.
     * <p>
     * Displays command-line syntax and examples for:
     * <ul>
     * <li>Interactive peer mode (both listening and connecting)</li>
     * </ul>
     * </p>
     */
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println(" java Main peer [name] [port] [listen|connect] [host] [remotePort]");
        System.out.println("");
        System.out.println("Interactive P2P Chat (NEW):");
        System.out.println(" java Main interactive [name] [port] listen");
        System.out.println(" - Start peer listening for connections");
        System.out.println("");
        System.out.println(" java Main interactive [name] [port] connect [host] [remotePort]");
        System.out.println(" - Connect to another peer");
        System.out.println("");
        System.out.println("Example - Terminal 1 (listening):");
        System.out.println(" java Main interactive Alice 9000 listen");
        System.out.println("");
        System.out.println("Example - Terminal 2 (connecting):");
        System.out.println(" java Main interactive Bob 9001 connect localhost 9000");
    }
}
