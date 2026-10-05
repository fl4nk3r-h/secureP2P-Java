# Security Notes

## Security status

This is an educational protocol demonstration, not a secure messaging product. Encryption is present, but confidentiality alone does not provide an authenticated or tamper-resistant channel.

## Implemented protections

- Java cryptographic providers supply DH, AES, SHA-256, and `SecureRandom` primitives.
- Each `KeyExchange` instance creates a fresh DH key pair.
- Peer messages are normally encrypted after the asynchronous handshake completes.
- Public keys and ciphertext are Base64-encoded for transport over line-oriented streams.

## Important limitations

### No peer authentication

The DH exchange has no certificate, fingerprint, pre-shared key, or authenticated public-key check. An active network attacker can replace both public keys and establish separate sessions with each endpoint. The exchanged peer ID is only a string and is not proof of identity.

### No ciphertext authentication

The default `AES` transformation does not provide an authentication tag. An attacker can modify, replay, reorder, or inject lines; decryption errors may be reported, but there is no protocol-level authenticity or replay protection.

### Weak and implicit cryptographic choices

The implementation uses 1024-bit finite-field DH and leaves the AES mode and padding implicit in `Cipher.getInstance("AES")`. The key derivation is a direct SHA-256 hash of a textual shared-secret representation, without domain separation or a standard KDF such as HKDF.

### Ordering and plaintext hazards

`sendMessageAsync` sends plaintext if called before `encryptionKey` is initialized. `performKeyExchangeAsync` is asynchronous and returns immediately, so application code must wait for completion. The CLI currently prints completion immediately after scheduling the exchange.

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

1. Prefer TLS 1.3 with certificate or public-key pinning, or define an authenticated protocol using a vetted library.
2. If retaining application-level encryption, use an AEAD mode such as AES-GCM or ChaCha20-Poly1305 with a unique nonce per message and authenticated framing.
3. Replace 1024-bit finite-field DH with a modern authenticated key agreement and an explicit key schedule.
4. Add peer authentication, transcript binding, protocol versioning, sequence numbers, replay handling, and message-size limits.
5. Enforce a state machine that rejects sends until the authenticated session is ready.
6. Use explicit UTF-8 encoding everywhere and avoid logging message content or sensitive handshake material.
7. Add negative tests for tampering, replay, malformed frames, handshake races, connection timeouts, and authentication failures.

Changes to cryptographic primitives should be reviewed as security-sensitive changes and accompanied by tests that prove both sides derive the same key and reject invalid input.
