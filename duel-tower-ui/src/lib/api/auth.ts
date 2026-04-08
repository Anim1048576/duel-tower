import { apiGet, apiPost, apiPostVoid } from './client'
import type { AuthUser, LoginRequest } from '../auth/types'

export function login(request: LoginRequest) {
  return apiPost<AuthUser, LoginRequest>('/api/auth/login', request, {
    handleUnauthorized: false,
  })
}

export function getCurrentUser() {
  return apiGet<AuthUser>('/api/auth/me', {
    handleUnauthorized: false,
  })
}

export function logout() {
  return apiPostVoid('/api/auth/logout', null, {
    handleUnauthorized: false,
  })
}
