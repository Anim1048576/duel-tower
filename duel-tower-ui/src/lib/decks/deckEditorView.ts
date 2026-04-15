import type { DeckType } from '../api/deckTypes'
import type { DeckEditorCardState } from './editorModel'

export type DeckCardItem = {
  id: string
  title: string
  subtitle?: string
  meta?: string
  note?: string
  tags?: DeckCardTagItem[]
}

export type DeckCardTagItem = {
  label: string
  tone?: 'accent' | 'muted' | 'success' | 'warning'
}

export function getDeckTypeTone(type: DeckType | '' | null | undefined): 'muted' | 'success' | 'warning' {
  if (type === 'PLAYER') {
    return 'success'
  }

  if (type === 'ENEMY') {
    return 'warning'
  }

  return 'muted'
}

export function getDeckCardMetaLabel(entry: DeckEditorCardState, position: number) {
  return `Count ${entry.count} | Entry ${position}`
}

export function buildDeckCardNote(entry: DeckEditorCardState, position: number, totalEntries: number) {
  const totalLabel = totalEntries === 1 ? 'draft entry' : 'draft entries'
  return `Card reference ${entry.cardId || 'N/A'} is currently tracked as item ${position} of ${totalEntries} ${totalLabel}.`
}

export function getDeckCardTagItems(entry: DeckEditorCardState, position: number): DeckCardTagItem[] {
  return [
    { label: `x${entry.count}`, tone: entry.count > 1 ? 'success' : 'muted' as const },
    { label: `Entry ${position}`, tone: 'accent' as const },
  ]
}

export function toDeckCardItem(
  entry: DeckEditorCardState,
  index: number,
  totalEntries: number,
): DeckCardItem {
  const position = index + 1

  return {
    id: entry.key,
    title: entry.cardId.trim() || 'Unnamed card reference',
    subtitle: 'Draft deck card',
    meta: getDeckCardMetaLabel(entry, position),
    note: buildDeckCardNote(entry, position, totalEntries),
    tags: getDeckCardTagItems(entry, position),
  }
}
