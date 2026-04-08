import type { CardDefinition, CardType } from '../api/contentTypes'

export type ContentTagTone = 'accent' | 'muted' | 'success' | 'warning'

export type ContentDisplayTag = {
  label: string
  tone?: ContentTagTone
}

const CARD_TYPE_LABELS: Record<CardType, string> = {
  SKILL: 'Skill',
  EX: 'EX',
  TOKEN: 'Token',
}

export function getCardTypeLabel(type: CardType | '' | null | undefined, fallback = 'Unknown') {
  if (!type) {
    return fallback
  }

  return CARD_TYPE_LABELS[type] ?? formatContentEnumLabel(type, fallback)
}

export function getCardTypeTone(type: CardType | null | undefined): ContentTagTone {
  switch (type) {
    case 'SKILL':
      return 'success'
    case 'EX':
      return 'warning'
    default:
      return 'muted'
  }
}

export function formatContentEnumLabel(value: string | null | undefined, fallback = 'N/A') {
  const normalized = value?.trim()

  if (!normalized) {
    return fallback
  }

  if (normalized.length <= 2 && normalized === normalized.toUpperCase()) {
    return normalized
  }

  return normalized
    .toLowerCase()
    .split(/[_-]+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(' ')
}

export function buildCardArchiveMeta(card: Pick<CardDefinition, 'type' | 'cost' | 'resolveTo'>) {
  const parts = [getCardTypeLabel(card.type)]

  if (card.cost !== null) {
    parts.push(`Cost ${card.cost}`)
  }

  if (card.resolveTo) {
    parts.push(`Resolve ${formatContentEnumLabel(card.resolveTo)}`)
  }

  return parts.join(' · ')
}

export function buildCardDisplayTags(
  card: Pick<CardDefinition, 'type' | 'keywords' | 'token'>,
): ContentDisplayTag[] {
  const tags: ContentDisplayTag[] = [
    { label: getCardTypeLabel(card.type), tone: getCardTypeTone(card.type) },
  ]

  for (const keyword of card.keywords.slice(0, 2)) {
    tags.push({ label: keyword, tone: 'accent' })
  }

  if (card.token) {
    tags.push({ label: 'Token Link', tone: 'warning' })
  }

  return tags
}
