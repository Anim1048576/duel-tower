import type {
  CardDef,
  CommandRequest,
  CreateSessionResponse,
  EngineResponse,
  JoinSessionResponse,
  SessionState,
} from './model'

import { normalizeCardDef } from './model'

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
