/**
 * Rhythm tiebreaker: coerce sequences from JSON / Socket.IO (arrays may become { "0": n, ... }).
 * Must stay in sync between server validation and client UI.
 */
export function coerceRhythmNumberArray(raw: unknown): number[] | null {
  if (Array.isArray(raw)) {
    const a = raw.map((x) => Math.trunc(Number(x)));
    if (a.some((n) => !Number.isFinite(n))) return null;
    return a;
  }
  if (raw && typeof raw === "object") {
    const o = raw as Record<string, unknown>;
    const keys = Object.keys(o)
      .filter((k) => /^\d+$/.test(k))
      .sort((a, b) => Number(a) - Number(b));
    if (keys.length === 0) return null;
    const a = keys.map((k) => Math.trunc(Number(o[k])));
    if (a.some((n) => !Number.isFinite(n))) return null;
    return a;
  }
  return null;
}

export function normalizeRhythmPads(nums: number[]): number[] {
  return nums.map((n) => (((n % 4) + 4) % 4));
}

/** For display / playback: invalid or missing → []. */
export function coerceRhythmPads(raw: unknown): number[] {
  const a = coerceRhythmNumberArray(raw);
  if (!a) return [];
  return normalizeRhythmPads(a);
}
