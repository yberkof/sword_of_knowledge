# Game Mode Engine — visual tour

Companion to [`GAME_MODE_ENGINE.md`](./GAME_MODE_ENGINE.md). This doc is heavy on
diagrams and light on prose. Read this first when onboarding; dive into the
reference doc when you need field names.

---

## 1. The big picture

```mermaid
flowchart LR
  subgraph client [Client  Next.js marefa-game]
    UI[UI]
  end

  subgraph transport [Transport  SocketGateway]
    Sock["Socket.IO :8081"]
    Auth[JWT auth]
    MM[Matchmaking]
    Bridge["RealtimeBridge impl"]
    Clock["MatchClock impl"]
  end

  subgraph engine [Engine domain/game/engine]
    Registry[GameModeRegistry]
    Mode[GameMode]
    Rules[ModeRules]
    Phase[Phase]
    Turn[Turn]
  end

  subgraph state [State realtime/match]
    Room[RoomState]
    Players[PlayerState]
    Regions[RegionState]
    Duel[DuelState]
  end

  subgraph tb [Tie-breaker domain/game/tiebreaker]
    Composer[TieBreakerAttackPhaseComposer]
    Numeric[NumericClosestStrategy]
    Xo[XoMinigameStrategy]
  end

  UI <-->|"GameEvents (socket)"| Sock
  Sock --> Auth & MM
  Sock -->|"resolve rulesetId"| Registry
  Registry --> Mode --> Rules
  Mode -.optional.-> Phase --> Turn
  Sock <-->|"read/write"| Room
  Room --> Players & Regions & Duel
  Sock --> Composer
  Composer --> Numeric & Xo
  Phase -.when extracted.-> Bridge & Clock
```

Green path today: `Socket → Gateway → RoomState` with `GameModeRegistry` answering
"which rules?". Dotted lines are the wiring that lights up as each phase gets
extracted into a `Phase` bean.

---

## 2. Phase flow (unchanged from before)

```mermaid
stateDiagram-v2
  [*] --> waiting
  waiting --> castle_placement: enough players joined
  castle_placement --> claiming_question: all castles placed
  claiming_question --> claiming_pick: estimations collected + ranked
  claiming_pick --> claiming_question: queue empty, regions remain
  claiming_pick --> battle: all regions claimed
  battle --> duel: attack emitted
  duel --> battle: winner resolved
  duel --> battle_tiebreaker: speed tie
  battle_tiebreaker --> battle: tie resolved
  battle --> ended: last team standing or maxRounds
  ended --> [*]
```

These state names are exactly what the client reads on `room_update.phase`.

---

## 3. Per-phase turn resolution → outcome

```mermaid
flowchart LR
  subgraph cp [castle_placement]
    cp1[PlaceCastle event] --> cp2["TurnOutcome.Placed"]
  end

  subgraph cq [claiming_question]
    cq1["all SubmitEstimation events"] --> cq2["TurnOutcome.Ranked"]
  end

  subgraph cpick [claiming_pick]
    p1[ClaimRegion event] --> p2["TurnOutcome.Expanding"]
  end

  subgraph b [battle: duel]
    b1["both SubmitAnswer events"] --> b2{Resolve MCQ}
    b2 -->|attacker wins| b3["TurnOutcome.Expanding"]
    b2 -->|defender wins| b4["TurnOutcome.FailedAttack"]
    b2 -->|speed tie| b5["TurnOutcome.TieBreaking"]
  end

  subgraph tb [battle_tiebreaker]
    t1["SubmitEstimation or XoMove"] --> t2{Resolve by strategy}
    t2 -->|attacker prevails| t3["TurnOutcome.Expanding"]
    t2 -->|defender prevails| t4["TurnOutcome.FailedAttack"]
  end
```

Your three first-class outcomes (`Expanding`, `FailedAttack`, `TieBreaking`) sit in
the attack lane. `Placed` and `Ranked` keep the other phases honest instead of
forcing them into the attack vocabulary.

---

## 4. Type relationships

