import type { SessionRequestAccess, SessionStateDto, SessionVersion } from '../api/sessionTypes'
import {
  startLiveSessionPolling,
  type LiveSessionPollingHandle,
} from './liveSessionPolling'

export type LiveSessionPageContext<TAccess> = {
  code: string | null
  access: TAccess
  invalidMessage: string | null
}

export type LiveSessionPagePollingContext<TAccess> = {
  code: string
  access: TAccess
  state: SessionStateDto
}

export type LiveSessionPageOptions<TAccess> = {
  readCode: () => string | null
  readAccess: () => TAccess
  getInvalidMessage?: (code: string | null, access: TAccess) => string | null
  canLoad?: (context: LiveSessionPageContext<TAccess>) => boolean
  loadState: (code: string) => Promise<SessionStateDto>
  getPollingAccess: (access: TAccess) => SessionRequestAccess | null
  canPoll: (context: LiveSessionPagePollingContext<TAccess>) => boolean
  onBeforeLoad: (context: LiveSessionPageContext<TAccess>) => void
  onLoaded: (state: SessionStateDto, context: LiveSessionPageContext<TAccess>) => void
  onPolled: (state: SessionStateDto, context: LiveSessionPageContext<TAccess>) => void
  onNotFound?: (error: unknown, context: LiveSessionPageContext<TAccess>) => void
  onError?: (error: unknown, context: LiveSessionPageContext<TAccess>) => void
  onLoadSettled?: (context: LiveSessionPageContext<TAccess>) => void
  onPollingError?: (error: unknown) => void
  shouldTreatAsNotFound?: (error: unknown) => boolean
  intervalMs?: number
}

function isDefaultNotFoundError(error: unknown) {
  return typeof error === 'object' && error !== null && 'status' in error && error.status === 404
}

export function createLiveSessionPage<TAccess>(options: LiveSessionPageOptions<TAccess>) {
  const {
    readCode,
    readAccess,
    getInvalidMessage = () => null,
    canLoad = (context) => Boolean(context.code) && !context.invalidMessage,
    loadState,
    getPollingAccess,
    canPoll,
    onBeforeLoad,
    onLoaded,
    onPolled,
    onNotFound,
    onError,
    onLoadSettled,
    onPollingError,
    shouldTreatAsNotFound = isDefaultNotFoundError,
    intervalMs,
  } = options

  let requestSequence = 0
  let pollingHandle: LiveSessionPollingHandle | null = null

  function getContext(overrides: Partial<Pick<LiveSessionPageContext<TAccess>, 'code' | 'access'>> = {}) {
    const code = overrides.code ?? readCode()
    const access = overrides.access ?? readAccess()

    return {
      code,
      access,
      invalidMessage: getInvalidMessage(code, access),
    }
  }

  function stopPolling() {
    pollingHandle?.stop()
    pollingHandle = null
  }

  function updatePollingVersion(nextVersion: SessionVersion) {
    pollingHandle?.updateVersion(nextVersion)
  }

  function invalidate() {
    requestSequence += 1
  }

  function startPolling(
    state: SessionStateDto,
    overrides: Partial<Pick<LiveSessionPageContext<TAccess>, 'code' | 'access'>> = {},
  ) {
    stopPolling()

    const context = getContext(overrides)
    const access = getPollingAccess(context.access)

    if (!context.code || !access || !canPoll({ code: context.code, access: context.access, state })) {
      return
    }

    pollingHandle = startLiveSessionPolling({
      code: context.code,
      access,
      initialVersion: state.version,
      intervalMs,
      onState: (nextState) => {
        const nextContext = getContext()

        if (
          !nextContext.code ||
          !canPoll({
            code: nextContext.code,
            access: nextContext.access,
            state: nextState,
          })
        ) {
          stopPolling()
          return
        }

        onPolled(nextState, nextContext)
      },
      onError: onPollingError,
    })
  }

  async function load() {
    const context = getContext()
    const requestId = ++requestSequence

    stopPolling()
    onBeforeLoad(context)

    if (!canLoad(context)) {
      if (requestId === requestSequence) {
        onLoadSettled?.(context)
      }
      return
    }

    try {
      const state = await loadState(context.code as string)

      if (requestId !== requestSequence) {
        return
      }

      onLoaded(state, context)
      startPolling(state, context)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (shouldTreatAsNotFound(error)) {
        onNotFound?.(error, context)
      } else {
        onError?.(error, context)
      }
    } finally {
      if (requestId === requestSequence) {
        onLoadSettled?.(context)
      }
    }
  }

  function dispose() {
    invalidate()
    stopPolling()
  }

  return {
    load,
    startPolling,
    stopPolling,
    updatePollingVersion,
    invalidate,
    dispose,
  }
}
