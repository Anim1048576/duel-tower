import { apiDeleteVoid, apiGet, apiPost, apiPut } from './client'
import type {
  CreatePresetRequest,
  PresetIdentifier,
  PresetResponse,
  UpdatePresetRequest,
} from './presetTypes'

const PRESETS_API_BASE = '/api/me/presets'

function getPresetResourcePath(id: PresetIdentifier) {
  const normalizedId = String(id).trim()

  if (!normalizedId) {
    throw new Error('preset id is required')
  }

  return `${PRESETS_API_BASE}/${encodeURIComponent(normalizedId)}`
}

export function listPresets() {
  return apiGet<PresetResponse[]>(PRESETS_API_BASE)
}

export function getPreset(id: PresetIdentifier) {
  return apiGet<PresetResponse>(getPresetResourcePath(id))
}

export function createPreset(payload: CreatePresetRequest) {
  return apiPost<PresetResponse, CreatePresetRequest>(PRESETS_API_BASE, payload)
}

export function updatePreset(id: PresetIdentifier, payload: UpdatePresetRequest) {
  return apiPut<PresetResponse, UpdatePresetRequest>(getPresetResourcePath(id), payload)
}

export function clonePreset(id: PresetIdentifier) {
  return apiPost<PresetResponse, null>(`${getPresetResourcePath(id)}/clone`, null)
}

export function deletePreset(id: PresetIdentifier) {
  return apiDeleteVoid(getPresetResourcePath(id))
}
