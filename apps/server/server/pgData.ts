import pg from "pg";

/** Postgres schema for this game on shared databases — must match `migrations/001_init.sql`. */
export const APP_DB_SCHEMA = "sword_of_knowledge";
const SCH = APP_DB_SCHEMA;

export type DuelQuestionDoc = {
  id: string;
  text: string;
  options: string[];
  correctIndex: number;
  category?: string;
  difficulty?: string;
};

let pool: pg.Pool | null = null;

export function getPool(): pg.Pool {
  if (!pool) {
    const url = process.env.DATABASE_URL;
    if (!url) {
      throw new Error(
        "DATABASE_URL is not set. Start Postgres (e.g. docker compose up -d) and copy .env.example to .env"
      );
    }
    pool = new pg.Pool({ connectionString: url, max: 20 });
  }
  return pool;
}

export async function closePool(): Promise<void> {
  if (pool) {
    await pool.end();
    pool = null;
  }
}

const FALLBACK_QUESTIONS: DuelQuestionDoc[] = [
  {
    id: "fallback-1",
    text: "ما عاصمة مصر؟",
    options: ["القاهرة", "الإسكندرية", "الأقصر", "أسوان"],
    correctIndex: 0,
    category: "local",
  },
];

export async function pickRandomQuestion(categoryFilter?: string | null): Promise<DuelQuestionDoc> {
  const p = getPool();
  try {
    const cf = categoryFilter?.trim() || null;
    const { rows } = await p.query<{
      id: string;
      text: string;
      options: string[];
      correct_index: number;
      category: string;
      difficulty: string | null;
    }>(
      `SELECT id::text AS id, text, options, correct_index, category, difficulty
       FROM ${SCH}.questions
       WHERE is_active = TRUE
         AND ($1::text IS NULL OR $1 = '' OR category = $1)
       ORDER BY random()
       LIMIT 1`,
      [cf]
    );
    const row = rows[0];
    if (!row) {
      if (cf) {
        const retry = await p.query<{
          id: string;
          text: string;
          options: string[];
          correct_index: number;
          category: string;
          difficulty: string | null;
        }>(
          `SELECT id::text AS id, text, options, correct_index, category, difficulty
           FROM ${SCH}.questions WHERE is_active = TRUE ORDER BY random() LIMIT 1`
        );
        const r2 = retry.rows[0];
        if (!r2) {
          console.warn("No questions in Postgres; using fallback.");
          return FALLBACK_QUESTIONS[Math.floor(Math.random() * FALLBACK_QUESTIONS.length)]!;
        }
        let m2 = mapRow(r2);
        if (optionsAreDistinct(m2.options)) return m2;
        for (let attempt = 0; attempt < 6; attempt++) {
          const alt = await p.query<{
            id: string;
            text: string;
            options: string[];
            correct_index: number;
            category: string;
            difficulty: string | null;
          }>(
            `SELECT id::text AS id, text, options, correct_index, category, difficulty
             FROM ${SCH}.questions WHERE is_active = TRUE ORDER BY random() LIMIT 1`
          );
          const r3 = alt.rows[0];
          if (!r3) break;
          const m3 = mapRow(r3);
          if (optionsAreDistinct(m3.options)) return m3;
        }
        const fb = FALLBACK_QUESTIONS.find((q) => optionsAreDistinct(q.options));
        return fb ?? m2;
      }
      console.warn("No questions in Postgres; using fallback.");
      return FALLBACK_QUESTIONS[Math.floor(Math.random() * FALLBACK_QUESTIONS.length)]!;
    }
    const mapped = mapRow(row);
    if (!optionsAreDistinct(mapped.options)) {
      for (let attempt = 0; attempt < 6; attempt++) {
        const alt = await p.query<{
          id: string;
          text: string;
          options: string[];
          correct_index: number;
          category: string;
          difficulty: string | null;
        }>(
          `SELECT id::text AS id, text, options, correct_index, category, difficulty
           FROM ${SCH}.questions WHERE is_active = TRUE ORDER BY random() LIMIT 1`
        );
        const r3 = alt.rows[0];
        if (!r3) break;
        const m3 = mapRow(r3);
        if (optionsAreDistinct(m3.options)) return m3;
      }
    }
    if (!optionsAreDistinct(mapped.options)) {
      const fb = FALLBACK_QUESTIONS.find((q) => optionsAreDistinct(q.options));
      if (fb) return fb;
      console.warn("Question has duplicate options; using first row anyway.");
    }
    return mapped;
  } catch (err) {
    console.warn(
      "pickRandomQuestion failed:",
      err instanceof Error ? err.message : err
    );
    return FALLBACK_QUESTIONS[Math.floor(Math.random() * FALLBACK_QUESTIONS.length)]!;
  }
}

