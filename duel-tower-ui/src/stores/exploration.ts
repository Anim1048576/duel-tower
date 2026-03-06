import { writable } from 'svelte/store'

export type ResultType = 'combat_end' | 'explore_fail' | 'reward'

export type ResultCard = {
  id: string
  type: ResultType
  title: string
  summary: string
  detail?: string
}

export type ExplorationResultContext = {
  source: string
  cards: ResultCard[]
  at: string
}

export const explorationResult = writable<ExplorationResultContext | null>(null)

export function setExplorationResult(source: string, cards: ResultCard[]) {
  explorationResult.set({
    source,
    cards,
    at: new Date().toISOString(),
  })
}

export function clearExplorationResult() {
  explorationResult.set(null)
}
