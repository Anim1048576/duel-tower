import { ApiError, type ApiErrorCode, type ApiRequestOptions } from './types'

const DEFAULT_API_CREDENTIALS: RequestCredentials = 'include'
let unauthorizedHandler: ((error: ApiError) => void | Promise<void>) | null = null

export function setApiUnauthorizedHandler(handler: ((error: ApiError) => void | Promise<void>) | null) {
  unauthorizedHandler = handler
}

function isJsonBody(body: ApiRequestOptions['body']): body is Record<string, unknown> | unknown[] {
  return (
    body !== null &&
    body !== undefined &&
    !(body instanceof FormData) &&
    !(body instanceof URLSearchParams) &&
    !(body instanceof Blob) &&
    !(body instanceof ArrayBuffer) &&
    !ArrayBuffer.isView(body) &&
    typeof body !== 'string'
  )
}

function getStatusCode(status: number): ApiErrorCode {
  switch (status) {
    case 400:
      return 'bad_request'
    case 401:
      return 'unauthorized'
    case 403:
      return 'forbidden'
    case 404:
      return 'not_found'
    case 409:
      return 'conflict'
    default:
      return status >= 500 ? 'server_error' : 'http_error'
  }
}

function getMessageFromBody(body: unknown) {
  if (typeof body === 'string') {
    const trimmed = body.trim()
    return trimmed ? trimmed : null
  }

  if (!body || typeof body !== 'object') return null

  const message =
    ('message' in body && typeof body.message === 'string' && body.message) ||
    ('error' in body && typeof body.error === 'string' && body.error) ||
    ('detail' in body && typeof body.detail === 'string' && body.detail) ||
    null

  return message
}

function getStatusMessage(status: number) {
  switch (status) {
    case 400:
      return '요청을 처리하지 못했습니다. 입력값을 확인해 주세요.'
    case 401:
      return '로그인이 필요합니다.'
    case 403:
      return '권한이 없습니다.'
    case 404:
      return 'The requested resource could not be found.'
    case 409:
      return '현재 상태와 충돌했습니다.'
    default:
      return status >= 500
        ? '서버 오류입니다. 잠시 후 다시 시도해 주세요.'
        : 'The request failed unexpectedly.'
  }
}

async function parseResponseBody(response: Response) {
  if (response.status === 204) return undefined

  const text = await response.text()
  if (!text) return undefined

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) return text

  try {
    return JSON.parse(text) as unknown
  } catch (cause) {
    throw new ApiError({
      status: response.status,
      statusText: response.statusText,
      code: 'parse_error',
      message: 'The server returned an invalid JSON response.',
      cause,
    })
  }
}

function createRequestInit(options: ApiRequestOptions): RequestInit {
  const { body, headers, credentials, handleUnauthorized: _handleUnauthorized, ...rest } = options
  const requestHeaders = new Headers(headers)
  let requestBody = body as BodyInit | null | undefined

  if (isJsonBody(body)) {
    requestHeaders.set('Content-Type', 'application/json')
    requestBody = JSON.stringify(body)
  }

  requestHeaders.set('Accept', 'application/json')

  return {
    ...rest,
    body: requestBody ?? undefined,
    credentials: credentials ?? DEFAULT_API_CREDENTIALS,
    headers: requestHeaders,
  }
}

export async function apiRequest<TResponse>(input: string, options: ApiRequestOptions = {}) {
  let response: Response

  try {
    response = await fetch(input, createRequestInit(options))
  } catch (cause) {
    throw new ApiError({
      status: null,
      code: 'network_error',
      message: '서버에 연결할 수 없습니다. 다시 시도해 주세요.',
      cause,
    })
  }

  const body = await parseResponseBody(response)

  if (!response.ok) {
    const error = new ApiError({
      status: response.status,
      statusText: response.statusText,
      code: getStatusCode(response.status),
      message: getMessageFromBody(body) ?? getStatusMessage(response.status),
      body,
    })

    if (response.status === 401 && options.handleUnauthorized !== false && unauthorizedHandler) {
      await unauthorizedHandler(error)
    }

    throw error
  }

  return body as TResponse
}

export async function apiGet<TResponse>(input: string, options: ApiRequestOptions = {}) {
  return apiRequest<TResponse>(input, {
    ...options,
    method: options.method ?? 'GET',
  })
}

export async function apiPost<TResponse, TBody = Record<string, unknown>>(
  input: string,
  body?: TBody,
  options: ApiRequestOptions = {},
) {
  return apiRequest<TResponse>(input, {
    ...options,
    method: options.method ?? 'POST',
    body: (body ?? null) as ApiRequestOptions['body'],
  })
}

export async function apiPostVoid<TBody = Record<string, unknown>>(
  input: string,
  body?: TBody,
  options: ApiRequestOptions = {},
) {
  await apiPost<void, TBody>(input, body, options)
}

export async function apiPut<TResponse, TBody = Record<string, unknown>>(
  input: string,
  body?: TBody,
  options: ApiRequestOptions = {},
) {
  return apiRequest<TResponse>(input, {
    ...options,
    method: options.method ?? 'PUT',
    body: (body ?? null) as ApiRequestOptions['body'],
  })
}

export async function apiDelete<TResponse>(input: string, options: ApiRequestOptions = {}) {
  return apiRequest<TResponse>(input, {
    ...options,
    method: options.method ?? 'DELETE',
  })
}

export async function apiDeleteVoid(input: string, options: ApiRequestOptions = {}) {
  await apiDelete<void>(input, options)
}