function mapRow(row: {
  id: string;
  text: string;
  options: unknown;
  correct_index: number;
  category: string;
  difficulty: string | null;
}): DuelQuestionDoc {
  const options = Array.isArray(row.options)
    ? row.options.map((o) => String(o))
    : [];
  return {
    id: row.id,
    text: row.text,
    options,
    correctIndex: row.correct_index,
    category: row.category || "عام",
    difficulty: row.difficulty ?? undefined,
  };
}

function optionsAreDistinct(opts: string[]): boolean {
  const s = new Set(opts.map((o) => o.trim().toLowerCase()));
  return s.size === opts.length && opts.length > 0;
}

export async function fetchUserTrophies(uid: string): Promise<number> {
  const p = getPool();
  const { rows } = await p.query<{ trophies: number }>(
    `SELECT trophies FROM ${SCH}.users WHERE id = $1`,
    [uid]
  );
  const t = rows[0]?.trophies;
  return typeof t === "number" && Number.isFinite(t) ? t : 0;
}

export async function persistActiveRoom(
  roomId: string,
  room: {
    phase: string;
    players: { uid: string }[];
    inviteCode?: string | null;
  }
): Promise<void> {
  const p = getPool();
  const playerUids = room.players.map((x) => x.uid);
  await p.query(
    `INSERT INTO ${SCH}.active_rooms (room_id, phase, player_uids, invite_code, updated_at)
     VALUES ($1, $2, $3::jsonb, $4, NOW())
     ON CONFLICT (room_id) DO UPDATE SET
       phase = EXCLUDED.phase,
       player_uids = EXCLUDED.player_uids,
       invite_code = EXCLUDED.invite_code,
       updated_at = NOW()`,
    [
      roomId,
      room.phase,
      JSON.stringify(playerUids),
      room.inviteCode ?? null,
    ]
  );
}

export async function deleteActiveRoomDoc(roomId: string): Promise<void> {
  const p = getPool();
  await p.query(`DELETE FROM ${SCH}.active_rooms WHERE room_id = $1`, [roomId]);
}

export async function applyMatchSettlement(
  winnerUid: string,
  loserUid: string
): Promise<void> {
  await applyMatchResults([
    { uid: winnerUid, place: 1 },
    { uid: loserUid, place: 2 },
  ]);
}

/** place 1 = best. Base XP tiers; streak bonuses applied in applyMatchResults. */
const BASE_XP_BY_PLACE: Record<number, number> = {
  1: 300,
  2: 200,
  3: 100,
  4: 50,
};

