import { apiGet, apiPost, apiPut } from './client'
import type {
  ApplyPresetToSessionRequest,
  CommandRequest,
  CreateSessionRequest,
  CreateSessionResponse,
  EngineResponseDto,
  JoinSessionRequest,
  JoinSessionResponse,
  KickPlayerRequest,
  RecentResultsResponse,
  ResetSessionRequest,
  RestoreGmAccessResponse,
  SessionCode,
  SessionEventsQuery,
  SessionEventPageResponse,
  SessionIdentifier,
  SessionLogsQuery,
  SessionLogPageResponse,
  SessionPlayerId,
  SessionRequestAccess,
  SessionRunChoicesResponse,
  SessionRunInventoryResponse,
  SessionStateDto,
  SessionToken,
  UpdatePlayerReadyRequest,
  UpdateSessionLoadoutRequest,
  RunStateDto,
} from './sessionTypes'

const SESSIONS_API_BASE = '/api/sessions'

function normalizeSessionPathValue(value: string, fieldName: string) {
  const normalized = value.trim()

  if (!normalized) {
    throw new Error(`${fieldName} is required`)
  }

  return encodeURIComponent(normalized)
}

function getSessionResourcePath(code: SessionIdentifier) {
  return `${SESSIONS_API_BASE}/${normalizeSessionPathValue(String(code), 'session code')}`
}

function getSessionPlayerResourcePath(code: SessionIdentifier, playerId: SessionPlayerId) {
  return `${getSessionResourcePath(code)}/players/${normalizeSessionPathValue(playerId, 'player id')}`
}

function createTokenHeaders(tokenName: 'X-GM-Token' | 'X-Player-Token', token: SessionToken) {
  const normalizedToken = token.trim()

  if (!normalizedToken) {
    throw new Error(`${tokenName} is required`)
  }

  return {
    headers: {
      [tokenName]: normalizedToken,
    },
    handleUnauthorized: false as const,
  }
}

function withPlayerToken(playerToken: SessionToken) {
  return createTokenHeaders('X-Player-Token', playerToken)
}

function withGmToken(gmToken: SessionToken) {
  return createTokenHeaders('X-GM-Token', gmToken)
}

function withSessionAccess(access: SessionRequestAccess) {
  return access.role === 'gm' ? withGmToken(access.gmToken) : withPlayerToken(access.playerToken)
}

function buildSessionQueryPath(
  code: SessionIdentifier,
  suffix: string,
  params: Record<string, number | null | undefined>,
) {
  const query = new URLSearchParams()

  for (const [key, value] of Object.entries(params)) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      query.set(key, String(value))
    }
  }

  const basePath = `${getSessionResourcePath(code)}${suffix}`
  const queryString = query.toString()

  return queryString ? `${basePath}?${queryString}` : basePath
}

export function createSession(payload: CreateSessionRequest) {
  return apiPost<CreateSessionResponse, CreateSessionRequest>(SESSIONS_API_BASE, payload)
}

export function getSessionState(code: SessionCode) {
  return apiGet<SessionStateDto>(getSessionResourcePath(code), {
    handleUnauthorized: false,
  })
}

export function getSessionStateAlias(code: SessionCode) {
  return apiGet<SessionStateDto>(`${getSessionResourcePath(code)}/state`, {
    handleUnauthorized: false,
  })
}

export function joinSession(code: SessionCode, payload: JoinSessionRequest) {
  return apiPost<JoinSessionResponse, JoinSessionRequest>(
    `${getSessionResourcePath(code)}/join`,
    payload,
  )
}

export function restoreGmAccess(code: SessionCode) {
  return apiPost<RestoreGmAccessResponse, null>(
    `${getSessionResourcePath(code)}/gm-access/restore`,
    null,
    {
      handleUnauthorized: false,
    },
  )
}

