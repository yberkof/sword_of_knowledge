import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";
import { createServer } from "http";
import { Server } from "socket.io";
import { getPool, checkAndRotateInactiveLeaders } from "../server/pgData.js";
import { createApiApp } from "./http/createApiApp.js";
import { attachGameServer } from "./game/attachGameServer.js";

const serverDir = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(serverDir, "../../.env") });
dotenv.config();

const PORT = Number(process.env.PORT) || 3000;

const corsOrigins =
  process.env.CORS_ORIGIN?.split(",").map((s) => s.trim()).filter(Boolean) ||
  ["http://localhost:5173", "http://127.0.0.1:5173", "*"];

function main() {
  getPool();
  const app = createApiApp();
  const httpServer = createServer(app);
  const io = new Server(httpServer, {
    cors: {
      origin: corsOrigins.includes("*") ? "*" : corsOrigins,
      methods: ["GET", "POST"],
    },
    destroyUpgrade: true,
  });
  attachGameServer(io, app);
  httpServer.listen(PORT, "0.0.0.0", () => {
    console.log(`Game server http://localhost:${PORT}`);
  });

  // Run inactivity rotation check every 24 hours
  setInterval(() => {
    checkAndRotateInactiveLeaders().catch((err) => {
      console.error("Inactivity rotation failed:", err);
    });
  }, 24 * 60 * 60 * 1000);
}

try {
  main();
} catch (err) {
  console.error("Failed to start server:", err);
  process.exit(1);
}
