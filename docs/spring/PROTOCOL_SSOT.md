# Spring Protocol: Single Source of Truth (SSOT)

This document is the **authoritative source** for all events sent between the Spring Boot backend and the client. It defines the strict order, triggers, and payload structures for every game phase.

## Phase Timeline & Event Order

### Stage 1: Entry & Lobby
| Trigger | Client Event (C2S) | Server Event (S2C) | Phase / Notes |
| :--- | :--- | :--- | :--- |
| User joins | `join_matchmaking` | `room_update` | `phase: waiting` |
| Player count < min | — | — | Wait for players |
| Host starts (Private) | `start_match` | — | Validates min players |
| Auto/Host Start | — | `room_update` | **Auto-Castle Placement** happens internally. Transitions to `claiming_question` |

### Stage 2: The Claiming War
| Trigger | Client Event (C2S) | Server Event (S2C) | Phase / Notes |
| :--- | :--- | :--- | :--- |
| Start Claim Phase | — | `estimation_question` | `phase: claiming_question` (18s) |
| User submits answer | `submit_estimation` | — | — |
| Resolution / Timeout | — | **`answer_event`** | Reveal ranking/animation (4s + 2s margin) |
| Transition to Pick | — | `claim_rankings` | `phase: claiming_pick` |
| User picks region | `claim_region` | `room_update` | Validates adjacency |
| Turn Timeout | — | `room_update` | **Auto-Claim** (Prioritizes adjacency) |
| Queue Empty / Round | — | — | Transition back to `claiming_question` (2s delay) |
| All Regions Owned | — | `phase_changed` | Transition to `battle` |

### Stage 3: Main Combat Engine
| Trigger | Client Event (C2S) | Server Event (S2C) | Phase / Notes |
| :--- | :--- | :--- | :--- |
| Start Turn | — | `room_update` | `phase: battle`. Current player's turn |
| User attacks | `attack` | `duel_start` | `phase: duel` (10s) |
| User submits answer | `submit_answer` | — | MCQ Duel |
| Resolution / Timeout | — | `duel_resolved` | — |
| Perfect MCQ Tie | — | `battle_tiebreaker_start` | `phase: battle_tiebreaker` (10s). Numeric estimation |
| User submits tiebreak | `submit_estimation` | — | — |
| Resolution / Timeout | — | `duel_resolved` | Final duel outcome |
| Turn continues | — | `room_update` | Next turn index |

### Stage 4: Settlement
| Trigger | Client Event (C2S) | Server Event (S2C) | Phase / Notes |
| :--- | :--- | :--- | :--- |
| Last Warrior Standing | — | `game_ended` | `phase: ended`. Final Rankings. |
| Victory Margin (5s) | — | **`achievement_event`** | Closure metadata |
| Victory Margin (5s) | — | **`leave_room`** | Signal to exit |
| Server Grace (60s) | — | — | Room destruction |

---

## Detailed Event Payloads

### S2C: `room_update`
The canonical state. Every client should render the game board based on this.
- `id`: Room ID
- `phase`: Current phase string
- `round`: Current round number
- `players`: List of `PlayerState` objects
- `mapState`: List of `RegionState` objects
- `scoreByUid`: Map of UID to score
- `activeDuel`: Present during `duel` or `battle_tiebreaker`

### S2C: `estimation_question` / `battle_tiebreaker_start`
- `id`: Question ID
- `text`: Question text
- `serverNowMs`: Current server time
- `phaseEndsAt`: Absolute timestamp for timeout
- `durationMs`: Total duration allowed

### S2C: `answer_event`
- `rankings`: Sorted list of results (`uid`, `rank`, `value`, `delta`, `latencyMs`)
- `correctAnswer`: The true value
- `claimPicks`: Map of UID to allowed picks

### S2C: `duel_start`
- `question`: MCQ question payload (text, options)
- `attackerUid`: UID of attacker
- `defenderUid`: UID of defender (or "neutral")
- `targetHexId`: Target region ID
- `phaseEndsAt`: Absolute timestamp for timeout

### S2C: `duel_resolved`
- `result`: Summary of outcome (`attackerWins`, `winnerUid`, `correctIndex`)
- `room`: (Optional) Updated room snapshot

### S2C: `leave_room`
- `roomId`: The room being closed
- `reason`: Why the room is closing (e.g., "match_finished")

---

## Timing Configuration (Defaults)
| Parameter | Value | Description |
| :--- | :--- | :--- |
| `estimationTurnMs` | 18,000ms | Claiming question duration |
| `answerAnimationMs` | 4,000ms | Displaying `answer_event` |
| `rankingSlideInMs` | 2,000ms | Buffer before picks start or next round |
| `duelMcqMs` | 10,000ms | MCQ Duel duration |
| `tiebreakNumericMs` | 10,000ms | Tie-break question duration |
| `victoryCinematicMs` | 5,000ms | Delay before room closure events |
| `reconnectGraceSeconds`| 60s | Time before room data is purged |
