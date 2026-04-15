import { readStoredSessionAccess, type StoredSessionAccess } from '../session/access'
import { apiGet, apiRequest } from './client'
import type { DeckIdentifier } from './deckTypes'
import {
  findScreenAction,
  isScreenActionDisabled,
  ScreenActionDisabledError,
  type ScreenActionAuth,
  type ScreenActionDto,
  type ScreenActionResponse,
  type ScreenKey,
  type ScreenRequestParamsByKey,
  type ScreenResponseBase,
} from './screenTypes'
import type { ApiRequestOptions } from './types'

const SCREENS_API_BASE = '/api/screens'
const GM_TOKEN_HEADER = 'X-GM-Token'
const PLAYER_TOKEN_HEADER = 'X-Player-Token'

type ScreenQueryValue = string | number | boolean | null | undefined

export type ScreenQueryParams = Record<string, ScreenQueryValue>

export type ScreenAuthContext = {
  sessionAccess?: StoredSessionAccess | null
  gmToken?: string | null
  playerToken?: string | null
  playerId?: string | null
  extraHeaders?: HeadersInit
}

export type ScreenAuthHeaderResolverArgs = {
  auth: ScreenActionAuth
  href: string
  screenKey?: ScreenKey | null
  actionId?: string | null
  context: ScreenAuthContext
}

export type ScreenAuthHeaderResolver = (
  args: ScreenAuthHeaderResolverArgs,
) => HeadersInit | null | undefined | Promise<HeadersInit | null | undefined>

export type ScreenRequestOptions = Omit<ApiRequestOptions, 'method' | 'body'> & {
  authContext?: ScreenAuthContext
  authHeaderResolver?: ScreenAuthHeaderResolver
  query?: ScreenQueryParams
}

export type InvokeScreenActionOptions = Omit<ApiRequestOptions, 'method'> & {
  authContext?: ScreenAuthContext
  authHeaderResolver?: ScreenAuthHeaderResolver
  allowDisabled?: boolean
}

const screenRouteAuth: Record<ScreenKey, ScreenActionAuth> = {
  PlayerLobby: 'sessionReadable',
  GmLobby: 'sessionReadable',
  Combat: 'sessionReadable',
  DeckEditor: 'loginCookie',
}

function normalizePathValue(value: string | number, fieldName: string) {
  const normalized = String(value).trim()

  if (!normalized) {
    throw new Error(`${fieldName} is required`)
  }

  return encodeURIComponent(normalized)
}

function normalizeOptionalToken(value: string | null | undefined) {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function normalizeOptionalPlayerId(value: string | null | undefined) {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function getSessionScreenBasePath(code: string) {
  return `${SCREENS_API_BASE}/sessions/${normalizePathValue(code, 'session code')}`
}

function getDeckEditorScreenPath(deckId?: DeckIdentifier | null) {
  const normalizedDeckId = deckId == null ? '' : String(deckId).trim()

  return normalizedDeckId
    ? `${SCREENS_API_BASE}/decks/${normalizePathValue(normalizedDeckId, 'deck id')}/editor`
    : `${SCREENS_API_BASE}/decks/new/editor`
}

function appendQueryParams(href: string, query: ScreenQueryParams | undefined) {
  if (!query) {
    return href
  }

  const searchParams = new URLSearchParams()

  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined) {
      continue
    }

    searchParams.set(key, String(value))
  }

  const queryString = searchParams.toString()
  return queryString ? `${href}?${queryString}` : href
}

function mergeHeaders(...sources: (HeadersInit | null | undefined)[]) {
  const mergedHeaders = new Headers()

  for (const source of sources) {
    if (!source) {
      continue
    }

    const sourceHeaders = new Headers(source)
    sourceHeaders.forEach((value, key) => {
      mergedHeaders.set(key, value)
    })
  }

  return mergedHeaders
}

