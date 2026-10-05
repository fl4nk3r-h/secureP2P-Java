# SecureP2P Java

SecureP2P is a Java 21 project for asynchronous peer connections and a migrating cryptographic session design.

This repository is **not production-ready secure messaging software**. The live `AsyncPeer` protocol now uses an ML-KEM-768 bootstrap and AES-256-GCM, but it still lacks authenticated peer identities, a Double Ratchet, replay protection, and hardened protocol framing. See [Security Notes](docs/security.md) before using it beyond local experiments.

## Quick Start

Requirements: JDK 21 and Maven 3.6 or newer.

```bash
mvn clean test
mvn package
```

Start the interactive demo in two terminals:

```bash
# Terminal 1: listener
java -cp target/securep2p-2.0.0-SNAPSHOT.jar com.zerotrust.Main interactive Alice 12346 listen

# Terminal 2: connector
java -cp target/securep2p-2.0.0-SNAPSHOT.jar com.zerotrust.Main interactive Bob 12347 connect localhost 12346
```

Use `/help`, `/status`, `/clear`, or `/quit` in the chat. The application exposes peer mode only; the legacy `Client` and `Server` classes remain internal library examples and are no longer selectable from `Main`.

## What Is Implemented

- `Main`: peer-only command-line entry point with `peer` and `interactive` modes.
- `AsyncPeer`: asynchronous accept/connect operations, peer-ID exchange, ML-KEM-768 session bootstrap, AES-GCM sends, a receive queue, and callbacks.
- `PeerConnection`, `SessionManager`, and `MessageListener`: separated transport, session, and inbound-delivery responsibilities.
- `AeadCrypto`: explicit AES-256-GCM encryption with random nonces and authenticated associated data.
- `MlKemKeyExchange`: Bouncy Castle-backed ML-KEM-768 encapsulation and decapsulation.
- `com.zerotrust.legacy`: archived DH/AES and plaintext echo classes retained for historical reference only.

## Documentation

- [High-level architecture](docs/high-level-architecture.md): components, responsibilities, and end-to-end flows.
- [Module-level design](docs/module-level-design.md): package boundaries, class diagrams, dependencies, state model, and message paths.
- [Architecture and security decisions](docs/decisions.md): decisions taken, rationale, alternatives, consequences, and pending gates.
- [Low-level design and API](docs/low-level-design.md): classes, state transitions, wire format, threading, and usage contracts.
- [Security notes](docs/security.md): implemented protections, known weaknesses, threat boundaries, and hardening priorities.
- [Development and testing](docs/development.md): project layout, Maven commands, test scope, and contribution guidance.
- [Operations and troubleshooting](docs/operations.md): runtime behavior, ports, failure modes, and cleanup.

## Repository Layout

```text
pom.xml                         Maven build and dependency configuration
src/main/java/com/zerotrust      Application, networking, and crypto code
src/test/java/com/zerotrust      JUnit tests for crypto and AsyncPeer behavior
src/main/java/com/zerotrust/legacy Archived pre-v2 classes, not wired into active code
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
mvn test -Dtest=PqcCryptoTest
mvn test -Dtest=AsyncPeerTest
```

The tests cover ML-KEM agreement, AES-GCM authentication, peer connection setup, peer-ID exchange, PQC session bootstrap, callbacks, message delivery, cleanup, and archived legacy primitives. The test count is determined by the current source, so documentation intentionally does not hard-code an expected number.

## Scope and Status

The project currently has no configuration file, persistence layer, authentication authority, message model, file transfer protocol, or TLS integration. SLF4J and Logback are declared in Maven but the application currently writes directly to standard output and standard error.

The PQC migration is in progress. ML-KEM-768 and AES-GCM are now wired into `SessionManager`, while identity authentication, protocol version negotiation, replay handling, and the Double Ratchet remain pending. Legacy DH/AES and echo code is archived and not wired into the active peer application.

## Why It Is Not Production Ready

The project is a useful protocol and concurrency demonstration, but it does not yet meet the security or operational bar for a production messaging system:

- **Peer identity is not authenticated.** The current DH exchange has no certificate, pinned identity key, signature, or verified fingerprint, so an active attacker can perform a man-in-the-middle attack.
- **The live cipher is not an authenticated encryption protocol.** `AsyncPeer` still uses the legacy `CryptoUtils` transformation, whose mode and padding are implicit and which provides no authentication tag, replay protection, or message ordering guarantees.
- **PQC is not active in peer sessions.** `MlKemKeyExchange` proves ML-KEM-768 encapsulation and decapsulation in isolation, but the live handshake still uses `KeyExchange` and legacy DH.
- **AES-GCM is not active in peer sessions.** `AeadCrypto` provides the planned AES-256-GCM primitive, but the live protocol does not yet supply ratchet-managed keys, authenticated headers, or enforced nonce/key lifecycle rules.
- **The Double Ratchet is not implemented.** There is no production-grade forward-secrecy, post-compromise recovery, skipped-message-key handling, or out-of-order message state machine.
- **The wire protocol is still a legacy line protocol.** It has no authenticated version negotiation, downgrade rejection, strict frame-size limits, structured message types, or robust malformed-frame handling.
- **Operational controls are incomplete.** There is no identity provisioning and rotation workflow, revocation/recovery process, rate limiting, resource quotas, health monitoring, security audit logging, or deployment hardening.
- **Validation is not a security certification.** The test suite covers cooperative behavior and primitive properties, but authentication failures, replay, downgrade, malformed frames, ratchet state, and hostile-network behavior remain incomplete.

Production readiness requires the versioned authenticated session, ML-KEM integration, reviewed ratchet construction, identity lifecycle, negative security tests, dependency review, bounded transport behavior, and operational controls described in [Architecture and Security Decisions](docs/decisions.md).

Treat changes to the wire protocol and cryptographic transformations as compatibility and security changes. Update the relevant documentation and tests in the same change.

## License

See [LICENSE](LICENSE).
