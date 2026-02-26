import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'
import type { EngineEvent } from '../lib/model'

export type LogItem = {
  id: string
  at: string
  level: 'info' | 'warn' | 'error'
  title: string
  message?: string
}

export type ToastItem = {
  id: string
  at: string
  title: string
  message?: string
}

function uid(prefix = 'l') {
  return `${prefix}-${Math.random().toString(16).slice(2)}-${Date.now()}`
}

const seed = load<{ items: LogItem[]; toasts: ToastItem[] }>(KEY.log, {
  items: [],
  toasts: [],
})

export const logs = writable<LogItem[]>(seed.items)
export const toasts = writable<ToastItem[]>(seed.toasts)

function persist() {
  let items: LogItem[] = []
  let ts: ToastItem[] = []
  logs.subscribe((v) => (items = v))()
  toasts.subscribe((v) => (ts = v))()
  save(KEY.log, { items, toasts: ts })
}

logs.subscribe(() => persist())
toasts.subscribe(() => persist())

export function clearLogs() {
  logs.set([])
}

export function pushToast(title: string, message?: string) {
  const item: ToastItem = { id: uid('t'), at: new Date().toISOString(), title, message }
  toasts.update((v) => [item, ...v].slice(0, 6))
  setTimeout(() => {
    toasts.update((v) => v.filter((x) => x.id !== item.id))
  }, 3500)
}

export function dismissToast(id: string) {
  toasts.update((v) => v.filter((x) => x.id !== id))
}

export function info(title: string, message?: string) {
  logs.update((v) => [{ id: uid(), at: new Date().toISOString(), level: 'info', title, message }, ...v].slice(0, 200))
}

export function warn(title: string, message?: string) {
  logs.update((v) => [{ id: uid(), at: new Date().toISOString(), level: 'warn', title, message }, ...v].slice(0, 200))
}

export function error(title: string, message?: string) {
  logs.update((v) => [{ id: uid(), at: new Date().toISOString(), level: 'error', title, message }, ...v].slice(0, 200))
  pushToast(title, message)
}

export function pushEngineEvents(events: EngineEvent[]) {
  for (const ev of events) {
    if (ev.type === 'LOG_APPENDED') {
      const line = String(ev.payload?.line ?? '')
      info('LOG', line)
    } else if (ev.type === 'PENDING_DECISION_SET') {
      const pid = String(ev.payload?.playerId ?? '')
      const reason = String(ev.payload?.reason ?? '')
      warn(`결정 필요 · ${pid}`, reason)
    } else if (ev.type === 'TURN_ADVANCED') {
      const next = String(ev.payload?.nextPlayerId ?? '')
      const round = String(ev.payload?.round ?? '')
      info(`턴 진행`, `R${round} · 다음: ${next}`)
    } else {
      info(ev.type, JSON.stringify(ev.payload))
    }
  }
}
