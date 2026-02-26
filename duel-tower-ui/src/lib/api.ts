import type {
  CardDef,
  CommandRequest,
  CreateSessionResponse,
  EngineResponse,
  JoinSessionResponse,
  SessionState,
} from './model'

import { normalizeCardDef } from './model'

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

type ApiError = Error & { status?: number; body?: any }

async function readJson(res: Response) {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  })

  if (!res.ok) {
    const e: ApiError = new Error(`HTTP ${res.status}`)
    e.status = res.status
    e.body = await readJson(res)
    throw e
  }

  return (await readJson(res)) as T
}

export function explainApiError(e: unknown) {
  const err = e as ApiError
  if (!err) return 'Unknown error'

  const status = err.status
  const msg = (err.body && (err.body.message || err.body.error)) || err.message
  const detail = err.body && err.body.path ? ` (${err.body.path})` : ''
  return status ? `${status} · ${msg}${detail}` : String(msg)
}

export async function listCardDefs(): Promise<CardDef[]> {
  const raw = await request<any[]>('/api/content/cards')
  return raw.map(normalizeCardDef)
}

export async function createSession(gmId: string): Promise<CreateSessionResponse> {
  return await request<CreateSessionResponse>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify({ gmId }),
  })
}

export async function getSessionState(code: string): Promise<SessionState> {
  return await request<SessionState>(`/api/sessions/${encodeURIComponent(code)}`)
}

export async function joinSession(code: string, playerId: string): Promise<JoinSessionResponse> {
  return await request<JoinSessionResponse>(`/api/sessions/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    body: JSON.stringify({ playerId }),
  })
}

export async function sendCommand(code: string, req: CommandRequest): Promise<EngineResponse> {
  return await request<EngineResponse>(`/api/sessions/${encodeURIComponent(code)}/command`, {
    method: 'POST',
    body: JSON.stringify(req),
  })
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
