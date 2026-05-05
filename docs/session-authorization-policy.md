# Session Authorization Policy

This note documents the current `/api/sessions/**` authorization behavior implemented in code.

## Policy Groups

| Policy | Endpoints | Allowed access |
| --- | --- | --- |
| `PUBLIC_SESSION_STATE` | `GET /api/sessions/{code}`, `GET /api/sessions/{code}/state` | Public. No login or session token required. |
| `AUTHENTICATED_SESSION_ENTRY` | `POST /api/sessions`, `POST /api/sessions/{code}/join` | Authenticated web user. Request `gmId` or `playerId` must match the authenticated username. |
| `SESSION_READABLE` | `GET /api/sessions/{code}/recent-results`, `GET /api/sessions/{code}/run`, `GET /api/sessions/{code}/inventory`, `GET /api/sessions/{code}/results`, `GET /api/sessions/{code}/choices`, `GET /api/sessions/{code}/events`, `GET /api/sessions/{code}/logs` | Valid `X-GM-Token`, valid `X-Player-Token`, or login fallback when both token headers are absent and the authenticated user is the session GM or a session participant. |
| `PLAYER_SELF` | `POST /api/sessions/{code}/leave`, `POST /api/sessions/{code}/players/{playerId}/forget`, `POST /api/sessions/{code}/players/{playerId}/deck`, `POST /api/sessions/{code}/players/{playerId}/loadout`, `PUT /api/sessions/{code}/players/{playerId}/ready`, player-owned commands | Valid `X-Player-Token`. When a path/body `playerId` exists, it must match the token owner. |
| `GM_ONLY` | `POST /api/sessions/{code}/players/{playerId}/kick`, `POST /api/sessions/{code}/reset`, `DELETE /api/sessions/{code}`, GM commands, `START_COMBAT` | Valid `X-GM-Token`. |

## Status Codes

- `401`: required token/authentication is missing or invalid.
- `403`: authentication is valid, but the authenticated user is not allowed for that session or player.

## SESSION_READABLE Rules

The `SESSION_READABLE` decision is centralized in `SessionAccessResolver.requireSessionReadable(...)`.

1. Valid `X-GM-Token` allows the request.
2. Valid `X-Player-Token` allows the request.
3. Login fallback is allowed only when both token headers are absent.
4. Authenticated but unrelated users receive `403`.
5. No token and no authenticated user receives `401`.
6. A present but invalid token header blocks login fallback and returns `401`.

Header presence rule:

- `null` or blank after `trim()` is treated as no token header.
- Non-blank after `trim()` is treated as a present token header.

Mixed token rule:

- A valid token is enough to allow read access even if the other token header is present and invalid.
- Example: valid `X-GM-Token` + invalid `X-Player-Token` => allowed.
- Example: valid `X-Player-Token` + invalid `X-GM-Token` => allowed.
- Invalid-token blocking applies only when no valid GM or player token succeeds.

## Filter Layer Mapping

- `SecurityPaths.PUBLIC_SESSION_STATE` is public at the Spring Security filter layer.
- `SecurityPaths.AUTHENTICATED_SESSION_ENTRY` requires login at the filter layer.
- `SecurityPaths.SESSION_READABLE`, `SecurityPaths.PLAYER_SELF_POST`, `SecurityPaths.PLAYER_SELF_PUT`, `SecurityPaths.GM_ONLY_POST`, `SecurityPaths.GM_ONLY_DELETE`, and `SecurityPaths.SESSION_COMMAND` are `permitAll` at the filter layer and are enforced inside controller/service authorization code.

## Screen API Mapping

- `GET /api/screens/sessions/{code}/player-lobby`, `GET /api/screens/sessions/{code}/gm-lobby`, and `GET /api/screens/sessions/{code}/combat` follow `SESSION_READABLE`.
- `POST /api/screens/sessions/{code}/gm-lobby/start-combat` is a GM-lobby screen action:
  it can execute with a valid `X-GM-Token`, or with authenticated GM login fallback when the backend can restore GM access.
- For GmLobby specifically:
  participant summary curation, start blocked-state evaluation, and start-combat procedure belong to the server-side Screen API / screen action layer, not the frontend page.
- For Combat specifically:
  card/status/sidebar/action metadata belong to the server-side Screen API, while
  command execution and pending-decision resolution belong to the combat screen action layer.
  The frontend keeps only local selection state such as selected card/targets/discard ids and
  applies the latest returned combat screen after polling or action follow-up.

## Controller And Service Ownership

- `SessionController.create` and `SessionController.join` enforce `AUTHENTICATED_SESSION_ENTRY`.
- `SessionController.state` serves `PUBLIC_SESSION_STATE`.
- `SessionController` and `SessionLogService` share the `SESSION_READABLE` decision and emit read-access logs using `SessionAccessDecision`.
- `SessionAccessResolver.requirePlayerSelf(...)` enforces `PLAYER_SELF`.
- `SessionAccessResolver.requirePlayerToken(...)` is used for player-token-only actions without a path player id, such as `leave`.
- `SessionAccessResolver.requireGm(...)` enforces `GM_ONLY`.
- `SessionCommandAuthorization` enforces command authorization using `SessionCommandType` and `SessionCommandAuth`.

## Logging

- Read-access logs are emitted only for `SESSION_READABLE` endpoints.
- Logged fields are `code`, `endpoint`, `source`, `tokenBased`, `loginBased`, `username`, and `playerId`.
- Session token values are never logged.
