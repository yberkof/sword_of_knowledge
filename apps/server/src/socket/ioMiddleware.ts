import type { Server } from "socket.io";

/** Per-IP soft cap on socket handshakes per minute. */
export function registerSocketConnectionRateLimit(io: Server): void {
  const socketConnBuckets = new Map<string, number[]>();
  io.use((socket, next) => {
    const ip = socket.handshake.address || "unknown";
    const now = Date.now();
    const arr = (socketConnBuckets.get(ip) || []).filter((t) => now - t < 60_000);
    if (arr.length > 40) {
      next(new Error("rate limit"));
      return;
    }
    arr.push(now);
    socketConnBuckets.set(ip, arr);
    next();
  });
}
