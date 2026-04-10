# Session authorization policy draft

This note documents the current `/api/sessions/**` authorization behavior and the target policy used by the session authorization refactor.

## Target policy

| Policy | Endpoints | Allowed access |
| --- | --- | --- |
| Public | `GET /api/sessions/{code}`, `GET /api/sessions/{code}/state` | No login or session token required. |
| Login required | `POST /api/sessions`, `POST /api/sessions/{code}/join` | Authenticated web user. Request `gmId` or `playerId` must match the authenticated username. |
| `SESSION_READABLE` | `recent-results`, `run`, `inventory`, `results`, `choices`, `events`, `logs` | Valid `X-GM-Token`, valid `X-Player-Token`, or authenticated user who is the session GM or a session participant. |
| `PLAYER_SELF` | `forget`, `deck`, `loadout`, `loadout/from-preset`, `ready`, `leave`, player command | Valid `X-Player-Token`; path/body `playerId` must match the token owner when a player id is present. |
| `GM_ONLY` | `kick`, `reset`, `delete`, GM command, `START_COMBAT` | Valid `X-GM-Token`. |

Status code rule:

- `401`: required token/authentication is missing or invalid.
- `403`: authentication is valid, but the authenticated user/token is not allowed for that session or player.

## Current behavior snapshot

### Security filter layer

- `POST /api/sessions` and `POST /api/sessions/{code}/join` require login in `SecurityConfig` via `SecurityPaths.SESSION_LOGIN_REQUIRED`.
- `GET /api/sessions/{code}` and `GET /api/sessions/{code}/state` are public through `SecurityPaths.SESSION_PUBLIC`.
- Read endpoints such as `events`, `logs`, `recent-results`, `run`, `inventory`, `results`, and `choices` are `permitAll` at the filter layer, then checked inside controller/service code.
- `command`, `leave`, `reset`, `delete`, `kick`, `deck`, `loadout`, `loadout/from-preset`, `forget`, and `ready` are `permitAll` at the filter layer, then checked inside controller/service policy code.

### Controller/service layer

- `SessionController.create` and `SessionController.join` require an authenticated username and reject mismatched `gmId`/`playerId` with `403`.
- `SessionController.state` has no internal authorization check.
- `SessionAccessResolver.requireReadable` allows a valid GM token, valid player token, authenticated session GM, or authenticated participant for `recent-results`, `run`, `inventory`, `results`, `choices`, `events`, and `logs`.
- Player self endpoints use `SessionAccessResolver.requirePlayerSelf`; missing or invalid player token returns `401`, and path/request `playerId` mismatch returns `403`.
- GM endpoints use `SessionAccessResolver.requireGm`; missing or invalid GM token returns `401`.
- `SessionController.command` uses `SessionCommandType`:
  - `SessionCommandAuth.PLAYER` commands require a valid player token and matching request `playerId`.
  - `SessionCommandAuth.GM` commands require a valid GM token.
  - `START_COMBAT` is GM-authorized and also requires a `playerId` for the command payload.
  - `SessionCommandAuthorization` owns the command authorization decision.

## Refactor status

| Area | Current | Target |
| --- | --- | --- |
| `GET /api/sessions/{code}/state` | Listed in `SecurityPaths.SESSION_PUBLIC`. | Public. |
| `PUT /ready` | Listed in `SecurityPaths.SESSION_PLAYER_SELF_PUT`. | `PLAYER_SELF` with valid player token and matching path player id. |
| `SESSION_READABLE` status codes | Centralized in `SessionAccessResolver.requireReadable`. | Consistent `401` for missing/invalid credentials, `403` for authenticated non-participants. |
| Read authorization helpers | Shared by `SessionController` and `SessionLogService`. | Single reusable authorization component. |
| GM helper naming | `SessionAccessResolver.requireGm`. | Generic GM-only helper. |
| Player self helper | `SessionAccessResolver.requirePlayerSelf`. | Single self-action helper. |
| Security path ownership | `SecurityPaths` mirrors public/login/readable/player-self/GM-only groups. | Explicit policy groups at the filter layer. |

## Implemented refactor shape

- `SessionAccessResolver` centralizes:
  - `authenticatedUsername(Authentication)` for optional login identity lookup.
  - `requireReadable(SessionRuntime, gmToken, playerToken, Authentication)` for `SESSION_READABLE`.
  - `requirePlayerSelf(SessionRuntime, playerToken, pathPlayerId, mismatchMessage)` for player-owned endpoints.
  - `requirePlayerToken(SessionRuntime, playerToken)` for player-token actions without a path player id, such as `leave`.
  - `requireGm(SessionRuntime, gmToken)` for GM-only endpoints and GM commands.
  - `401` vs `403` decisions for token/login policy failures.
- `SessionCommandAuthorization` owns `/api/sessions/{code}/command` authorization using `SessionCommandType` and `SessionCommandAuth`.
- Keep controller methods thin: fetch session, call the policy method, then call service/domain logic.
- Event/log read checks use the same helper as `recent-results`, `run`, `inventory`, `results`, and `choices`.
- `SecurityPaths` names match policy groups:
  - `SESSION_PUBLIC`
  - `SESSION_LOGIN_REQUIRED`
  - `SESSION_READABLE`
  - `SESSION_PLAYER_SELF_POST`
  - `SESSION_PLAYER_SELF_PUT`
  - `SESSION_GM_ONLY_POST`
  - `SESSION_GM_ONLY_DELETE`
- Focused integration tests cover:
  - `/state` anonymous access.
  - `/ready` with valid player token and no login.
  - `SESSION_READABLE` with invalid token, no auth, authenticated non-participant, GM login, participant login, GM token, player token.
  - `GM_ONLY` rejects player token and accepts GM token.
  - Player command rejects mismatched request `playerId`.
