# Security Notes

## Security status

This is an educational protocol demonstration, not a secure messaging product. Encryption is present, but confidentiality alone does not provide an authenticated or tamper-resistant channel.

The active peer protocol now uses an ML-KEM-768 bootstrap and AES-256-GCM through `SessionManager`. This provides a post-quantum KEM primitive and authenticated encryption for message payloads, but it does not by itself authenticate peer identity or provide a Double Ratchet. The older DH/AES implementation is archived under `com.zerotrust.legacy` and is not wired into active code.

The rationale and status of the migration choices are recorded in [Architecture and Security Decisions](decisions.md), including provider selection, ML-KEM parameter selection, AES-GCM usage, protocol compatibility, identity authentication, and the Double Ratchet security gate.

## Implemented protections

- Bouncy Castle supplies ML-KEM-768 key generation, encapsulation, and decapsulation.
- Peer messages use AES-256-GCM with random 12-byte nonces and 128-bit tags.
- Protocol metadata is supplied as authenticated associated data for message encryption.
- Session sends are rejected until the PQC bootstrap completes.
- Public keys, KEM ciphertexts, and encrypted payloads are Base64-encoded for line transport.

## Important limitations

### No peer authentication

The DH exchange has no certificate, fingerprint, pre-shared key, or authenticated public-key check. An active network attacker can replace both public keys and establish separate sessions with each endpoint. The exchanged peer ID is only a string and is not proof of identity.

### No ciphertext authentication in the live protocol

AES-GCM authenticates an individual payload, but the current protocol has no authenticated peer identity, sequence number, replay cache, or Double Ratchet. An attacker can still replay valid ciphertext, reorder messages, or attempt session-level injection. The archived `AES` transformation is not used by active peer sessions.

### Weak and implicit cryptographic choices

The archived implementation used 1024-bit finite-field DH and an implicit `Cipher.getInstance("AES")` transformation. The active ML-KEM bootstrap derives a session key by hashing ordered KEM secrets with a protocol context, but a reviewed KDF and authenticated transcript binding remain part of the v2 hardening work.

### Ordering and plaintext hazards

`SessionManager` rejects `sendMessageAsync` before the PQC session key is initialized. `performKeyExchangeAsync` is still asynchronous and returns immediately, so application code must wait for completion. The CLI currently prints completion immediately after scheduling the exchange.

### Metadata and operational exposure

Peer IDs, addresses, connection events, truncated message previews, and exceptions are printed to standard output/error. The line protocol has no message-size limit, rate limit, timeout policy, or resource quota.

## Threat model

The current implementation may be acceptable for local demonstrations where both endpoints and the network are trusted. It does not protect against:

- A man-in-the-middle attacker.
- A malicious or compromised peer.
- A passive observer learning traffic metadata.
- Active ciphertext tampering or replay.
- Resource exhaustion through connections or oversized lines.

## Hardening priorities

Before treating this as a real secure channel:

1. Add pinned identity keys and authenticated transcript signatures; ML-KEM alone does not prevent MITM attacks.
2. Replace the current hash-based session derivation with a reviewed KDF and bind all handshake capabilities and identities.
3. Implement a reviewed Double Ratchet with sequence numbers, replay handling, skipped-key limits, and out-of-order delivery.
4. Add structured versioned framing, downgrade rejection, message-size limits, timeouts, and resource quotas.
5. Use explicit UTF-8 encoding everywhere and avoid logging message content or sensitive handshake material.
6. Add negative tests for identity failure, tampering, replay, malformed frames, handshake races, connection timeouts, and ratchet failures.

Changes to cryptographic primitives should be reviewed as security-sensitive changes and accompanied by tests that prove both sides derive the same key and reject invalid input.
