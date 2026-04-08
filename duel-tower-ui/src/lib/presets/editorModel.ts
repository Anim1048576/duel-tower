import type {
  CreatePresetRequest,
  PresetResponse,
  UpdatePresetRequest,
} from '../api/presetTypes'

export type PresetEditorState = {
  name: string
  characterId: number | null
  deckCardIds: string[]
  exCardId: string
  passiveIds: string[]
}

export function createEmptyPresetEditorState(): PresetEditorState {
  return {
    name: '',
    characterId: null,
    deckCardIds: [],
    exCardId: '',
    passiveIds: [],
  }
}

export function clonePresetEditorState(state: PresetEditorState): PresetEditorState {
  return {
    name: state.name,
    characterId: state.characterId,
    deckCardIds: [...state.deckCardIds],
    exCardId: state.exCardId,
    passiveIds: [...state.passiveIds],
  }
}

export function normalizePresetIdentifier(value: string | null | undefined) {
  return String(value ?? '').trim()
}

export function normalizePresetIdentifierList(values: readonly string[]) {
  const uniqueValues = new Set<string>()

  for (const value of values) {
    const normalized = normalizePresetIdentifier(value)

    if (!normalized) {
      continue
    }

    uniqueValues.add(normalized)
  }

  return Array.from(uniqueValues)
}

export function createPresetEditorState(response: PresetResponse): PresetEditorState {
  return {
    name: response.name.trim(),
    characterId: response.characterId,
    deckCardIds: normalizePresetIdentifierList(response.deckCardIds),
    exCardId: normalizePresetIdentifier(response.exCardId),
    passiveIds: normalizePresetIdentifierList(response.passiveIds),
  }
}

export function normalizePresetEditorState(state: PresetEditorState): PresetEditorState {
  return {
    name: state.name.trim(),
    characterId:
      typeof state.characterId === 'number' && Number.isFinite(state.characterId) && state.characterId > 0
        ? state.characterId
        : null,
    deckCardIds: normalizePresetIdentifierList(state.deckCardIds),
    exCardId: normalizePresetIdentifier(state.exCardId),
    passiveIds: normalizePresetIdentifierList(state.passiveIds),
  }
}

export function addPresetIdentifier(values: readonly string[], nextValue: string) {
  return normalizePresetIdentifierList([...values, nextValue])
}

export function toPresetEditorPayload(state: PresetEditorState): CreatePresetRequest | UpdatePresetRequest {
  const normalized = normalizePresetEditorState(state)

  return {
    name: normalized.name,
    characterId: normalized.characterId ?? 0,
    deckCardIds: normalized.deckCardIds,
    exCardId: normalized.exCardId,
    passiveIds: normalized.passiveIds,
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

export function isPresetEditorStateDirty(source: PresetEditorState, state: PresetEditorState) {
  if (source.name !== state.name) {
    return true
  }

  if (source.characterId !== state.characterId) {
    return true
  }

  if (source.exCardId !== state.exCardId) {
    return true
  }

  if (!areStringListsEqual(source.deckCardIds, state.deckCardIds)) {
    return true
  }

  if (!areStringListsEqual(source.passiveIds, state.passiveIds)) {
    return true
  }

  return false
}
