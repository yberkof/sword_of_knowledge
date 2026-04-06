/**
 * Idempotent seed of demo questions into Postgres (replaces client Firestore seed in App.tsx).
 */
import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";
import pg from "pg";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(__dirname, "../../.env") });
dotenv.config();

/** Must match APP_DB_SCHEMA in server/pgData.ts and migrations/001_init.sql */
const DB_SCHEMA = "sword_of_knowledge";

const questions: {
  text: string;
  options: string[];
  correctIndex: number;
  category: string;
  difficulty: string;
}[] = [
  {
    text: "ما هي عاصمة الأردن؟",
    options: ["عمان", "القاهرة", "الرياض", "بغداد"],
    correctIndex: 0,
    category: "الجغرافيا",
    difficulty: "سهل",
  },
  {
    text: "من هو مكتشف الجاذبية؟",
    options: ["أينشتاين", "نيوتن", "جاليليو", "تسلا"],
    correctIndex: 1,
    category: "العلوم",
    difficulty: "سهل",
  },
  {
    text: "في أي عام انتهت الحرب العالمية الثانية؟",
    options: ["1918", "1939", "1945", "1950"],
    correctIndex: 2,
    category: "التاريخ",
    difficulty: "متوسط",
  },
  {
    text: "ما هي أكبر قارة في العالم؟",
    options: ["أفريقيا", "أوروبا", "آسيا", "أمريكا الشمالية"],
    correctIndex: 2,
    category: "الجغرافيا",
    difficulty: "سهل",
  },
  {
    text: 'من هو صاحب رواية "البؤساء"؟',
    options: ["شكسبير", "فيكتور هوجو", "تولستوي", "نجيب محفوظ"],
    correctIndex: 1,
    category: "الأدب",
    difficulty: "متوسط",
  },
];

async function main() {
  const url = process.env.DATABASE_URL;
  if (!url) {
    console.error("Set DATABASE_URL");
    process.exit(1);
  }
  const pool = new pg.Pool({ connectionString: url });
  for (const q of questions) {
    await pool.query(
      `INSERT INTO ${DB_SCHEMA}.questions (text, options, correct_index, category, difficulty, is_active)
       SELECT $1, $2::jsonb, $3, $4, $5, TRUE
       WHERE NOT EXISTS (
         SELECT 1 FROM ${DB_SCHEMA}.questions WHERE text = $1 AND category = $4 LIMIT 1
       )`,
      [q.text, JSON.stringify(q.options), q.correctIndex, q.category, q.difficulty]
    );
  }
  console.log("seed-questions: done");
  await pool.end();
}

main().catch((e: unknown) => {
  const err = e as { code?: string };
  if (err.code === "42P01") {
    console.error(
      `Missing ${DB_SCHEMA} tables. Run migrations first: npm run db:migrate`
    );
  }
  console.error(e);
  process.exit(1);
});
