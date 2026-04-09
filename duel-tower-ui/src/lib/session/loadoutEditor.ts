import type { PlayerStateDto, SessionStateDto } from '../api/sessionTypes'
import {
  normalizePresetIdentifier,
  normalizePresetIdentifierList,
} from '../presets/editorModel'

export type SessionLoadoutDraft = {
  characterId: number | null
  deckOwnedCardIds: string[]
  exCardId: string
  passiveIds: string[]
}

export function createEmptySessionLoadoutDraft(): SessionLoadoutDraft {
  return {
    characterId: null,
    deckOwnedCardIds: [],
    exCardId: '',
    passiveIds: [],
  }
}

export function cloneSessionLoadoutDraft(draft: SessionLoadoutDraft): SessionLoadoutDraft {
  return {
    characterId: draft.characterId,
    deckOwnedCardIds: [...draft.deckOwnedCardIds],
    exCardId: draft.exCardId,
    passiveIds: [...draft.passiveIds],
  }
}

export function createSessionLoadoutDraft(
  player: PlayerStateDto | null,
  session: SessionStateDto | null,
  fallbackCharacterId: number | null = null,
): SessionLoadoutDraft {
  if (!player) {
    return createEmptySessionLoadoutDraft()
  }

  return {
    characterId:
      typeof fallbackCharacterId === 'number' && Number.isFinite(fallbackCharacterId) && fallbackCharacterId > 0
        ? fallbackCharacterId
        : null,
    deckOwnedCardIds: normalizePresetIdentifierList(player.deckOwnedCardIds),
    exCardId: resolveSessionLoadoutExCardId(player, session),
    passiveIds: normalizePresetIdentifierList(player.passiveIds),
  }
}

export function resolveSessionLoadoutExCardId(
  player: PlayerStateDto | null,
  session: SessionStateDto | null,
) {
  const currentExCard = player?.exCard?.trim()

  if (!currentExCard) {
    return ''
  }

  const resolvedExCardId = session?.cards[currentExCard]?.defId
  return normalizePresetIdentifier(resolvedExCardId ?? currentExCard)
}

export function normalizeSessionLoadoutDraft(draft: SessionLoadoutDraft): SessionLoadoutDraft {
  return {
    characterId:
      typeof draft.characterId === 'number' && Number.isFinite(draft.characterId) && draft.characterId > 0
        ? draft.characterId
        : null,
    deckOwnedCardIds: normalizePresetIdentifierList(draft.deckOwnedCardIds),
    exCardId: normalizePresetIdentifier(draft.exCardId),
    passiveIds: normalizePresetIdentifierList(draft.passiveIds),
  }
}

function areStringListsEqual(source: readonly string[], draft: readonly string[]) {
  if (source.length !== draft.length) {
    return false
  }

  for (const [index, sourceValue] of source.entries()) {
    if (sourceValue !== draft[index]) {
      return false
    }
  }

  return true
}

export function isSessionLoadoutDraftDirty(source: SessionLoadoutDraft, draft: SessionLoadoutDraft) {
  if (source.characterId !== draft.characterId) {
    return true
  }

  if (source.exCardId !== draft.exCardId) {
    return true
  }

  if (!areStringListsEqual(source.deckOwnedCardIds, draft.deckOwnedCardIds)) {
    return true
  }

  if (!areStringListsEqual(source.passiveIds, draft.passiveIds)) {
    return true
  }

  return false
}
