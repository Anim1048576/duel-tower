/** @typedef {import('./editorModel').DeckEditorState} DeckEditorState */
/** @typedef {import('../api/screenTypes').DeckEditorDraftDto} DeckEditorDraftDto */
/** @typedef {import('../api/screenTypes').DeckEditorValidationDto} DeckEditorValidationDto */
/** @typedef {{ cardId: string, count: number | null | undefined }} SignatureCard */
/** @typedef {{ type: string | null | undefined, cards: SignatureCard[] | null | undefined }} SignatureDraft */

/** @param {string | null | undefined} type */
function normalizeDeckType(type) {
  return typeof type === 'string' ? type.trim() : ''
}

/** @param {string | null | undefined} cardId */
function normalizeCardId(cardId) {
  return typeof cardId === 'string' ? cardId.trim() : ''
}

/** @param {number | null | undefined} count */
function normalizeCardCount(count) {
  return typeof count === 'number' && Number.isFinite(count) ? Math.max(1, Math.floor(count)) : 1
}

/** @param {SignatureCard[] | null | undefined} cards */
function buildCardsToken(cards) {
  return (cards ?? [])
    .map((card) => `${normalizeCardId(card.cardId)}:${normalizeCardCount(card.count)}`)
    .join('|')
}

/**
 * Canonical signature for deck-editor freshness checks.
 * Order is preserved because deck entry order is meaningful in the editor.
 *
 * @param {SignatureDraft | Pick<DeckEditorState, 'type' | 'cards'> | Pick<DeckEditorDraftDto, 'type' | 'cards'>} draftLike
 */
export function createDeckEditorDraftSignature(draftLike) {
  return `type=${normalizeDeckType(draftLike.type)};cards=${buildCardsToken(draftLike.cards)}`
}

/**
 * @param {Pick<DeckEditorState, 'type' | 'cards'>} state
 * @param {Pick<DeckEditorValidationDto, 'validatedDraftSignature'> | null | undefined} validation
 */
export function isDeckEditorValidationStale(state, validation) {
  const validatedDraftSignature = validation?.validatedDraftSignature ?? ''

  if (!validatedDraftSignature) {
    return true
  }

  return createDeckEditorDraftSignature(state) !== validatedDraftSignature
}
