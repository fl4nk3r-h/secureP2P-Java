# Security Notes

## Security status

The active peer protocol uses an ML-KEM-768 bootstrap and AES-256-GCM through `SessionManager`. This provides a post-quantum KEM primitive and authenticated encryption for message payloads. The older DH/AES implementation is archived under `com.zerotrust.legacy` and is not wired into active code.

The rationale and status of the migration choices are recorded in [Architecture and Security Decisions](decisions.md), including provider selection, ML-KEM parameter selection, AES-GCM usage, protocol compatibility, identity authentication, and the Double Ratchet security gate.

## Implemented protections

- Bouncy Castle supplies ML-KEM-768 key generation, encapsulation, and decapsulation.
- Peer messages use AES-256-GCM with random 12-byte nonces and 128-bit tags.
- Protocol metadata is supplied as authenticated associated data for message encryption.
- Session sends are rejected until the PQC bootstrap completes.
- Public keys, KEM ciphertexts, and encrypted payloads are Base64-encoded for line transport.

## Current protocol boundaries

### Peer identity binding

Peer IDs are exchanged as protocol metadata. The roadmap adds pinned identity keys or signatures so a peer can bind a displayed identity to the key material used in the ML-KEM handshake.

### Session-level replay handling

AES-GCM authenticates each encrypted payload. The roadmap adds session counters, replay caches, authenticated headers, and ratchet state so the session layer can enforce ordering and replay policy across messages.

### Cryptographic migration notes

The archived implementation used finite-field DH and a provider-default AES transformation. The active ML-KEM bootstrap derives session material from ordered KEM secrets with a protocol context. A reviewed KDF and authenticated transcript binding are part of the next session-design milestone.

### Handshake sequencing

`SessionManager` rejects `sendMessageAsync` before the PQC session key is initialized. `performKeyExchangeAsync` is still asynchronous and returns immediately, so application code must wait for completion. The CLI currently prints completion immediately after scheduling the exchange.

### Runtime metadata

Peer IDs, addresses, connection events, truncated message previews, and exceptions are printed to standard output/error. Message-size limits, rate limits, timeout policy, and resource quotas are tracked as transport-hardening work.

## Operating model

The current implementation is designed for local and controlled-network peer sessions while the authenticated v2 protocol work continues. Priority scenarios for the hardening roadmap include:

- Peer identity verification.
- Malicious or compromised peer behavior.
- Traffic metadata exposure.
- Ciphertext replay and ordering policy.
- Resource exhaustion through connections or oversized lines.

## Hardening priorities

1. Add pinned identity keys and authenticated transcript signatures; ML-KEM alone does not prevent MITM attacks.
2. Replace the current hash-based session derivation with a reviewed KDF and bind all handshake capabilities and identities.
3. Implement a reviewed Double Ratchet with sequence numbers, replay handling, skipped-key limits, and out-of-order delivery.
4. Add structured versioned framing, downgrade rejection, message-size limits, timeouts, and resource quotas.
5. Use explicit UTF-8 encoding everywhere and avoid logging message content or sensitive handshake material.
6. Add negative tests for identity failure, tampering, replay, malformed frames, handshake races, connection timeouts, and ratchet failures.

Changes to cryptographic primitives should be reviewed as security-sensitive changes and accompanied by tests that prove both sides derive the same key and reject invalid input.
