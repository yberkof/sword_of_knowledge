# Game Mode Engine — frontend integration guide

Audience: the Marefa frontend team (and anyone else that speaks to the backend over
Socket.IO). Read this once; after that, the `room_update` shape you already handle is
all you need.

## TL;DR for the client

- **The wire contract is unchanged.** Every event you emit today (`join_matchmaking`,
  `place_castle`, `claim_region`, `attack`, `submit_answer`, `submit_estimation`,
  `use_powerup`, `tiebreaker_xo_move`) still works the same way, and every event the
  server emits (`room_update`, `duel_start`, `duel_resolved`, `estimation_question`,
  `battle_tiebreaker_start`, `tiebreaker_xo_start`, `tiebreaker_xo_replay`,
  `game_ended`) keeps its existing payload shape.
- `room_update.rulesetId` is now **authoritative**. It resolves to a concrete game
  mode on the backend, and different modes can change pacing, team policy, tie-break
  mode, claim pick counts, HP and other rule numbers without the client caring.
- Treat `rulesetId` as **the mode id**. When we ship a new mode (e.g. `sok_v2_rush`),
  the client may want to key its copy / help text / tutorial overlays on that string,
  but the socket handlers stay identical.

Nothing in this document requires frontend changes to land. The doc exists so you
understand the backend model when we *do* ask for a targeted UI tweak per mode.

## What actually changed on the backend

Backend only. A new package at
[`src/main/java/com/sok/backend/domain/game/engine`](../src/main/java/com/sok/backend/domain/game/engine)
introduces a pluggable game-mode layer. Today it runs the same rules as before — it's
the scaffolding we need to add new modes without forking `SocketGateway`.

### Core concepts

| Concept | Type | What it answers |
|---------|------|------------------|
| `GameMode` | interface | "Which ruleset is active for this room?" |
| `ModeRules` | record | "What numbers and policies does this mode use?" |
| `PhaseId` | enum | "Which phase is the match in right now?" (wire value of `room_update.phase`) |
| `TurnOutcome` | sealed interface | "What happened on the last turn?" |
| `GameEvent` | sealed interface | "What did a player just ask the server to do?" |
| `Phase` | interface | "How does phase X advance when it receives an event?" |
| `Turn` | interface | "How does the current in-flight unit of play resolve?" |
| `GameModeRegistry` | Spring bean | "Given a `rulesetId`, hand me the right `GameMode`." |
| `RealtimeBridge` / `MatchClock` | ports | Outbound emit / timer scheduling, injectable for tests. |

### Turn outcomes (the three situations you asked about)

Every decisive moment in a match resolves to one of these variants:

```java
TurnOutcome.Expanding(actorUid, regionId, hpDelta)   // attacker took region / player placed castle
TurnOutcome.FailedAttack(attackerUid, defenderUid)   // attack bounced, defender stands
TurnOutcome.TieBreaking(reason, strategyId)          // tie → triggers the tie-break sub-flow
TurnOutcome.Placed(actorUid, regionId)               // non-contested placement (future use)
TurnOutcome.Ranked(orderedUids)                      // multi-actor round ranked by closeness
TurnOutcome.NoOp()                                   // event accepted, no phase change
```

On the wire the client still sees exactly what it sees today:

| Outcome | Wire signal the client receives |
|---------|---------------------------------|
| `Expanding` (attack) | `duel_resolved` with `attackerWins: true`, followed by `room_update` with updated `mapState` / `hp` |
| `FailedAttack` | `duel_resolved` with `attackerWins: false`, `room_update` |
| `TieBreaking` | `battle_tiebreaker_start` (numeric) or `tiebreaker_xo_start` (X-O), `room_update` with `activeDuel.tiebreakKind` set |
| `Placed` | `room_update` with the new castle region |
| `Ranked` | `room_update` with updated `claimQueue` / `claimTurnUid` / `claimPicksLeftByUid` |
| `NoOp` | Usually a `room_update` with no phase change |

So the existing client translation logic does not need to branch on outcome type.

### Game events (inbound, normalised)

Every raw socket event is mapped to a `GameEvent` variant before reaching phase
logic:

