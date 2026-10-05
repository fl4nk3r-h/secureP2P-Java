# SecureP2P Java

SecureP2P is a small Java 21 learning project that demonstrates line-oriented TCP communication, asynchronous peer connections, and a basic Diffie-Hellman/AES message flow. It is useful for studying socket lifecycle, concurrency, and Java cryptography APIs.

This repository is **not production-ready secure messaging software**. The current protocol does not authenticate peers, authenticate ciphertext, negotiate algorithms, or provide a nonce-based encryption mode. See [Security Notes](docs/security.md) before using it beyond local experiments.

## Quick Start

Requirements: JDK 21 and Maven 3.6 or newer.

```bash
mvn clean test
mvn package
```

Start the interactive demo in two terminals:

```bash
# Terminal 1: listener
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Alice 12346 listen

# Terminal 2: connector
java -cp target/securep2p-1.0-SNAPSHOT.jar com.zerotrust.Main interactive Bob 12347 connect localhost 12346
```

Use `/help`, `/status`, `/clear`, or `/quit` in the chat. The simpler `server` and `client` modes demonstrate an unencrypted TCP echo service.

## What Is Implemented

- `Main`: command-line entry point with `server`, `client`, `peer`, and `interactive` modes.
- `Client` and `Server`: one-client, line-oriented TCP echo example.
- `AsyncPeer`: asynchronous accept/connect operations, peer-ID exchange, DH key exchange, encrypted sends, a receive queue, and callbacks.
- `CryptoUtils`: AES helper methods and SHA-256-to-AES key derivation.
- `KeyExchange`: 1024-bit finite-field DH key generation and Base64 public-key serialization.
- `AeadCrypto`: explicit AES-256-GCM encryption with random nonces and authenticated associated data.
- `MlKemKeyExchange`: Bouncy Castle-backed ML-KEM-768 encapsulation and decapsulation foundation.

## Documentation

- [High-level architecture](docs/high-level-architecture.md): components, responsibilities, and end-to-end flows.
- [Module-level design](docs/module-level-design.md): package boundaries, class diagrams, dependencies, state model, and message paths.
- [Low-level design and API](docs/low-level-design.md): classes, state transitions, wire format, threading, and usage contracts.
- [Security notes](docs/security.md): implemented protections, known weaknesses, threat boundaries, and hardening priorities.
- [Development and testing](docs/development.md): project layout, Maven commands, test scope, and contribution guidance.
- [Operations and troubleshooting](docs/operations.md): runtime behavior, ports, failure modes, and cleanup.

## Repository Layout

```text
pom.xml                         Maven build and dependency configuration
src/main/java/com/zerotrust      Application, networking, and crypto code
src/test/java/com/zerotrust      JUnit tests for crypto and AsyncPeer behavior
docs/                            Maintained project documentation
```

Generated output under `target/` is not source documentation and should not be committed.

## Testing

Run the complete test suite:

```bash
mvn test
```

Run focused suites:

```bash
mvn test -Dtest=CryptoUtilsTest
mvn test -Dtest=KeyExchangeTest
mvn test -Dtest=AsyncPeerTest
```

The tests cover crypto round trips, DH agreement, peer connection setup, peer-ID exchange, key exchange, callbacks, message delivery, and cleanup. The test count is determined by the current source, so documentation intentionally does not hard-code an expected number.

## Scope and Status

The project currently has no configuration file, persistence layer, authentication authority, message model, file transfer protocol, or TLS integration. SLF4J and Logback are declared in Maven but the application currently writes directly to standard output and standard error.

The PQC migration is in progress. The tested ML-KEM and AES-GCM primitives are not yet wired into `AsyncPeer`; the current peer protocol remains the legacy unauthenticated DH/AES path until the versioned session protocol and module split are complete.

Treat changes to the wire protocol and cryptographic transformations as compatibility and security changes. Update the relevant documentation and tests in the same change.

## License

See [LICENSE](LICENSE).
