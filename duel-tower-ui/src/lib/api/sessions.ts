import { apiGet, apiPost, apiPut } from './client'
import type {
  ApplyPresetToSessionRequest,
  CreateSessionRequest,
  CreateSessionResponse,
  JoinSessionRequest,
  JoinSessionResponse,
  KickPlayerRequest,
  ResetSessionRequest,
  SessionCode,
  SessionIdentifier,
  SessionPlayerId,
  SessionStateDto,
  SessionToken,
  UpdatePlayerReadyRequest,
  UpdateSessionLoadoutRequest,
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
