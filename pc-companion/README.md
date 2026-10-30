# Aero SHAREit PC Companion (WebRTC signaling)

This folder adds a browser-based PC companion and a small signaling service.

## Flow

1. Open the companion on a PC and choose **Receive on this computer**.
2. The service creates a random, one-use room and displays a QR link.
3. The other device opens the link after scanning the QR.
4. The two browsers negotiate a WebRTC data channel. File bytes are sent over that data channel; the signaling server forwards only SDP/ICE setup messages and does not receive file contents.

## Run locally

Requires Node.js 20+.

```sh
cd pc-companion
npm install
npm start
```

Open http://localhost:8787. For use from another device, the service must be deployed behind HTTPS/WSS with a publicly reachable hostname.

## Deployment requirements

Deploy this Node service to a WebSocket-capable host (for example Render). Set the service start command to `npm start`. The host must support persistent WebSocket connections. Do not deploy the signaling process as a short-lived serverless function.

For reliable connections across restrictive NATs/firewalls, configure a TURN service and add its credentials to the client ICE server list. The current prototype uses public STUN servers only; some network combinations will fail to establish a direct peer connection. A TURN relay is a fallback and may relay encrypted WebRTC traffic.

## Security and current scope

- Pairing room IDs and keys are random; rooms expire after 10 minutes and admit at most two peers.
- The server accepts only signaling message types and does not expose a file upload/download endpoint.
- Use HTTPS/WSS in production. The browser validates WebRTC's DTLS connection, but this prototype does not yet add a separate verified device identity or file-level SHA-256 receipt.
- The browser companion currently supports browser-to-browser file transfer. It is **not yet wired into the Android native app's file picker, transfer history, or progress UI**. That integration and physical Android/Windows testing are required before claiming the full Aero SHAREit cross-platform flow is complete.
- The QR page currently requires the other device to open the link in a browser. Native app deep-link routing is a follow-up integration task.
