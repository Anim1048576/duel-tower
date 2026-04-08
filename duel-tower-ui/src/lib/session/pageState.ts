export type SessionPageFeedback = {
  title: string
  message: string
}

const sessionPageFeedbackKey = 'duel-tower:session-page-feedback'

export const sessionPageStateCopy = {
  loading: {
    title: 'Loading session state',
    message: 'Fetching the latest live session state for the current code.',
  },
  notFound: {
    title: 'Requested session is unavailable',
    message: 'The requested session code could not be found in the session API.',
  },
  invalidPlayerAccess: {
    title: 'Player access required',
    message: 'Player session access is not available for the requested code. Re-enter through the session entry page first.',
  },
  invalidGmAccess: {
    title: 'GM access required',
    message: 'GM session access is not available for the requested code. Re-enter through the session entry page first.',
  },
} as const

export const sessionEntryStateCopy = {
  createdFeedback: {
    title: 'GM session created',
    message: 'The new session is ready and GM access has been restored for the current code.',
  },
  joinedFeedback: {
    title: 'Player session joined',
    message: 'The player lobby is ready and player access has been restored for the current code.',
  },
  leftFeedback: {
    title: 'Left session',
    message: 'The current player session was closed and the entry screen is ready for the next join or create flow.',
  },
} as const

export const playerLobbyStateCopy = {
  presetAppliedFeedback: {
    title: 'Preset applied',
    message: 'The selected preset was applied to the current live loadout.',
  },
  loadoutSavedFeedback: {
    title: 'Loadout saved',
    message: 'The current live loadout was saved and synced from the latest session response.',
  },
} as const

export const gmLobbyStateCopy = {
  playerRemovedFeedback: {
    title: 'Player removed',
    message: 'The selected player was removed and the GM lobby synced from the latest session response.',
  },
  sessionResetFeedback: {
    title: 'Session reset',
    message: 'The session was reset and the GM lobby synced from the latest session response.',
  },
} as const

function getSessionPageStorage() {
  if (typeof window === 'undefined') {
    return null
  }

  return window.sessionStorage
}

export function setSessionPageFeedback(feedback: SessionPageFeedback) {
  const storage = getSessionPageStorage()

  if (!storage) {
    return
  }

  storage.setItem(sessionPageFeedbackKey, JSON.stringify(feedback))
}

export function readSessionPageFeedback() {
  const storage = getSessionPageStorage()

  if (!storage) {
    return null
  }

  const raw = storage.getItem(sessionPageFeedbackKey)

  if (!raw) {
    return null
  }

  storage.removeItem(sessionPageFeedbackKey)

  try {
    const parsed = JSON.parse(raw) as Partial<SessionPageFeedback>

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
