import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { randomUUID } from "node:crypto";
import {
  countActiveQuestions,
  shopPurchase,
  getUserById,
  createUser,
  touchUserLogin,
  patchUserProfile,
  rowToClientProfile,
} from "../../server/pgData.js";
import { requireFirebaseUser } from "./firebaseAuth.js";

export type { Express } from "express";

/** Registers `/api/*` routes on a fresh Express app (JSON body, rate limit). */
export function createApiApp(): express.Express {
  const app = express();

  const corsOrigins =
    process.env.CORS_ORIGIN?.split(",").map((s) => s.trim()).filter(Boolean) ||
    ["http://localhost:5173", "http://127.0.0.1:5173"];
  app.use(
    cors({
      origin: corsOrigins,
      credentials: true,
    })
  );

  const apiLimiter = rateLimit({
    windowMs: 60_000,
    max: 120,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use("/api/", apiLimiter);

  app.get("/api/health", (req, res) => {
    res.json({ status: "ok" });
  });

  app.use(express.json());

  app.get("/api/profile", requireFirebaseUser, async (req, res) => {
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    try {
      const row = await getUserById(uid);
      if (!row) {
        res.status(404).json({ error: "Not found" });
        return;
      }
      await touchUserLogin(uid, randomUUID());
      res.json(rowToClientProfile(row));
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/profile", requireFirebaseUser, async (req, res) => {
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    try {
      const existing = await getUserById(uid);
      if (existing) {
        res.status(200).json(rowToClientProfile(existing));
        return;
      }
      const body = (req.body || {}) as Record<string, unknown>;
      const display_name = typeof body.name === "string" ? body.name : "Warrior";
      const username = typeof body.username === "string" ? body.username : display_name;
      const avatar_url = typeof body.avatar === "string" ? body.avatar : "";
      const row = await createUser(uid, {
        display_name,
        username,
        avatar_url,
      });
      await touchUserLogin(uid, randomUUID());
      res.status(201).json(rowToClientProfile(row));
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.patch("/api/profile", requireFirebaseUser, async (req, res) => {
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    try {
      const b = (req.body || {}) as Record<string, unknown>;
      const row = await patchUserProfile(uid, {
        display_name: typeof b.name === "string" ? b.name : undefined,
        username: typeof b.username === "string" ? b.username : undefined,
        avatar_url: typeof b.avatar === "string" ? b.avatar : undefined,
        country_flag: typeof b.countryFlag === "string" ? b.countryFlag : undefined,
      });
      if (!row) {
        res.status(404).json({ error: "Not found" });
        return;
      }
      res.json(rowToClientProfile(row));
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/iap/validate", (req, res) => {
    res.json({
      ok: true,
      note: "Validate receipts server-side with store APIs before granting currency.",
    });
  });

  app.get("/api/cms/questions-stats", async (_req, res) => {
    try {
      const count = await countActiveQuestions();
      res.json({ count });
    } catch {
      res.json({ count: -1 });
    }
  });

  app.post("/api/cms/report", (req, res) => {
    const { reporterUid, targetUid, reason } = (req.body || {}) as {
      reporterUid?: string;
      targetUid?: string;
      reason?: string;
    };
    if (!reporterUid || !reason) {
      res.status(400).json({ ok: false });
      return;
    }
    console.log("[report]", { reporterUid, targetUid, reason: String(reason).slice(0, 500) });
    res.json({ ok: true, queued: true });
  });

  app.get("/api/clans", (_req, res) => {
    res.json({ clans: [] });
  });

  app.post("/api/shop/purchase", requireFirebaseUser, async (req, res) => {
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    const { itemId, costGold } = (req.body || {}) as {
      itemId?: string;
      costGold?: number;
    };
    if (!itemId) {
      res.status(400).json({ ok: false });
      return;
    }
    const cost = Number(costGold) || 0;
    try {
      await shopPurchase(uid, String(itemId), cost);
      res.json({ ok: true, itemId });
    } catch {
      res.status(500).json({ ok: false });
    }
  });

  return app;
}
