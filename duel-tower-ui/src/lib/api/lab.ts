import { apiGet, apiPost } from './client'
import type {
  LabDiceRequest,
  LabDiceResponse,
  LabEffectCardOptionDto,
  LabEffectProbeRequest,
  LabEffectProbeResponse,
} from './labTypes'

const LAB_API_BASE = '/api/lab'

export function rollLabDice(request: LabDiceRequest): Promise<LabDiceResponse> {
  return apiPost<LabDiceResponse, LabDiceRequest>(`${LAB_API_BASE}/dice`, request)
}

export function getLabEffectCards(): Promise<LabEffectCardOptionDto[]> {
  return apiGet<LabEffectCardOptionDto[]>(`${LAB_API_BASE}/effects/cards`)
}

export function probeLabEffect(request: LabEffectProbeRequest): Promise<LabEffectProbeResponse> {
  return apiPost<LabEffectProbeResponse, LabEffectProbeRequest>(
    `${LAB_API_BASE}/effects/probe`,
    request,
  )
}
