export type SessionCode = string

export type SessionIdentifier = SessionCode

export type SessionRole = 'gm' | 'player'

export type SessionPlayerId = string

export type SessionToken = string

export type SessionVersion = number

export type OwnedCardModifierDto = {
  modifierId: string
  value: number | null
}

export type OwnedCardDto = {
  ownedCardId: string
  cardId: string
  modifiers: OwnedCardModifierDto[]
  strengthened: boolean | null
  weakened: boolean | null
  lockedInDeck: boolean | null
  forgettable: boolean | null
  notForgettableReason: string | null
}

export type PendingDecisionDto = {
  type: string | null
  reason: string | null
  limit: number | null
  pickCount: number | null
  candidateIds: string[]
  destination: string | null
  shuffleAfterPick: boolean | null
  groupIndex: number | null
  actorKeys: string[]
}

export type PlayerEquippedItemDto = {
  slot: string
  inventoryEquipId: string | null
  equipId: string | null
  bound: boolean
  loadedAmmo: number | null
  maxLoadedAmmo: number | null
  actionAvailable: boolean
}

export type PlayerStateDto = {
  playerId: string
  ready: boolean
  passiveIds: string[]
  ownedCards: OwnedCardDto[]
  deck: string[]
  deckOwnedCardIds: string[]
  hand: string[]
  grave: string[]
  field: string[]
  excluded: string[]
  exCard: string | null
  exOnCooldown: boolean
  pendingDecision: PendingDecisionDto | null
  swappedThisTurn: boolean
  cardsPlayedThisTurn: number
  usedExThisTurn: boolean
  handLimit: number
  fieldLimit: number
  ownedCardCount: number
  maxOwnedCardCount: number
  forgettingRequired: boolean
  equippedItems: PlayerEquippedItemDto[]
}

export type CombatSummonDto = {
  summonId: string
  owner: string
  hp: number
  atk: number
  heal: number
  actionAvailable: boolean
}

export type CombatEnemyDto = {
  enemyId: string
  hp: number
  maxHp: number
  ap: number
  attackPower: number
  healPower: number
  exCardId: string | null
  exActivatable: boolean
  exOnCooldown: boolean
  statuses: Record<string, number>
}

export type CombatStateDto = {
  round: number
  turnOrder: string[]
  currentTurnIndex: number
  currentTurnPlayer: string | null
  phase: string | null
  initiatives: Record<string, number>
  initiativeTieGroups: string[][]
  summons: CombatSummonDto[]
  enemies: CombatEnemyDto[]
}

export type CardInstanceDto = {
  instanceId: string
  defId: string
  ownerId: string | null
  zone: string
  counters: Record<string, number>
  sourceOwnedCardId: string | null
  modifiers: OwnedCardModifierDto[]
}

export type RunCurrentNodeDto = {
  id: string
  name: string
  typeLabel: string
  phase: string
  danger: string
  floor: number
}

export type RunNodeChoiceDto = {
  id: string
  name: string
  typeLabel: string
  rule: string
  phase: string
  danger: string
  disabled: boolean
  disabledReason: string | null
}

export type RunRecentResultDto = {
  id: string
  type: string
  title: string
  summary: string
  detail: string | null
  source: string | null
  at: string | null
}

export type RunInventoryItemDto = {
  entryType: string
  id: string
  inventoryEquipId: string | null
  name: string
  count: number
  bound: boolean
  battleUsable: boolean
  loadedAmmo: number | null
  maxLoadedAmmo: number | null
  summary: string | null
  description: string | null
  tags: string[]
}

export type RunInventoryDto = {
  keys: number
  chests: number
  gold: number
  items: RunInventoryItemDto[]
}

export type RunStateDto = {
  floor: number
  currentFloorCleared: boolean
  currentFloorSafeZone: boolean
  canAdvanceToNextFloor: boolean
  status: string
  resultPending: boolean
  currentNode: RunCurrentNodeDto | null
  availableChoices: RunNodeChoiceDto[]
  recentResults: RunRecentResultDto[]
  inventory: RunInventoryDto | null
}

export type SessionStateDto = {
  sessionCode: SessionCode
  sessionId: string
  version: SessionVersion
  seed: number
  nodeState: string | null
  players: Record<string, PlayerStateDto>
  combat: CombatStateDto | null
  cards: Record<string, CardInstanceDto>
  run: RunStateDto | null
}

export type CreateSessionRequest = {
  gmId: string
}

export type CreateSessionResponse = {
  code: SessionCode
  gmId: string
  gmToken: SessionToken
  state: SessionStateDto
}

export type JoinSessionRequest = {
  playerId: string
  characterId: number | null
  passiveIds: readonly string[]
  presetDeckOwnedCardIds?: readonly string[] | null
  presetDeckCardIds?: readonly string[] | null
  presetExCardId?: string | null
  ownedCards?: readonly OwnedCardDto[] | null
}

export type JoinSessionResponse = {
  state: SessionStateDto
  playerToken: SessionToken
}

export type UpdateSessionLoadoutRequest = {
  characterId: number | null
  passiveIds: readonly string[]
  deckOwnedCardIds: readonly string[]
  exCardId: string
}

export type ApplyPresetToSessionRequest = {
  presetId: number
}

export type UpdatePlayerReadyRequest = {
  ready: boolean
}

export type KickPlayerRequest = {
  reason?: string | null
}

export type ResetSessionRequest = {
  keepPlayers?: boolean | null
  keepLoadouts?: boolean | null
  newSeed?: number | null
}