export async function applyMatchResults(
  placements: { uid: string; place: number }[]
): Promise<void> {
  const sorted = [...placements].sort((a, b) => a.place - b.place);
  if (sorted.length === 0) return;
  const maxPlace = Math.max(...sorted.map((s) => s.place));

  const pool = getPool();
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const uids = sorted.map((s) => s.uid);
    const { rows: userRows } = await client.query<{
      id: string;
      level: number;
      win_streak: number;
      no_loss_streak: number;
    }>(
      `SELECT id, level,
              COALESCE(win_streak, 0)::int AS win_streak,
              COALESCE(no_loss_streak, 0)::int AS no_loss_streak
       FROM ${SCH}.users WHERE id = ANY($1::text[]) FOR UPDATE`,
      [uids]
    );
    type URow = {
      id: string;
      level: number;
      win_streak: number;
      no_loss_streak: number;
    };
    const byUid = new Map<string, URow>(userRows.map((r) => [r.id, r]));

    for (const row of sorted) {
      const place = row.place;
      const baseXp = BASE_XP_BY_PLACE[place] ?? 50;
      const isWinner = place === 1;
      const isLast = place === maxPlace;
      const u = byUid.get(row.uid);
      const myLv = u?.level ?? 1;
      const others = sorted.filter((x) => x.uid !== row.uid);
      const avgLv =
        others.reduce((s, x) => s + (byUid.get(x.uid)?.level ?? 1), 0) /
        Math.max(1, others.length);
      const opponentBonus = Math.max(0, Math.min(80, Math.floor((avgLv - myLv) * 5)));

      const prevWs = u?.win_streak ?? 0;
      const prevNls = u?.no_loss_streak ?? 0;
      const newWinStreak = isWinner ? prevWs + 1 : 0;
      const winStreakXp = isWinner ? Math.min(100 * newWinStreak, 1000) : 0;
      const newNoLossStreak = !isLast ? prevNls + 1 : 0;
      const noLossXp = !isLast ? Math.min(50 * newNoLossStreak, 500) : 0;

      const ratioBonus = Math.floor(baseXp * 0.15);
      const totalXp = Math.max(
        0,
        baseXp + opponentBonus + winStreakXp + noLossXp + ratioBonus
      );

      const trophyDelta = isWinner ? 25 : isLast ? -5 : 4;
      const goldDelta = isWinner ? 50 : isLast ? 10 : 22;
      const addLoss = !isWinner ? 1 : 0;

      await client.query(
        `UPDATE ${SCH}.users SET
          trophies = GREATEST(0, trophies + $2),
          xp = xp + $3,
          gold = gold + $4,
          matches_played = matches_played + 1,
          wins = wins + CASE WHEN $5 THEN 1 ELSE 0 END,
          losses = losses + $6,
          win_streak = $7,
          no_loss_streak = $8,
          updated_at = NOW()
         WHERE id = $1`,
        [
          row.uid,
          trophyDelta,
          totalXp,
          goldDelta,
          isWinner,
          addLoss,
          newWinStreak,
          newNoLossStreak,
        ]
      );
    }

    await client.query("COMMIT");
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    client.release();
  }
}

export async function countActiveQuestions(): Promise<number> {
  const p = getPool();
  const { rows } = await p.query<{ c: string }>(
    `SELECT COUNT(*)::text AS c FROM ${SCH}.questions WHERE is_active = TRUE`
  );
  return parseInt(rows[0]?.c ?? "0", 10) || 0;
}

export async function shopPurchase(
  uid: string,
  itemId: string,
  costGold: number
): Promise<void> {
  const p = getPool();
  const client = await p.connect();
  try {
    await client.query("BEGIN");
    const { rows } = await client.query<{ inventory: unknown }>(
      `SELECT inventory FROM ${SCH}.users WHERE id = $1 FOR UPDATE`,
      [uid]
    );
    if (rows.length === 0) {
      throw new Error("user not found");
    }
    let inv: string[] = [];
    const raw = rows[0]!.inventory;
    if (Array.isArray(raw)) inv = raw.map(String);
    else if (raw && typeof raw === "object")
      inv = Object.values(raw as object).map(String);
    if (!inv.includes(itemId)) inv.push(itemId);
    await client.query(
      `UPDATE ${SCH}.users SET gold = gold - $2, inventory = $3::jsonb, updated_at = NOW() WHERE id = $1`,
      [uid, costGold, JSON.stringify(inv)]
    );
    await client.query("COMMIT");
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    client.release();
  }
}

