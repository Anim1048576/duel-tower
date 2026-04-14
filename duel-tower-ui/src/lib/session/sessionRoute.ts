import { resolveRouteMatch, type PageKey } from '../navigation'
import { readSelectionHandoff, selectionHandoffKeys } from '../selectionHandoff'
import { normalizeSessionCode, type StoredSessionAccess } from './access'

export type RequestedSessionCodeSource = 'stored-access' | 'route' | 'handoff' | 'none'

export type ReadRequestedSessionCodeOptions = {
  pageKey?: PageKey
  pathname?: string
  storedAccess?: StoredSessionAccess | null
  handoffCode?: string | null
  preferStoredAccess?: boolean
  allowHandoff?: boolean
}

function getCurrentPathname(pathname?: string) {
  if (typeof pathname === 'string') {
    return pathname
  }

  if (typeof window === 'undefined') {
    return null
  }

  return window.location.pathname
}

function normalizeOptionalSessionCode(value: string | null | undefined) {
  const normalized = value?.trim()
  return normalized ? normalizeSessionCode(normalized) : null
}

export function readSessionCodeFromRoute(pageKey: PageKey, pathname?: string) {
  const currentPathname = getCurrentPathname(pathname)

  if (!currentPathname) {
    return null
  }

  const match = resolveRouteMatch(currentPathname)

  if (match?.page.key !== pageKey) {
    return null
  }

  return normalizeOptionalSessionCode(match.params.code)
}

export function readRequestedSessionCodeFromAccessOrHandoff({
  pageKey,
  pathname,
  storedAccess,
  handoffCode,
  preferStoredAccess = true,
  allowHandoff = true,
}: ReadRequestedSessionCodeOptions = {}) {
  const routeCode = pageKey ? readSessionCodeFromRoute(pageKey, pathname) : null
  const storedCode = normalizeOptionalSessionCode(storedAccess?.code)
  const nextHandoffCode = allowHandoff
    ? normalizeOptionalSessionCode(handoffCode ?? readSelectionHandoff(selectionHandoffKeys.sessionCode))
    : null

  if (preferStoredAccess && storedCode) {
    return {
      code: storedCode,
      source: 'stored-access' as const,
    }
  }

  if (routeCode) {
    return {
      code: routeCode,
      source: 'route' as const,
    }
  }

  if (!preferStoredAccess && storedCode) {
    return {
      code: storedCode,
      source: 'stored-access' as const,
    }
  }

  if (nextHandoffCode) {
    return {
      code: nextHandoffCode,
      source: 'handoff' as const,
    }
  }

  return {
    code: null,
    source: 'none' as const,
  }
}
