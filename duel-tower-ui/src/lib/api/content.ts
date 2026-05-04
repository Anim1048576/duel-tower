import { apiGet } from './client'
import type {
  CardDefinition,
  CardDetailResponse,
  CardKeywordValues,
  CardListQueryParams,
  ContentIdentifier,
  ItemDefinition,
  KeywordDefinition,
  PassiveDefinition,
  StatusDefinition,
} from './contentTypes'

const CONTENT_API_BASE = '/api/content'

type RawContentId = string | { value?: unknown }

type RawCardDefinition = {
  id: RawContentId
  name: string
  type: CardDefinition['type']
  cost: number | null
  keywords?: string[] | Record<string, number> | null
  resolveTo: CardDefinition['resolveTo']
  token: boolean | string | null
  description: string
}

type RawCardDetailResponse = RawCardDefinition & {
  playSpec: CardDetailResponse['playSpec']
}

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

function normalizeContentId(value: RawContentId): string {
  if (typeof value === 'string') {
    return value.trim()
  }

  if (value && typeof value === 'object' && typeof value.value === 'string') {
    return value.value.trim()
  }

  return ''
}

function normalizeKeywords(value: RawCardDefinition['keywords']): {
  keywords: string[]
  keywordValues: CardKeywordValues
} {
  if (Array.isArray(value)) {
    const keywords = value
      .filter((item): item is string => typeof item === 'string')
      .map((item) => item.trim())
      .filter(Boolean)

    return {
      keywords,
      keywordValues: Object.fromEntries(keywords.map((keyword) => [keyword, 1])),
    }
  }

  if (value && typeof value === 'object') {
    const entries = Object.entries(value).filter(
      (entry): entry is [string, number] =>
        typeof entry[0] === 'string' &&
        entry[0].trim().length > 0 &&
        typeof entry[1] === 'number',
    )

    return {
      keywords: entries.map(([keyword]) => keyword),
      keywordValues: Object.fromEntries(entries),
    }
  }

  return {
    keywords: [],
    keywordValues: {},
  }
}

function normalizeCardDefinition(raw: RawCardDefinition): CardDefinition {
  const { keywords, keywordValues } = normalizeKeywords(raw.keywords)

  return {
    id: normalizeContentId(raw.id),
    name: raw.name ?? '',
    type: raw.type,
    cost: typeof raw.cost === 'number' ? raw.cost : null,
    keywords,
    keywordValues,
    resolveTo: raw.resolveTo ?? null,
    token: Boolean(raw.token),
    description: raw.description ?? '',
  }
}

export async function listCards(params: CardListQueryParams = {}) {
  const response = await apiGet<RawCardDefinition[]>(getCardsCollectionPath(params))
  return response.map(normalizeCardDefinition)
}

export async function getCard(id: ContentIdentifier) {
  const response = await apiGet<RawCardDetailResponse>(getContentResourcePath('cards', id))
  return {
    ...normalizeCardDefinition(response),
    playSpec: response.playSpec ?? null,
  } satisfies CardDetailResponse
}

export function listKeywords() {
  return apiGet<KeywordDefinition[]>(`${CONTENT_API_BASE}/keywords`)
}

export function listAllKeywords() {
  return apiGet<KeywordDefinition[]>(`${CONTENT_API_BASE}/keywords/all`)
}

export function listAttachedKeywords(parentKeywordId: ContentIdentifier) {
  return apiGet<KeywordDefinition[]>(
    `${CONTENT_API_BASE}/keywords/${encodeURIComponent(String(parentKeywordId).trim())}/attached`,
  )
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
