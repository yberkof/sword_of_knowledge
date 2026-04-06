/**
 * One-off: copy `users` and `questions` from Firestore (Admin SDK) into PostgreSQL.
 * Requires DATABASE_URL, GOOGLE_APPLICATION_CREDENTIALS or service-account.json, and firebase-applet-config.json.
 */
import dotenv from "dotenv";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { initializeApp, applicationDefault, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import pg from "pg";

/** Must match APP_DB_SCHEMA in server/pgData.ts and migrations/001_init.sql */
const DB_SCHEMA = "sword_of_knowledge";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const serverRoot = path.join(__dirname, "..");
const repoRoot = path.join(__dirname, "..", "..");
dotenv.config({ path: path.join(repoRoot, ".env") });
dotenv.config();

function resolveAdminCredential() {
  const envPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (envPath && fs.existsSync(envPath)) {
    return cert(envPath);
  }
  for (const base of [repoRoot, serverRoot]) {
    const localJson = path.join(base, "service-account.json");
    if (fs.existsSync(localJson)) {
      return cert(localJson);
    }
  }
  return applicationDefault();
}

async function main() {
  const url = process.env.DATABASE_URL;
  if (!url) {
    console.error("Set DATABASE_URL");
    process.exit(1);
  }
  const firebaseConfigPath = [repoRoot, serverRoot]
    .map((d) => path.join(d, "firebase-applet-config.json"))
    .find((p) => fs.existsSync(p));
  if (!firebaseConfigPath) {
    console.error("Missing firebase-applet-config.json (repo or apps/server root)");
    process.exit(1);
  }
  const firebaseConfig = JSON.parse(fs.readFileSync(firebaseConfigPath, "utf8"));
  const app = initializeApp({
    credential: resolveAdminCredential(),
    projectId: firebaseConfig.projectId,
  });
  const fsdb = firebaseConfig.firestoreDatabaseId
    ? getFirestore(app, firebaseConfig.firestoreDatabaseId)
    : getFirestore(app);

  const pool = new pg.Pool({ connectionString: url });

  const usersSnap = await fsdb.collection("users").get();
  for (const d of usersSnap.docs) {
    const u = d.data() as Record<string, unknown>;
    const id = d.id;
    const display_name = String(u.name ?? u.displayName ?? "Warrior");
    const username = String(u.username ?? display_name);
    const avatar_url = String(u.avatar ?? "");
    const country_flag = String(u.countryFlag ?? "🇸🇦");
    const title = String(u.title ?? "Knowledge Knight");
    const level = Number(u.level ?? 1);
    const xp = Number(u.xp ?? 0);
    const gold = Number(u.gold ?? 1000);
    const gems = Number(u.gems ?? 50);
    const trophies = Number(u.trophies ?? 0);
    const rank = String(u.rank ?? "Bronze");
    const matches_played = Number(u.matchesPlayed ?? u.matches_played ?? 0);
    const wins = Number(u.wins ?? 0);
    const losses = Number(u.losses ?? 0);
    const inventory = JSON.stringify(Array.isArray(u.inventory) ? u.inventory : []);
    await pool.query(
      `INSERT INTO ${DB_SCHEMA}.users (
        id, display_name, username, avatar_url, country_flag, title, level, xp, gold, gems, trophies, rank,
        matches_played, wins, losses, inventory, updated_at
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16::jsonb,NOW())
      ON CONFLICT (id) DO UPDATE SET
        display_name = EXCLUDED.display_name,
        username = EXCLUDED.username,
        gold = EXCLUDED.gold,
        gems = EXCLUDED.gems,
        trophies = EXCLUDED.trophies,
        updated_at = NOW()`,
      [
        id,
        display_name,
        username,
        avatar_url,
        country_flag,
        title,
        level,
        xp,
        gold,
        gems,
        trophies,
        rank,
        matches_played,
        wins,
        losses,
        inventory,
      ]
    );
  }
  console.log(`Imported ${usersSnap.size} users`);

  const qSnap = await fsdb.collection("questions").get();
  let qCount = 0;
  for (const d of qSnap.docs) {
    const raw = d.data() as Record<string, unknown>;
    const text =
      typeof raw.text === "string"
        ? raw.text
        : typeof raw.question === "string"
          ? raw.question
          : null;
    const optionsRaw = raw.options;
    if (!text || !Array.isArray(optionsRaw) || optionsRaw.length === 0) continue;
    const options = JSON.stringify(optionsRaw.map((o) => String(o)));
    const ciRaw =
      raw.correctIndex ?? raw.correct_index ?? raw.correctAnswer ?? raw.correct_answer;
    const correctIndex =
      typeof ciRaw === "number" ? Math.trunc(ciRaw) : parseInt(String(ciRaw), 10);
    if (!Number.isFinite(correctIndex)) continue;
    const category = typeof raw.category === "string" ? raw.category : "عام";
    const difficulty =
      typeof raw.difficulty === "string" ? raw.difficulty : null;
    await pool.query(
      `INSERT INTO ${DB_SCHEMA}.questions (text, options, correct_index, category, difficulty, is_active)
       SELECT $1, $2::jsonb, $3, $4, $5, TRUE
       WHERE NOT EXISTS (SELECT 1 FROM ${DB_SCHEMA}.questions WHERE text = $1 AND category = $4 LIMIT 1)`,
      [text, options, correctIndex, category, difficulty]
    );
    qCount++;
  }
  console.log(`Processed ${qCount} question documents (skipped duplicates by text+category)`);

  await pool.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