export function updateSessionLoadout(
  code: SessionCode,
  playerId: SessionPlayerId,
  payload: UpdateSessionLoadoutRequest,
  playerToken: SessionToken,
) {
  return apiPost<SessionStateDto, UpdateSessionLoadoutRequest>(
    `${getSessionPlayerResourcePath(code, playerId)}/loadout`,
    payload,
    withPlayerToken(playerToken),
  )
}

export function applyPresetToSession(
  code: SessionCode,
  playerId: SessionPlayerId,
  payload: ApplyPresetToSessionRequest,
  playerToken: SessionToken,
) {
  return apiPost<SessionStateDto, ApplyPresetToSessionRequest>(
    `${getSessionPlayerResourcePath(code, playerId)}/loadout/from-preset`,
    payload,
    withPlayerToken(playerToken),
  )
}

export function updatePlayerReady(
  code: SessionCode,
  playerId: SessionPlayerId,
  payload: UpdatePlayerReadyRequest,
  playerToken: SessionToken,
) {
  return apiPut<SessionStateDto, UpdatePlayerReadyRequest>(
    `${getSessionPlayerResourcePath(code, playerId)}/ready`,
    payload,
    withPlayerToken(playerToken),
  )
}

export function leaveSession(code: SessionCode, playerToken: SessionToken) {
  return apiPost<SessionStateDto, null>(
    `${getSessionResourcePath(code)}/leave`,
    null,
    withPlayerToken(playerToken),
  )
}

export function kickPlayer(
  code: SessionCode,
  playerId: SessionPlayerId,
  payload: KickPlayerRequest,
  gmToken: SessionToken,
) {
  return apiPost<SessionStateDto, KickPlayerRequest>(
    `${getSessionPlayerResourcePath(code, playerId)}/kick`,
    payload,
    withGmToken(gmToken),
  )
}

export function resetSession(
  code: SessionCode,
  payload: ResetSessionRequest,
  gmToken: SessionToken,
) {
  return apiPost<SessionStateDto, ResetSessionRequest>(
    `${getSessionResourcePath(code)}/reset`,
    payload,
    withGmToken(gmToken),
  )
}

export function executeSessionCommand(
  code: SessionCode,
  payload: CommandRequest,
  access: SessionRequestAccess,
) {
  return apiPost<EngineResponseDto, CommandRequest>(
    `${getSessionResourcePath(code)}/command`,
    payload,
    withSessionAccess(access),
  )
}

export function getSessionEvents(
  code: SessionCode,
  params: SessionEventsQuery,
  access: SessionRequestAccess,
) {
  return apiGet<SessionEventPageResponse>(
    buildSessionQueryPath(code, '/events', params),
    withSessionAccess(access),
  )
}

export function getSessionLogs(
  code: SessionCode,
  params: SessionLogsQuery,
  access: SessionRequestAccess,
) {
  return apiGet<SessionLogPageResponse>(
    buildSessionQueryPath(code, '/logs', params),
    withSessionAccess(access),
  )
}

export function getSessionResults(code: SessionCode, access: SessionRequestAccess) {
  return apiGet<RecentResultsResponse>(
    `${getSessionResourcePath(code)}/results`,
    withSessionAccess(access),
  )
}

export function getSessionRecentResults(code: SessionCode, access: SessionRequestAccess) {
  return apiGet<RecentResultsResponse>(
    `${getSessionResourcePath(code)}/recent-results`,
    withSessionAccess(access),
  )
}

export function getSessionRun(code: SessionCode, access: SessionRequestAccess) {
  return apiGet<RunStateDto>(
    `${getSessionResourcePath(code)}/run`,
    withSessionAccess(access),
  )
}

export function getSessionInventory(code: SessionCode, access: SessionRequestAccess) {
  return apiGet<SessionRunInventoryResponse>(
    `${getSessionResourcePath(code)}/inventory`,
    withSessionAccess(access),
  )
}

export function getSessionChoices(code: SessionCode, access: SessionRequestAccess) {
  return apiGet<SessionRunChoicesResponse>(
    `${getSessionResourcePath(code)}/choices`,
    withSessionAccess(access),
  )
}
