export type PresetPageFeedback = {
  title: string
  message: string
}

const presetPageFeedbackKey = 'duel-tower:preset-page-feedback'

export const presetListStateCopy = {
  loadingTitle: 'Loading preset archive',
  loadingMessage: 'Restoring the current preset list from the preset API.',
  loadErrorTitle: 'Unable to load the preset archive',
  emptyMessage: 'No preset records are available yet.',
  detailLoadingTitle: 'Preparing selected preset',
  detailLoadingMessage: 'The current preset selection is being summarized.',
  detailErrorTitle: 'Selected preset summary is unavailable',
  detailEmptyTitle: 'No preset is selected',
  deletedFeedback: {
    title: 'Preset deleted',
    message: 'The selected preset was removed from the archive.',
  },
  createActionLabel: 'Create new preset',
} as const

export const presetEditorStateCopy = {
  cloneErrorTitle: 'Clone failed',
  cloneSuccessFeedback: {
    title: 'Preset cloned',
    message: 'A new preset copy was created from the current record.',
  },
  deleteConfirmTitle: 'Delete this preset?',
  deleteConfirmMessage: 'This preset will be removed from the archive. This action cannot be undone.',
  deleteLoadingTitle: 'Deleting preset',
  deleteLoadingMessage: 'Removing the current preset from the archive and returning to the preset list.',
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
