export type CombatTone = 'accent' | 'muted' | 'success' | 'warning'

export const COMBAT_SIDEBAR_TABS = ['command', 'log', 'result', 'inspector'] as const

export type CombatSidebarTab = (typeof COMBAT_SIDEBAR_TABS)[number]

export type CombatInspectorEntityReference =
  | {
      kind: 'player'
      id: string
    }
  | {
      kind: 'enemy'
      id: string
    }
  | {
      kind: 'summon'
      id: string
      owner: string
    }

export type CombatPresentationState = {
  activeSidebarTab: CombatSidebarTab
  headerExpanded: boolean
  hoveredEntity: CombatInspectorEntityReference | null
  pinnedEntity: CombatInspectorEntityReference | null
  hoveredHandCard: string | null
  pinnedHandCard: string | null
  handExpanded: boolean
}

export type CombatMetric = {
  label: string
  value: string | number
  note: string
}

export type CombatTag = {
  label: string
  tone?: CombatTone
}

export type CombatActorSummary = {
  raw: string | null
  kind: 'player' | 'enemy' | 'unknown' | 'none'
  id: string | null
  label: string
  note: string
  tone: CombatTone
}

export type CombatStatusViewModel = {
  sessionCode: string
  version: number
  round: number | null
  currentTurnPlayer: string | null
  phase: string | null
  currentTurnLabel: string
  currentTurnTone: CombatTone
  currentTurnNote: string
  turnOrderSummary: string
  battlefieldSummary: string
  runSummary: string
  initiativeSummary: string
  tieGroupSummary: string
}

export type ResolvedCombatCardViewModel = {
  instanceId: string
  defId: string | null
  title: string
  subtitle: string
  meta: string
  description: string
  unresolved: boolean
  tags: CombatTag[]
}

export type CombatCommandRequirementViewModel = {
  sourceLabel: string
  targetSummary: string
  discardSummary: string
  fieldSelectionSummary: string
  choiceSummary: string
}

export type CombatPlayerViewModel = {
  playerId: string
  ready: boolean
  stateLabel: string
  stateTone: CombatTone
  metrics: CombatMetric[]
  summaryLines: string[]
  statusTags: CombatTag[]
  passives: string[]
  handCards: ResolvedCombatCardViewModel[]
  fieldCards: ResolvedCombatCardViewModel[]
  graveCards: ResolvedCombatCardViewModel[]
  excludedCards: ResolvedCombatCardViewModel[]
}

export type CombatEnemyViewModel = {
  enemyId: string
  stateLabel: string
  stateTone: CombatTone
  metrics: CombatMetric[]
  summaryLines: string[]
  statusEntries: string[]
}

export type CombatSummonViewModel = {
  summonId: string
  owner: string
  stateLabel: string
  stateTone: CombatTone
  metrics: CombatMetric[]
  summaryLines: string[]
}

export type CommandOptionViewModel = {
  id: string
  title: string
  note: string
  disabled: boolean
}

export type CombatActionButtonViewModel = {
  label: string
  selected?: boolean
  disabled?: boolean
  onClick: () => void
}

export type CombatFeedEntry = {
  title: string
  lines: string[]
}

export type CombatRecentResultEntry = {
  title: string
  summary: string
  meta: string
}