function resolveScreenAuthContext(context: ScreenAuthContext | undefined): ScreenAuthContext {
  const sessionAccess =
    context?.sessionAccess === undefined ? readStoredSessionAccess() : context.sessionAccess

  return {
    sessionAccess,
    gmToken: normalizeOptionalToken(context?.gmToken) ?? normalizeOptionalToken(sessionAccess?.gmToken),
    playerToken:
      normalizeOptionalToken(context?.playerToken) ?? normalizeOptionalToken(sessionAccess?.playerToken),
    playerId:
      normalizeOptionalPlayerId(context?.playerId) ?? normalizeOptionalPlayerId(sessionAccess?.playerId),
    extraHeaders: context?.extraHeaders,
  }
}

function applyOptionalHeader(headers: Headers, headerName: string, value: string | null | undefined) {
  if (value) {
    headers.set(headerName, value)
  }
}

export function defaultScreenAuthHeaderResolver({
  auth,
  context,
}: ScreenAuthHeaderResolverArgs) {
  const resolvedContext = resolveScreenAuthContext(context)
  const headers = new Headers(resolvedContext.extraHeaders)

  switch (auth) {
    case 'gmToken':
      applyOptionalHeader(headers, GM_TOKEN_HEADER, resolvedContext.gmToken)
      break
    case 'playerToken':
      applyOptionalHeader(headers, PLAYER_TOKEN_HEADER, resolvedContext.playerToken)
      break
    case 'sessionReadable':
      if (resolvedContext.gmToken) {
        headers.set(GM_TOKEN_HEADER, resolvedContext.gmToken)
      } else {
        applyOptionalHeader(headers, PLAYER_TOKEN_HEADER, resolvedContext.playerToken)
      }
      break
    case 'public':
    case 'loginCookie':
      break
    default:
      break
  }

  return headers
}

function resolveHandleUnauthorized(auth: ScreenActionAuth, handleUnauthorized: boolean | undefined) {
  if (typeof handleUnauthorized === 'boolean') {
    return handleUnauthorized
  }

  return auth === 'loginCookie'
}

async function createScreenRequestOptions({
  auth,
  href,
  screenKey,
  actionId,
  authContext,
  authHeaderResolver,
  headers,
  handleUnauthorized,
  ...rest
}: {
  auth: ScreenActionAuth
  href: string
  screenKey?: ScreenKey | null
  actionId?: string | null
  authContext?: ScreenAuthContext
  authHeaderResolver?: ScreenAuthHeaderResolver
  headers?: HeadersInit
  handleUnauthorized?: boolean
} & Omit<ApiRequestOptions, 'headers'>): Promise<ApiRequestOptions> {
  const resolvedContext = resolveScreenAuthContext(authContext)
  const resolveHeaders = authHeaderResolver ?? defaultScreenAuthHeaderResolver
  const authHeaders = await resolveHeaders({
    auth,
    href,
    screenKey,
    actionId,
    context: resolvedContext,
  })

  return {
    ...rest,
    headers: mergeHeaders(headers, authHeaders),
    handleUnauthorized: resolveHandleUnauthorized(auth, handleUnauthorized),
  }
}

function resolveActionArgs(
  actionOrScreen: ScreenActionDto | Pick<ScreenResponseBase, 'possibleActions'>,
  actionIdOrOptions?: string | InvokeScreenActionOptions,
  maybeOptions?: InvokeScreenActionOptions,
) {
  if (typeof actionIdOrOptions === 'string') {
    const action = findScreenAction(actionOrScreen as Pick<ScreenResponseBase, 'possibleActions'>, actionIdOrOptions)

    if (!action) {
      throw new Error(`Screen action not found: ${actionIdOrOptions}`)
    }

    return {
      action,
      options: maybeOptions ?? {},
    }
  }

  return {
    action: actionOrScreen as ScreenActionDto,
    options: actionIdOrOptions ?? {},
  }
}

