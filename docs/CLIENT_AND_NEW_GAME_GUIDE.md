# Client integration and adding new games

This guide describes **what the Spring realtime backend (`SocketGateway`) now exposes** on every `room_update`, how **Marefa** (or any client) should consume it, and how to **add a new game variant** on the server and in the UI without forking the whole stack.

---

## 1. Backend: `room_update` payload (authoritative)

Every `room_update` event includes at least:

| Field | Type | Meaning |
|--------|------|---------|
| `id` | string | Room id (use as match id in the client). |
| `phase` | string | Spring phase: `waiting`, `castle_placement`, `claiming_question`, `claiming_pick`, `battle`, `duel`, `battle_tiebreaker`, `ended`, … |
| `round` | number | Battle / match round index. |
| `battleRound` | number | Same as `round` (alias for clients that already expect `battleRound`). |
| `currentTurnIndex` | number | Index into `players` for **battle** phase turn order. |
| `claimTurnUid` | string \| null | Who may emit `claim_region` when `phase === "claiming_pick"`. |
| `mapId` | string | Id that must match a **client map registry** entry (e.g. Marefa `basic_1v1_map`). |
| `matchMode` | string | `"ffa"` (default) or `"teams_2v2"`. |
| `rulesetId` | string | Identifier for the active rule module (default `"sok_v1"`). |
| `players` | array | See below. |
| `mapState` | array | Regions / hexes. |
| `scoreByUid` | object | Scores keyed by uid. |
| `activeDuel` | object \| absent | Present during duels / tiebreaker UI. |

Each **player** row includes:

| Field | Type | Meaning |
|--------|------|---------|
| `uid`, `name`, `hp`, `color`, `isCapitalLost`, … | | Existing fields. |
| `teamId` | string \| null | `null` in FFA. For `teams_2v2`: `"A"` or `"B"` (join order: slots 0–1 → A, 2–3 → B). |

### Team mode (`teams_2v2`)

- **Start condition:** auto-start and host `start_match` require **four** players when `matchMode` is `teams_2v2`.
- **Combat:** attacking a region owned by a **teammate** is rejected (`attack_invalid` reason `ally_territory`).
- **Win:** when only one team still has living players, the match ends with reason `team_survival` and `winnerUid` is the **top scorer among survivors** on that team.

### Join-time metadata (first player in an empty room)

Optional fields on **`join_matchmaking`** (only the **first** joiner’s values apply to that waiting room):

| Field | Effect |
|--------|--------|
| `matchMode` | `"ffa"` or `"teams_2v2"` (normalized server-side). |
| `rulesetId` | Stored on the room and echoed on every `room_update`. |
| `mapId` | Stored on the room; must align with the client’s map assets. |

If omitted, defaults come from **runtime game config** (`GameRuntimeConfig`: `defaultMapId`, `defaultMatchMode`, `defaultRulesetId`), typically `"basic_1v1_map"`, `"ffa"`, `"sok_v1"`.

---

## 2. Implementing the Marefa client (checklist)

Work in the **marefa-game** frontend repository (paths below are repo-relative).

1. **Types** — Extend `src/types/sok.types.ts` (`SOKMatchState`) with `mapId`, `matchMode`, `rulesetId`, `battleRound` (optional). Extend each player with optional `teamId`.

2. **Adapter** — In `src/services/backend/sokAdapter.ts`, map:
   - `mapId` → `MatchState.mapId`
   - `rulesetId` / `matchMode` → new fields on `MatchState` or a nested `matchConfig` object
   - `teamId` → extend `Player` or a parallel structure (today `Player` only has `faction`).

3. **Turn UI** — Drive “whose turn” from **`serverPhase`** plus `claimTurnUid`, `turnUid` (from `currentTurnIndex`), and duel `activeDuel` — not from `activeAttackerId` alone. Use one small **pure function** (turn resolver) shared by `TurnIndicator` and `HUD`.

4. **Map actions** — `src/hooks/useMapRegionActions.ts` already gates by `castle_placement`, `claiming_pick`, `battle`; keep commands unchanged (`place_castle`, `claim_region`, `attack`).

5. **Teams HUD** — When `matchMode === 'teams_2v2'`, group panels by `teamId` and use server `color` hex before falling back to faction palette (`src/constants/factionColors.ts`).

6. **Mocks / tests** — Extend `src/services/backend/MockBackend.ts` to emit `serverPhase`, `claimTurnUid`, `turnUid`, and optional `teamId` so tests cover team and FFA paths.

7. **Locale** — Any new UI strings → `messages/ar.json` per project rules.

---

## 3. Adding a new **game** on the backend

“New game” here means a **new ruleset** or **mode** that still uses the same socket transport (`room_update`, same command names) or adds **new phases** handled inside `SocketGateway` / domain services.

### 3a. Same protocol, different rules (recommended first step)

1. Pick a **`rulesetId`** string (e.g. `sok_v2_rush`).
2. When creating the room (first `join_matchmaking`), set `rulesetId` in the payload (or default it in `GameRuntimeConfig` for testing).
3. In [`SocketGateway.java`](../src/main/java/com/sok/backend/realtime/SocketGateway.java), branch on `room.rulesetId` only where rules differ (e.g. `maxRounds`, claim pick counts, `canAttackRegion`, win condition). Keep **one** `roomToClient` shape so clients stay compatible.
4. Document new phases or reasons in this file and in [`docs/spring/game-flow-deep-dive.md`](./spring/game-flow-deep-dive.md).

### 3b. New phases or events

