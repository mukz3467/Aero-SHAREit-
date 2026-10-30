# Aero TrustLink security and transport status

This document describes the implementation currently present on
`feature/offline-mesh-foundation`. It is an engineering status note, not a
security certification or a claim that all advertised transfer modes work.

## Implemented in the active LAN file-transfer path

- Nearby-device discovery uses UDP broadcast on the local IP network.
- File transfer uses a TCP connection on the local network.
- The active client/server transfer path creates ephemeral ECDH P-256 key pairs,
  derives an AES-256 key using HKDF-SHA-256, and encrypts/decrypts file chunks
  with AES-GCM. The file index and chunk offset are included as AEAD associated data.
- The receiver checks file SHA-256 before committing the received file and displays
  an incoming-transfer approval request when SafeDrop approval is enabled.
- Packet sizes, file counts, batch size, socket timeouts, and concurrent incoming
  connections have bounds in the current transfer implementation.
- `EndToEndCrypto` and `PairingCodeManager` contain additional standalone
  cryptographic/invitation helpers and unit tests. They are not yet integrated into
  the active transfer handshake.

## Pairing and identity limitations

- The active discovery PIN is generated locally and broadcast in the UDP discovery
  packet. It is therefore visible to devices on the same local network.
- The receiver QR now encodes the receiver's local IPv4 address, TCP port,
  six-digit discovery code, and display name. Scanning it lets the sender select
  the receiver directly without waiting for the UDP peer list to populate.
- The QR payload is a connection locator, not an authenticated invitation or a
  cryptographic identity credential. The active TCP server does not validate the
  six-digit code as a one-time invitation, and the `PairingCodeManager`
  expiry/attempt limits are not enforced by the active path.
- The QR flow still requires both phones to share a reachable local Wi-Fi network
  or hotspot. It does not create a Wi-Fi Direct group and does not require internet.
- The active ECDH exchange does not authenticate either public key. Encryption
  protects traffic against passive observers after key agreement, but an active
  man-in-the-middle can substitute keys unless a separate authenticated check is
  implemented.
- The standalone short-authentication-string helper is not shown to users in the
  active transfer flow, and there is no verified mutual SAS confirmation yet.
- Device names and discovery packets are self-asserted and can be spoofed on the LAN.
- If SafeDrop approval is disabled, incoming requests are automatically approved.
  Keep approval enabled when accepting transfers from untrusted local networks.

## Offline and mesh limitations

- Current discovery and transfer require both devices to share a reachable local
  IP network (for example, the same Wi-Fi network or a configured hotspot). The
  receiver QR supplies its local IP and TCP port to simplify peer selection.
- The discovery code does not create a Wi-Fi Direct group. The current discovery
  class explicitly performs LAN broadcast discovery and does not negotiate Wi-Fi
  Direct.
- `OfflineMeshRouter` currently provides route-learning/relay-envelope logic and
  unit tests, but it is not proof of a working multi-hop radio mesh transport.
- No fixed 100 m or 800 m range, cross-network reach, or internet-free operation
  outside a shared local network is guaranteed by the current implementation.

## Required before a security or mesh-ready release

1. Bind protocol version, both peer identities/roles, ephemeral public keys, and
   invitation material into a canonical authenticated transcript.
2. Require both endpoints to verify the same short authentication string or use
   a properly authenticated QR exchange before accepting the session key.
3. Validate pairing invitations on the receiver side, enforce expiry and attempt
   limits at the transport boundary, and rate-limit repeated connection attempts.
4. Prevent discovery spoofing from being treated as trusted identity; authenticate
   the peer before showing a trusted-device state.
5. Connect relay routing to an actual supported radio/network transport, or describe
   the feature as LAN-only routing logic rather than a functioning mesh.
6. Test sender-to-receiver transfers, rejection, cancellation, resume, large files,
   storage exhaustion, and tampered traffic on multiple physical Android devices.
7. Review key lifecycle, nonce handling, logs, temporary files, backup behavior,
   and release configuration; obtain an independent security review before making
   strong security claims.

## Verification status

Unit tests cover selected helper and routing behavior. GitHub Actions must pass the
debug APK build and unit-test tasks for the exact commit before build success is
claimed. CI does not replace physical-device testing or an independent security
review.
