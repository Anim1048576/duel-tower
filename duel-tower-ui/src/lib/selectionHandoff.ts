export const selectionHandoffKeys = {
  characterId: 'duel-tower:selected-character-id',
  deckApplyCharacterId: 'duel-tower:deck-apply-character-id',
  deckId: 'duel-tower:selected-deck-id',
  presetId: 'duel-tower:selected-preset-id',
  sessionId: 'duel-tower:selected-session-id',
  sessionCode: 'duel-tower:selected-session-code',
} as const

export type SelectionHandoffKey = (typeof selectionHandoffKeys)[keyof typeof selectionHandoffKeys]

export type RouteFirstSelectionResult<T extends string> = {
  value: T | null
  source: 'route' | 'handoff' | 'none'
  missingRouteValue: string | null
}

export function readSelectionHandoff(key: SelectionHandoffKey): string | null {
  if (typeof window === 'undefined') return null
  return window.sessionStorage.getItem(key)
}

export function getSelectionHandoff(key: SelectionHandoffKey, fallback: string): string {
  return readSelectionHandoff(key) ?? fallback
}

export function setSelectionHandoff(key: SelectionHandoffKey, value: string) {
  if (typeof window === 'undefined' || !value) return
  window.sessionStorage.setItem(key, value)
}

export function removeSelectionHandoff(key: SelectionHandoffKey) {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(key)
}

export function resolveRouteFirstSelection<T extends string>({
  routeValue,
  handoffValue,
  isValid,
}: {
  routeValue: string | null
  handoffValue?: string | null
  isValid: (value: string) => value is T
}): RouteFirstSelectionResult<T> {
  if (routeValue !== null) {
    return isValid(routeValue)
      ? {
          value: routeValue,
          source: 'route',
          missingRouteValue: null,
        }
      : {
          value: null,
          source: 'none',
          missingRouteValue: routeValue,
        }
  }

  if (handoffValue && isValid(handoffValue)) {
    return {
      value: handoffValue,
      source: 'handoff',
      missingRouteValue: null,
    }
  }

  return {
    value: null,
    source: 'none',
    missingRouteValue: null,
  }
}