```mermaid
classDiagram
  class GameMode {
    <<interface>>
    +String id()
    +ModeRules rules()
    +List~PhaseId~ phaseOrder()
    +Phase phaseFor(PhaseId)
  }

  class ModeRules {
    <<record>>
    +TeamPolicy teamPolicy
    +int minPlayersToStart
    +int maxRounds
    +int initialCastleHp
    +int claimFirstPicks
    +int claimSecondPicks
    +int duelDurationMs
    +String tieBreakerMode
    +boolean isFriendlyFire(a, b)
  }

  class TeamPolicy {
    <<enum>>
    NONE
    TEAMS_2V2
  }

  class Phase {
    <<interface>>
    +PhaseId id()
    +enter(MatchContext)
    +TurnOutcome handle(ctx, GameEvent)
    +exit(MatchContext)
  }

  class Turn {
    <<interface>>
    +String id()
    +boolean isReady()
    +TurnOutcome accept(ctx, GameEvent)
  }

  class TurnOutcome {
    <<sealed>>
  }

  class GameEvent {
    <<sealed>>
  }

  class MatchContext {
    <<record>>
    +RoomState room
    +GameMode mode
    +RealtimeBridge bridge
    +MatchClock clock
  }

  class GameModeRegistry {
    +GameMode resolve(rulesetId)
    +boolean has(rulesetId)
  }

  class DefaultGameMode {
    id = "sok_v1"
    +ModeRules rulesFor(matchMode)
  }

  GameMode <|.. DefaultGameMode
  GameMode --> ModeRules
  ModeRules --> TeamPolicy
  GameMode --> Phase
  Phase --> Turn
  Phase --> TurnOutcome
  Turn --> TurnOutcome
  Phase --> GameEvent
  Turn --> GameEvent
  GameModeRegistry --> GameMode
  MatchContext --> GameMode
```

---

## 5. Attack sequence (where the three outcomes live)

```mermaid
sequenceDiagram
  autonumber
  actor A as Attacker
  actor D as Defender
  participant G as SocketGateway
  participant R as GameModeRegistry
  participant B as BattlePhaseService
  participant Tb as TieBreakerComposer

  A->>G: attack { targetHexId }
  G->>R: resolve(room.rulesetId)
  R-->>G: GameMode (sok_v1)
  G->>G: sameTeam? via ModeRules.isFriendlyFire
  G-->>A: attack_invalid (ally_territory) if same team
  G->>A: duel_start (MCQ)
  G->>D: duel_start (MCQ)
  A->>G: submit_answer
  D->>G: submit_answer
  G->>B: resolveMcqAsOutcome(...)
  alt attacker correct, defender wrong or slower
    B-->>G: TurnOutcome.Expanding(attacker, region, hp-1)
    G->>G: transfer region, damage HP
    G-->>A: duel_resolved attackerWins=true
    G-->>D: duel_resolved attackerWins=true
  else defender correct and attacker wrong or slower
    B-->>G: TurnOutcome.FailedAttack(attacker, defender)
    G-->>A: duel_resolved attackerWins=false
    G-->>D: duel_resolved attackerWins=false
  else both correct, same latency
    B-->>G: TurnOutcome.TieBreaking("mcq_speed_tie", strategyId)
    G->>Tb: composer.resolve(mode, defender)
    Tb-->>G: NumericClosest or Xo strategy
    G->>A: battle_tiebreaker_start
    G->>D: battle_tiebreaker_start
  end
```

---

## 6. Mode selection on join

```mermaid
sequenceDiagram
  autonumber
  actor P1 as First player
  participant G as SocketGateway
  participant Reg as GameModeRegistry
  participant Cfg as RuntimeGameConfigService

  P1->>G: join_matchmaking { rulesetId?: "sok_v2_rush" }
  alt first player in empty room
    G->>G: room.rulesetId = payload.rulesetId
  else subsequent joiners
    G->>G: ignore joiner's rulesetId (room already decided)
  end

  Note over G: later, any rules decision...
  G->>Reg: resolve(room.rulesetId)
  alt registry has mode
    Reg-->>G: GameMode
  else unknown id
    Reg->>Cfg: getDefaultRulesetId()
    Cfg-->>Reg: "sok_v1"
    Reg-->>G: DefaultGameMode (fallback + warn log)
  end
  G->>G: rules = mode.rules() (or rulesFor(matchMode))
  G->>G: apply rules.minPlayersToStart, rules.isFriendlyFire, ...
```

---

## 7. Adding a new mode (developer flow)

```mermaid
flowchart TD
  A["Decide: only numbers change?"] -->|yes| B1["new @Component implements GameMode"]
  A -->|"no, phase behaviour changes"| B2["also ship a Phase bean"]

  B1 --> C1["override id() and rules()"]
  B1 --> C2["Spring picks it up automatically"]
  C2 --> C3["client sends rulesetId on join_matchmaking"]
  C3 --> C4["room runs with new ModeRules"]

  B2 --> D1["implement Phase: id(), enter(ctx), handle(ctx, event)"]
  D1 --> D2["return TurnOutcome variants"]
  D2 --> D3["emit via ctx.bridge(), schedule via ctx.clock()"]
  D3 --> D4["mode's phaseFor(PhaseId) returns this bean"]

  C4 --> Done((live))
  D4 --> Done
```

