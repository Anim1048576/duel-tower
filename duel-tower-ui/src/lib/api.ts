import type {
  CardDef,
  CommandRequest,
  CreateSessionResponse,
  EngineResponse,
  ApiErrorShape,
  JoinSessionResponse,
  OwnedCard,
  PassiveDefinition,
  RecentResultsResponse,
  SessionSnapshot,
} from './model'

export type { CardDef, PassiveDefinition } from './model'

import { normalizeCardDef, normalizePassiveDefinition } from './model'
import { adaptEngineResponse, adaptSessionSnapshot } from './adapters/combatAdapter'

// Deck API models (DB-backed)
export type DeckType = 'PLAYER' | 'ENEMY'
export type DeckCardDto = { cardId: string; count: number }
export type DeckResponse = {
  id: number
  name: string
  type: DeckType
  totalCards: number
  cards: DeckCardDto[]
}
export type DeckCardSpec = { cardId: string; count?: number | null }
export type CreateDeckRequest = { name?: string | null; type: DeckType; cards?: DeckCardSpec[] | null }
export type UpdateDeckRequest = { name?: string | null; type?: DeckType | null; cards?: DeckCardSpec[] | null }
export type AddDeckCardsRequest = { cards?: DeckCardSpec[] | null }

// Character Profile API models (DB-backed)
export type CharacterGender = 'MALE' | 'FEMALE' | 'OTHER'
export type CombatStats = {
  maxHp: number
  maxAp: number
  attackPower: number
  healPower: number
}
export type CharacterProfileResponse = {
  id: number
  name: string
  gender: CharacterGender
  age: number | null
  wish: string
  disposition: string
  oneLiner: string
  story: string
  physical: number
  technique: number
  sense: number
  willpower: number
  trait1: string | null
  trait2: string | null
  ownedCards: string
  currentSkillDeck?: string[] | null
  exCard: string
  combatStats: CombatStats
  createDate: string
  updateDate: string
}
export type CharacterProfileRequest = {
  name: string
  gender: CharacterGender
  age: number | null
  wish: string
  disposition: string
  oneLiner: string
  story: string
  physical: number
  technique: number
  sense: number
  willpower: number
  trait1: string | null
  trait2: string | null
  ownedCards: string
  currentSkillDeck?: string[] | null
  exCard: string
}

export type AuthUserResponse = {
  username: string
  roles: string[]
}

export type SignupRequest = {
  username: string
  password: string
}

export type LoginRequest = {
  username: string
  password: string
}

type ApiError = Error & { status?: number; body?: any; apiError?: ApiErrorShape }

type RequestAuthOptions = {
  gmToken?: string
  playerToken?: string
  includeGmToken?: boolean
  includePlayerToken?: boolean
}

async function readJson(res: Response) {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}


function normalizeApiErrorShape(status: number | undefined, body: any, fallbackMessage: string): ApiErrorShape {
  const code = typeof body?.code === 'string' && body.code.trim() ? body.code.trim() : inferLegacyErrorCode(status, body, fallbackMessage)
  const category = typeof body?.category === 'string' && body.category.trim() ? body.category.trim() : inferLegacyErrorCategory(status)
  const userMessageRaw = body?.userMessage ?? body?.message ?? body?.error ?? fallbackMessage
  const userMessage = normalizeApiErrorMessage(status, userMessageRaw)
  const debugMessage = typeof body?.debugMessage === 'string' && body.debugMessage.trim() ? body.debugMessage : String(userMessageRaw || fallbackMessage)
  const details = body?.details ?? null
  const path = typeof body?.path === 'string' ? body.path : undefined
  return { code, category, userMessage, debugMessage, details, status, path }
}

function inferLegacyErrorCategory(status: number | undefined): string {
  if (status === 404) return 'NOT_FOUND'
  if (status === 409) return 'CONFLICT'
  if (status && status >= 500) return 'INTERNAL'
  if (status === 401 || status === 403) return 'RULE'
  return 'VALIDATION'
}

