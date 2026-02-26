import { writable } from 'svelte/store'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'
import type { CardDef } from '../lib/model'
import { explainApiError, listCardDefs } from '../lib/api'

export type ContentState = {
  status: 'idle' | 'loading' | 'ok' | 'error'
  cards: CardDef[]
  cardsById: Record<string, CardDef>
  lastError?: string
  lastLoadedAt?: string
}

function indexById(cards: CardDef[]) {
  const m: Record<string, CardDef> = {}
  for (const c of cards) m[c.id] = c
  return m
}

const seed: ContentState = load(KEY.presets + '.content.v1', {
  status: 'idle',
  cards: [],
  cardsById: {},
})
seed.cardsById = indexById(seed.cards)

export const content = writable<ContentState>(seed)
content.subscribe((v) => {
  save(KEY.presets + '.content.v1', {
    status: v.status,
    cards: v.cards,
    cardsById: {},
    lastError: v.lastError,
    lastLoadedAt: v.lastLoadedAt,
  })
})

let inflight: Promise<void> | null = null

export async function ensureCards() {
  if (inflight) return inflight
  inflight = (async () => {
    content.update((s) => ({ ...s, status: 'loading', lastError: undefined }))
    try {
      const cards = await listCardDefs()
      content.set({
        status: 'ok',
        cards,
        cardsById: indexById(cards),
        lastLoadedAt: new Date().toISOString(),
      })
    } catch (e) {
      content.update((s) => ({
        ...s,
        status: s.cards.length ? 'ok' : 'error',
        lastError: explainApiError(e),
      }))
    } finally {
      inflight = null
    }
  })()
  return inflight
}
