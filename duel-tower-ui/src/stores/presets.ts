import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type DeckPreset = {
  id: string
  name: string
  deck: string[] // card defIds
  ex: string | null
  createdAt: string
  updatedAt: string
}

type PresetState = {
  presets: DeckPreset[]
  selectedId: string | null
}

function uid(prefix = 'pr') {
  return `${prefix}-${Math.random().toString(16).slice(2)}-${Date.now()}`
}

function now() {
  return new Date().toISOString()
}

const seed: PresetState = load(KEY.presets, {
  presets: [],
  selectedId: null,
})

if (!seed.presets.length) {
  const t = now()
  seed.presets = [
    {
      id: uid(),
      name: '기본 프리셋',
      deck: ['C001', 'C001', 'C001', 'C001', 'C001', 'C001', 'C002', 'C002', 'C002', 'C002', 'C002', 'C002'],
      ex: 'C001',
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
  const p: DeckPreset = { id: uid(), name, deck: [], ex: null, createdAt: t, updatedAt: t }
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