function inferLegacyErrorCode(status: number | undefined, body: any, fallbackMessage: string): string {
  const message = String(body?.message ?? body?.error ?? fallbackMessage ?? '')
  if (message.includes('deck edit invalid')) return 'DECK_EDIT_INVALID'
  if (message.includes('locked-in-deck')) return 'CARD_LOCKED_IN_DECK'
  if (message.includes('cannot forget') || message.includes('not forgettable')) return 'CARD_NOT_FORGETTABLE'
  if (message.includes('forgetting required')) return 'FORGET_REQUIRED'
  if (message.includes('pending decision')) return 'INVALID_PENDING_DECISION'
  if (status === 404) return 'NOT_FOUND'
  if (status === 409) return 'CONFLICT'
  if (status === 401) return 'UNAUTHORIZED'
  if (status === 403) return 'FORBIDDEN'
  if (status && status >= 500) return 'INTERNAL_ERROR'
  return 'INVALID_REQUEST'
}

export function getApiErrorShape(e: unknown): ApiErrorShape {
  const err = e as ApiError
  if (!err) {
    return { code: 'UNKNOWN_ERROR', category: 'INTERNAL', userMessage: 'Unknown error' }
  }
  if (err.apiError) return err.apiError
  return normalizeApiErrorShape(err.status, err.body, err.message || 'Unknown error')
}

export function explainEngineRejection(errorDetails?: ApiErrorShape[] | null, errors?: string[] | null) {
  const primary = errorDetails?.[0]
  if (primary?.userMessage) return primary.userMessage
  return (errors && errors.length ? errors.join('\n') : '') || '커맨드가 거부되었습니다.'
}

function createAuthHeaders(auth?: RequestAuthOptions): Record<string, string> {
  const headers: Record<string, string> = {}

  if (auth?.includeGmToken && auth.gmToken) {
    headers['X-GM-Token'] = auth.gmToken
  }

  if (auth?.includePlayerToken && auth.playerToken) {
    headers['X-Player-Token'] = auth.playerToken
  }

  return headers
}

async function request<T>(path: string, init?: RequestInit, auth?: RequestAuthOptions): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...createAuthHeaders(auth),
      ...(init?.headers ?? {}),
    },
  })

  if (!res.ok) {
    const e: ApiError = new Error(`HTTP ${res.status}`)
    e.status = res.status
    e.body = await readJson(res)
    e.apiError = normalizeApiErrorShape(e.status, e.body, e.message)
    throw e
  }

  return (await readJson(res)) as T
}

export function explainApiError(e: unknown) {
  const apiError = getApiErrorShape(e)
  const detail = apiError.path ? ` (${apiError.path})` : ''
  return apiError.status ? `${apiError.status} · ${apiError.userMessage}${detail}` : String(apiError.userMessage)
}

function normalizeApiErrorMessage(status: number | undefined, message: unknown): string {
  const msg = String(message || '')

  if (status === 401) {
    if (msg === 'player authorization required' || msg === 'Full authentication is required to access this resource') {
      return 'player authorization required'
    }
    if (msg === 'gm only' || msg === 'gm authorization required') {
      return 'gm authorization required'
    }
  }

  if (status === 403 && msg === 'gm only') {
    return 'gm authorization required'
  }

  return msg
}

export async function signup(req: SignupRequest): Promise<AuthUserResponse> {
  return await request<AuthUserResponse>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export async function login(req: LoginRequest): Promise<AuthUserResponse> {
  return await request<AuthUserResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export async function getCurrentUser(): Promise<AuthUserResponse> {
  return await request<AuthUserResponse>('/api/auth/me')
}

export async function logout(): Promise<void> {
  await request<null>('/api/auth/logout', {
    method: 'POST',
  })
}

export async function listCardDefs(): Promise<CardDef[]> {
  const raw = await request<any[]>('/api/content/cards')
  return raw.map(normalizeCardDef)
}


export async function listPassives(): Promise<PassiveDefinition[]> {
  const raw = await request<any[]>('/api/content/passives')
  return raw.map(normalizePassiveDefinition)
}

export async function createSession(gmId: string): Promise<CreateSessionResponse> {
  const raw = await request<any>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify({ gmId }),
  })
  return { ...raw, state: adaptSessionSnapshot(raw?.state ?? {}) }
}

export async function getSessionState(code: string): Promise<SessionSnapshot> {
  const raw = await request<any>(`/api/sessions/${encodeURIComponent(code)}`)
  return adaptSessionSnapshot(raw)
}

