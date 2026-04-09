export type FlexibleStringEnum<T extends string> = T | (string & {})

export type ContentIdentifier = string

export type CardType = 'SKILL' | 'EX' | 'TOKEN'

export type Zone = FlexibleStringEnum<
  'HAND' | 'BOARD' | 'DISCARD' | 'GRAVEYARD' | 'DECK' | 'TOKEN' | 'RESOLVE'
>

export type StatusKind = FlexibleStringEnum<'BUFF' | 'DEBUFF' | 'SPECIAL' | 'NEUTRAL'>

export type StatusScope = FlexibleStringEnum<
  'SELF' | 'TARGET' | 'ALLY_TEAM' | 'ENEMY_TEAM' | 'GLOBAL' | 'BOARD'
>

export type StatusTag = FlexibleStringEnum<
  'CONTROL' | 'DOT' | 'HOT' | 'STACKING' | 'STEALTH' | 'SHIELD' | 'TURN_BASED'
>

export type CardListQueryParams = {
  type?: CardType | null
  q?: string | null
  keywordId?: string | null
}

export type CardKeywordValues = Record<string, number>

export type CardDefinition = {
  id: ContentIdentifier
  name: string
  type: CardType
  cost: number | null
  keywords: string[]
  keywordValues: CardKeywordValues
  resolveTo: Zone | null
  token: boolean
  description: string
}

export type CardPlaySpec = Record<string, unknown> | string | null

export type CardDetailResponse = CardDefinition & {
  playSpec: CardPlaySpec
}

export type KeywordDefinition = {
  id: ContentIdentifier
  name: string
  parameterized: boolean
  description: string
}

export type PassiveDefinition = {
  id: ContentIdentifier
  name: string
  priority: number | null
  description: string
}

export type StatusDefinition = {
  id: ContentIdentifier
  name: string
  kind: StatusKind
  scope: StatusScope
  tags: StatusTag[]
  priority: number | null
  persistsAfterCombat: boolean
  description: string
}

export type ItemDefinition = {
  id: ContentIdentifier
  name: string
  battleUsable: boolean
  summary: string
  description: string
  tags: string[]
}
