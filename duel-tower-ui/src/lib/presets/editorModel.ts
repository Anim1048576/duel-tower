import type {
  CreatePresetRequest,
  UpdatePresetRequest,
} from '../api/presetTypes'
import type { PresetEditorActionPayload, PresetEditorDraftDto } from '../api/screenTypes'

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

export function createPresetEditorState(draft: PresetEditorDraftDto): PresetEditorState {
  return {
    name: draft.name.trim(),
    characterId: draft.characterId,
    deckCardIds: normalizePresetIdentifierList(draft.deckCardIds),
    exCardId: normalizePresetIdentifier(draft.exCardId),
    passiveIds: normalizePresetIdentifierList(draft.passiveIds),
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

export function buildPresetEditorActionPatch(
  actionId: string,
  state: PresetEditorState,
): Partial<PresetEditorActionPayload> | null {
  if (actionId === 'presetEditor.clone' || actionId === 'presetEditor.delete') {
    return null
  }

  const normalized = normalizePresetEditorState(state)

  return {
    name: normalized.name,
    characterId: normalized.characterId ?? 0,
    deckCardIds: normalized.deckCardIds,
    exCardId: normalized.exCardId,
    passiveIds: normalized.passiveIds,
  }
}
