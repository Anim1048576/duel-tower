export type ApiErrorCode =
  | 'bad_request'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'server_error'
  | 'network_error'
  | 'parse_error'
  | 'http_error'

export type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  body?: BodyInit | Record<string, unknown> | unknown[] | null
  handleUnauthorized?: boolean
}

export class ApiError extends Error {
  status: number | null
  statusText: string | null
  code: ApiErrorCode
  body?: unknown
  cause?: unknown

  constructor({
    status,
    statusText,
    code,
    message,
    body,
    cause,
  }: {
    status: number | null
    statusText?: string | null
    code: ApiErrorCode
    message: string
    body?: unknown
    cause?: unknown
  }) {
    super(message)

    this.name = 'ApiError'
    this.status = status
    this.statusText = statusText ?? null
    this.code = code
    this.body = body
    this.cause = cause
  }
}

export function getApiErrorMessage(
  error: unknown,
  fallback = 'An unexpected error occurred while processing the request.',
) {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}