export async function getRecentResults(
  code: string,
  gmToken?: string,
  playerToken?: string,
): Promise<RecentResultsResponse> {
  return await request<RecentResultsResponse>(
    `/api/sessions/${encodeURIComponent(code)}/recent-results`,
    undefined,
    {
      gmToken,
      playerToken,
      includeGmToken: true,
      includePlayerToken: true,
    },
  )
}

export async function joinSession(
  code: string,
  playerId: string,
  passiveIds?: string[],
  presetDeckCardIds?: string[] | null,
  presetExCardId?: string | null,
  ownedCards?: OwnedCard[] | null,
  characterId?: number | null,
): Promise<JoinSessionResponse> {
  const hasPresetDeck = Array.isArray(presetDeckCardIds)
  const hasPresetEx = typeof presetExCardId === 'string' && presetExCardId.trim().length > 0

  const raw = await request<any>(`/api/sessions/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    body: JSON.stringify({
      playerId,
      characterId: typeof characterId === 'number' && Number.isFinite(characterId) ? characterId : undefined,
      passiveIds,
      presetDeckCardIds: hasPresetDeck ? presetDeckCardIds : undefined,
      presetExCardId: hasPresetEx ? presetExCardId : undefined,
      ownedCards,
    }),
  })
  return { ...raw, state: adaptSessionSnapshot(raw?.state ?? {}) }
}

export async function sendCommand(
  code: string,
  req: CommandRequest,
  gmToken?: string,
  playerToken?: string,
): Promise<EngineResponse> {
  const raw = await request<any>(`/api/sessions/${encodeURIComponent(code)}/command`, {
    method: 'POST',
    body: JSON.stringify(req),
  }, {
    gmToken,
    playerToken,
    includeGmToken: ['START_COMBAT', 'ENEMY_PLAY_CARD', 'ENEMY_USE_EX', 'ENEMY_END_TURN'].includes(req.type?.toUpperCase?.() ?? ''),
    includePlayerToken: true,
  })
  return adaptEngineResponse(raw)
}

export async function updateSessionDeck(
  code: string,
  playerId: string,
  deckOwnedCardIds: string[],
  playerToken?: string,
): Promise<SessionSnapshot> {
  const raw = await request<any>(
    `/api/sessions/${encodeURIComponent(code)}/players/${encodeURIComponent(playerId)}/deck`,
    {
      method: 'POST',
      body: JSON.stringify({ deckOwnedCardIds }),
    },
    {
      playerToken,
      includePlayerToken: true,
    },
  )
  return adaptSessionSnapshot(raw)
}

// ------------------
// Deck API
// ------------------

export async function listDecks(): Promise<DeckResponse[]> {
  return await request<DeckResponse[]>('/api/content/decks')
}

export async function getDeck(id: number): Promise<DeckResponse> {
  return await request<DeckResponse>(`/api/content/decks/${id}`)
}

export async function createDeck(req: CreateDeckRequest): Promise<DeckResponse> {
  return await request<DeckResponse>('/api/content/decks', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export async function updateDeck(id: number, req: UpdateDeckRequest): Promise<DeckResponse> {
  return await request<DeckResponse>(`/api/content/decks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  })
}

export async function deleteDeck(id: number): Promise<void> {
  await request<void>(`/api/content/decks/${id}`, { method: 'DELETE' })
}

export async function addDeckCards(id: number, req: AddDeckCardsRequest): Promise<DeckResponse> {
  return await request<DeckResponse>(`/api/content/decks/${id}/cards/add`, {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

// ------------------
// Character Profile API
// ------------------

export async function listCharacterProfiles(): Promise<CharacterProfileResponse[]> {
  return await request<CharacterProfileResponse[]>('/api/content/characters')
}

export async function getCharacterProfile(id: number): Promise<CharacterProfileResponse> {
  return await request<CharacterProfileResponse>(`/api/content/characters/${id}`)
}

export async function createCharacterProfile(req: CharacterProfileRequest): Promise<CharacterProfileResponse> {
  return await request<CharacterProfileResponse>('/api/content/characters', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export async function updateCharacterProfile(id: number, req: CharacterProfileRequest): Promise<CharacterProfileResponse> {
  return await request<CharacterProfileResponse>(`/api/content/characters/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  })
}

export async function deleteCharacterProfile(id: number): Promise<void> {
  await request<void>(`/api/content/characters/${id}`, { method: 'DELETE' })
}
