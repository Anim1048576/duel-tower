export type DeckPageFeedback = {
  title: string
  message: string
}

const deckPageFeedbackKey = 'duel-tower:deck-page-feedback'

export const deckListStateCopy = {
  overviewDescription: 'Browse live deck records, open the editor, or start a new deck from the archive.',
  loadingTitle: 'Loading deck archive',
  loadingMessage: 'Refreshing the current deck list from the content API.',
  loadErrorTitle: 'Unable to load the deck archive',
  emptyMessage: 'No decks are currently available in the archive.',
  detailLoadingTitle: 'Preparing selected deck',
  detailLoadingMessage: 'The current deck selection is being summarized.',
  detailErrorTitle: 'Selected deck summary is unavailable',
  detailEmptyTitle: 'No deck is selected',
  detailDescription: 'Review the current selection and move into the deck editor from here.',
  deletedFeedback: {
    title: 'Deck deleted',
    message: 'The selected deck was removed from the archive.',
  },
  createActionLabel: 'Create new deck',
} as const

export const deckEditorStateCopy = {
  loadingTitle: 'Loading deck',
  loadingMessage: 'Fetching the selected deck from the content API.',
  notFoundTitle: 'Deck not found',
  notFoundDescription: 'The requested deck id could not be found in the content API.',
  notFoundMessage: 'Open a saved deck from the deck list and try again.',
  loadErrorTitle: 'Deck could not be loaded',
  loadErrorMessageTitle: 'Unable to load deck data',
  createDescription: 'Create a new deck record from the current draft.',
  editDescription: 'Review the current deck, adjust its cards, run validation, save changes, or delete it.',
  noCardsTitle: 'No cards are assigned',
  noCardsMessage: 'This deck currently has no saved card entries.',
  createErrorTitle: 'Create failed',
  saveErrorTitle: 'Save failed',
  saveSuccessTitle: 'Deck saved',
  validationLoadingTitle: 'Validating deck draft',
  validationLoadingMessage: 'Sending the current local card list to the server validation endpoint.',
  validationErrorTitle: 'Validation request failed',
  validationNoIssuesMessage: 'The validation endpoint did not return any issues for the current deck card draft.',
  deleteConfirmTitle: 'Delete this deck?',
  deleteConfirmMessage: 'This deck will be removed from the archive. This action cannot be undone.',
  deleteLoadingTitle: 'Deleting deck',
  deleteLoadingMessage: 'Removing the current deck from the archive and returning to the deck list.',
  deleteErrorTitle: 'Delete failed',
  createModeValidationMessage: 'Server validation becomes available after this deck is created and receives a real id.',
  selectionTitle: 'Deck selection unavailable',
  selectionDescription: 'Open a deck from the deck list to restore the expected editor context.',
  selectionRouteMessage: 'No deck id is present in the current URL.',
  selectionActionMessage: 'Use the deck list to open an existing deck or create a new one.',
} as const

function getDeckPageStorage() {
  if (typeof window === 'undefined') {
    return null
  }

  return window.sessionStorage
}

export function setDeckPageFeedback(feedback: DeckPageFeedback) {
  const storage = getDeckPageStorage()

  if (!storage) {
    return
  }

  storage.setItem(deckPageFeedbackKey, JSON.stringify(feedback))
}

export function readDeckPageFeedback() {
  const storage = getDeckPageStorage()

  if (!storage) {
    return null
  }

  const raw = storage.getItem(deckPageFeedbackKey)

  if (!raw) {
    return null
  }

  storage.removeItem(deckPageFeedbackKey)

  try {
    const parsed = JSON.parse(raw) as Partial<DeckPageFeedback>

    if (typeof parsed.title === 'string' && typeof parsed.message === 'string') {
      return {
        title: parsed.title,
        message: parsed.message,
      }
    }
  } catch {
    return null
  }

  return null
}
