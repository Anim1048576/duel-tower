export type PresetPageFeedback = {
  title: string
  message: string
}

const presetPageFeedbackKey = 'duel-tower:preset-page-feedback'

export const presetListStateCopy = {
  loadingTitle: 'Loading preset archive',
  loadingMessage: '프리셋 목록을 불러오는 중입니다.',
  loadErrorTitle: 'Unable to load the preset archive',
  emptyMessage: '표시할 프리셋이 없습니다.',
  detailLoadingTitle: 'Preparing selected preset',
  detailLoadingMessage: '선택한 프리셋을 불러오는 중입니다.',
  detailErrorTitle: 'Selected preset summary is unavailable',
  detailEmptyTitle: 'No preset is selected',
  deletedFeedback: {
    title: 'Preset deleted',
    message: '선택한 프리셋을 삭제했습니다.',
  },
  createActionLabel: 'Create new preset',
} as const

export const presetEditorStateCopy = {
  cloneErrorTitle: 'Clone failed',
  cloneSuccessFeedback: {
    title: 'Preset cloned',
    message: '프리셋 복사본을 생성했습니다.',
  },
  deleteConfirmTitle: 'Delete this preset?',
  deleteConfirmMessage: '이 프리셋을 삭제합니다. 되돌릴 수 없습니다.',
  deleteLoadingTitle: 'Deleting preset',
  deleteLoadingMessage: '프리셋을 삭제하는 중입니다.',
  deleteErrorTitle: 'Delete failed',
} as const

function getPresetPageStorage() {
  if (typeof window === 'undefined') {
    return null
  }

  return window.sessionStorage
}

export function setPresetPageFeedback(feedback: PresetPageFeedback) {
  const storage = getPresetPageStorage()

  if (!storage) {
    return
  }

  storage.setItem(presetPageFeedbackKey, JSON.stringify(feedback))
}

export function readPresetPageFeedback() {
  const storage = getPresetPageStorage()

  if (!storage) {
    return null
  }

  const raw = storage.getItem(presetPageFeedbackKey)

  if (!raw) {
    return null
  }

  storage.removeItem(presetPageFeedbackKey)

  try {
    const parsed = JSON.parse(raw) as Partial<PresetPageFeedback>

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
