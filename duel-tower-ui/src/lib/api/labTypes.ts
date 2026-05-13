export type LabDiceRequest = {
  notation: string
  rollCount?: number | null
  seed?: number | null
}

export type LabDiceSpecDto = {
  count: number
  sides: number
  modifier: number
}

export type LabDiceHistogramEntryDto = {
  value: number
  count: number
}

export type LabDiceExpressionDto = {
  terms: LabDiceTermDto[]
}

export type LabDiceTermDto = {
  sign: number
  kind: 'DICE' | 'CONSTANT' | string
  value?: number | null
  count?: number | null
  sides?: number | null
  selector?: string | null
  selectorAmount?: number | null
  display: string
}

export type LabDiceResponse = {
  notation: string
  normalizedNotation?: string | null
  spec?: LabDiceSpecDto | null
  expression?: LabDiceExpressionDto | null
  min: number
  max: number
  expectedAvailable?: boolean
  expected: string
  expectedNumerator?: number | null
  expectedDenominator?: number | null
  expectedNote?: string | null
  rollCount: number
  seed?: number | null
  rolls: number[]
  histogram: LabDiceHistogramEntryDto[]
}

export type LabEffectCardOptionDto = {
  cardId: string
  name: string
  type: string
  cost: number
  text: string
  tags: string[]
}

export type LabProbeActorInput = {
  attackPower: number
  healPower: number
  hp: number
  maxHp: number
  statuses?: Record<string, number> | null
}

export type LabProbeTargetInput = {
  kind: string
  id?: string | null
  hp?: number | null
  maxHp?: number | null
  statuses?: Record<string, number> | null
}

export type LabProbeSelectionInput = {
  targets?: LabProbeTargetInput[] | null
  discardIds?: string[] | null
  selectedIds?: string[] | null
  discardAliases?: string[] | null
  selectedAliases?: string[] | null
  choiceId?: string | null
}

export type LabProbeExtraCardInput = {
  alias: string
  cardId: string
  zone?: string | null
}

export type LabEffectProbeRequest = {
  cardId: string
  actor: LabProbeActorInput
  target?: LabProbeTargetInput | null
  targets?: LabProbeTargetInput[] | null
  selection?: LabProbeSelectionInput | null
  extraCards?: LabProbeExtraCardInput[] | null
  seed?: number | null
  validateOnly?: boolean | null
}

export type LabProbeSnapshotDto = {
  actor: {
    id: string
    hp: number
    maxHp: number
    ap?: number | null
    statuses: Record<string, number>
  }
  targets: Array<{
    kind: string
    id: string
    hp: number
    maxHp: number
    ap?: number | null
    statuses: Record<string, number>
  }>
}

export type LabProbeChangesDto = {
  actor: LabProbeEntityChangesDto
  targets: LabProbeTargetChangesDto[]
}

export type LabProbeEntityChangesDto = {
  hpChange: number
  statusChanges: LabProbeStatusChangeDto[]
  addedStatuses: string[]
  removedStatuses: string[]
  changedStatuses: string[]
}

export type LabProbeTargetChangesDto = LabProbeEntityChangesDto & {
  kind: string
  id: string
}

export type LabProbeStatusChangeDto = {
  statusId: string
  before: number
  after: number
}

export type LabProbeEventDto = {
  type: string
  message: string
  data?: Record<string, unknown> | null
}

export type LabEffectProbeResponse = {
  cardId: string
  cardName: string
  valid: boolean
  validationErrors: string[]
  resolved: boolean
  probeError?: string | null
  before: LabProbeSnapshotDto
  after: LabProbeSnapshotDto
  changes: LabProbeChangesDto
  events: LabProbeEventDto[]
  notes: string[]
}
