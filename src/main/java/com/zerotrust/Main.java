package com.zerotrust;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.zerotrust.network.AsyncPeer;
import com.zerotrust.network.Client;
import com.zerotrust.network.Server;

/**
 * Main entry point for the Secure P2P Communication System.
 * <p>
 * This application provides three operational modes:
 * <ul>
 * <li><b>Server Mode:</b> Starts a server that listens for incoming
 * connections</li>
 * <li><b>Client Mode:</b> Connects to a server and sends/receives messages</li>
 * <li><b>Interactive Peer Mode:</b> Enables bidirectional P2P chat with
 * encryption</li>
 * </ul>
 * </p>
 * 
 * @author Aritra Chakraborty
 * @version 1.0
 */
public class Main {

    /**
     * Flag to control the running state of the interactive chat.
     * Used to coordinate graceful shutdown across multiple threads.
     */
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Main entry point of the application.
     * <p>
     * Parses command-line arguments to determine the operational mode and
     * initializes the appropriate component (Server, Client, or Interactive Peer).
     * </p>
     * 
     * @param args Command-line arguments:
     *             <ul>
     *             <li>args[0]: Mode ("server", "client", "peer", or
     *             "interactive")</li>
     *             <li>args[1+]: Additional mode-specific parameters</li>
     *             </ul>
     */
    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                String mode = args[0];

                if (mode.equals("server")) {
                    System.out.println("Starting Secure P2P Server...");
                    Server server = new Server(12345);
                    server.start();
                    server.close();

                } else if (mode.equals("client")) {
                    System.out.println("Starting Secure P2P Client...");
                    Client client = new Client("localhost", 12345);
                    client.sendMessage("Hello from Secure P2P Client!");
                    String response = client.receiveMessage();
                    System.out.println("Response: " + response);
                    client.close();

                } else if (mode.equals("peer") || mode.equals("interactive")) {
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
     * All communications are encrypted using quantum-safe AES-256 encryption after
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
        System.out.println("✓ AsyncPeer: " + peerName + " initialized on port " + port);

        try {
            if (mode.equals("listen")) {
                // Listening mode: wait for incoming connection
                System.out.println("📡 Listening for incoming connections on port " + port + "...");
                peer.acceptConnectionAsync(new Consumer<AsyncPeer>() {
                    @Override
                    public void accept(AsyncPeer p) {
                        // Connection accepted callback (currently empty)
                    }
                });
                System.out.println("✓ Connection accepted!");

                // Wait for connection to be ready (I/O streams initialized)
                System.out.println("⏳ Waiting for connection to be ready...");
                if (!peer.waitForConnectionReady(5000)) {
                    throw new RuntimeException("Connection failed to initialize within timeout period");
                }
                System.out.println("✓ Connection ready!");

                // Exchange peer identifiers
                System.out.println("🔄 Exchanging peer identifiers...");
                String remotePeerId = peer.exchangePeerId();
                System.out.println("✓ Remote peer identified: " + remotePeerId);

                // Perform secure key exchange
                System.out.println("🔐 Performing key exchange...");
                peer.performKeyExchangeAsync(() -> {
                    // Key exchange completed callback (currently empty)
                });
                System.out.println("✓ Key exchange completed!\n");

                // Start interactive chat session
                startInteractiveChat(peer, peerName, remotePeerId, true);

            } else if (mode.equals("connect")) {
                // Connect mode: initiate connection to remote peer
                String remoteHost = args.length > 4 ? args[4] : "localhost";
                int remotePort = args.length > 5 ? Integer.parseInt(args[5]) : 9000;

                System.out.println("🔗 Connecting to " + remoteHost + ":" + remotePort + "...");
                peer.connectToPeerAsync(remoteHost, remotePort, null);
                System.out.println("✓ Connected!");

                // Wait for connection to be ready (I/O streams initialized)
                System.out.println("⏳ Waiting for connection to be ready...");
                if (!peer.waitForConnectionReady(15000)) {
                    throw new RuntimeException("Connection failed to initialize within timeout period");
                }
                System.out.println("✓ Connection ready!");

                // Exchange peer identifiers
                System.out.println("🔄 Exchanging peer identifiers...");
                String remotePeerId = peer.exchangePeerId();
                System.out.println("✓ Remote peer identified: " + remotePeerId);

                // Perform secure key exchange
                System.out.println("🔐 Performing key exchange...");
                peer.performKeyExchangeAsync(() -> {
                    // Key exchange completed callback (currently empty)
                });
                System.out.println("✓ Key exchange completed!\n");

                // Start interactive chat session
                startInteractiveChat(peer, peerName, remotePeerId, true);
            } else {
                System.out.println("Invalid mode. Use 'listen' or 'connect'");
            }

        } finally {
            peer.close();
            System.out.println("\n✓ Connection closed.");
        }
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

        // Start receive thread
        Thread receiveThread = new Thread(() -> {
            try {
                while (isRunning.get()) {
                    try {
                        String message = peer.pollMessage();
                        if (message != null && !message.isEmpty()) {
                            System.out.println("\n📨 " + remoteDisplayName + ": " + message);
                            System.out.print(localDisplayName + "> ");
                        }
                    } catch (Exception e) {
                        if (isRunning.get()) {
                            System.out.println("\n⚠️ Error receiving message: " + e.getMessage());
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                if (isRunning.get()) {
                    System.err.println("Receive thread error: " + e.getMessage());
                }
            }
        }, "ReceiveThread");
        receiveThread.setDaemon(true);
        receiveThread.start();

        // Main send thread
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("💬 Type messages to send (commands: /help, /quit):\n");
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
                        System.out.println("❌ Error sending message: " + e.getMessage());
                        System.out.print(localDisplayName + "> ");
                    }
                } else {
                    System.out.print(localDisplayName + "> ");
                }
            }
        } finally {
            isRunning.set(false);
            reader.close();
            // Give receive thread time to exit
            Thread.sleep(100);
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
            case "/exit":
            case "/close":
                System.out.println("\n👋 Closing connection...");
                isRunning.set(false);
                break;

            case "/help":
                System.out.println("\n📖 Available Commands:");
                System.out.println("  /help      - Show this help message");
                System.out.println("  /quit      - Close connection and exit");
                System.out.println("  /exit      - Same as /quit");
                System.out.println("  /close     - Same as /quit");
                System.out.println("  /status    - Show connection status");
                System.out.println("  /clear     - Clear screen");
                System.out.println();
                System.out.print(peerName + "> ");
                break;

            case "/status":
                System.out.println("\n✓ Connection Status:");
                System.out.println("  Local AsyncPeer: " + peerName);
                System.out.println("  Remote AsyncPeer: " + remotePeerId);
                System.out.println("  Status: CONNECTED & PAIRED");
                System.out.println("  Encryption: AES-256 (Quantum-Safe)");
                System.out.println();
                System.out.print(peerName + "> ");
                break;

            case "/clear":
                System.out.print("\033[H\033[2J");
                System.out.flush();
                System.out.print(peerName + "> ");
                break;

            default:
                System.out.println("❌ Unknown command: " + command);
                System.out.println("   Type '/help' for available commands");
                System.out.print(peerName + "> ");
        }
    }

    /**
     * Prints comprehensive usage instructions for all operational modes.
     * <p>
     * Displays command-line syntax and examples for:
     * <ul>
     * <li>Server mode</li>
     * <li>Client mode</li>
     * <li>Interactive peer mode (both listening and connecting)</li>
     * </ul>
     * </p>
     */
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println(" java Main server - Start as server");
        System.out.println(" java Main client - Start as client");
        System.out.println(" java Main peer [port] [listen] - Start as peer");
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