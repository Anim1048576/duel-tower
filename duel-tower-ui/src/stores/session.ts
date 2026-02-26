import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type Player = { id: string; name: string; ready: boolean }
export type Session = {
  code: string
  phase: 'lobby' | 'node' | 'combat'
  gm: string
  players: Player[]
  createdAt: string
}

function now() { return new Date().toISOString() }
function code() {
  const chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

const seed: Session = load(KEY.session, {
  code: '—',
  phase: 'lobby',
  gm: 'GM',
  players: [{ id: 'me', name: 'Me', ready: false }],
  createdAt: now(),
})

export const session = writable<Session>(seed)
session.subscribe(v => save(KEY.session, v))

export function createSession() {
  session.set({
    code: code(),
    phase: 'lobby',
    gm: 'GM',
    players: [{ id: 'me', name: 'Me', ready: false }],
    createdAt: now(),
  })
}

export function joinSession(joinCode: string) {
  session.update(s => ({
    ...s,
    code: (joinCode || code()).trim().toUpperCase(),
    phase: 'lobby',
    players: s.players?.length ? s.players : [{ id: 'me', name: 'Me', ready: false }],
  }))
}