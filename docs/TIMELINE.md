# ⏳ Sword of Knowledge: Game Timeline

This document explains how the server manages time, animations, and turn-based interactions using the `GameTimelineService`.

## ⚙️ Configuration
Located in `GameRuntimeConfig.java`. These values ensure the server stays in sync with frontend animations.

| Property | Value | Role |
| :--- | :--- | :--- |
| `phaseAnimationBufferMs` | 3000ms | Pause between phases for "Round Start" UI. |
| `estimationTurnMs` | 15000ms | Window for all players to answer the numeric question. |
| `claimPickTurnMs` | 10000ms | Individual time per player to pick a hex. |
| `battleAttackTurnMs` | 20000ms | Individual time to select an adjacent target. |
| `duelMcqMs` | 10000ms | Time allowed to answer a battle question. |
| `minigameMoveTurnMs` | 15000ms | Time per move in tie-breakers (e.g., Avoid Bombs). |

---

## 🗺️ Game Flow Diagram

```mermaid
graph TD
    Start((Match Start)) --> CP[Castle Placement]
    CP --> B1[Buffer 3s]
    
    subgraph "Claiming Cycle"
    B1 --> Est[Estimation Question 15s]
    Est --> B2[Buffer 3s]
    B2 --> Pick[Pick Turn 10s]
    Pick -- "Timeout/Success" --> PickCheck{Queue Empty?}
    PickCheck -- No --> Pick
    PickCheck -- Yes --> HexCheck{Map Full?}
    HexCheck -- No --> Est
    end

    HexCheck -- Yes --> B3[Buffer 3s]

    subgraph "Battle Cycle"
    B3 --> Turn[Attack Turn 20s]
    Turn -- Attack --> Duel[Duel/Tiebreak]
    Duel --> B4[Buffer 3s]
    Turn -- Timeout --> Skip[Skip Turn]
    Skip --> B4
    B4 --> RoundCheck{Round End?}
    RoundCheck -- No --> Turn
    RoundCheck -- Yes --> Turn
    end

    RoundCheck -- "Win Condition" --> End((Game Over))
```

---

## ⏱️ Per-Turn Logic Deep Dive

Unlike the previous version which used "Phase Timers", the new system uses **Action-Specific Timers**.

### ⚔️ Battle Turn Sequence
This ensures that a single AFK player cannot stall the entire match.

```mermaid
sequenceDiagram
    participant P as Player
    participant S as Server
    participant T as TimelineService

    Note over S: Phase: BATTLE
    S->>P: room_update (Your Turn)
    S->>T: schedule battle_turn_timeout (20s)
    
    alt Player Attacks
        P->>S: attack(targetHexId)
        S->>T: cancel battle_turn_timeout
        S->>P: duel_start (10s)
    else Timeout
        T-->>S: fire timeout
        S->>S: advanceTurn()
        S->>P: room_update (Next Player)
        S->>T: schedule battle_turn_timeout (20s)
    end
```

### 💣 Tie-Breaker (Avoid Bombs)
Every move is now precision-timed to keep the minigame snappy.

1.  **Placement:** Both players have `avoidBombsPlacementMs`.
2.  **Move Timer:** Once opening begins, the active player has `minigameMoveTurnMs`.
3.  **Handoff:** On a successful "Open", the timer is cancelled and reset for the next player.

---

## 🏛️ Best Practices Applied
1.  **Centralization:** All timers go through `GameTimelineService`. No more `new Timer()` or `Thread.sleep` scattered in controllers.
2.  **Concurrency:** Every timer task is wrapped in `executors.submitToRoom`, ensuring that state changes only happen on the safe room-thread.
3.  **Safety:** `cancelTurnTimeout` is called aggressively before starting any new timer to prevent "Double Advancement" bugs.
