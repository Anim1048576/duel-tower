import { apiDeleteVoid, apiGet, apiPost, apiPut } from './client'
import type {
  CharacterProfileIdentifier,
  CharacterProfileRequest,
  CharacterProfileResponse,
} from './characterTypes'

const CHARACTERS_API_BASE = '/api/content/characters'

function getCharacterResourcePath(id: CharacterProfileIdentifier) {
  const normalizedId = String(id).trim()

  if (!normalizedId) {
    throw new Error('character id is required')
  }

  return `${CHARACTERS_API_BASE}/${encodeURIComponent(normalizedId)}`
}

export function listCharacters() {
  return apiGet<CharacterProfileResponse[]>(CHARACTERS_API_BASE)
}

export function getCharacter(id: CharacterProfileIdentifier) {
  return apiGet<CharacterProfileResponse>(getCharacterResourcePath(id))
}

export function createCharacter(payload: CharacterProfileRequest) {
  return apiPost<CharacterProfileResponse, CharacterProfileRequest>(CHARACTERS_API_BASE, payload)
}

export function updateCharacter(id: CharacterProfileIdentifier, payload: CharacterProfileRequest) {
  return apiPut<CharacterProfileResponse, CharacterProfileRequest>(getCharacterResourcePath(id), payload)
}

export function deleteCharacter(id: CharacterProfileIdentifier) {
  return apiDeleteVoid(getCharacterResourcePath(id))
}
