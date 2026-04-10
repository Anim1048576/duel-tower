import { getSessionEvents, getSessionState } from '../api/sessions'
import type {
  SessionCode,
  SessionRequestAccess,
  SessionStateDto,
  SessionVersion,
} from '../api/sessionTypes'

const defaultLiveSessionPollIntervalMs = 800
const defaultLiveSessionEventLimit = 25

export type LiveSessionPollingOptions = {
  code: SessionCode
  access: SessionRequestAccess
  initialVersion: SessionVersion
  intervalMs?: number
  onState: (state: SessionStateDto) => void
  onError?: (error: unknown) => void
}

export type LiveSessionPollingHandle = {
  updateVersion: (nextVersion: SessionVersion) => void
  stop: () => void
}

function normalizePositiveNumber(value: number | undefined, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback
}

function getLatestSessionVersion(currentVersion: SessionVersion, nextVersion: SessionVersion) {
  if (!Number.isFinite(nextVersion)) {
    return currentVersion
  }

  return Math.max(currentVersion, nextVersion)
}

export function startLiveSessionPolling({
  code,
  access,
  initialVersion,
  intervalMs,
  onState,
  onError,
}: LiveSessionPollingOptions): LiveSessionPollingHandle {
  const pollingIntervalMs = normalizePositiveNumber(intervalMs, defaultLiveSessionPollIntervalMs)
  let lastSeenVersion = normalizePositiveNumber(initialVersion, 0)
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  let inFlight = false
  let disposed = false

  function updateVersion(nextVersion: SessionVersion) {
    lastSeenVersion = getLatestSessionVersion(lastSeenVersion, nextVersion)
  }

  function scheduleNextPoll() {
    if (disposed || timeoutId !== null) {
      return
    }

    timeoutId = setTimeout(() => {
      timeoutId = null
      void poll()
    }, pollingIntervalMs)
  }

  async function poll() {
    if (disposed) {
      return
    }

    if (inFlight) {
      scheduleNextPoll()
      return
    }

    inFlight = true

    try {
      const events = await getSessionEvents(
        code,
        { afterVersion: lastSeenVersion, limit: defaultLiveSessionEventLimit },
        access,
      )

      if (disposed || events.items.length === 0) {
        return
      }

      const nextState = await getSessionState(code)

      if (disposed) {
        return
      }

      updateVersion(nextState.version)
      onState(nextState)
    } catch (error) {
      if (!disposed) {
        onError?.(error)
      }
    } finally {
      inFlight = false
      scheduleNextPoll()
    }
  }

  scheduleNextPoll()

  return {
    updateVersion,
    stop() {
      disposed = true

      if (timeoutId !== null) {
        clearTimeout(timeoutId)
        timeoutId = null
      }
    },
  }
}
