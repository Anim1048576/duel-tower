import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type DeckPreset = {
  id: string
  name: string
  deck: string[] // card defIds
  ex: string | null
  passiveIds: string[]
  createdAt: string
  updatedAt: string
}

type PresetState = {
  presets: DeckPreset[]
  selectedId: string | null
}

type LegacyDeckPreset = Omit<DeckPreset, 'passiveIds'> & { passiveIds?: string[] }
type LegacyPresetState = {
  presets?: LegacyDeckPreset[]
  selectedId?: string | null
}

function uid(prefix = 'pr') {
  return `${prefix}-${Math.random().toString(16).slice(2)}-${Date.now()}`
}

function now() {
  return new Date().toISOString()
}

function migratePresetState(raw: LegacyPresetState): PresetState {
  const presets = Array.isArray(raw?.presets)
    ? raw.presets.map((p) => ({
        id: String(p.id ?? uid()),
        name: String(p.name ?? '새 프리셋'),
        deck: Array.isArray(p.deck) ? p.deck.map(String).slice(0, 12) : [],
        ex: p.ex ? String(p.ex) : null,
        passiveIds: Array.isArray(p.passiveIds) ? p.passiveIds.map(String).slice(0, 2) : [],
        createdAt: String(p.createdAt ?? now()),
        updatedAt: String(p.updatedAt ?? now()),
      }))
    : []

  const selectedId = typeof raw?.selectedId === 'string' ? raw.selectedId : null
  return { presets, selectedId }
}

const legacySeed = load<LegacyPresetState>(KEY.presets, {
  presets: [],
  selectedId: null,
})
const seed: PresetState = migratePresetState(legacySeed)

if (!seed.presets.length) {
  const t = now()
  seed.presets = [
    {
      id: uid(),
      name: '기본 프리셋',
      deck: ['C001', 'C001', 'C001', 'C001', 'C001', 'C001', 'C002', 'C002', 'C002', 'C002', 'C002', 'C002'],
      ex: 'C001',
      passiveIds: [],
      createdAt: t,
      updatedAt: t,
    },
  ]
  seed.selectedId = seed.presets[0].id
}

export const presets = writable<PresetState>(seed)
presets.subscribe((v) => save(KEY.presets, v))

export function selectPreset(id: string) {
  presets.update((s) => ({ ...s, selectedId: id }))
}

export function createPreset(name = '새 프리셋') {
  const t = now()
  const p: DeckPreset = { id: uid(), name, deck: [], ex: null, passiveIds: [], createdAt: t, updatedAt: t }
  presets.update((s) => ({ presets: [p, ...s.presets], selectedId: p.id }))
}

export function renamePreset(id: string, name: string) {
  presets.update((s) => ({
    ...s,
    presets: s.presets.map((p) => (p.id === id ? { ...p, name: name.trim() || p.name, updatedAt: now() } : p)),
  }))
}

export function deletePreset(id: string) {
  presets.update((s) => {
    const rest = s.presets.filter((p) => p.id !== id)
    const selectedId = s.selectedId === id ? (rest[0]?.id ?? null) : s.selectedId
    return { presets: rest, selectedId }
  })
}

export function setDeck(id: string, deck: string[]) {
  presets.update((s) => ({
    ...s,
    presets: s.presets.map((p) => (p.id === id ? { ...p, deck: deck.slice(0, 12), updatedAt: now() } : p)),
  }))
}

export function setEx(id: string, ex: string | null) {
  presets.update((s) => ({
    ...s,
    presets: s.presets.map((p) => (p.id === id ? { ...p, ex, updatedAt: now() } : p)),
  }))
}

export function setPassiveIds(id: string, passiveIds: string[]) {
  presets.update((s) => ({
    ...s,
    presets: s.presets.map((p) => (p.id === id ? { ...p, passiveIds: passiveIds.slice(0, 2), updatedAt: now() } : p)),
  }))
}