| Socket event | GameEvent variant |
|---------------|-------------------|
| `place_castle` | `PlaceCastle(uid, regionId)` |
| `claim_region` | `ClaimRegion(uid, regionId)` |
| `attack` | `Attack(uid, targetRegionId)` |
| `submit_answer` | `SubmitAnswer(uid, choice, latencyMs)` |
| `submit_estimation` | `SubmitEstimation(uid, value, latencyMs)` |
| `tiebreaker_xo_move` | `XoMove(uid, cellIndex)` |
| `use_powerup` | `UsePowerup(uid, powerupId, regionId?)` |
| internal timer expiry | `TimerFired(timerId)` |
| client disconnect | `PlayerDisconnected(uid)` |

No new event names on the wire. No payload changes.

### Mode rules = numbers and policies a mode can tune

`ModeRules` is what a new mode overrides without writing any new phases:

```java
record ModeRules(
    TeamPolicy teamPolicy,       // NONE (ffa) or TEAMS_2V2
    int minPlayersToStart,       // 2 for FFA, 4 for teams_2v2
    int maxPlayers,
    int maxRounds,
    int initialCastleHp,
    int claimFirstPicks,
    int claimSecondPicks,
    int duelDurationMs,
    int claimDurationMs,
    int tiebreakDurationMs,
    String tieBreakerMode,       // numeric_closest | mcq_retry | attacker_advantage | minigame_xo
    int maxMcqTieRetries,
    int xoDrawMaxReplay)
```

The default mode (`sok_v1`) reads these values live from the admin-editable
`GameRuntimeConfig`, so runtime reloads keep working the same way.

`TeamPolicy` decides friendly-fire and the minimum player count. In FFA
(`TeamPolicy.NONE`), `teamId` is always `null` on every `player` in `room_update`. In
`TEAMS_2V2`, players 0–1 belong to team `"A"` and 2–3 to team `"B"`, and any attack
emitted against a teammate returns `attack_invalid` with reason `ally_territory` —
exactly like today.

### Phases

The phase sequence is unchanged:

```
waiting
  → castle_placement
    → claiming_question (all players answer numeric simultaneously)
      → claiming_pick   (ranked player(s) pick regions)
        → battle
          ├── duel               (MCQ attack vs defender)
          └── battle_tiebreaker  (numeric closest / MCQ retry / X-O minigame)
        → ended
```

The strings above are exactly what `room_update.phase` carries, and the client's
phase-to-screen mapping still applies verbatim.

## How "crafting a game mode" works on the backend

A game mode is a Spring `@Component` implementing `GameMode`:

```java
@Component
class RushMode implements GameMode {
    @Override public String id() { return "sok_v2_rush"; }
    @Override public String displayName() { return "Rush"; }
    @Override public ModeRules rules() {
        return new ModeRules(
            TeamPolicy.NONE, 2, 6, 8,      // max 8 rounds
            2,                             // fragile castles
            1, 1,
            6000, 10000, 6000,             // fast timers
            "attacker_advantage", 0, 0);
    }
    // Optional: @Override public Phase phaseFor(PhaseId id) { ... }
}
```

`GameModeRegistry` auto-picks it up. A room with `rulesetId = "sok_v2_rush"` then
runs with Rush's numbers. No edit to `SocketGateway`.

When a mode needs to change *how* a phase works (not just its numbers), it supplies a
`Phase` bean via `phaseFor(PhaseId)`. Until that ship-tray fills up, modes only need
to override `rules()`.

### What triggers mode selection

1. The first player in an empty room may send `rulesetId` (and optionally
   `matchMode`, `mapId`) on `join_matchmaking`.
2. If omitted, the room defaults to `GameRuntimeConfig.defaultRulesetId`
   (currently `sok_v1`).
3. Every `room_update` echoes `rulesetId`, so late joiners always know which mode
   they're in.
4. If the string doesn't resolve to a registered `GameMode`, the registry logs a
   warning and falls back to `sok_v1`. The room still runs.

## Impact on the frontend — practical checklist

Nothing is **required**. If you want to leverage the engine, here's the menu:

1. **Surface the active mode in UI.** Read `matchState.rulesetId` (already exposed
   via `SOKMatchState`) and display a small badge or tutorial overlay when it is not
   `sok_v1`. See
   [`src/services/backend/sokAdapter.ts`](../../marefa-game/src/services/backend/sokAdapter.ts)
   for the existing mapping hook.
2. **Per-mode copy / help text.** Keep a small `rulesetId → i18n key` map on the
   client; fall back to the default when the id is unknown so unknown modes keep
   rendering.
3. **Don't hardcode player counts or timers.** If you need them, read from
   `activeDuel`, `phaseTimer` and player list — the backend sets these from
   `ModeRules` already. We will not send you a separate `modeRules` object on the
   wire (ask if you need one; we can add it).
