import type { ContentIdentifier } from './contentTypes'

export type DeckType = 'PLAYER' | 'ENEMY'

export type DeckId = number

export type DeckIdentifier = DeckId | string

export type DeckCardCountValue = number

export type DeckCardSpec = {
  cardId: ContentIdentifier
  count?: DeckCardCountValue | null
}

export type DeckCardDto = {
  cardId: ContentIdentifier
  count: DeckCardCountValue
}

export type DeckResponse = {
  id: DeckId
  name: string
  type: DeckType
  totalCards: number
  cards: DeckCardDto[]
}

export type DeckUpsertRequest = {
  name?: string | null
  type?: DeckType | null
  cards?: readonly DeckCardSpec[] | null
}

export type DeckValidationRequest = {
  type?: DeckType | null
  cards?: readonly DeckCardSpec[] | null
}

export type CreateDeckRequest = DeckUpsertRequest

export type UpdateDeckRequest = DeckUpsertRequest

export type AddDeckCardsRequest = {
  cards?: readonly DeckCardSpec[] | null
}

export type ReplaceDeckCardsRequest = {
  cards: readonly DeckCardSpec[] | null
}

export type RemoveDeckCardsRequest = {
  cards: readonly DeckCardSpec[] | null
}

export type DeckValidationIssue = {
  code: string
  message: string
  field: string | null
}

export type DeckValidationResponse = {
  valid: boolean
  issues: DeckValidationIssue[]
  normalizedTotalCards: number
}

export function getDeckCardCountValue(count: number | null | undefined, fallback = 1) {
  return count ?? fallback
}

export function toDeckCardDto(spec: DeckCardSpec): DeckCardDto {
  const cardId = String(spec.cardId).trim()

  if (!cardId) {
    throw new Error('cardId is required')
  }

  return {
    cardId,
    count: getDeckCardCountValue(spec.count),
  }
}

export function toDeckCardDtoList(cards: readonly DeckCardSpec[] | null | undefined) {
  return (cards ?? []).map(toDeckCardDto)
}

export function getDeckCardTotal(
  cards: readonly Pick<DeckCardSpec, 'count'>[] | readonly DeckCardDto[] | null | undefined,
) {
  return (cards ?? []).reduce((total, card) => total + getDeckCardCountValue(card.count), 0)
}
