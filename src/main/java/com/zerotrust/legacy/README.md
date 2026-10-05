# Archived Legacy Code

This package contains the pre-v2 plaintext echo classes and unauthenticated DH/AES helpers retained for historical reference and migration comparison.

Nothing under `com.zerotrust.legacy` is imported by the active peer application. Do not use these classes for new code or production security decisions. Active code uses `PeerConnection`, `SessionManager`, `MessageListener`, `MlKemKeyExchange`, and `AeadCrypto`.
