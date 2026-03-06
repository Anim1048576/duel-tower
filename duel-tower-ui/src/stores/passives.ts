import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'
import type { PassiveDefinition } from '../lib/model'
import { explainApiError, listPassives } from '../lib/api'

export type PassivesState = {
  status: 'idle' | 'loading' | 'ok' | 'error'
  passives: PassiveDefinition[]
  passivesById: Record<string, PassiveDefinition>
  lastError?: string
  lastLoadedAt?: string
}

function indexById(passives: PassiveDefinition[]) {
  const m: Record<string, PassiveDefinition> = {}
  for (const p of passives) m[p.id] = p
  return m
}

const seed: PassivesState = load(KEY.presets + '.passives.v1', {
  status: 'idle',
  passives: [],
  passivesById: {},
})
seed.passivesById = indexById(seed.passives)

export const passives = writable<PassivesState>(seed)
passives.subscribe((v) => {
  save(KEY.presets + '.passives.v1', {
    status: v.status,
    passives: v.passives,
    passivesById: {},
    lastError: v.lastError,
    lastLoadedAt: v.lastLoadedAt,
  })
})

let inflight: Promise<void> | null = null

export async function ensurePassives() {
  if (inflight) return inflight
  inflight = (async () => {
    passives.update((s) => ({ ...s, status: 'loading', lastError: undefined }))
    try {
      const items = await listPassives()
      passives.set({
        status: 'ok',
        passives: items,
        passivesById: indexById(items),
        lastLoadedAt: new Date().toISOString(),
      })
    } catch (e) {
      passives.update((s) => ({
        ...s,
        status: s.passives.length ? 'ok' : 'error',
        lastError: explainApiError(e),
      }))
    } finally {
      inflight = null
    }
  })()
  return inflight
}
