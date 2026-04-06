import { tiebreakerClientPayload } from "../../server/tieBreaker.js";

/** Strip non-JSON timer fields and sanitize tieBreaker for clients. */
export function roomToClient(room: unknown) {
  if (!room || typeof room !== "object") return room;
  const {
    duelTimerId: _dt,
    expansionTimerId: _et,
    tiebreakerPickTimerId: _tbp,
    ...rest
  } = room as Record<string, unknown> & {
    duelTimerId?: unknown;
    expansionTimerId?: unknown;
    tiebreakerPickTimerId?: unknown;
  };
  const out = { ...rest } as Record<string, unknown>;
  if (out.tieBreaker && typeof out.tieBreaker === "object") {
    out.tieBreaker = tiebreakerClientPayload(out.tieBreaker as Record<string, unknown>);
  }
  return out;
}
