import type { PlayerState, SummonState } from '../model'

export type CombatTarget = {
  type: 'player' | 'summon'
  playerId: string
  summonId?: string
}

export type ActionKind = 'play' | 'useEx' | 'summon'

export type PendingAction = {
  kind: ActionKind
  label: string
  cardId?: string
  summonId?: string
  sourcePlayerId: string
  requiresTarget: boolean
  target?: CombatTarget
}

export type ActionStage = 'idle' | 'targeting' | 'confirming'

export type TeamSide = 'ally' | 'enemy'

export type TeamPlayer = PlayerState & {
  side: TeamSide
}

export type TeamSummon = SummonState
