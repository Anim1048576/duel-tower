import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type SessionClient = {
  mode: 'api' | 'local'
  code: string
  gmId: string
  meId: string
  createdAt: string
  lastError?: string
}

function now() {
  return new Date().toISOString()
}

function randomId(prefix = 'p') {
  return `${prefix}-${Math.random().toString(16).slice(2, 6)}${Math.random().toString(16).slice(2, 6)}`
}

const seed: SessionClient = load(KEY.session, {
  mode: 'api',
  code: '',
  gmId: '',
  meId: 'me',
  createdAt: now(),
})

if (!seed.meId) seed.meId = randomId('p')

export const session = writable<SessionClient>(seed)
session.subscribe((v) => save(KEY.session, v))

export function setMeId(meId: string) {
  session.update((s) => ({ ...s, meId: (meId || 'me').trim() }))
}

export function setSessionCode(code: string) {
  session.update((s) => ({ ...s, code: (code || '').trim().toUpperCase() }))
}

export function setGmId(gmId: string) {
  session.update((s) => ({ ...s, gmId: (gmId || '').trim() }))
}

export function setLastError(msg?: string) {
  session.update((s) => ({ ...s, lastError: msg }))
}

export function resetSession() {
  session.set({
    mode: 'api',
    code: '',
    gmId: '',
    meId: seed.meId || randomId('p'),
    createdAt: now(),
  })
}
