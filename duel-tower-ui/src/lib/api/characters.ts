import { apiDeleteVoid, apiGet, apiPost, apiPut } from './client'
import type {
  CharacterProfileIdentifier,
  CharacterCombatStats,
  CharacterCombatStatsPreviewRequest,
  CharacterCreateOptionsResponse,
  CharacterCurrentSkillDeckRequest,
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

export function getCharacterCreateOptions() {
  return apiGet<CharacterCreateOptionsResponse>(`${CHARACTERS_API_BASE}/create-options`)
}

export function previewCharacterCombatStats(payload: CharacterCombatStatsPreviewRequest) {
  return apiPost<CharacterCombatStats, CharacterCombatStatsPreviewRequest>(
    `${CHARACTERS_API_BASE}/combat-stats/preview`,
    payload,
  )
}

export function createCharacter(payload: CharacterProfileRequest) {
  return apiPost<CharacterProfileResponse, CharacterProfileRequest>(CHARACTERS_API_BASE, payload)
}

export function updateCharacter(id: CharacterProfileIdentifier, payload: CharacterProfileRequest) {
  return apiPut<CharacterProfileResponse, CharacterProfileRequest>(getCharacterResourcePath(id), payload)
}

export function applySavedDeckToCharacter(
  characterId: CharacterProfileIdentifier,
  deckId: string | number,
) {
  const normalizedDeckId = String(deckId).trim()

  if (!normalizedDeckId) {
    throw new Error('deck id is required')
  }

  return apiPost<CharacterProfileResponse, null>(
    `${getCharacterResourcePath(characterId)}/current-skill-deck/from-deck/${encodeURIComponent(normalizedDeckId)}`,
    null,
  )
}

export function replaceCharacterCurrentSkillDeck(
  characterId: CharacterProfileIdentifier,
  payload: CharacterCurrentSkillDeckRequest,
) {
  return apiPut<CharacterProfileResponse, CharacterCurrentSkillDeckRequest>(
    `${getCharacterResourcePath(characterId)}/current-skill-deck`,
    payload,
  )
}

export function deleteCharacter(id: CharacterProfileIdentifier) {
  return apiDeleteVoid(getCharacterResourcePath(id))
}
