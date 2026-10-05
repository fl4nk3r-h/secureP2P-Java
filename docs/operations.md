# Operations and Troubleshooting

## Running the peer application

Build once:

```bash
mvn clean package
```

Run peer chat in two terminals:

```bash
# Listener: binds local port 12346
java -jar target/securep2p-2.0.0-SNAPSHOT.jar interactive Alice 12346 listen

# Connector: binds local port 12347 and connects to listener
java -jar target/securep2p-2.0.0-SNAPSHOT.jar interactive Bob 12347 connect localhost 12346
```

The listener's port is the remote port used by the connector. Each peer also binds its own local port, so both ports must be available.

## Chat commands

- `/help` prints the command list.
- `/status` prints local and remote peer information plus encryption state.
- `/clear` emits terminal clear-screen control characters.
- `/quit`, `/exit`, and `/close` stop the chat and close the peer.

## Port conflicts

A `java.net.BindException: Address already in use` error means another process owns the selected local port. Choose another port or identify the process with an operating-system tool such as:

```bash
lsof -i :12346
```

Avoid killing processes blindly; verify the process identity first.

## Connection failures

- `Connection refused`: start the listener first, verify host and remote port, and check firewall rules.
- Readiness timeout: the asynchronous connect/accept task has not initialized both streams within the configured timeout.
- Peer-ID exchange failure: both sides must reach the exchange step, and the two calls should run concurrently.
- Key exchange failure: both sides must run `performKeyExchangeAsync`; wait for both completion callbacks before sending.
- Decryption failure: confirm both peers used the same handshake sequence and have not received malformed or modified ciphertext.

## Shutdown and diagnostics

Use `/quit` for an interactive session. `AsyncPeer.close()` closes the active socket, listening socket, streams, listener thread, and executor. The application prints stack traces for asynchronous errors, so capture standard error when diagnosing failures.

The project has no external configuration file, service manager, health endpoint, metrics, or persistent logs. Port, peer ID, and connection settings are supplied as command-line arguments or are fixed in `Main` defaults. The legacy `Client` and `Server` classes are not application modes.

## Deployment boundary

The application is intended for local or controlled-network use. Do not expose the current peer protocol to an untrusted network. Network encryption is not a substitute for authentication, authorization, patch management, firewalling, or process isolation.