4. **Keep your existing turn-resolution logic** (section 5 of
   [`CLIENT_AND_NEW_GAME_GUIDE.md`](./CLIENT_AND_NEW_GAME_GUIDE.md)). It's still
   correct.

## What did **not** change

- Event names, payload shapes, order of operations.
- Tie-break behaviour (strategies in `com.sok.backend.domain.game.tiebreaker/` are
  still the extension point for new minigames).
- `GameRuntimeConfig` fields — admin-editable runtime config keeps the same keys.
- The `SocketGateway` public contract (every event handler still exists with the
  same arguments).
- Team assignment rules for `teams_2v2`.

## Roadmap (not implemented yet)

These are the **next** steps of the refactor. They do not affect the client when
they land, but the MD documents them so there's no surprise:

1. **Per-phase `Phase` beans.** The four big private methods in `SocketGateway`
   (`startCastlePlacementPhase`, `startClaimingQuestionRound`, `startBattlePhase`,
   `startDuel`) will move behind `Phase` implementations one at a time.
2. **`MatchEngine` coordinator.** Once phases are beans, `SocketGateway` will
   forward every raw socket event as a `GameEvent` to `MatchEngine.handle(roomId,
   event)` and become a thin transport adapter. Wire payloads stay identical.
3. **Per-mode overlays on `GameRuntimeConfig`.** Added only when a second real mode
   demands different knob defaults; YAGNI until then.

## File reference

Backend (new, this refactor):

- [`domain/game/engine/PhaseId.java`](../src/main/java/com/sok/backend/domain/game/engine/PhaseId.java)
- [`domain/game/engine/TurnOutcome.java`](../src/main/java/com/sok/backend/domain/game/engine/TurnOutcome.java)
- [`domain/game/engine/GameEvent.java`](../src/main/java/com/sok/backend/domain/game/engine/GameEvent.java)
- [`domain/game/engine/GameMode.java`](../src/main/java/com/sok/backend/domain/game/engine/GameMode.java)
- [`domain/game/engine/ModeRules.java`](../src/main/java/com/sok/backend/domain/game/engine/ModeRules.java)
- [`domain/game/engine/TeamPolicy.java`](../src/main/java/com/sok/backend/domain/game/engine/TeamPolicy.java)
- [`domain/game/engine/Phase.java`](../src/main/java/com/sok/backend/domain/game/engine/Phase.java)
- [`domain/game/engine/Turn.java`](../src/main/java/com/sok/backend/domain/game/engine/Turn.java)
- [`domain/game/engine/MatchContext.java`](../src/main/java/com/sok/backend/domain/game/engine/MatchContext.java)
- [`domain/game/engine/MatchClock.java`](../src/main/java/com/sok/backend/domain/game/engine/MatchClock.java)
- [`domain/game/engine/RealtimeBridge.java`](../src/main/java/com/sok/backend/domain/game/engine/RealtimeBridge.java)
- [`domain/game/engine/GameModeRegistry.java`](../src/main/java/com/sok/backend/domain/game/engine/GameModeRegistry.java)
- [`domain/game/engine/DefaultGameMode.java`](../src/main/java/com/sok/backend/domain/game/engine/DefaultGameMode.java)

Backend (moved out of `SocketGateway`):

- [`realtime/match/RoomState.java`](../src/main/java/com/sok/backend/realtime/match/RoomState.java)
- [`realtime/match/PlayerState.java`](../src/main/java/com/sok/backend/realtime/match/PlayerState.java)
- [`realtime/match/RegionState.java`](../src/main/java/com/sok/backend/realtime/match/RegionState.java)

Legacy references (still applicable):

- [`CLIENT_AND_NEW_GAME_GUIDE.md`](./CLIENT_AND_NEW_GAME_GUIDE.md) — `room_update`
  payload dictionary, turn resolution table. Section 3a (`branch on rulesetId inside
  SocketGateway`) is **superseded** by this doc: prefer adding a `GameMode` bean.
- [`SOCKET_PROTOCOL.md`](./SOCKET_PROTOCOL.md) — event-by-event wire spec.
- [`GAME_FLOW.md`](./GAME_FLOW.md) — phase narrative.
- [`AVOID_BOMBS_TIEBREAK.md`](./AVOID_BOMBS_TIEBREAK.md) — avoid-bombs minigame (`minigame_avoid_bombs`, `tiebreaker_avoid_bombs_*`).
