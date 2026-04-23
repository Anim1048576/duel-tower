export type DeckPageFeedback = {
  title: string
  message: string
}

const deckPageFeedbackKey = 'duel-tower:deck-page-feedback'

export const deckListStateCopy = {
  overviewDescription: '덱을 조회하고 편집합니다.',
  loadingTitle: 'Loading deck archive',
  loadingMessage: '덱 목록을 불러오는 중입니다.',
  loadErrorTitle: 'Unable to load the deck archive',
  emptyMessage: '표시할 덱이 없습니다.',
  detailLoadingTitle: 'Preparing selected deck',
  detailLoadingMessage: '선택한 덱을 불러오는 중입니다.',
  detailErrorTitle: 'Selected deck summary is unavailable',
  detailEmptyTitle: 'No deck is selected',
  detailDescription: '선택한 덱을 확인하고 편집합니다.',
  deletedFeedback: {
    title: 'Deck deleted',
    message: '선택한 덱을 삭제했습니다.',
  },
  createActionLabel: 'Create new deck',
} as const

export const deckEditorStateCopy = {
  loadingTitle: 'Loading deck',
  loadingMessage: '덱을 불러오는 중입니다.',
  notFoundTitle: 'Deck not found',
  notFoundDescription: '요청한 덱을 찾을 수 없습니다.',
  notFoundMessage: '덱 목록에서 다시 열어 주세요.',
  loadErrorTitle: 'Deck could not be loaded',
  loadErrorMessageTitle: 'Unable to load deck data',
  createDescription: '새 덱을 생성합니다.',
  editDescription: '덱을 수정하고 저장합니다.',
  noCardsTitle: 'No cards are assigned',
  noCardsMessage: '저장된 카드가 없습니다.',
  createErrorTitle: 'Create failed',
  saveErrorTitle: 'Save failed',
  saveSuccessTitle: 'Deck saved',
  validationLoadingTitle: 'Validating deck draft',
  validationLoadingMessage: '덱을 검증하는 중입니다.',
  validationErrorTitle: 'Validation request failed',
  validationNoIssuesMessage: '검증 문제가 없습니다.',
  deleteConfirmTitle: 'Delete this deck?',
  deleteConfirmMessage: '이 덱을 삭제합니다. 되돌릴 수 없습니다.',
  deleteLoadingTitle: 'Deleting deck',
  deleteLoadingMessage: '덱을 삭제하는 중입니다.',
  deleteErrorTitle: 'Delete failed',
  createModeValidationMessage: '덱을 생성한 뒤 검증할 수 있습니다.',
  selectionTitle: 'Deck selection unavailable',
  selectionDescription: '덱 목록에서 덱을 선택해 주세요.',
  selectionRouteMessage: 'No deck id is present in the current URL.',
  selectionActionMessage: '덱 목록에서 열거나 새로 생성해 주세요.',
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
