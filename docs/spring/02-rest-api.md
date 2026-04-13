# Spring REST API Contract

Version stamp: 2026-04-11

Base URL: `http://<host>:<PORT>` (default `8080`)

## Authentication
- Protected endpoints require Firebase bearer token.
- Missing token returns `401 {"error":"Unauthorized"}`.
- Invalid token returns `401 {"error":"Invalid token"}`.

## Public endpoints

### `GET /api/health`
- Response `200`: `{"status":"ok"}`

### `GET /api/cms/questions-stats`
- Response `200`: `{"count":-1}`

### `POST /api/cms/report`
- Request body fields:
  - `reporterUid` (required non-empty string)
  - `reason` (required non-empty string)
- Success `200`: `{"ok":true,"queued":true}`
- Validation failure `400`: `{"ok":false}`

### `GET /api/clans`
- Response `200`: `{"clans":[]}`

### `POST /api/iap/validate`
- Response `200`: `{"ok":true,"note":"..."}`

## Protected endpoints

### `GET /api/profile`
- Success `200`: profile object
- Not found `404`: `{"error":"Not found"}`

### `POST /api/profile`
- Creates profile if missing for authenticated uid.
- Success `201`: profile object

### `PATCH /api/profile`
- Current behavior `501`: `{"error":"Not found"}`

### `POST /api/shop/purchase`
- Request body:
  - `itemId` (required string)
- Success `200`: `{"ok":true,"itemId":"<id>"}`
- Failure `400`: `{"ok":false}`
- Price trusted from backend catalog only.

### `GET /api/admin/game-config`
- Admin-only endpoint (uid in `ADMIN_UIDS` list).
- Success `200`: runtime config JSON.
- Forbidden `403`: `{"error":"forbidden"}`

### `PUT /api/admin/game-config`
- Admin-only update endpoint.
- Body must match runtime config shape.
- Success `200`: updated runtime config.
- Invalid config `400` via `IllegalArgumentException`.

## Global errors
- `BadRequestException` -> `400` custom body.
- `ForbiddenException` -> `403 {"error":"..."}`
- fallback -> `500 {"error":"Server error"}`

## Unsupported or roadmap
- Pagination/filtering for CMS endpoints absent.
- Profile patch semantics not implemented.

## Last verified commands
- `mvn clean test` (from `apps/spring-backend`)
