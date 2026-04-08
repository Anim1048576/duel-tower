import { apiDeleteVoid, apiGet, apiPost, apiPut } from './client'
import type {
  AddDeckCardsRequest,
  CreateDeckRequest,
  DeckIdentifier,
  DeckResponse,
  DeckValidationResponse,
  RemoveDeckCardsRequest,
  ReplaceDeckCardsRequest,
  UpdateDeckRequest,
} from './deckTypes'

const DECKS_API_BASE = '/api/content/decks'

function getDeckResourcePath(id: DeckIdentifier) {
  const normalizedId = String(id).trim()

  if (!normalizedId) {
    throw new Error('deck id is required')
  }

  return `${DECKS_API_BASE}/${encodeURIComponent(normalizedId)}`
}

function getDeckSubresourcePath(id: DeckIdentifier, suffix: string) {
  return `${getDeckResourcePath(id)}/${suffix}`
}

export function listDecks() {
  return apiGet<DeckResponse[]>(DECKS_API_BASE)
}

export function getDeck(id: DeckIdentifier) {
  return apiGet<DeckResponse>(getDeckResourcePath(id))
}

export function createDeck(payload: CreateDeckRequest) {
  return apiPost<DeckResponse, CreateDeckRequest>(DECKS_API_BASE, payload)
}

export function updateDeck(id: DeckIdentifier, payload: UpdateDeckRequest) {
  return apiPut<DeckResponse, UpdateDeckRequest>(getDeckResourcePath(id), payload)
}

export function deleteDeck(id: DeckIdentifier) {
  return apiDeleteVoid(getDeckResourcePath(id))
}

export function addDeckCards(id: DeckIdentifier, payload: AddDeckCardsRequest) {
  return apiPost<DeckResponse, AddDeckCardsRequest>(getDeckSubresourcePath(id, 'cards/add'), payload)
}

export function replaceDeckCards(id: DeckIdentifier, payload: ReplaceDeckCardsRequest) {
  return apiPut<DeckResponse, ReplaceDeckCardsRequest>(getDeckSubresourcePath(id, 'cards'), payload)
}

export function removeDeckCards(id: DeckIdentifier, payload: RemoveDeckCardsRequest) {
  return apiPost<DeckResponse, RemoveDeckCardsRequest>(
    getDeckSubresourcePath(id, 'cards/remove'),
    payload,
  )
}

export function validateDeck(id: DeckIdentifier, payload?: ReplaceDeckCardsRequest | null) {
  return apiPost<DeckValidationResponse, ReplaceDeckCardsRequest | null>(
    getDeckSubresourcePath(id, 'validate'),
    payload ?? null,
  )
}
