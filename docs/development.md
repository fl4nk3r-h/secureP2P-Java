# Development and Testing

## Prerequisites

- JDK 21 or newer.
- Maven 3.6 or newer.
- A local TCP stack. The integration-style peer tests bind ephemeral localhost ports.

The Maven build declares JUnit Jupiter for tests and SLF4J/Logback dependencies, although application code currently uses `System.out` and `System.err` directly.

## Common commands

```bash
# Compile and run tests
mvn test

# Clean, test, and package
mvn clean package

# Install the artifact in the local Maven repository
mvn clean install

# Run one suite
mvn test -Dtest=CryptoUtilsTest
mvn test -Dtest=KeyExchangeTest
mvn test -Dtest=AsyncPeerTest

# Run one test method
mvn test -Dtest=CryptoUtilsTest#testEncryption
```

The packaged artifact is `target/securep2p-1.0-SNAPSHOT.jar`.

## Test scope

`CryptoUtilsTest` checks AES key generation, encryption/decryption, and key derivation. `KeyExchangeTest` checks public-key exchange and shared-secret agreement. `AsyncPeerTest` exercises asynchronous connection setup, readiness, peer-ID exchange, key exchange, callbacks, queue behavior, and cleanup.

Tests create and close real local sockets. A failure can therefore be caused by a busy ephemeral port, an interrupted process, or an environment that blocks localhost networking.

## Change workflow

1. Read the relevant class and nearby tests before changing behavior.
2. Preserve the line-oriented protocol unless the change explicitly updates the protocol.
3. Add or update focused tests for lifecycle, concurrency, crypto, or wire-format changes.
4. Run the narrowest relevant test class, then `mvn test`.
5. Update the relevant file in `docs/` and the root README when public behavior changes.
6. Keep generated `target/` output out of commits.

## Style and design conventions

- Use Java 21 language and API features only when they improve clarity.
- Keep networking and cryptographic responsibilities separated.
- Close sockets, streams, and executors reliably.
- Do not describe a planned feature as implemented.
- Treat protocol and cryptographic changes as compatibility-sensitive.

## Suggested future test areas

The current tests do not fully specify malformed input, timeout behavior, send-before-key-exchange behavior, concurrent sends, message ordering, executor rejection, socket failure during shutdown, or security properties such as authentication and tamper detection. These are useful targets for future work.