No file in `SocketGateway` edited for either branch.

---

## 8. Wire protocol: what the client sees per outcome

```mermaid
flowchart LR
  subgraph outcomes [Backend outcome]
    O1[Expanding attack]
    O2[Expanding placement]
    O3[FailedAttack]
    O4[TieBreaking]
    O5[Ranked]
    O6[Placed]
  end

  subgraph wire [Wire events]
    W1[duel_resolved attackerWins=true]
    W2[room_update mapState update]
    W3[duel_resolved attackerWins=false]
    W4[battle_tiebreaker_start or tiebreaker_xo_start]
    W5["room_update claimQueue,claimTurnUid"]
    W6[room_update castle placed]
  end

  O1 --> W1 & W2
  O2 --> W6 & W2
  O3 --> W3 & W2
  O4 --> W4 & W2
  O5 --> W5 & W2
  O6 --> W6 & W2
```

No new event names on the wire. The client's existing handlers stay valid.

---

## 9. Boundary: what lives where

```mermaid
flowchart TB
  subgraph keep [Stays in SocketGateway forever]
    K1[Socket.IO connect / disconnect]
    K2[JWT auth]
    K3["Matchmaking: allocate room, invite codes"]
    K4[Socket room membership]
    K5[Metrics, snapshot publishing]
    K6["Event -> GameEvent normaliser"]
  end

  subgraph moved [Already moved out]
    M1[RoomState, PlayerState, RegionState]
    M2[ModeRules decisions min players, isFriendlyFire]
    M3[Outcome types for domain services]
  end

  subgraph roadmap [Moves out next]
    R1[Phase transition logic]
    R2[Turn rotation]
    R3[Duel lifecycle]
    R4["Timer scheduling (phases use MatchClock)"]
  end
```

---

## 10. File map

```mermaid
flowchart LR
  subgraph engine [domain/game/engine NEW]
    F1[PhaseId]
    F2[TurnOutcome]
    F3[GameEvent]
    F4[TeamPolicy]
    F5[ModeRules]
    F6[GameMode]
    F7[Phase]
    F8[Turn]
    F9[MatchContext]
    F10[MatchClock]
    F11[RealtimeBridge]
    F12[GameModeRegistry]
    F13[DefaultGameMode]
  end

  subgraph match [realtime/match MOVED]
    S1[RoomState]
    S2[PlayerState]
    S3[RegionState]
    S4[DuelState existing]
    S5[AnswerMetric existing]
    S6[DuelAnswer existing]
  end

  subgraph dom [domain/game ADAPTED]
    A1["BattlePhaseService +resolveMcqAsOutcome"]
    A2["ClaimingPhaseService +rankAsOutcome"]
    A3["CastlePlacementPhaseService +placementOutcome"]
    A4[ResolutionPhaseService unchanged]
  end

  subgraph tb [domain/game/tiebreaker existing extension point]
    T1[Composer]
    T2[Strategies]
    T3[RealtimeBridge port]
  end

  subgraph gate [realtime]
    G1["SocketGateway injects registry + defaultMode"]
  end

  F6 -.-> F13
  F6 -.-> F12
  G1 --> F12 & F13
  G1 --> S1
  A1 & A2 & A3 -.-> F2
  G1 --> A1 & A2 & A3
  G1 --> T1
```

---

## 11. Minimal example: the Rush mode in 20 lines

```java
@Component
public class RushMode implements GameMode {
  @Override public String id() { return "sok_v2_rush"; }

  @Override public String displayName() { return "Rush"; }

  @Override public ModeRules rules() {
    return new ModeRules(
        TeamPolicy.NONE,
        /* minPlayersToStart */ 2,
        /* maxPlayers        */ 6,
        /* maxRounds         */ 8,
        /* initialCastleHp   */ 2,
        /* claimFirstPicks   */ 1,
        /* claimSecondPicks  */ 1,
        /* duelDurationMs    */ 6000,
        /* claimDurationMs   */ 10000,
        /* tiebreakDurationMs*/ 6000,
        /* tieBreakerMode    */ "attacker_advantage",
        /* maxMcqTieRetries  */ 0,
        /* xoDrawMaxReplay   */ 0);
  }
}
```

Result:

1. Spring discovers the bean on startup.
2. `GameModeRegistry` logs `mode(s): [sok_v1, sok_v2_rush]`.
3. Any room with `rulesetId = "sok_v2_rush"` starts with two players, attackers
   always win ties, timers are shorter. Other modes still run unchanged.

No line of `SocketGateway` was modified.