export function buildScreenHref(screenKey: 'DeckEditor', params?: ScreenRequestParamsByKey['DeckEditor']): string
export function buildScreenHref<TKey extends Exclude<ScreenKey, 'DeckEditor'>>(
  screenKey: TKey,
  params: ScreenRequestParamsByKey[TKey],
): string
export function buildScreenHref(screenKey: ScreenKey, params?: ScreenRequestParamsByKey[ScreenKey]) {
  return buildScreenHrefInternal(screenKey, params)
}

function buildScreenHrefInternal(screenKey: ScreenKey, params?: ScreenRequestParamsByKey[ScreenKey]) {
  switch (screenKey) {
    case 'PlayerLobby':
      if (!params || !('code' in params)) {
        throw new Error('PlayerLobby params are required')
      }
      return `${getSessionScreenBasePath(params.code)}/player-lobby`
    case 'GmLobby':
      if (!params || !('code' in params)) {
        throw new Error('GmLobby params are required')
      }
      return `${getSessionScreenBasePath(params.code)}/gm-lobby`
    case 'Combat':
      if (!params || !('code' in params)) {
        throw new Error('Combat params are required')
      }
      return `${getSessionScreenBasePath(params.code)}/combat`
    case 'DeckEditor':
      return getDeckEditorScreenPath(params && 'deckId' in params ? params.deckId : null)
    default:
      throw new Error(`Unsupported screen key: ${screenKey}`)
  }
}

export function getScreen<TScreen extends ScreenResponseBase = ScreenResponseBase>(
  screenKey: 'DeckEditor',
  params?: ScreenRequestParamsByKey['DeckEditor'],
  options?: ScreenRequestOptions,
): Promise<TScreen>
export function getScreen<TKey extends Exclude<ScreenKey, 'DeckEditor'>, TScreen extends ScreenResponseBase = ScreenResponseBase>(
  screenKey: TKey,
  params: ScreenRequestParamsByKey[TKey],
  options?: ScreenRequestOptions,
): Promise<TScreen>
export async function getScreen<TScreen extends ScreenResponseBase = ScreenResponseBase>(
  screenKey: ScreenKey,
  params?: ScreenRequestParamsByKey[ScreenKey],
  options: ScreenRequestOptions = {},
) {
  const href = appendQueryParams(buildScreenHrefInternal(screenKey, params), options.query)
  const requestOptions = await createScreenRequestOptions({
    ...options,
    auth: screenRouteAuth[screenKey],
    href,
    screenKey,
  })

  return apiGet<TScreen>(href, requestOptions)
}

export function invokeScreenAction<
  TScreen extends ScreenResponseBase = ScreenResponseBase,
  TResponse = ScreenActionResponse<TScreen>,
>(action: ScreenActionDto, options?: InvokeScreenActionOptions): Promise<TResponse>
export function invokeScreenAction<
  TScreen extends ScreenResponseBase = ScreenResponseBase,
  TResponse = ScreenActionResponse<TScreen>,
>(
  screen: Pick<ScreenResponseBase, 'possibleActions'>,
  actionId: string,
  options?: InvokeScreenActionOptions,
): Promise<TResponse>
export async function invokeScreenAction<
  TScreen extends ScreenResponseBase = ScreenResponseBase,
  TResponse = ScreenActionResponse<TScreen>,
>(
  actionOrScreen: ScreenActionDto | Pick<ScreenResponseBase, 'possibleActions'>,
  actionIdOrOptions?: string | InvokeScreenActionOptions,
  maybeOptions?: InvokeScreenActionOptions,
) {
  const { action, options } = resolveActionArgs(actionOrScreen, actionIdOrOptions, maybeOptions)

  if (!options.allowDisabled && isScreenActionDisabled(action)) {
    throw new ScreenActionDisabledError(action)
  }

  const requestOptions = await createScreenRequestOptions({
    ...options,
    auth: action.auth,
    href: action.href,
    actionId: action.id,
  })

  return apiRequest<TResponse>(action.href, {
    ...requestOptions,
    method: action.method,
    body: options.body,
  })
}
