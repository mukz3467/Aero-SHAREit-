import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";

const root = path.dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 8787);
const rooms = new Map();
const MAX_ROOMS = 500;
const ROOM_TTL_MS = 10 * 60 * 1000;

const server = http.createServer(async (req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, service: "aero-shareit-signaling" }));
  }
  if (req.method === "POST" && req.url === "/api/rooms") {
    if (rooms.size >= MAX_ROOMS) {
      res.writeHead(503); return res.end("Pairing service is busy. Try again shortly.");
    }
    const id = randomBytes(9).toString("base64url");
    const key = randomBytes(24).toString("base64url");
    rooms.set(id, { key, peers: new Set(), expiresAt: Date.now() + ROOM_TTL_MS });
    res.writeHead(201, { "content-type": "application/json", "cache-control": "no-store" });
    return res.end(JSON.stringify({ room: id, key, expiresAt: Date.now() + ROOM_TTL_MS }));
  }
  if (req.method === "GET" && (req.url === "/" || req.url.startsWith("/?"))) {
    try {
      const html = await readFile(path.join(root, "public", "index.html"));
      res.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      return res.end(html);
    } catch {
      res.writeHead(500); return res.end("Companion UI missing");
    }
  }
  res.writeHead(404, { "content-type": "text/plain" });
  res.end("Not found");
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 1024 * 1024 });
server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url || "/", "http://localhost");
  if (url.pathname !== "/signal") return socket.destroy();
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, url));
});

wss.on("connection", (ws, url) => {
  const roomId = url.searchParams.get("room") || "";
  const key = url.searchParams.get("key") || "";
  const room = rooms.get(roomId);
  if (!room || room.key !== key || room.expiresAt < Date.now() || room.peers.size >= 2) {
    ws.close(1008, "Invalid, expired, or full pairing room"); return;
  }
  room.peers.add(ws);
  ws.send(JSON.stringify({ type: "joined", peerCount: room.peers.size }));
  for (const peer of room.peers) if (peer !== ws && peer.readyState === WebSocket.OPEN) {
    peer.send(JSON.stringify({ type: "peer-joined" }));
  }
  ws.on("message", raw => {
    let message;
    try { message = JSON.parse(raw.toString()); } catch { return; }
    if (!["offer", "answer", "ice", "ready", "hangup"].includes(message.type)) return;
    const payload = JSON.stringify(message);
    for (const peer of room.peers) {
      if (peer !== ws && peer.readyState === WebSocket.OPEN) peer.send(payload);
    }
  });
  ws.on("close", () => {
    room.peers.delete(ws);
    for (const peer of room.peers) if (peer.readyState === WebSocket.OPEN) {
      peer.send(JSON.stringify({ type: "peer-left" }));
    }
    if (room.peers.size === 0) rooms.delete(roomId);
  });
});

const cleanup = setInterval(() => {
  const now = Date.now();
  for (const [id, room] of rooms) if (room.expiresAt < now) {
    for (const peer of room.peers) peer.close(1008, "Pairing expired");
    rooms.delete(id);
  }
}, 30_000);
cleanup.unref();

server.listen(port, "0.0.0.0", () => {
  console.log(`Aero SHAREit signaling service listening on :${port}`);
});
