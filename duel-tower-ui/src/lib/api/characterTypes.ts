export type CharacterGender = 'MALE' | 'FEMALE' | 'OTHER'

export type CharacterProfileId = number

export type CharacterProfileIdentifier = CharacterProfileId | string

export type CharacterCombatStats = {
  maxHp: number
  maxAp: number
  attackPower: number
  healPower: number
}

export type CharacterTimestampValue = string | number

export type OwnedCardModifierRequest = {
  modifierId: string
  value: number | null
}

export type OwnedCardRequest = {
  ownedCardId: string | null
  cardId: string
  modifiers: OwnedCardModifierRequest[]
  strengthened: boolean | null
  weakened: boolean | null
  lockedInDeck: boolean | null
  forgettable: boolean | null
  notForgettableReason?: string | null
}

export type CharacterProfileRequest = {
  name: string
  gender: CharacterGender | null
  age: number | null
  wish: string
  disposition: string
  oneLiner: string
  story: string
  physical: number | null
  technique: number | null
  sense: number | null
  willpower: number | null
  trait1: string | null
  trait2: string | null
  hiddenTraitIds: string[]
  ownedCards?: string
  exCard?: string
  ownedCardList?: OwnedCardRequest[]
  exCardId?: string | null
}

export type CharacterProfileResponse = {
  id: CharacterProfileId
  name: string
  gender: CharacterGender
  age: number | null
  wish: string
  disposition: string
  oneLiner: string
  story: string
  physical: number
  technique: number
  sense: number
  willpower: number
  trait1: string | null
  trait2: string | null
  hiddenTraitIds: string[]
  ownedCards: string
  currentSkillDeckPreviewCardIds: string[]
  exCard: string
  combatStats: CharacterCombatStats
  createDate: CharacterTimestampValue
  updateDate: CharacterTimestampValue
}
