export const selectionHandoffKeys = {
  sessionId: 'duel-tower:selected-session-id',
} as const

export type SelectionHandoffKey = (typeof selectionHandoffKeys)[keyof typeof selectionHandoffKeys]

export function getSelectionHandoff(key: SelectionHandoffKey, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  return window.sessionStorage.getItem(key) ?? fallback
}

export function setSelectionHandoff(key: SelectionHandoffKey, value: string) {
  if (typeof window === 'undefined' || !value) return
  window.sessionStorage.setItem(key, value)
}

export function removeSelectionHandoff(key: SelectionHandoffKey) {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(key)
}
