export function normalizePrivateCode(raw: string): string {
  const s = String(raw || "")
    .replace(/[^a-zA-Z0-9]/g, "")
    .toUpperCase()
    .slice(0, 8);
  return s;
}

const PROFANITY_SUBSTRINGS = ["spam", "curse"];

export function sanitizeChatMessage(text: string): string {
  const s = String(text || "").slice(0, 280);
  for (const bad of PROFANITY_SUBSTRINGS) {
    if (s.toLowerCase().includes(bad)) return "";
  }
  return s.trim();
}

export function coerceChoiceIndex(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value === "bigint") {
    const n = Number(value);
    return Number.isSafeInteger(n) ? n : null;
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  if (typeof value === "string" && value.trim() !== "") {
    const n = parseInt(value, 10);
    return Number.isFinite(n) ? n : null;
  }
  if (typeof value === "object" && value !== null) {
    const v = value as { valueOf?: () => unknown };
    if (typeof v.valueOf === "function") {
      const inner = v.valueOf.call(value);
      if (inner !== value) return coerceChoiceIndex(inner);
    }
  }
  return null;
}

export const DUEL_DURATION_MS = 10_000;
export const EXPANSION_ROUND_MS = 18_000;
export const MIN_PLAYERS = 2;
export const MAX_PLAYERS = 4;
export const PLAYER_COLORS = ["#C41E3A", "#228B22", "#1E90FF", "#9333EA"];
export const HEX_POINTS_NORMAL = 100;
export const HEX_POINTS_CAPITAL_TILE = 400;
export const ATTACKS_PER_BATTLE_ROUND = 4;
export const EXPANSION_MAX_ROUNDS = 2;

export const ESTIMATION_QUESTIONS = [
  { text: "كم عدد أيام السنة الكبيسة؟", answer: 366 },
  { text: "كم عدد الدول العربية الأعضاء في جامعة الدول العربية (تقريباً)؟", answer: 22 },
  { text: "في أي عام ميلادي سقطت القسطنطينية (تقريباً)؟", answer: 1453 },
  { text: "كم دقيقة في يوم كامل؟", answer: 1440 },
];
