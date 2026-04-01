// Shared UI models (client-side friendly)

export type ApiErrorShape = {
  code: string
  category: string
  userMessage: string
  debugMessage?: string | null
  details?: Record<string, unknown> | string[] | null
  status?: number
  path?: string | null
}

export type CardDefId = string

export type CardDef = {
  id: CardDefId
  name: string
  type?: string
  cost: number
  keywords: string[]
  resolveTo?: string
  token: boolean
  text?: string
}

export type CardInstance = {
  instanceId: string
  defId: CardDefId
  ownerId: string
  zone: string
  counters: Record<string, number>
}

export type PendingDecision = {
  type: 'DISCARD_TO_HAND_LIMIT' | 'SEARCH_PICK' | 'INITIATIVE_TIE_ORDER' | string
  reason?: string
  limit?: number
  pickCount?: number
  candidateIds?: string[]
  destination?: string
  shuffleAfterPick?: boolean
  groupIndex?: number
  actorKeys?: string[]
}

export type OwnedCardModifier = {
  modifierId: string
  value: number
}

export type PlayerOwnedCard = {
  ownedCardId: string
  cardId: string
  modifiers: OwnedCardModifier[]
  strengthened: boolean
  weakened: boolean
  lockedInDeck: boolean
  forgettable: boolean
  notForgettableReason?: string
}

export type PlayerState = {
  playerId: string
  ownedCards?: PlayerOwnedCard[]
  deck: string[]
  deckOwnedCardIds?: string[]
  hand: string[]
  grave: string[]
  field: string[]
  excluded: string[]
  exCard: string | null
  passiveIds: string[]
  exOnCooldown: boolean
  pendingDecision: PendingDecision | null
  swappedThisTurn?: boolean
  cardsPlayedThisTurn?: number
  usedExThisTurn?: boolean
  handLimit: number
  fieldLimit: number
  ownedCardCount?: number
  maxOwnedCardCount?: number
  forgettingRequired?: boolean
}

export type SummonState = {
  summonId: string
  owner: string
  hp: number
  atk: number
  heal: number
  actionAvailable: boolean
}

export type EnemyState = {
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

export type CombatTarget =
  | {
      type: 'player'
      playerId: string
    }
  | {
      type: 'summon'
      playerId: string
      summonId: string
    }
  | {
      type: 'enemy'
      enemyId: string
    }

export type ActionDescriptor = {
  id: string
  kind: 'play' | 'useEx' | 'summon'
  label: string
  commandType: 'PLAY_CARD' | 'USE_EX' | 'USE_SUMMON_ACTION'
  sourcePlayerId: string
  cardId?: string
  summonId?: string
  requiresTarget: boolean
  validTargets: CombatTarget[]
  disabledReason?: string
}

export type ResolutionLog = {
  id: string
  at: string
  level: 'info' | 'warn' | 'error'
  summary: string
  breakdown: string
}

export type CharacterView = PlayerState & {
  availableActions: ActionDescriptor[]
}

export type CombatSnapshot = CombatState & {
  availableActions: ActionDescriptor[]
}

export type CombatState = {
  round: number
  turnOrder: string[]
  currentTurnIndex: number
  currentTurnPlayer: string
  phase?: string
  summons?: SummonState[]
  enemies?: EnemyState[]
}



export type RunCurrentNode = {
  id: string
  name: string
  typeLabel: string
  phase: string
  danger: string
  floor: number
}

export type RunChoice = {
  id: string
  name: string
  typeLabel: string
  rule: string
  phase: string
  danger: string
  disabled?: boolean
  disabledReason?: string
}

export type RunResult = {
  id: string
  type: string
  title: string
  summary: string
  detail?: string
  source?: string
  at?: string
}

export type RunInventoryItem = {
  id: string
  name: string
  count: number
  bound: boolean
  battleUsable: boolean
  summary: string
  description: string
  tags: string[]
}

export type RunInventory = {
  keys: number
  chests: number
  gold: number
  items: RunInventoryItem[]
}

export type RunSnapshot = {
  floor: number
  status: string
  resultPending: boolean
  currentNode: RunCurrentNode | null
  availableChoices: RunChoice[]
  recentResults: RunResult[]
  inventory: RunInventory
}

export type SessionSnapshot = {
  sessionCode: string
  sessionId: string
  version: number
  seed: number
  players: Record<string, CharacterView>
  combat: CombatSnapshot | null
  cards: Record<string, CardInstance>
  run: RunSnapshot | null
}

// Backward-compat alias while migrating views.

export type EngineEvent = {
  type: string
  payload: Record<string, unknown>
}

export type EngineResponse = {
  accepted: boolean
  errors: string[]
  errorDetails?: ApiErrorShape[]
  events: EngineEvent[]
  state: SessionSnapshot
  resolutionLogs?: ResolutionLog[]
}

export type CreateSessionResponse = {
  code: string
  gmId: string
  gmToken: string
  state: SessionSnapshot
}

export type JoinSessionResponse = {
  playerToken: string
  state: SessionSnapshot
}

export type TargetRef = {
  playerId?: string
  enemyId?: string
  summonOwnerPlayerId?: string
  summonInstanceId?: string
}

export type CommandRequest = {
  /**
   * API command type (e.g. START_COMBAT, PLAY_CARD, RESOLVE_INITIATIVE_TIE)
   */
  type: string
  commandId?: string
  expectedVersion?: number
  playerId: string
  count?: number
  enemyId?: string
  discardIds?: string[]
  cardId?: string
  summonId?: string
  targetPlayerIds?: string[]
  targetEnemyIds?: string[]
  targets?: TargetRef[]
  /**
   * Required when type === 'RESOLVE_INITIATIVE_TIE': pending tie group index from INITIATIVE_TIE_ORDER.
   */
  tieGroupIndex?: number
  /**
   * Required when type === 'RESOLVE_INITIATIVE_TIE': final actor key order chosen by the user.
   */
  orderedActorKeys?: string[]
  selectedIds?: string[]
  choiceId?: string
}

export type OwnedCard = {
  ownedCardId?: string
  cardId: string
  modifiers?: OwnedCardModifier[]
  strengthened?: boolean
  weakened: boolean
  lockedInDeck?: boolean
}

export type PassiveDefinition = {
  id: string
  name: string
  priority: number
  description?: string
}

export function normalizeCardDef(raw: any): CardDef {
  // Backend CardDefinition serializes id as { value: 'C001' }
  const id = String(raw?.id?.value ?? raw?.id ?? '')
  return {
    id,
    name: String(raw?.name ?? id),
    type: raw?.type ? String(raw.type) : undefined,
    cost: Number(raw?.cost ?? 0),
    keywords: Array.isArray(raw?.keywords) ? raw.keywords.map(String) : [],
    resolveTo: raw?.resolveTo ? String(raw.resolveTo) : undefined,
    token: Boolean(raw?.token ?? false),
    text: raw?.text ? String(raw.text) : undefined,
  }
}

export function normalizePassiveDefinition(raw: any): PassiveDefinition {
  const id = String(raw?.id ?? '')
  return {
    id,
    name: String(raw?.name ?? id),
    priority: Number(raw?.priority ?? 0),
    description: raw?.description ? String(raw.description) : undefined,
  }
}
