/** @typedef {import('./editorModel').DeckEditorState} DeckEditorState */
/** @typedef {import('../api/screenTypes').DeckEditorDraftDto} DeckEditorDraftDto */

import { createDeckEditorDraftSignature } from './validationFreshness.js'

/** @param {string | null | undefined} name */
function normalizeDeckName(name) {
  return typeof name === 'string' ? name.trim() : ''
}

/** @param {Pick<DeckEditorState, 'cards'>} state */
export function getDeckEditorLocalTotalCards(state) {
  return (state.cards ?? []).reduce((total, card) => total + (Number.isFinite(card.count) ? Math.max(1, Math.floor(card.count)) : 1), 0)
}

/** @param {Pick<DeckEditorState, 'name' | 'type' | 'cards'>} state
 * @param {Pick<DeckEditorDraftDto, 'name' | 'type' | 'cards'>} draft
 */
export function isDeckEditorLocalDirty(state, draft) {
  if (normalizeDeckName(state.name) !== normalizeDeckName(draft.name)) {
    return true
  }

  return createDeckEditorDraftSignature(state) !== createDeckEditorDraftSignature(draft)
}

/** @param {Pick<DeckEditorState, 'name'>} state
 * @param {Pick<DeckEditorDraftDto, 'name'>} draft
 * @param {'create' | 'edit'} mode
 */
export function getDeckEditorLocalTitle(state, draft, mode) {
  const localName = normalizeDeckName(state.name)

  if (localName) {
    return localName
  }

  const draftName = normalizeDeckName(draft.name)

  if (draftName) {
    return draftName
  }

  return mode === 'create' ? 'New deck' : 'Untitled deck'
}

/** @param {DeckEditorState['type'] | DeckEditorDraftDto['type'] | null | undefined} type */
export function getDeckEditorDeckTypeLabel(type) {
  switch (type) {
    case 'PLAYER':
      return 'Player'
    case 'ENEMY':
      return 'Enemy'
    default:
      return 'N/A'
  }
}

/** @param {Pick<DeckEditorState, 'cards'>} state */
export function buildDeckEditorLocalSummary(state) {
  if (!state.cards.length) {
    return 'This draft currently has no card entries.'
  }

  const entryLabel = state.cards.length === 1 ? 'entry' : 'entries'
  return `${getDeckEditorLocalTotalCards(state)} total cards are currently distributed across ${state.cards.length} ${entryLabel}.`
}
