# Low-Level Design and API

The package and class diagrams for this API are maintained in [Module-Level Design](module-level-design.md).

## Packages

```text
com.zerotrust
  Main.java
  crypto/
    AeadCrypto.java
    MlKemKeyExchange.java
  network/
    AsyncPeer.java
    PeerConnection.java
    SessionManager.java
    MessageListener.java
  legacy/
    CryptoUtils.java
    KeyExchange.java
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

## Archived legacy classes

The archived `com.zerotrust.legacy` package contains the pre-v2 synchronous echo and DH/AES examples. They compile for historical comparison and tests, but active code does not import them.

### `com.zerotrust.legacy.Client`

```java
Client(String address, int port) throws IOException
void sendMessage(String message)
String receiveMessage() throws IOException
void close() throws IOException
```

The constructor opens the socket and creates auto-flushing text streams. `sendMessage` writes one line. `receiveMessage` blocks until a line or EOF. This class has no encryption or retry behavior.

### `com.zerotrust.legacy.Server`

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

Construction creates a four-thread executor and delegates transport setup to `PeerConnection`, PQC session state to `SessionManager`, and inbound delivery to `MessageListener`. The `PeerConnection` binds `port`; accept/connect methods initialize its socket and streams asynchronously. Callers must wait for readiness before using the streams.

### Handshake

```java
String exchangePeerId() throws IOException
void performKeyExchangeAsync(Runnable onComplete)
```

`exchangePeerId()` writes `PEER_ID:<local-id>` and reads the matching line from the other peer. Both peers should call it concurrently because each side writes and then reads.

`performKeyExchangeAsync` exchanges `PQC_V2:ML-KEM-768` public-key frames, encapsulates to the remote public key, exchanges encapsulation ciphertexts, derives a key from both ordered KEM secrets, and starts the message listener. The method returns before the operation completes; use `onComplete` or another synchronization mechanism before sending encrypted application data.

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

After the PQC session key is set, sends are Base64-encoded AES-GCM values containing a nonce, ciphertext, and tag. Before the key is set, `SessionManager` rejects the send instead of transmitting plaintext. `MessageListener` puts the raw line into `messageQueue`; a worker decrypts the line and invokes `onMessageReceived` with the plaintext. `pollMessage()` therefore returns the queued wire value, not the callback payload.

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

## `AeadCrypto`

```java
byte[] encrypt(byte[] plaintext, byte[] key, byte[] associatedData) throws GeneralSecurityException
byte[] decrypt(byte[] encrypted, byte[] key, byte[] associatedData) throws GeneralSecurityException
```

`AeadCrypto` requires a 32-byte AES key, generates a fresh 12-byte nonce per encryption, and authenticates optional associated data with a 128-bit GCM tag.

## `MlKemKeyExchange`

```java
KeyPair generateKeyPair() throws GeneralSecurityException
Encapsulation encapsulate(byte[] encodedPublicKey) throws Exception
byte[] decapsulate(byte[] encodedPrivateKey, byte[] encapsulation) throws Exception
```

The active implementation uses ML-KEM-768. Public and private keys use provider encodings; the session protocol transports public keys and encapsulation ciphertexts as Base64.

## Wire format

The current protocol has three line shapes:

```text
PEER_ID:<peer identifier>
PQC_V2:ML-KEM-768:<Base64-encoded public key>
PQC_CT:<Base64-encoded encapsulation ciphertext>
<Base64-encoded AES-GCM nonce+ciphertext+tag>
```

The current line protocol has no structured length field, authenticated identity, sequence number, replay cache, or Double Ratchet header. The `PQC_V2` marker identifies the active bootstrap format but is not yet an authenticated negotiation.
