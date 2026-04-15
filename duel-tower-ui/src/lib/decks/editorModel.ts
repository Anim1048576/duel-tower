import type {
  DeckCardSpec,
  DeckType,
} from '../api/deckTypes'
import type { DeckEditorActionPayload, DeckEditorDraftCardDto, DeckEditorDraftDto } from '../api/screenTypes'

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
  card: Pick<DeckEditorDraftCardDto, 'key' | 'cardId' | 'count'>,
  index: number,
): DeckEditorCardState {
  return {
    key: card.key?.trim() || createDeckEditorCardKey(index),
    cardId: String(card.cardId),
    count: card.count,
  }
}

export function createDeckEditorState(draft: DeckEditorDraftDto): DeckEditorState {
  return {
    name: draft.name,
    type: draft.type,
    cards: draft.cards.map(createDeckEditorCardState),
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

export function buildDeckEditorActionPatch(
  actionId: string,
  state: DeckEditorState,
): Partial<DeckEditorActionPayload> {
  const cards = toDeckEditorCardSpecs(state.cards)
  const type = state.type || null

  if (actionId === 'deckEditor.validate') {
    return {
      type,
      cards,
    }
  }

  return {
    name: state.name.trim(),
    type,
    cards,
  }
}
