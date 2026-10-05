# Architecture and Security Decisions

This document records decisions made during the security-hardening migration. It is intentionally separate from the design documents: the design describes the target system, while this record explains why choices were made and whether they are implemented, provisional, or still pending.

## Major version decision

**Status:** Accepted

The project version is now `2.0.0-SNAPSHOT`. This is a major-version development line because the migration removes the `server` and `client` CLI modes, splits `AsyncPeer` into focused modules, changes session ownership and send-before-establishment behavior, and prepares a versioned secure protocol. The `SNAPSHOT` suffix remains until the authenticated ML-KEM/session migration is complete and release acceptance criteria pass.

## Decision status

- **Accepted:** selected for the project and reflected in the current implementation or migration direction.
- **In progress:** implementation has started, but the live peer protocol does not use it yet.
- **Pending:** requires a design or security review before implementation.
- **Rejected:** considered and intentionally not selected.

## ADR-001: Use a vetted provider instead of implementing PQC primitives

**Status:** Accepted

**Decision:** Use Bouncy Castle `bcprov-jdk18on` version `1.80`, pinned in `pom.xml`.

**Why:** Java 21 does not provide the complete ML-KEM API needed by this project as a portable application dependency. The provider exposes ML-KEM-768 and supporting cryptographic primitives without requiring the project to implement lattice cryptography itself. Provider-backed primitives are easier to review, test, and update than handwritten cryptography.

**Alternatives considered:**

- Implement ML-KEM in this repository: rejected because cryptographic primitive implementation is outside the project's safe maintenance boundary.
- Use the first available provider version: rejected because the required ML-KEM API was not present in the initially checked `1.78.1` artifact.
- Depend on platform-specific JDK APIs: rejected because the project targets Java 21 portability.

**Consequence:** Provider upgrades require Java 21 compatibility testing, algorithm/API review, licensing review, and dependency/CVE scanning.

## ADR-002: Select ML-KEM-768 for the initial PQC KEM

**Status:** Implemented foundation; protocol hardening pending

**Decision:** Use ML-KEM-768 for the initial encapsulation/decapsulation foundation.

**Why:** ML-KEM-768 is the middle standardized parameter set and provides a practical balance between security level, key/ciphertext size, and runtime cost for a small peer application. It avoids treating the largest parameter set as automatically better when transport and memory costs matter.

**Current evidence:** `MlKemKeyExchange` generates ML-KEM-768 key pairs and `PqcCryptoTest` verifies matching encapsulated and decapsulated secrets.

**Consequence:** The parameter set must be included in authenticated protocol capabilities. Changing it later requires protocol versioning or a carefully authenticated negotiation.

## ADR-003: Use explicit AES-256-GCM instead of provider-default AES

**Status:** Implemented foundation; ratchet hardening pending

**Decision:** Use `AES/GCM/NoPadding` with 256-bit keys, 12-byte random nonces, 128-bit authentication tags, and caller-supplied associated data.

**Why:** The previous `Cipher.getInstance("AES")` transformation left mode and padding implicit and provided no authentication tag. AES-GCM gives confidentiality and ciphertext/header integrity when nonce uniqueness and key lifecycle are correctly enforced. Associated data allows protocol metadata to be authenticated without encrypting it.

**Current evidence:** `AeadCrypto` implements nonce-prefixing and AAD, and tests verify round trips, nonce variation, modified-ciphertext rejection, and modified-AAD rejection.

**Consequence:** The current session layer uses the primitive and authenticates a fixed message context, but it does not yet provide replay protection or ratchet-managed key rotation. Those controls remain required before production use.

## ADR-004: Break wire compatibility with a versioned secure protocol

**Status:** Accepted

**Decision:** The hardened protocol will be a new version and will reject legacy DH/AES peers. It will not silently downgrade to the current protocol.

**Why:** Supporting both protocols without a strict authenticated negotiation creates downgrade risk and makes it easy to mistake legacy encryption for the hardened session. The current wire format has no version, authentication, frame limit, replay protection, or algorithm negotiation.

**Alternatives considered:**

- Keep the current wire format and replace the cipher in place: rejected because the existing framing cannot safely carry the new handshake and ratchet metadata.
- Support legacy and secure peers indefinitely: rejected unless a future compatibility mode has explicit, authenticated downgrade policy and separate user-visible status.

**Consequence:** Existing peers will need the new implementation. Documentation and startup output must state the protocol version and reject unsupported peers clearly.

## ADR-005: Keep `AsyncPeer` as a façade while splitting ownership

**Status:** Implemented

**Decision:** Extract three focused modules: `PeerConnection`, `MessageListener`, and `SessionManager`. Retain `AsyncPeer` as a thin façade during migration.

