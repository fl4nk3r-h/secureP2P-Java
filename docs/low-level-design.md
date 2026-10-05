# Low-Level Design and API

The package and class diagrams for this API are maintained in [Module-Level Design](module-level-design.md).

## Packages

```text
com.zerotrust
  Main.java
  crypto/
    CryptoUtils.java
    KeyExchange.java
    AeadCrypto.java
    MlKemKeyExchange.java
  network/
    AsyncPeer.java
    PeerConnection.java
    SessionManager.java
    MessageListener.java
    Client.java
    Server.java
```

## `Main`

`Main.main(String[])` accepts these modes:

| Mode | Arguments | Behavior |
| --- | --- | --- |
| `peer` | Same as `interactive` | Alias for interactive peer mode. |
| `interactive` | `[peerId] [localPort] [listen\|connect] [host] [remotePort]` | Runs encrypted terminal chat. Host and remote port apply to `connect`. |

Invalid or missing modes print usage information. Exceptions are reported to standard error.

## Legacy `Client` and `Server` classes

These classes remain as source-level synchronous echo examples but are no longer selectable through `Main`.

### `Client`

```java
Client(String address, int port) throws IOException
void sendMessage(String message)
String receiveMessage() throws IOException
void close() throws IOException
```

The constructor opens the socket and creates auto-flushing text streams. `sendMessage` writes one line. `receiveMessage` blocks until a line or EOF. This class has no encryption or retry behavior.

### `Server`

```java
Server(int port) throws IOException
void start() throws IOException
void close() throws IOException
```

The constructor binds a `ServerSocket`. `start()` accepts one client and loops over `readLine()`, replying with `Echo:` plus the input. It does not accept multiple clients and is not an encrypted service.

## `AsyncPeer`

### Construction and connection

```java
AsyncPeer(String peerId, int port) throws Exception
void acceptConnectionAsync(Consumer<AsyncPeer> callback)
void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback)
boolean waitForConnectionReady(long timeoutMillis) throws InterruptedException
```

Construction creates a four-thread executor and delegates transport setup to `PeerConnection`, session state to `SessionManager`, and inbound delivery to `MessageListener`. The `PeerConnection` binds `port`; accept/connect methods initialize its socket and streams asynchronously. Callers must wait for readiness before using the streams.

### Handshake

```java
String exchangePeerId() throws IOException
void performKeyExchangeAsync(Runnable onComplete)
```

`exchangePeerId()` writes `PEER_ID:<local-id>` and reads the matching line from the other peer. Both peers should call it concurrently because each side writes and then reads.

`performKeyExchangeAsync` writes a Base64 public key, reads the peer key, computes DH, hashes the Base64 shared-secret string with SHA-256, stores the resulting AES key, and starts the message listener. The method returns before the operation completes; use `onComplete` or another synchronization mechanism before sending encrypted application data.

### Messaging and callbacks

```java
void sendMessageAsync(String message)
void sendMessageAsync(String message, Consumer<Boolean> callback)
String pollMessage()
String pollMessage(long timeout, TimeUnit unit) throws InterruptedException
void onMessageReceived(Consumer<String> callback)
void onError(Consumer<Exception> callback)
void onSendComplete(Consumer<Boolean> callback)
```

After the key is set, sends are Base64-encoded ciphertext lines. Before the key is set, `SessionManager` rejects the send instead of transmitting plaintext. `MessageListener` puts the raw line into `messageQueue`; a worker decrypts the line and invokes `onMessageReceived` with the plaintext. `pollMessage()` therefore returns the queued wire value, not the callback payload.

The registered `onSendComplete` callback is used when a per-call callback is not supplied. Callbacks receive `true` only after the delegated transport write succeeds and `false` when session or transport handling fails.

### State and lifecycle methods

```java
boolean isConnected()
boolean isEncrypted()
String getPeerId()
String getRemotePeerId()
int getPort()
int getQueueSize()
void close()
```

A practical lifecycle is:

```text
constructed -> accepting/connecting -> connection ready
           -> peer IDs exchanged -> key exchange complete -> messaging -> closed
```

`close()` closes the transport first to unblock the listener, stops message delivery, clears session state, and shuts down the executor. Use it in a `finally` block.

## `CryptoUtils`

```java
String encrypt(String data, SecretKey key) throws Exception
String decrypt(String encryptedData, SecretKey key) throws Exception
SecretKey generateKey() throws Exception
SecretKey getKeyFromString(String keyString) throws Exception
```

`generateKey()` creates a random 256-bit AES key. `getKeyFromString()` uses SHA-256 over UTF-8 bytes and wraps the 32-byte digest as an AES key. Encrypt/decrypt convert text using the platform default charset.

## `KeyExchange`

```java
KeyExchange() throws Exception
String getPublicKeyString()
byte[] generateSharedSecret(String otherPublicKeyString) throws Exception
String getSharedSecretString(String otherPublicKeyString) throws Exception
```

Public keys are X.509-encoded DH keys represented as Base64. The implementation initializes a 1024-bit DH key pair and uses Java `KeyAgreement` to compute the shared secret.

## Wire format

The current protocol has three line shapes:

```text
PEER_ID:<peer identifier>
<Base64-encoded X.509 DH public key>
<Base64-encoded AES ciphertext or plaintext before key setup>
```

There is no framing metadata, message type, sequence number, nonce, authentication tag, protocol version, or length field.
