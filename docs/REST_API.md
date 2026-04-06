# HTTP REST API (`/api/*`)

- **Base:** Game server host (default `http://localhost:3000`). The Vite dev app (`apps/web`) proxies `/api` to that host when `VITE_API_BASE_URL` is unset.
- **JSON:** `POST` / `PATCH` bodies use `application/json` where applicable.
- **Rate limit:** Approximately 120 requests per minute per IP on `/api/*` (see `apps/server/src/http/createApiApp.ts`).

## Auth (Firebase ID token)

Protected routes expect:

```http
Authorization: Bearer <Firebase ID token>
```

Verification uses Firebase Admin ([apps/server/src/http/firebaseAuth.ts](../apps/server/src/http/firebaseAuth.ts)).

## Routes

- **`GET /api/health`** — Returns `{ "status": "ok" }` (no auth).
- **`GET /api/profile`** — Returns user profile row as JSON or `404` (auth).
- **`POST /api/profile`** — Creates profile if missing with body `name`, `username`, `avatar` fields optional (auth).
- **`PATCH /api/profile`** — Partial update: `name`, `username`, `avatar`, `countryFlag` optional (auth).
- **`POST /api/iap/validate`** — Placeholder store validation response (no auth).
- **`GET /api/cms/questions-stats`** — Returns `{ count: number }` for active questions (no auth).
- **`POST /api/cms/report`** — Body `reporterUid`, optional `targetUid`, `reason`; stub queue (no auth).
- **`GET /api/clans`** — Returns `{ clans: [] }` placeholder (no auth).
- **`POST /api/shop/purchase`** — Body `itemId`, optional `costGold` (auth).

## CORS

Server enables CORS for origins listed in `CORS_ORIGIN` (comma-separated), or defaults to `http://localhost:5173` and `http://127.0.0.1:5173`, with `credentials: true`. See `apps/server/src/http/createApiApp.ts`.

## Environment

- **`DATABASE_URL`** — Postgres connection string (`server/pgData.ts`, `apps/server/scripts/*`).
- **`GOOGLE_APPLICATION_CREDENTIALS`** or `service-account.json` beside the server app for Firebase Admin.
- **`CORS_ORIGIN`** — Optional list of allowed browser origins.
