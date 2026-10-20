# Aero TrustLink security integration status

This document records the security components currently present on
`feature/offline-mesh-foundation`. It is not a security certification.

## Implemented primitives

- `EndToEndCrypto`: ephemeral ECDH key pairs, HKDF-SHA-256 session-key derivation,
  AES-256-GCM authenticated encryption/decryption, associated-data support, and a
  six-digit short authentication string derived from the session key and transcript.
- `PairingCodeManager`: short-lived, single-use six-digit PIN invitations and
  high-entropy URL-safe QR tokens, with per-invitation attempt limits.
- `OfflineMeshRouter`: platform-independent route-learning and relay-envelope
  logic; retry behavior no longer consumes message deduplication state when no
  route exists.

## Required integration before release

1. Bind a canonical transcript containing protocol version, both device identities,
   roles, and the chosen QR/PIN invitation to the ECDH-derived key context.
2. Require both peers to compare/confirm the short authentication string or
   verify an authenticated QR transcript before trusting the session. ECDH alone
   does not prevent man-in-the-middle attacks.
3. Integrate encryption/decryption into the actual file-chunk transport. Relay
   nodes must forward ciphertext only and must never receive endpoint keys.
4. Make invitation attempt limits effective across the discovery/transport layer;
   a local in-memory counter alone cannot prevent distributed brute-force attempts.
5. Use unique nonces per AES-GCM key. Do not reuse a nonce with the same session
   key; define sequence numbers and authenticate transfer/file/chunk metadata as AAD.
6. Authenticate the receiver's explicit approval, enforce bounded packet lengths,
   timeouts, concurrency limits, and safe failure/recovery behavior.
7. Integrate Wi-Fi Direct or another real offline radio transport and test on
   multiple physical Android devices. Unit tests cannot prove radio reach or mesh range.
8. Review key lifecycle, logging, backups, temporary-file handling, and release build
   configuration; perform independent security review before claiming strong security.

## Current limitations

The pairing-code helper and crypto primitives are not yet wired into the Android UI,
discovery, or the existing file-transfer protocol. QR/PIN codes currently represent
invitation lifecycle only; they do not themselves locate a remote phone. No claim is
made that the app is already fully secure, that all mesh transfers work, or that
100-meter/800-meter radio range is guaranteed.
