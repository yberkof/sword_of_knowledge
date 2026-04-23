# Collection tie-break (`minigame_collection`)

## Overview

Players vote independently on one of four sub-minigames. Same pick plays that game; **different picks choose uniformly between the two choices**. Vote timeout fills missing picks with uniform random catalog choices; if **both** are still absent, resolution picks uniformly from all four options.

Configure with `tieBreakerMode: minigame_collection` in [`GameRuntimeConfig`](../src/main/java/com/sok/backend/service/config/GameRuntimeConfig.java) (`collectionPickMs`, `memoryPeekMs`, `rhythmTimeoutBaseMs`, `rhythmTimeoutExtraPerRoundMs`).

## Wire events (Socket.IO)

| Direction | Event | Notes |
|-----------|-------|-------|
| S → room | `tiebreaker_collection_start` | `options`, `pickEndsAtMs`, `pickDeadlineMs`, `serverNowMs` |
| C → S | `tiebreaker_collection_pick` | `{ roomId, uid, choice }` choice ∈ `avoid_bombs`,`rps`,`rhythm`,`memory` |
| S → room | `tiebreaker_collection_resolved` | `resolvedGame`, `attackerPick`, `defenderPick` |
| | | Then one sub-minigame starts (below). |

### Avoid bombs

Existing `tiebreaker_avoid_bombs_*` — unchanged.

### Rock–paper–scissors (best of 3)

| Direction | Event |
|-----------|-------|
| S → room | `tiebreaker_rps_start`, `tiebreaker_rps_round` |
| C → S | `tiebreaker_rps_throw` `{ hand: rock|paper|scissors }` |

### Rhythm (Simon)

Shared sequence length `4 + rhythmRound`; both players replay; **both wrong ⇒ defender wins**. Timeout uses `rhythmTimeoutBaseMs + rhythmRound * rhythmTimeoutExtraPerRoundMs`.

| Direction | Event |
|-----------|-------|
| S → room | `tiebreaker_rhythm_round` — `sequence`, `round`, `deadlineMs` |
| C → S | `tiebreaker_rhythm_submit` `{ inputs: number[] }` color indices `0..3` |

### Memory (6×6, 18 pairs)

Peek then play; mismatch switches turn; higher pair count wins; tie ⇒ defender.

| Direction | Event |
|-----------|-------|
| S → room | `tiebreaker_memory_peek`, `tiebreaker_memory_play`, `tiebreaker_memory_flip` |
| C → S | `tiebreaker_memory_flip` `{ cellIndex }` |

## Duel state snapshot

Extended fields on [`DuelState`](../src/main/java/com/sok/backend/realtime/match/DuelState.java) and [`RoomSnapshotDto.DuelSnapshotDto`](../src/main/java/com/sok/backend/realtime/persistence/RoomSnapshotDto.java): collection picks, RPS counters, rhythm sequence/deadlines, memory grid.

## Related

- [`GAME_MODE_ENGINE.md`](./GAME_MODE_ENGINE.md)
- [`AVOID_BOMBS_TIEBREAK.md`](./AVOID_BOMBS_TIEBREAK.md)
