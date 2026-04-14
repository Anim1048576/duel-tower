import {
  removeSelectionHandoff,
  selectionHandoffKeys,
  setSelectionHandoff,
} from '../selectionHandoff'
import { normalizeSessionCode } from './access'

export function syncSessionSelectionHandoff(sessionCode: string) {
  const normalized = normalizeSessionCode(sessionCode)

  if (!normalized) {
    return
  }

  setSelectionHandoff(selectionHandoffKeys.sessionCode, normalized)
  removeSelectionHandoff(selectionHandoffKeys.sessionId)
}
