import type {
  PlayerStateDto,
  PreviewSessionLoadoutDraftDto,
  PreviewSessionLoadoutRequest,
  SessionStateDto,
} from '../api/sessionTypes'
import type { PlayerLobbyLoadoutDto, PlayerLobbySaveLoadoutPayload } from '../api/screenTypes'
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

type SessionLoadoutDraftLike = {
  characterId: number | null | undefined
  deckOwnedCardIds: readonly string[] | null | undefined
  exCardId: string | null | undefined
  passiveIds: readonly string[] | null | undefined
}

export type SessionLoadoutDraftEditFlags = {
  exCardEdited: boolean
  deckOwnedCardIdsEdited: boolean
  passiveIdsEdited: boolean
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

export function createEmptySessionLoadoutDraftEditFlags(): SessionLoadoutDraftEditFlags {
  return {
    exCardEdited: false,
    deckOwnedCardIdsEdited: false,
    passiveIdsEdited: false,
  }
}

export function createSessionLoadoutDraftFromScreen(
  loadout: PlayerLobbyLoadoutDto | null,
): SessionLoadoutDraft {
  if (!loadout) {
    return createEmptySessionLoadoutDraft()
  }

  return {
    characterId:
      typeof loadout.characterId === 'number' && Number.isFinite(loadout.characterId) && loadout.characterId > 0
        ? loadout.characterId
        : null,
    deckOwnedCardIds: normalizePresetIdentifierList(loadout.deckOwnedCardIds),
    exCardId: normalizePresetIdentifier(loadout.exCardId),
    passiveIds: normalizePresetIdentifierList(loadout.passiveIds),
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

export function buildSessionLoadoutActionPatch(
  draft: SessionLoadoutDraft,
  savedDraft: SessionLoadoutDraft,
  editFlags: SessionLoadoutDraftEditFlags,
): PlayerLobbySaveLoadoutPayload {
  const normalizedDraft = normalizeSessionLoadoutDraft(draft)
  const characterChanged = normalizedDraft.characterId !== savedDraft.characterId

  return {
    characterId: normalizedDraft.characterId,
    ...(!characterChanged || editFlags.passiveIdsEdited
      ? { passiveIds: normalizedDraft.passiveIds }
      : {}),
    ...(!characterChanged || editFlags.deckOwnedCardIdsEdited
      ? { deckOwnedCardIds: normalizedDraft.deckOwnedCardIds }
      : {}),
    ...(!characterChanged || editFlags.exCardEdited
      ? { exCardId: normalizedDraft.exCardId }
      : {}),
  }
}

export function buildSessionLoadoutPreviewRequest(
  draft: SessionLoadoutDraft,
  clientRequestId: string | null = null,
): PreviewSessionLoadoutRequest {
  const normalizedDraft = normalizeSessionLoadoutDraft(draft)

  return {
    characterId: normalizedDraft.characterId,
    passiveIds: normalizedDraft.passiveIds,
    deckOwnedCardIds: normalizedDraft.deckOwnedCardIds,
    exCardId: normalizedDraft.exCardId,
    clientRequestId,
  }
}

function normalizeSessionLoadoutDraftLike(draft: SessionLoadoutDraftLike): SessionLoadoutDraft {
  return normalizeSessionLoadoutDraft({
    characterId:
      typeof draft.characterId === 'number' && Number.isFinite(draft.characterId) && draft.characterId > 0
        ? draft.characterId
        : null,
    deckOwnedCardIds: normalizePresetIdentifierList(draft.deckOwnedCardIds ?? []),
    exCardId: normalizePresetIdentifier(draft.exCardId),
    passiveIds: normalizePresetIdentifierList(draft.passiveIds ?? []),
  })
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

export function areSessionLoadoutDraftsEqual(
  source: SessionLoadoutDraftLike,
  draft: SessionLoadoutDraftLike,
) {
  const normalizedSource = normalizeSessionLoadoutDraftLike(source)
  const normalizedDraft = normalizeSessionLoadoutDraftLike(draft)
  return !isSessionLoadoutDraftDirty(normalizedSource, normalizedDraft)
}

export function isPreviewSessionLoadoutDraftCurrent(
  source: PreviewSessionLoadoutDraftDto | null | undefined,
  draft: SessionLoadoutDraftLike,
) {
  if (!source) {
    return false
  }
  return areSessionLoadoutDraftsEqual(source, draft)
}
