export type NodePhase = 'judgement' | 'combat' | 'event'

export type NodeChoice = {
  id: string
  name: string
  typeLabel: string
  rule: string
  phase: NodePhase
  danger: 'low' | 'mid' | 'high'
  disabled?: boolean
  disabledReason?: string
}
