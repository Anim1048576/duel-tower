// Shared UI models (client-side friendly)

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
  type: 'DISCARD_TO_HAND_LIMIT' | 'SEARCH_PICK' | string
  reason?: string
  limit?: number
  pickCount?: number
}

export type PlayerState = {
  playerId: string
  deck: string[]
  hand: string[]
  grave: string[]
  field: string[]
  excluded: string[]
  exCard: string | null
  passiveIds: string[]
  exOnCooldown: boolean
  pendingDecision: PendingDecision | null
  handLimit: number
  fieldLimit: number
}

export type SummonState = {
  summonId: string
  owner: string
  hp: number
  atk: number
  heal: number
  actionAvailable: boolean
}

export type CombatState = {
  round: number
  turnOrder: string[]
  currentTurnIndex: number
  currentTurnPlayer: string
  phase?: string
  summons?: SummonState[]
}

export type SessionState = {
  sessionCode: string
  sessionId: string
  version: number
  seed: number
  players: Record<string, PlayerState>
  combat: CombatState | null
  cards: Record<string, CardInstance>
}

export type EngineEvent = {
  type: string
  payload: Record<string, unknown>
}

export type EngineResponse = {
  accepted: boolean
  errors: string[]
  events: EngineEvent[]
  state: SessionState
}

export type CreateSessionResponse = {
  code: string
  gmId: string
  gmToken: string
  state: SessionState
}

export type JoinSessionResponse = {
  state: SessionState
}

export type TargetRef = {
  playerId?: string
  enemyId?: string
  summonOwnerPlayerId?: string
  summonInstanceId?: string
}

export type CommandRequest = {
  type: string
  commandId?: string
  expectedVersion?: number
  playerId: string
  count?: number
  discardIds?: string[]
  cardId?: string
  summonId?: string
  targetPlayerIds?: string[]
  targetEnemyIds?: string[]
  targets?: TargetRef[]
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
