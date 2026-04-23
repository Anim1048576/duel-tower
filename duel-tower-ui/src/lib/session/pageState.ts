export type SessionPageFeedback = {
  title: string
  message: string
}

const sessionPageFeedbackKey = 'duel-tower:session-page-feedback'

export const sessionPageStateCopy = {
  loading: {
    title: 'Loading session state',
    message: '세션 상태를 불러오는 중입니다.',
  },
  notFound: {
    title: 'Requested session is unavailable',
    message: '요청한 세션을 찾을 수 없습니다.',
  },
  invalidPlayerAccess: {
    title: 'Player access required',
    message: '플레이어 권한이 없습니다. 세션 입장에서 다시 들어가 주세요.',
  },
  invalidGmAccess: {
    title: 'GM access required',
    message: 'GM 권한이 없습니다. 세션 입장에서 다시 들어가 주세요.',
  },
} as const

export const sessionEntryStateCopy = {
  createdFeedback: {
    title: 'GM session created',
    message: '새 세션이 준비되었습니다.',
  },
  joinedFeedback: {
    title: 'Player session joined',
    message: '플레이어 로비에 입장했습니다.',
  },
  leftFeedback: {
    title: 'Left session',
    message: '세션에서 나왔습니다.',
  },
} as const

export const playerLobbyStateCopy = {
  presetAppliedFeedback: {
    title: 'Preset applied',
    message: '선택한 프리셋을 적용했습니다.',
  },
  loadoutSavedFeedback: {
    title: 'Loadout saved',
    message: '로드아웃을 저장했습니다.',
  },
} as const

export const gmLobbyStateCopy = {
  playerRemovedFeedback: {
    title: 'Player removed',
    message: '선택한 플레이어를 제거했습니다.',
  },
  sessionResetFeedback: {
    title: 'Session reset',
    message: '세션을 초기화했습니다.',
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
