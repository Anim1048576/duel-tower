import type { ActionDescriptor, CharacterView, CombatTarget, SummonState } from '../model'

export type PendingAction = ActionDescriptor & {
  target?: CombatTarget
}

export type ActionStage = 'idle' | 'targeting' | 'confirming'

export type TeamSide = 'ally' | 'enemy'

export type TeamPlayer = CharacterView & {
  side: TeamSide
}

export type TeamSummon = SummonState