1. Add a **phase constant** (like `PHASE_BATTLE`) and transition logic in one place.
2. Emit **`phase_changed`** or reuse existing named events so the client can show the right overlay.
3. Extend **`roomToClient`** only if new fields are strictly necessary; prefer driving UI from `phase` + existing duel / claim fields.

### 3c. New map topology

1. Extend **`GameRuntimeConfig`**: `regionPoints`, `neighbors` must define every region id used by that map.
2. Set **`defaultMapId`** (or per-room `mapId` on join) so the client loads matching SVG / PNG.
3. Ensure **castle bases** count ≥ players for that mode (teams need four starts in current layout).

---

## 4. Adding a new **game** on the frontend (Marefa)

1. **Register the map** — Add a config under `src/constants/maps/` and register it in `src/constants/regionConfigs.ts`; `mapId` must equal the server’s `mapId`.

2. **Ruleset switch** — If the UI differs by `rulesetId`, use a small map:

   ```text
   rulesetId → optional layout components / copy / tutorial
   ```

   Avoid branching inside low-level hooks; keep routing in the game page or a dedicated `RulesetShell` component.

3. **Backend contract** — Never invent fields on the wire; mirror what `room_update` sends after your Spring changes.

4. **Verification** — `npx tsc --noEmit`, unit tests for adapter + turn resolver, `npm run build && npx playwright test` for critical flows.

---

## 5. Quick reference: turn resolution (client)

Derive the **active actor uid** from:

| `phase` | Active player(s) |
|---------|-------------------|
| `claiming_pick` | `claimTurnUid` |
| `battle` | `players[currentTurnIndex]` (expose as `turnUid` in adapter) |
| `duel` / `battle_tiebreaker` | `activeDuel.attackerUid` / `defenderUid` |

This keeps map clicks and HUD aligned with the server without hard-coding player counts.

---

## 6. Tie-breaking modes (MCQ duel speed tie)

When **both** players answer the MCQ correctly with **identical latency**, the server must break the tie. Configure globally via **`GameRuntimeConfig`** (runtime JSON):

| `tieBreakerMode` | Behaviour |
|------------------|-----------|
| `numeric_closest` (default) | Second-phase **estimation** question (`battle_tiebreaker_start`). Answers via existing `submit_estimation` while `phase === battle_tiebreaker`; answers are stored server-side on the duel (not only in room claiming state). Closest numeric wins; equal distance → faster submission wins. |
| `mcq_retry` | Run another **full MCQ duel** up to **`maxMcqTieRetries`** times. If still tied by same rule, falls back to **`numeric_closest`** for that resolution only. |
| `attacker_advantage` | Attacker wins immediately (no extra round). |
| `minigame_xo` | **Tic-tac-toe**: attacker is **1**, defender **2**, attacker opens. **`room_update.activeDuel`** includes `tiebreakKind: "xo"`, `xoCells` (length 9: `0` empty, `1` attacker, `2` defender), `xoTurnUid`. Client sends **`tiebreaker_xo_move`** `{ roomId, uid, cellIndex }` with `cellIndex` 0–8. Server emits **`tiebreaker_xo_start`** on entry and **`tiebreaker_xo_replay`** after a **draw** if **`xoDrawMaxReplay`** allows a cleared board; otherwise a draw awards **defender** the duel. |

### Client checklist (Marefa)

1. On `battle_tiebreaker_start`, open the **estimation** card only if `activeDuel.tiebreakKind` is absent or `"numeric"` (same as legacy).
2. If `tiebreakKind === "xo"`, render a 3×3 grid; on tap emit `tiebreaker_xo_move`; refresh from `room_update`.
3. Listen for `tiebreaker_xo_replay` to animate a new empty board.

### Adding another minigame (backend)

Use the **`com.sok.backend.domain.game.tiebreaker`** layer (SOLID extension points):

1. **`TieBreakerModeIds`** — add a new canonical id string (e.g. `minigame_rps`).
2. **`TieBreakerAttackPhaseStrategy`** — new `@Component` implementation with `@Order` **above** `NumericClosestTieBreakerAttackPhaseStrategy` (lower precedence / higher order value = tried first when multiple could match). Implement `supports(mode, defenderUid)` and `begin(BeginContext)` using **`TieBreakerRealtimeBridge`** only for I/O (no direct `SocketGateway` dependency).
3. **`TieBreakerAttackPhaseComposer`** — injects all strategies; **no change** if the new bean is on the classpath.
4. **Pure rules** — put win/loss math in a dedicated class (same pattern as **`XoBoardRules`**, **`NumericTiebreakEvaluator`**).
5. **Moves** — optional `@Service` (like **`XoTieBreakInteractionService`**) returning an outcome enum; **`SocketGateway`** maps outcomes to `finishBattle` / emits only.
6. **`McqSpeedTieResolutionService`** — extend only if the **MCQ speed tie** policy itself changes (retries, attacker advantage, …).

Keep **`submit_estimation`** wired only when **`tiebreakKind === "numeric"`**; keep **`resolveTiebreaker`** for numeric autofill + **`NumericTiebreakEvaluator`**.

---

## 7. Files touched on the backend (reference)

- [`GameRuntimeConfig.java`](../src/main/java/com/sok/backend/service/config/GameRuntimeConfig.java) — defaults for map/mode/ruleset, tie-break fields (`tieBreakerMode`, `maxMcqTieRetries`, `xoDrawMaxReplay`).
- [`SocketGateway.java`](../src/main/java/com/sok/backend/realtime/SocketGateway.java) — room metadata, teams, tie-break branches, X-O events, `activeDuel` extensions.

Build with JDK 17+: `mvn -q compile` from the `srf` project root.
