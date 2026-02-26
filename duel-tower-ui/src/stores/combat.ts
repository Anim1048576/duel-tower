import { writable, get } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'
import type { CommandRequest, EngineResponse, SessionState } from '../lib/model'
import { explainApiError, getSessionState, joinSession, sendCommand } from '../lib/api'
import { session } from './session'
import { error as logError, info as logInfo, pushEngineEvents } from './log'

export type CombatStore = {
  state: SessionState | null
  lastSyncAt?: string
  lastError?: string
  polling: boolean
}

const seed: CombatStore = load(KEY.combat, {
  state: null,
  polling: false,
})

export const combat = writable<CombatStore>(seed)
combat.subscribe((v) => save(KEY.combat, v))

let timer: number | null = null

export function startPolling(ms = 1000) {
  if (timer) return
  combat.update((s) => ({ ...s, polling: true }))
  timer = window.setInterval(() => {
    refreshState().catch(() => {})
  }, ms)
}

export function stopPolling() {
  if (timer) window.clearInterval(timer)
  timer = null
  combat.update((s) => ({ ...s, polling: false }))
}

export async function refreshState() {
  const s = get(session)
  const code = (s.code || '').trim()
  if (!code) return
  try {
    const state = await getSessionState(code)
    combat.update((c) => ({ ...c, state, lastError: undefined, lastSyncAt: new Date().toISOString() }))
  } catch (e) {
    combat.update((c) => ({ ...c, lastError: explainApiError(e) }))
  }
}

export async function ensureJoined() {
  const s = get(session)
  const code = (s.code || '').trim()
  const pid = (s.meId || '').trim()
  if (!code || !pid) return
  try {
    await joinSession(code, pid)
    await refreshState()
  } catch (e) {
    combat.update((c) => ({ ...c, lastError: explainApiError(e) }))
  }
}

export async function command(req: Omit<CommandRequest, 'playerId'> & { playerId?: string }): Promise<EngineResponse | null> {
  const s = get(session)
  const code = (s.code || '').trim()
  if (!code) {
    logError('세션 코드 없음', '홈에서 세션 만들기/참가를 먼저 해줘')
    return null
  }

  const playerId = (req.playerId || s.meId || '').trim()
  try {
    const res: EngineResponse = await sendCommand(code, { ...req, playerId })
    if (!res.accepted) {
      logError('커맨드 거부', (res.errors || []).join('\n') || 'unknown')
    } else {
      logInfo('커맨드', req.type)
    }
    if (res.events?.length) pushEngineEvents(res.events)
    combat.update((c) => ({ ...c, state: res.state, lastError: undefined, lastSyncAt: new Date().toISOString() }))
    return res
  } catch (e) {
    logError('API 오류', explainApiError(e))
    combat.update((c) => ({ ...c, lastError: explainApiError(e) }))
    return null
  }
}
