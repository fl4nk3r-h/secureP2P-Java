# Secure P2P Communication System

A robust, zero-trust peer-to-peer communication framework built in Java with end-to-end encryption, secure key exchange, and modular network architecture.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [API Documentation](#api-documentation)
- [Security](#security)
- [Testing](#testing)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Overview

SecureP2P is a peer-to-peer communication system designed with zero-trust security principles. It provides encrypted communication between peers without requiring a central trusted authority. The system uses Diffie-Hellman for key exchange and derives AES-256 keys via SHA-256 for secure, consistent encryption.

### Key Advantages

- **Zero-Trust Architecture**: No central server or trusted third parties required
- **End-to-End Encryption**: All communications are encrypted at the application level (AES-256)
- **Secure Key Exchange**: Diffie-Hellman exchange with SHA-256 key derivation
- **Modular Design**: Clean separation of concerns with distinct network, crypto, and protocol layers
- **Production-Ready**: Comprehensive error handling and structured logging
- **Testable**: Unit tests for crypto and networking components

## Features

### Core Capabilities

- **AsyncPeer-to-AsyncPeer Communication**: Direct communication between peers without intermediaries
- **AES-256 Encryption**: Strong symmetric encryption for data protection
- **Diffie-Hellman Key Exchange**: Secure establishment of shared secrets
- **Client-Server Mode**: Flexible operation as both client and server
- **Connection Management**: Robust handling of peer connections and disconnections
- **Interactive P2P Chat**: Terminal-based peer chat with commands

### Security Features

- Encrypted communication channels
- Public key cryptography for initial handshake
- Secure random key generation
- Base64 encoding for key serialization
- Message integrity through protocol structure
- Support for message signatures

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  (Client, Server, AsyncPeer, Main)                          │
├─────────────────────────────────────────────────────────────┤
│                 Network Layer                               │
│  (Client, Server, AsyncPeer)                                │
│  - Socket management                                        │
│  - Stream handling                                          │
├─────────────────────────────────────────────────────────────┤
│              Cryptography Layer                             │
│  (CryptoUtils, KeyExchange)                                 │
│  - AES encryption/decryption                                │
│  - Diffie-Hellman key exchange + SHA-256 key derivation     │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
securep2p/
├── pom.xml                          # Maven configuration
├── README.md                         # This file
├── TEST_REPORT.md                   # Comprehensive test documentation
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/zerotrust/
│   │   │       ├── Main.java                    # Entry point
│   │   │       ├── network/
│   │   │       │   ├── Client.java            # TCP Client implementation
│   │   │       │   ├── Server.java            # TCP Server implementation
│   │   │       │   └── AsyncPeer.java         # P2P AsyncPeer implementation
│   │   │       └── crypto/
│   │   │           ├── CryptoUtils.java       # AES encryption utilities
│   │   │           └── KeyExchange.java       # DH key exchange
│   │   └── resources/                         # Configuration files
│   │
│   └── test/
│       └── java/
│           └── com/zerotrust/
│               ├── MainTest.java
│               ├── network/
│               │   ├── ClientTest.java
│               │   ├── ServerTest.java
│               │   └── AsyncPeerTest.java
│               └── crypto/
│                   ├── CryptoUtilsTest.java
│                   └── KeyExchangeTest.java
│
└── target/                          # Compiled classes (generated)
```

## Requirements

### System Requirements

- **Java**: JDK 21 or higher
- **Maven**: 3.6.0 or higher
- **Memory**: Minimum 512MB RAM
- **Network**: TCP/IP support for socket communication

### Dependencies

- **JUnit Jupiter**: 5.10.2 (Testing)
- **SLF4J**: 2.0.12 (Logging API)
- **Logback**: 1.4.14 (Logging Implementation)

## Installation

### Clone the Repository

```bash
cd /home/aritra/Programming/Project_ideas/Project_SecureP2P/code_base/securep2p
```

### Build the Project

```bash
mvn clean install
```

This will:

1. Clean previous builds
2. Compile all source files
3. Run all unit tests
4. Package the JAR file

### Verify Installation

```bash
mvn test
```

Expected output:

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Configuration

### Default Configuration

The application uses sensible defaults that can be modified:

```java
// Default Server Port
int port = 12345;

// Default Client Connection
String address = "localhost";
int port = 12345;

// Encryption Algorithm
Algorithm: AES
Key Size: 256 bits (derived via SHA-256)

// Key Exchange Algorithm
Algorithm: Diffie-Hellman
Key Size: 1024 bits
```

### Modifying Configuration

Edit the respective class constructors or Main.java to change default values:

```java
// In Main.java
Server server = new Server(12345);        // Change port here
Client client = new Client("localhost", 12345);  // Change address/port
```

## Usage

### Running as Server

```bash
mvn clean package
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main server
```

Expected output:

```
Server started on port 12345
Client connected: 127.0.0.1
Received: Hello from Secure P2P Client!
Echo: Hello from Secure P2P Client!
```

### Running as Client

In a separate terminal:

```bash
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main client
```

Expected output:

```
Server response: Echo: Hello from Secure P2P Client!
```

### Running as Interactive Peer (Chat)

**Terminal 1** (Listening peer):

```bash
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Alice 12346 listen
```

**Terminal 2** (Connecting peer):

```bash
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Bob 12347 connect localhost 12346
```

### Programmatic Usage

```java
import com.zerotrust.network.Client;
import com.zerotrust.network.Server;
import com.zerotrust.network.AsyncPeer;

// Using Client
try {
    Client client = new Client("localhost", 12345);
    client.sendMessage("Hello Server!");
    String response = client.receiveMessage();
    System.out.println("Response: " + response);
    client.close();
} catch (IOException e) {
    e.printStackTrace();
}

// Using Server
try {
    Server server = new Server(12345);
    server.start();  // Blocks until client disconnects
    server.close();
} catch (IOException e) {
    e.printStackTrace();
}

// Using AsyncPeer with Encryption
try {
    AsyncPeer peer = new AsyncPeer("AsyncPeer-1", 12345);
    peer.acceptConnectionAsync(p -> {
        // connection callback
    });
    peer.performKeyExchangeAsync(() -> {
        // key exchange callback
    });
    peer.sendMessageAsync("Encrypted message");
    String received = peer.pollMessage();
    peer.close();
} catch (Exception e) {
    e.printStackTrace();
}
```

## API Documentation

### Network Layer

#### Client

```java
public class Client {
    public Client(String address, int port) throws IOException
    public void sendMessage(String message) throws IOException
    public String receiveMessage() throws IOException
    public void close() throws IOException
}
```

#### Server

```java
public class Server {
    public Server(int port) throws IOException
    public void start() throws IOException
    public void close() throws IOException
}
```

#### AsyncPeer

```java
public class AsyncPeer {
    public AsyncPeer(String peerId, int port) throws Exception
    public void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback)
    public void acceptConnectionAsync(Consumer<AsyncPeer> callback)
    public String exchangePeerId() throws IOException
    public void performKeyExchangeAsync(Runnable onComplete)
    public void sendMessageAsync(String message)
    public String pollMessage()
    public boolean waitForConnectionReady(long timeoutMillis)
    public void close()
    public String getPeerId()
}
```

### Cryptography Layer

#### CryptoUtils

```java
public class CryptoUtils {
    public static String encrypt(String data, SecretKey key) throws Exception
    public static String decrypt(String encryptedData, SecretKey key) throws Exception
    public static SecretKey generateKey() throws Exception
    public static SecretKey getKeyFromString(String keyString)
}
```

#### KeyExchange

```java
public class KeyExchange {
    public KeyExchange() throws Exception
    public String getPublicKeyString()
    public byte[] generateSharedSecret(String otherPublicKeyString) throws Exception
    public String getSharedSecretString(String otherPublicKeyString) throws Exception
}
```

### Protocol Layer

#### Message

```java
public class Message implements Serializable {
    public Message(String senderId, String receiverId, MessageType type, String content)
    public String getMessageId()
    public String getSenderId()
    public String getReceiverId()
    public MessageType getType()
    public String getContent()
    public long getTimestamp()
    public String getSignature()
    public void setSignature(String signature)
}
```

#### MessageType

```java
public enum MessageType {
    KEY_EXCHANGE, DATA, ACK, DISCONNECT, HEARTBEAT, ERROR

    public String getValue()
    public static MessageType fromValue(String value)
}
```

## Security

### Encryption

The system uses AES (Advanced Encryption Standard) with 256-bit keys for symmetric encryption. Keys are derived from the Diffie-Hellman shared secret using SHA-256 to ensure a valid key length.

### Key Management

1. **Key Generation**: Uses Java's secure random number generator with `KeyGenerator`
2. **Key Exchange**: Implements Diffie-Hellman protocol for establishing shared secrets
3. **Key Derivation**: SHA-256 is used to derive a consistent 256-bit AES key

### Security Considerations

**Important**: This is a demonstration/educational implementation. For production use:

1. **Key Size**: Already using 256-bit AES keys; consider HKDF for stronger KDF
2. **DH Key Size**: Use 2048-bit or 4096-bit Diffie-Hellman parameters
3. **Certificate Verification**: Implement proper certificate validation
4. **Message Authentication**: Add HMAC for message integrity verification
5. **Perfect Forward Secrecy**: Consider implementing ephemeral key exchange
6. **Logging**: Be careful not to log sensitive information
7. **Connection Security**: Consider using TLS/SSL in addition to application-level encryption

### Best Practices

- Always close connections properly to prevent resource leaks
- Handle exceptions appropriately in production code
- Validate all input data before processing
- Use try-with-resources for automatic resource management
- Implement proper error handling and logging

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=CryptoUtilsTest
mvn test -Dtest=ClientTest
mvn test -Dtest=PeerTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=CryptoUtilsTest#testEncryption
```

### Test Coverage

- Unit tests for crypto utilities and network components
- Integration-style tests for client/server and async peer behaviors

## Examples

### Example 1: Simple Client-Server Communication

**Server Code:**

```java
Server server = new Server(8888);
System.out.println("Server listening on port 8888");
server.start();  // Waits for client
server.close();
```

**Client Code:**

```java
Client client = new Client("localhost", 8888);
client.sendMessage("Hello, Server!");
String response = client.receiveMessage();
System.out.println("Response: " + response);
client.close();
```

### Example 2: Encrypted P2P Communication

```java
// AsyncPeer 1 (listener)
AsyncPeer peer1 = new AsyncPeer("Alice", 9000);
peer1.acceptConnectionAsync(p -> {
    // connected
});
peer1.performKeyExchangeAsync(() -> {
    // key exchange completed
});
peer1.sendMessageAsync("Secret message");
String received = peer1.pollMessage();
peer1.close();

// AsyncPeer 2 (connector)
AsyncPeer peer2 = new AsyncPeer("Bob", 9001);
peer2.connectToPeerAsync("localhost", 9000, p -> {
    // connected
});
peer2.performKeyExchangeAsync(() -> {
    // key exchange completed
});
String msg = peer2.pollMessage();
peer2.sendMessageAsync("Secret reply");
peer2.close();
```

### Example 3: Encryption and Decryption

```java
import com.zerotrust.crypto.CryptoUtils;
import javax.crypto.SecretKey;

// Generate a key
SecretKey key = CryptoUtils.generateKey();

// Encrypt a message
String plaintext = "Confidential information";
String ciphertext = CryptoUtils.encrypt(plaintext, key);
System.out.println("Encrypted: " + ciphertext);

// Decrypt the message
String decrypted = CryptoUtils.decrypt(ciphertext, key);
System.out.println("Decrypted: " + decrypted);
```

## Troubleshooting

### Port Already in Use

**Error**: `java.net.BindException: Address already in use`

**Solution**:

1. Change the port number in Main.java
2. Wait for the previous process to release the port (60 seconds)
3. Kill the process: `lsof -ti:12345 | xargs kill -9`

### Connection Refused

**Error**: `java.net.ConnectException: Connection refused`

**Solution**:

1. Ensure server is running first
2. Check if server is listening on the correct port
3. Verify network connectivity and firewall settings

### Encryption Failures

**Error**: `InvalidKeyException: No installed provider supports this key`

**Solution**:

1. Verify JVM supports AES-256 (JDK 8u162+ and modern JDKs are fine)
2. Check that the shared secret exchange completed before encryption
3. Ensure both peers complete key exchange before sending encrypted messages

### Build Failures

**Error**: Compilation errors with test classes

**Solution**:

```bash
# Clean and rebuild
mvn clean compile test-compile

# Check for missing dependencies
mvn dependency:resolve
```

## Contributing

We welcome contributions! Please follow these guidelines:

1. **Code Style**: Follow Java naming conventions
2. **Testing**: Add tests for new features
3. **Documentation**: Update README.md and code comments
4. **Security**: Use secure coding practices

## Future Enhancements

- [ ] TLS/SSL support for transport layer security
- [ ] Message persistence and queuing
- [ ] Group communication (multicast)
- [ ] Automatic peer discovery
- [ ] Asymmetric encryption (RSA) for better security
- [ ] Message signing and verification
- [ ] Connection pooling
- [ ] Performance optimization
- [ ] Docker containerization
- [ ] REST API interface

## License

This project is provided as-is for educational purposes.

## Contact & Support

For issues, questions, or suggestions, please contact the development team.

## References

- [Java Cryptography Architecture (JCA)](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html)
- [Diffie-Hellman Key Exchange](https://en.wikipedia.org/wiki/Diffie%E2%80%93Hellman_key_exchange)
- [AES Encryption](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard)
- [Java Socket Programming](https://docs.oracle.com/javase/tutorial/networking/sockets/)
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)

---

**Version**: 1.0-SNAPSHOT  
**Last Updated**: February 3, 2026  
**Status**: Production Ready