**Why:** The current `AsyncPeer` owns sockets, handshake, crypto keys, executor tasks, listener threads, queues, callbacks, and shutdown. Separating those responsibilities creates a security boundary around session state and makes connection, message delivery, and cryptographic tests independently possible. Keeping the façade limits immediate application churn.

**Ownership rules:**

- `PeerConnection` owns sockets, streams, framing, readiness, timeouts, and transport shutdown.
- `MessageListener` owns inbound reading, bounded delivery, callbacks, and listener shutdown.
- `SessionManager` owns identity verification, handshake state, ratchet keys, message encryption/decryption, replay policy, and session shutdown state.
- `AsyncPeer` coordinates these modules and exposes only the supported application-facing API.

**Consequence:** The façade must stop exposing legacy raw-ciphertext semantics as the primary secure message API and must reject sends until the session is established.

## ADR-006: Authenticate identity separately from ML-KEM

**Status:** Pending security review

**Decision direction:** Use pinned long-term identity keys/fingerprints to authenticate the handshake. Prefer a post-quantum identity signature such as ML-DSA when the selected provider and operational model support it.

**Why:** ML-KEM establishes a shared secret but does not prove who owns a public key. Without identity authentication, the current and proposed KEM handshakes remain vulnerable to man-in-the-middle attacks.

**Still to decide:** identity key provisioning, fingerprint display/verification, rotation, revocation, recovery, and whether a PQ identity signature or a hybrid identity scheme is operationally appropriate.

**Rejected direction:** Calling peer IDs or exchanged ML-KEM public keys authentication. They are untrusted metadata unless bound to a verified identity key.

## ADR-007: Treat the Double Ratchet construction as a security gate

**Status:** Pending security review

**Decision direction:** Implement a full asynchronous ratchet only after selecting a vetted construction or completing a formal cryptographic review. The session must support root/chain keys, message counters, authenticated headers, skipped-message-key limits, replay rejection, and out-of-order delivery.

**Why:** ML-KEM is a KEM, not a drop-in Diffie-Hellman ratchet primitive. A naive custom “PQC Double Ratchet” could provide a false sense of forward secrecy or post-compromise recovery. The ratchet must be designed as a protocol, not added as a counter around AES-GCM.

**Alternatives considered:**

- Use ML-KEM as if it were a DH ratchet: rejected because the primitive has different interaction and key-update semantics.
- Implement an ad hoc ratchet immediately: rejected until test vectors, state-machine tests, and cryptographic review exist.
- Use a vetted hybrid with ML-KEM bootstrap and a vetted classical DH ratchet: acceptable fallback if a reviewed KEM-native ratchet is unavailable, with any remaining quantum-security tradeoffs documented.

## ADR-008: Fail closed before session establishment

**Status:** Implemented for the current PQC session; authenticated v2 enforcement remains pending

**Decision:** Sending application messages before authenticated session establishment will fail instead of sending plaintext or unauthenticated ciphertext.

**Why:** Asynchronous ordering previously allowed sends before a session key existed. Session state must be explicit and enforced by the session owner.

**Required behavior:** reject sends in `NEW`, `CONNECTED`, `AUTHENTICATING`, `FAILED`, and `CLOSED`; allow sends only in `ESTABLISHED`; surface the failure through the send callback and error callback. The extracted `SessionManager` now rejects sends before the PQC bootstrap completes; authenticated v2 state enforcement remains pending.

## ADR-009: Test security properties, not only successful exchanges

**Status:** Accepted

**Decision:** Every protocol stage will have positive and negative tests, including tampering, wrong identity, malformed frames, replay, downgrade, out-of-order delivery, size limits, timeouts, concurrent close, and send-before-establishment.

**Why:** A successful two-peer exchange proves only that two cooperative endpoints can communicate. It does not prove authentication, integrity, replay resistance, downgrade resistance, or safe failure behavior.

**Current evidence:** The initial `PqcCryptoTest` covers AES-GCM authentication and ML-KEM agreement. Live-session negative tests remain pending until the new session and transport modules exist.

## Current implementation boundary

Implemented now:

- Bouncy Castle provider pinning.
- ML-KEM-768 key generation, encapsulation, and decapsulation foundation.
- AES-256-GCM with nonce and AAD handling.
- Focused primitive tests.

Still pending:

- Authenticated identity provisioning and pin verification.
- Versioned v2 handshake and downgrade rejection.
- Double Ratchet state machine and skipped-message handling.
- Authenticated identity, replay protection, and ratchet wiring into `SessionManager`.

Implemented in this migration batch:

- `PeerConnection`, `MessageListener`, and `SessionManager` extraction.
- Peer-only `Main` CLI; legacy `server` and `client` options were removed.

Until those items are complete, the live peer protocol should be described precisely as an ML-KEM bootstrap plus AES-GCM session without identity binding, replay policy, or ratchet guarantees.
