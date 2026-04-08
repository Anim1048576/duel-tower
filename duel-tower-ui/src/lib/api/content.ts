import { apiGet } from './client'
import type {
  CardDefinition,
  CardDetailResponse,
  CardListQueryParams,
  ContentIdentifier,
  ItemDefinition,
  KeywordDefinition,
  PassiveDefinition,
  StatusDefinition,
} from './contentTypes'

const CONTENT_API_BASE = '/api/content'

function getContentResourcePath(resource: string, id: ContentIdentifier) {
  const normalizedId = String(id).trim()

  if (!normalizedId) {
    throw new Error(`${resource} id is required`)
  }

  return `${CONTENT_API_BASE}/${resource}/${encodeURIComponent(normalizedId)}`
}

function createQueryString(params: Record<string, string | null | undefined>) {
  const searchParams = new URLSearchParams()

  for (const [key, value] of Object.entries(params)) {
    const normalized = value?.trim()

    if (!normalized) {
      continue
    }

    searchParams.set(key, normalized)
  }

  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

function getCardsCollectionPath(params: CardListQueryParams = {}) {
  return `${CONTENT_API_BASE}/cards${createQueryString({
    type: params.type ?? null,
    q: params.q ?? null,
    keywordId: params.keywordId ?? null,
  })}`
}

export function listCards(params: CardListQueryParams = {}) {
  return apiGet<CardDefinition[]>(getCardsCollectionPath(params))
}

export function getCard(id: ContentIdentifier) {
  return apiGet<CardDetailResponse>(getContentResourcePath('cards', id))
}

export function listKeywords() {
  return apiGet<KeywordDefinition[]>(`${CONTENT_API_BASE}/keywords`)
}

export function getKeyword(id: ContentIdentifier) {
  return apiGet<KeywordDefinition>(getContentResourcePath('keywords', id))
}

export function listPassives() {
  return apiGet<PassiveDefinition[]>(`${CONTENT_API_BASE}/passives`)
}

export function getPassive(id: ContentIdentifier) {
  return apiGet<PassiveDefinition>(getContentResourcePath('passives', id))
}

export function listStatuses() {
  return apiGet<StatusDefinition[]>(`${CONTENT_API_BASE}/statuses`)
}

export function getStatus(id: ContentIdentifier) {
  return apiGet<StatusDefinition>(getContentResourcePath('statuses', id))
}

export function listItems() {
  return apiGet<ItemDefinition[]>(`${CONTENT_API_BASE}/items`)
}

export function getItem(id: ContentIdentifier) {
  return apiGet<ItemDefinition>(getContentResourcePath('items', id))
}
