# 🛠 Phase 2: Client Integration Guide

This guide outlines the API changes and new endpoints introduced in Phase 2 to support the leveling system, match history, and AI session summaries.

---

## 1. Updated Profile Response
The `GET /api/profile` and `PATCH /api/profile` endpoints now return additional fields to support the XP progress bar and leveling UI.

**New Fields in `ProfileResponse`:**
| Field | Type | Description |
| :--- | :--- | :--- |
| `xpInLevel` | `int` | The amount of XP the player has earned *within* their current level. |
| `nextLevelXp` | `int` | The total XP required to reach the *next* level from the start of the current one. |

**Example UI Calculation:**
Progress Percentage = `(xpInLevel / nextLevelXp) * 100`

---

## 2. Match History API
A new endpoint is available to display the "Hall of Valor" (Match History).

- **Endpoint:** `GET /api/matches/history`
- **Query Params:** `limit` (default 10), `offset` (default 0)
- **Response Format:**
```json
[
  {
    "id": "room_123:1714856000",
    "map_id": "marefa_basic",
    "mode": "ffa",
    "end_time": "2026-05-04T14:30:00Z",
    "place": 1,
    "score": 1250,
    "xp_earned": 120,
    "trophies_delta": 20
  }
]
```

---

## 3. AI Session Summary
Use this endpoint to trigger the "Sage" character dialogue when the player logs in or visits their profile.

- **Endpoint:** `GET /api/profile/summary`
- **Response Format:**
```json
{
  "summary": "I saw your triumph in the Southern Hexes, young one. Your speed with the quill is matched only by your sharp mind..."
}
```
*Note: This is cached per day. The first call of the day triggers the AI generation; subsequent calls return the cached text.*

---

## 4. Enhanced Matchmaking
The `join_matchmaking` socket event logic remains the same, but the backend is now "Skill-Aware."

- **Client Action:** No change to the socket payload.
- **Visual Suggestion:** You may want to add a "Searching for worthy opponents..." message to the matchmaking UI, as the system now prioritizes players with similar trophy counts.
- **Expanding Search:** If a match isn't found within 5-10 seconds, the backend automatically widens the trophy range.

---

## 5. Smart Question Rotation
The backend now tracks `user_question_history` automatically. 

- **Impact:** Players will see significantly fewer repeat questions.
- **Client Integration:** None required. This is handled entirely during the `duel_start` and `estimation_question` phases on the server.

---

## 6. Play With Friends
We continue to use the **Invite Code** system. 

1. **Host:** Joins matchmaking with a `privateCode` (e.g., "GOLDEN-EAGLE").
2. **Friends:** Join using the same `privateCode`.
3. **Host:** Clicks "Start Match" once the lobby is full.

---

## 🚀 Summary of Changes for UI/UX
1. **Profile:** Add a progress bar using `xpInLevel` and `nextLevelXp`.
2. **Dashboard:** Add a "Match History" list using the new `/history` endpoint.
3. **Login/Profile:** Add a text bubble for **The Sage** using the `/summary` endpoint.
4. **Matchmaking:** Update status text to reflect skill-based matching.
