/**
 * Socket.IO soak (socket.io-client v2; server netty-socketio 2.0.13+ also accepts v3/v4 clients).
 * Install: npm ci --prefix scripts/load
 * Usage: TOKEN=jwt BASE_URL=http://127.0.0.1:8081 ROOMS=50 node scripts/load/socket-soak.js
 */
const io = require("socket.io-client");

const base = process.env.BASE_URL || "http://127.0.0.1:8081";
const token = process.env.TOKEN || "";
const rooms = Math.max(1, parseInt(process.env.ROOMS || "20", 10));

function oneClient(i) {
  const q = token ? { token } : { token: "x" };
  const s = io(base, {
    path: "/socket.io",
    transports: ["websocket"],
    forceNew: true,
    query: q,
    reconnection: false,
  });
  s.on("connect", () => {
    s.emit("join_matchmaking", {
      uid: "soak_" + i,
      name: "Soak" + i,
      privateCode: "",
    });
  });
  s.on("disconnect", () => {});
  s.on("connect_error", () => process.exitCode = 2);
}

for (let i = 0; i < rooms; i++) {
  oneClient(i);
}
setInterval(() => {}, 60000);
