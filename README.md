# Sword of Knowledge (monorepo)

Workspace layout:

- **`apps/web`** — Vite + React client (`@sok/web`).
- **`apps/server`** — Express + Socket.IO game server (`@sok/server`).
- **`packages/shared`** — `@sok/shared` (e.g. rhythm coercion for client + server).
- **`packages/game-contract`** — `@sok/game-contract` (shared types + socket event name lists).
- **`docs/`** — API and UI integration docs for third-party clients.

## Local development

1. **Install:** `npm install` (repo root).
2. **Env:** copy `.env.example` to `.env` at repo root (`DATABASE_URL`, Firebase, optional `GEMINI_API_KEY` for web).
3. **Run both processes:** `npm run dev`  
   - Web: `http://localhost:5173` (proxies `/api` and `/socket.io` to port 3000).  
   - Server: `http://localhost:3000`.

## Production-oriented build

- **Build static web:** `npm run build` (outputs `apps/web/dist`).
- **Serve API + optional SPA:** set `NODE_ENV=production`, optionally `WEB_DIST_PATH` to the built `dist` folder, then `npm run start` (runs `@sok/server`).

## Docs

- [docs/GAME_FLOW.md](docs/GAME_FLOW.md) — phases and client↔server socket sequencing
- [docs/SOCKET_PROTOCOL.md](docs/SOCKET_PROTOCOL.md)
- [docs/REST_API.md](docs/REST_API.md)
- [docs/UI_COMPONENTS.md](docs/UI_COMPONENTS.md)
- [docs/NEW_CLIENT_CHECKLIST.md](docs/NEW_CLIENT_CHECKLIST.md)

## Database

From root: `npm run db:migrate`, `npm run db:seed`, etc. (execute in `apps/server` with migrations under `apps/server/migrations`).
