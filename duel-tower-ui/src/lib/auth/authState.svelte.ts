import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'
import { setApiUnauthorizedHandler } from '../api/client'
import { ApiError, getApiErrorMessage } from '../api/types'
import type { AuthUser, LoginRequest } from './types'

function createAuthState() {
  let user = $state<AuthUser | null>(null)
  let loading = $state(false)
  let error = $state<string | null>(null)
  let initialized = $state(false)
  let bootstrapTask: Promise<AuthUser | null> | null = null

  function resetSessionState(nextError: string | null = null) {
    user = null
    initialized = true
    error = nextError
  }

  function clearError() {
    error = null
  }

  function handleUnauthorized(
    message = 'Your session has expired. Sign in again to continue.',
  ) {
    resetSessionState(message)
  }

  setApiUnauthorizedHandler(() => {
    handleUnauthorized()
  })

  async function bootstrap() {
    if (bootstrapTask) return bootstrapTask

    loading = true
    clearError()

    bootstrapTask = (async () => {
      try {
        const nextUser = await getCurrentUser()
        user = nextUser
        initialized = true
        return nextUser
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          resetSessionState()
          return null
        }

        resetSessionState(getApiErrorMessage(cause, 'Unable to restore the current session.'))
        return null
      } finally {
        loading = false
        bootstrapTask = null
      }
    })()

    return bootstrapTask
  }

  async function login(credentials: LoginRequest) {
    loading = true
    clearError()

    try {
      const nextUser = await loginRequest(credentials)
      user = nextUser
      initialized = true
      return nextUser
    } catch (cause) {
      error = getApiErrorMessage(cause, 'Unable to sign in with the provided credentials.')
      initialized = true
      throw cause
    } finally {
      loading = false
    }
  }

  async function logout() {
    loading = true
    clearError()

    try {
      await logoutRequest()
    } catch (cause) {
      if (!(cause instanceof ApiError && cause.status === 401)) {
        error = getApiErrorMessage(cause, 'Unable to sign out from the current session.')
        throw cause
      }
    } finally {
      user = null
      initialized = true
      loading = false
    }
  }

  return {
    get user() {
      return user
    },
    get isAuthenticated() {
      return user !== null
    },
    get loading() {
      return loading
    },
    get error() {
      return error
    },
    get initialized() {
      return initialized
    },
    bootstrap,
    handleUnauthorized,
    login,
    logout,
    clearError,
  }
}

export const authState = createAuthState()
