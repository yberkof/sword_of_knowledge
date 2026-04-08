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
  createClan,
  getClanDetails,
  getClanMembers,
  getMember,
  updateMemberRole,
  removeMember,
  updateLastKickAt,
  updateLastBroadcastAt,
  listClans,
  createClanApplication,
  getClanApplications,
  deleteClanApplication,
  acceptClanApplication,
  transferLeadership,
} from "../../server/pgData.js";
import { requireFirebaseUser } from "./firebaseAuth.js";
import {
  CLAN_CREATE_MIN_LEVEL,
  CLAN_CREATE_GOLD_COST,
  CLAN_ELDER_KICK_COOLDOWN_MS,
  CLAN_BROADCAST_COOLDOWN_MS,
  ClanRole,
} from "@sok/shared";

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

  app.post("/api/clans/:id/transfer-leadership", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    const { newLeaderUid } = (req.body || {}) as { newLeaderUid?: string };

    if (!newLeaderUid) {
      res.status(400).json({ error: "newLeaderUid required" });
      return;
    }

    try {
      const actor = await getMember(clanId, actingUid);
      if (!actor || actor.role !== ClanRole.LEADER) {
        res.status(403).json({ error: "Only leader can transfer leadership" });
        return;
      }

      const target = await getMember(clanId, newLeaderUid);
      if (!target || target.role !== ClanRole.CO_LEADER) {
        res.status(400).json({ error: "New leader must be a co-leader" });
        return;
      }

      await transferLeadership(clanId, actingUid, newLeaderUid);
      res.json({ ok: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/apply", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const user = await getUserById(uid);
      if (!user) {
        res.status(404).json({ error: "User not found" });
        return;
      }
      if (user.clan_id) {
        res.status(400).json({ error: "Already in a clan" });
        return;
      }

      await createClanApplication(clanId, uid);
      res.json({ ok: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.get("/api/clans/:id/applications", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const actor = await getMember(clanId, actingUid);
      if (!actor || (actor.role !== ClanRole.LEADER && actor.role !== ClanRole.CO_LEADER && actor.role !== ClanRole.ELDER)) {
        res.status(403).json({ error: "Permission denied" });
        return;
      }

      const applications = await getClanApplications(clanId);
      res.json({ applications });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/applications/:uid/accept", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const targetUid = req.params.uid;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const actor = await getMember(clanId, actingUid);
      if (!actor || (actor.role !== ClanRole.LEADER && actor.role !== ClanRole.CO_LEADER && actor.role !== ClanRole.ELDER)) {
        res.status(403).json({ error: "Permission denied" });
        return;
      }

      await acceptClanApplication(clanId, targetUid);
      res.json({ ok: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/applications/:uid/reject", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const targetUid = req.params.uid;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const actor = await getMember(clanId, actingUid);
      if (!actor || (actor.role !== ClanRole.LEADER && actor.role !== ClanRole.CO_LEADER && actor.role !== ClanRole.ELDER)) {
        res.status(403).json({ error: "Permission denied" });
        return;
      }

      await deleteClanApplication(clanId, targetUid);
      res.json({ ok: true });
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

  app.get("/api/clans", async (_req, res) => {
    try {
      const clans = await listClans();
      res.json({ clans });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans", requireFirebaseUser, async (req, res) => {
    const uid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    const { name, description } = (req.body || {}) as { name?: string; description?: string };
    if (!name) {
      res.status(400).json({ error: "Clan name is required" });
      return;
    }
    try {
      const user = await getUserById(uid);
      if (!user) {
        res.status(404).json({ error: "User not found" });
        return;
      }
      if (user.clan_id) {
        res.status(400).json({ error: "Already in a clan" });
        return;
      }
      if (user.level < CLAN_CREATE_MIN_LEVEL) {
        res.status(400).json({ error: `Requires level ${CLAN_CREATE_MIN_LEVEL}` });
        return;
      }
      if (user.gold < CLAN_CREATE_GOLD_COST) {
        res.status(400).json({ error: `Requires ${CLAN_CREATE_GOLD_COST} gold` });
        return;
      }
      const clanId = await createClan(uid, name, description || "");
      res.status(201).json({ clanId });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.get("/api/clans/:id", async (req, res) => {
    const clanId = req.params.id;
    try {
      const clan = await getClanDetails(clanId);
      if (!clan) {
        res.status(404).json({ error: "Clan not found" });
        return;
      }
      const members = await getClanMembers(clanId);
      res.json({ ...clan, members });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/members/:uid/promote", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const targetUid = req.params.uid;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const actor = await getMember(clanId, actingUid);
      const target = await getMember(clanId, targetUid);
      if (!actor || !target) {
        res.status(404).json({ error: "Not found" });
        return;
      }

      // Leader can promote anyone to anything below Leader
      // Co-Leader can promote Member to Elder
      let newRole: ClanRole | null = null;
      if (actor.role === ClanRole.LEADER) {
        if (target.role === ClanRole.MEMBER) newRole = ClanRole.ELDER;
        else if (target.role === ClanRole.ELDER) newRole = ClanRole.CO_LEADER;
      } else if (actor.role === ClanRole.CO_LEADER) {
        if (target.role === ClanRole.MEMBER) newRole = ClanRole.ELDER;
      }

      if (!newRole) {
        res.status(403).json({ error: "Permission denied or invalid promotion" });
        return;
      }

      await updateMemberRole(clanId, targetUid, newRole);
      res.json({ ok: true, newRole });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/members/:uid/demote", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const targetUid = req.params.uid;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      const actor = await getMember(clanId, actingUid);
      const target = await getMember(clanId, targetUid);
      if (!actor || !target) {
        res.status(404).json({ error: "Not found" });
        return;
      }

      let newRole: ClanRole | null = null;
      if (actor.role === ClanRole.LEADER) {
        if (target.role === ClanRole.CO_LEADER) newRole = ClanRole.ELDER;
        else if (target.role === ClanRole.ELDER) newRole = ClanRole.MEMBER;
      }

      if (!newRole) {
        res.status(403).json({ error: "Permission denied or invalid demotion" });
        return;
      }

      await updateMemberRole(clanId, targetUid, newRole);
      res.json({ ok: true, newRole });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.delete("/api/clans/:id/members/:uid", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const targetUid = req.params.uid;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;

    try {
      if (targetUid === actingUid) {
        // Leaving clan
        const target = await getMember(clanId, targetUid);
        if (target?.role === ClanRole.LEADER) {
          res.status(400).json({ error: "Leader cannot leave without transferring leadership" });
          return;
        }
        await removeMember(clanId, targetUid);
        res.json({ ok: true });
        return;
      }

      const actor = await getMember(clanId, actingUid);
      const target = await getMember(clanId, targetUid);
      if (!actor || !target) {
        res.status(404).json({ error: "Not found" });
        return;
      }

      let allowed = false;
      if (actor.role === ClanRole.LEADER) {
        allowed = true; // Can kick anyone
      } else if (actor.role === ClanRole.CO_LEADER) {
        allowed = target.role === ClanRole.ELDER || target.role === ClanRole.MEMBER;
      } else if (actor.role === ClanRole.ELDER) {
        allowed = target.role === ClanRole.MEMBER;
        if (allowed && actor.last_kick_at) {
          const lastKick = new Date(actor.last_kick_at).getTime();
          if (Date.now() - lastKick < CLAN_ELDER_KICK_COOLDOWN_MS) {
            res.status(429).json({ error: "Kick on cooldown" });
            return;
          }
        }
      }

      if (!allowed) {
        res.status(403).json({ error: "Permission denied" });
        return;
      }

      await removeMember(clanId, targetUid);
      if (actor.role === ClanRole.ELDER) {
        await updateLastKickAt(clanId, actingUid);
      }
      res.json({ ok: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
  });

  app.post("/api/clans/:id/broadcast", requireFirebaseUser, async (req, res) => {
    const clanId = req.params.id;
    const actingUid = (req as express.Request & { firebaseUid: string }).firebaseUid;
    const { message } = (req.body || {}) as { message?: string };

    if (!message) {
      res.status(400).json({ error: "Message required" });
      return;
    }

    try {
      const actor = await getMember(clanId, actingUid);
      if (!actor || actor.role !== ClanRole.LEADER) {
        res.status(403).json({ error: "Only leader can broadcast" });
        return;
      }

      if (actor.last_broadcast_at) {
        const lastB = new Date(actor.last_broadcast_at).getTime();
        if (Date.now() - lastB < CLAN_BROADCAST_COOLDOWN_MS) {
          res.status(429).json({ error: "Broadcast on cooldown" });
          return;
        }
      }

      await updateLastBroadcastAt(clanId, actingUid);
      // In a real app, this would trigger push notifications
      console.log(`[Clan Broadcast] Clan ${clanId} Leader ${actingUid}: ${message}`);
      res.json({ ok: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Server error" });
    }
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
