import type { SessionCode, SessionRole } from '../api/sessionTypes'

export type StoredSessionAccess = {
  code: SessionCode
  role: SessionRole
  gmToken?: string
  playerToken?: string
  playerId?: string
  characterId?: number
}

export type StoredSessionAccessPatch = Partial<StoredSessionAccess>

const storedSessionAccessKey = 'duel-tower:session-access'

function getSessionAccessStorage() {
  if (typeof window === 'undefined') {
    return null
  }

  return window.sessionStorage
}

export function normalizeSessionCode(value: string) {
  return value.trim().toUpperCase()
}

function normalizeOptionalText(value: string | undefined) {
  const normalized = value?.trim()
  return normalized ? normalized : undefined
}

function normalizeOptionalCharacterId(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : undefined
}

function sanitizeStoredSessionAccess(access: StoredSessionAccess): StoredSessionAccess {
  return {
    code: normalizeSessionCode(access.code),
    role: access.role,
    gmToken: normalizeOptionalText(access.gmToken),
    playerToken: normalizeOptionalText(access.playerToken),
    playerId: normalizeOptionalText(access.playerId),
    characterId: normalizeOptionalCharacterId(access.characterId),
  }
}

function isSessionRole(value: unknown): value is SessionRole {
  return value === 'gm' || value === 'player'
}

function isStoredSessionAccess(value: unknown): value is StoredSessionAccess {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<StoredSessionAccess>

  return (
    typeof candidate.code === 'string' &&
    candidate.code.trim().length > 0 &&
    isSessionRole(candidate.role)
  )
}

export function isStoredGmSessionAccess(
  access: StoredSessionAccess | null,
): access is StoredSessionAccess & { role: 'gm'; gmToken: string } {
  return access?.role === 'gm' && typeof access.gmToken === 'string' && access.gmToken.length > 0
}

export function isStoredPlayerSessionAccess(
  access: StoredSessionAccess | null,
): access is StoredSessionAccess & { role: 'player'; playerToken: string; playerId: string } {
  return (
    access?.role === 'player' &&
    typeof access.playerToken === 'string' &&
    access.playerToken.length > 0 &&
    typeof access.playerId === 'string' &&
    access.playerId.length > 0
  )
}

export function readStoredSessionAccess() {
  const storage = getSessionAccessStorage()

  if (!storage) {
    return null
  }

  const raw = storage.getItem(storedSessionAccessKey)

  if (!raw) {
    return null
  }

  try {
    const parsed = JSON.parse(raw) as unknown

    if (!isStoredSessionAccess(parsed)) {
      storage.removeItem(storedSessionAccessKey)
      return null
    }

    return sanitizeStoredSessionAccess(parsed)
  } catch {
    storage.removeItem(storedSessionAccessKey)
    return null
  }
}

export function setStoredSessionAccess(access: StoredSessionAccess) {
  const storage = getSessionAccessStorage()
  const normalized = sanitizeStoredSessionAccess(access)

  if (!storage) {
    return normalized
  }

  storage.setItem(storedSessionAccessKey, JSON.stringify(normalized))
  return normalized
}

export function updateStoredSessionAccess(patch: StoredSessionAccessPatch) {
  const current = readStoredSessionAccess()

  if (!current) {
    return null
  }

  return setStoredSessionAccess({
    ...current,
    ...patch,
  })
}

export function clearStoredSessionAccess() {
  const storage = getSessionAccessStorage()

  if (!storage) {
    return
  }

  storage.removeItem(storedSessionAccessKey)
}

export function hasStoredSessionCode(access: StoredSessionAccess | null, code: string) {
  if (!access) {
    return false
  }

  return access.code === normalizeSessionCode(code)
}
