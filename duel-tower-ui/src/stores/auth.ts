import { writable } from 'svelte/store'
import { getCurrentUser, login as loginApi, logout as logoutApi, signup as signupApi, explainApiError } from '../lib/api'
import { KEY } from '../lib/keys'
import { load, save } from '../lib/storage'

export type AuthState = {
  username: string
  roles: string[]
  status: 'unknown' | 'authenticated' | 'anonymous'
  lastError?: string
}

const seed = load<AuthState>(KEY.auth, {
  username: '',
  roles: [],
  status: 'unknown',
})

export const auth = writable<AuthState>(seed)
auth.subscribe((v) => save(KEY.auth, v))

function setAuthenticated(username: string, roles: string[]) {
  auth.set({ username, roles, status: 'authenticated' })
}

function setAnonymous(lastError?: string) {
  auth.set({ username: '', roles: [], status: 'anonymous', lastError })
}

export async function restoreAuth() {
  try {
    const me = await getCurrentUser()
    setAuthenticated(me.username, me.roles ?? [])
  } catch {
    setAnonymous()
  }
}

export async function doSignup(username: string, password: string) {
  try {
    const user = await signupApi({ username: username.trim(), password })
    setAuthenticated(user.username, user.roles ?? [])
    return { ok: true as const }
  } catch (e) {
    const msg = explainApiError(e)
    setAnonymous(msg)
    return { ok: false as const, message: msg }
  }
}

export async function doLogin(username: string, password: string) {
  try {
    const user = await loginApi({ username: username.trim(), password })
    setAuthenticated(user.username, user.roles ?? [])
    return { ok: true as const }
  } catch (e) {
    const msg = explainApiError(e)
    setAnonymous(msg)
    return { ok: false as const, message: msg }
  }
}

export async function doLogout() {
  try {
    await logoutApi()
  } finally {
    setAnonymous()
  }
}