export type UserRow = {
  id: string;
  display_name: string;
  username: string | null;
  avatar_url: string;
  country_flag: string;
  title: string;
  level: number;
  xp: number;
  gold: number;
  gems: number;
  trophies: number;
  rank: string;
  matches_played: number;
  wins: number;
  losses: number;
  inventory: unknown;
};

export function rowToClientProfile(row: UserRow): Record<string, unknown> {
  let inventory: string[] = [];
  if (Array.isArray(row.inventory)) inventory = row.inventory.map(String);
  else if (row.inventory && typeof row.inventory === "string")
    try {
      inventory = JSON.parse(row.inventory);
    } catch {
      inventory = [];
    }
  return {
    uid: row.id,
    name: row.display_name,
    username: row.username ?? row.display_name,
    countryFlag: row.country_flag,
    title: row.title,
    level: row.level,
    xp: row.xp,
    gold: row.gold,
    gems: row.gems,
    avatar: row.avatar_url,
    rank: row.rank,
    trophies: row.trophies,
    inventory,
  };
}

export async function getUserById(uid: string): Promise<UserRow | null> {
  const p = getPool();
  const { rows } = await p.query<UserRow>(
    `SELECT id, display_name, username, avatar_url, country_flag, title, level, xp, gold, gems,
            trophies, rank, matches_played, wins, losses, inventory
     FROM ${SCH}.users WHERE id = $1`,
    [uid]
  );
  return rows[0] ?? null;
}

export async function createUser(uid: string, defaults: {
  display_name: string;
  username: string;
  avatar_url: string;
}): Promise<UserRow> {
  const p = getPool();
  const { rows } = await p.query<UserRow>(
    `INSERT INTO ${SCH}.users (id, display_name, username, avatar_url, country_flag, title, level, xp, gold, gems, trophies, rank, inventory)
     VALUES ($1, $2, $3, $4, '🇸🇦', 'Knowledge Knight', 1, 0, 1000, 50, 0, 'Bronze', '[]'::jsonb)
     RETURNING id, display_name, username, avatar_url, country_flag, title, level, xp, gold, gems,
               trophies, rank, matches_played, wins, losses, inventory`,
    [uid, defaults.display_name, defaults.username, defaults.avatar_url]
  );
  return rows[0]!;
}

export async function touchUserLogin(uid: string, sessionId: string): Promise<void> {
  const p = getPool();
  await p.query(
    `UPDATE ${SCH}.users SET last_login_at = NOW(), active_session_id = $2, updated_at = NOW() WHERE id = $1`,
    [uid, sessionId]
  );
}

export async function patchUserProfile(
  uid: string,
  patch: Partial<{
    display_name: string;
    username: string;
    avatar_url: string;
    country_flag: string;
  }>
): Promise<UserRow | null> {
  const p = getPool();
  const sets: string[] = [];
  const vals: unknown[] = [];
  let i = 1;
  if (patch.display_name != null) {
    sets.push(`display_name = $${i++}`);
    vals.push(patch.display_name);
  }
  if (patch.username != null) {
    sets.push(`username = $${i++}`);
    vals.push(patch.username);
  }
  if (patch.avatar_url != null) {
    sets.push(`avatar_url = $${i++}`);
    vals.push(patch.avatar_url);
  }
  if (patch.country_flag != null) {
    sets.push(`country_flag = $${i++}`);
    vals.push(patch.country_flag);
  }
  if (sets.length === 0) return getUserById(uid);
  sets.push("updated_at = NOW()");
  vals.push(uid);
  const { rows } = await p.query<UserRow>(
    `UPDATE ${SCH}.users SET ${sets.join(", ")} WHERE id = $${i}
     RETURNING id, display_name, username, avatar_url, country_flag, title, level, xp, gold, gems,
               trophies, rank, matches_played, wins, losses, inventory`,
    vals
  );
  return rows[0] ?? null;
}
