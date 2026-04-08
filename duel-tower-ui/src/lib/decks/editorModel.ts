import type {
  DeckCardDto,
  DeckCardSpec,
  DeckResponse,
  DeckType,
  ReplaceDeckCardsRequest,
  UpdateDeckRequest,
} from '../api/deckTypes'
import { getDeckCardTotal } from '../api/deckTypes'

export type DeckEditorCardState = {
  key: string
  cardId: string
  count: number
}

export type DeckEditorState = {
  name: string
  type: DeckType | ''
  cards: DeckEditorCardState[]
}

function createDeckEditorCardKey(index: number) {
  return `deck-card-${index + 1}`
}

export function createEmptyDeckEditorState(): DeckEditorState {
  return {
    name: '',
    type: '',
    cards: [],
  }
}

export function createDeckEditorCardState(
  card: Pick<DeckCardDto, 'cardId' | 'count'>,
  index: number,
): DeckEditorCardState {
  return {
    key: createDeckEditorCardKey(index),
    cardId: String(card.cardId),
    count: card.count,
  }
}

export function createDeckEditorState(response: DeckResponse): DeckEditorState {
  return {
    name: response.name,
    type: response.type,
    cards: response.cards.map(createDeckEditorCardState),
  }
}

export function toDeckEditorCardSpec(card: Pick<DeckEditorCardState, 'cardId' | 'count'>): DeckCardSpec {
  return {
    cardId: String(card.cardId).trim(),
    count: card.count,
  }
}

export function toDeckEditorCardSpecs(cards: readonly DeckEditorCardState[]) {
  return cards.map(toDeckEditorCardSpec)
}

export function toDeckEditorUpdateRequest(state: DeckEditorState): UpdateDeckRequest {
  return {
    name: state.name.trim(),
    type: state.type || null,
    cards: toDeckEditorCardSpecs(state.cards),
  }
}

export function toDeckEditorReplaceCardsRequest(
  state: Pick<DeckEditorState, 'cards'>,
): ReplaceDeckCardsRequest {
  return {
    cards: toDeckEditorCardSpecs(state.cards),
  }
}

export function getDeckEditorTotalCards(state: Pick<DeckEditorState, 'cards'>) {
  return getDeckCardTotal(toDeckEditorCardSpecs(state.cards))
}

function areDeckCardsEqual(sourceCards: readonly DeckCardDto[], draftCards: readonly DeckEditorCardState[]) {
  if (sourceCards.length !== draftCards.length) {
    return false
  }

  for (const [index, sourceCard] of sourceCards.entries()) {
    const draftCard = draftCards[index]

    if (!draftCard) {
      return false
    }

    if (String(sourceCard.cardId) !== String(draftCard.cardId).trim()) {
      return false
    }

    if (sourceCard.count !== draftCard.count) {
      return false
    }
  }

  return true
}

export function isDeckEditorStateDirty(source: DeckResponse, state: DeckEditorState) {
  if (source.name !== state.name.trim()) {
    return true
  }

  if (source.type !== state.type) {
    return true
  }

  return !areDeckCardsEqual(source.cards, state.cards)
}
