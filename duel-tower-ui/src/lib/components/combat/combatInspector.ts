import { findCombatAction, type CombatScreenResponse } from '../../api/screenTypes'
import type {
  CombatInspectorEntityReference,
  CombatInspectorInteractionSource,
  CombatInspectorTarget,
  CombatInspectorViewModel,
  CombatMetric,
  CombatTag,
  CombatTone,
} from './types'

type CombatInspectorStateInput = {
  pinnedEntity: CombatInspectorEntityReference | null
  pinnedHandCard: string | null
  hoveredEntity: CombatInspectorEntityReference | null
  hoveredHandCard: string | null
}

type BuildCombatInspectorViewModelInput = {
  screen: CombatScreenResponse | null
  target: CombatInspectorTarget
  source: CombatInspectorInteractionSource
  selectedCardId: string | null
  selectedDiscardIds: readonly string[]
}

function portraitLabel(title: string) {
  const trimmed = title.trim()
  if (!trimmed) {
    return null
  }

  return trimmed.slice(0, 2).toUpperCase()
}

function selectionTone(selected: boolean, warning = false): CombatTone {
  if (selected) {
    return 'warning'
  }

  return warning ? 'warning' : 'muted'
}

function normalizeTagTone(value: string | null | undefined): CombatTone {
  switch (value) {
    case 'accent':
    case 'muted':
    case 'success':
    case 'warning':
      return value
    default:
      return 'muted'
  }
}

function normalizeMetricValue(value: string | number | boolean | null): string | number {
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No'
  }

  return value ?? 'N/A'
}

function normalizeMetricNote(value: string | null | undefined) {
  return value ?? ''
}

function toInspectorMetrics(
  metrics: readonly {
    label: string
    value: string | number | boolean | null
    note: string | null
  }[],
): CombatMetric[] {
  return metrics.map((metric) => ({
    label: metric.label,
    value: normalizeMetricValue(metric.value),
    note: normalizeMetricNote(metric.note),
  }))
}

function formatPlayerDetailLines(player: CombatScreenResponse['actors']['players'][number]) {
  const detailLines: string[] = []

  detailLines.push(player.ready ? '준비 완료 참가자입니다.' : '준비 대기 중인 참가자입니다.')

  if (player.passives.length > 0) {
    detailLines.push(`Passives: ${player.passives.join(', ')}`)
  }

  detailLines.push(`Hand ${player.handCards.length} | Field ${player.fieldCards.length} | Grave ${player.graveCards.length} | Excluded ${player.excludedCards.length}`)

  if (player.exCard?.title) {
    detailLines.push(`EX card: ${player.exCard.title}`)
  }

  return detailLines
}

function formatEnemyDetailLines(enemy: CombatScreenResponse['actors']['enemies'][number]) {
  const detailLines: string[] = []

  if (enemy.statusEntries.length > 0) {
    detailLines.push(`Statuses: ${enemy.statusEntries.join(', ')}`)
  }

  return detailLines
}

function buildSelectionSummaries(input: {
  screen: CombatScreenResponse
  instanceId: string
  selectedCardId: string | null
  selectedDiscardIds: readonly string[]
}) {
  const { screen, instanceId, selectedCardId, selectedDiscardIds } = input
  const playCardAction = findCombatAction(screen, 'combat.playCard')
  const metadata = playCardAction?.metadata
  const sourceOption = metadata?.kind === 'playCard'
    ? metadata.sourceOptions.find((option) => option.instanceId === instanceId) ?? null
    : null

  const selectionSummaries = [
    {
      label: selectedCardId === instanceId ? 'Currently selected for play' : 'Not selected for play',
      tone: selectionTone(selectedCardId === instanceId),
    },
    {
      label: selectedDiscardIds.includes(instanceId) ? 'Marked as discard input' : 'Not marked for discard',
      tone: selectionTone(selectedDiscardIds.includes(instanceId)),
    },
  ]

  if (!playCardAction) {
    selectionSummaries.push({
      label: 'Play-card action metadata unavailable',
      tone: 'muted',
    })
    return selectionSummaries
  }

  if (!playCardAction.enabled) {
    selectionSummaries.push({
      label: playCardAction.disabledReason?.userMessage ?? 'Play-card action is currently disabled.',
      tone: 'warning',
    })
    return selectionSummaries
  }

  if (!sourceOption) {
    selectionSummaries.push({
      label: '지금은 이 카드를 사용할 수 없습니다.',
      tone: 'warning',
    })
    return selectionSummaries
  }

  selectionSummaries.push({
    label: sourceOption.supported
      ? 'Playable in the current combat step'
      : (sourceOption.unsupportedReason ?? '지금은 이 카드를 사용할 수 없습니다.'),
    tone: sourceOption.supported ? 'success' : 'warning',
  })

  if (sourceOption.requirementView?.targetSummary) {
    selectionSummaries.push({
      label: sourceOption.requirementView.targetSummary,
      tone: 'muted',
    })
  }

  return selectionSummaries
}

export function resolveCombatInspectorTarget(
  input: CombatInspectorStateInput,
): { source: CombatInspectorInteractionSource; target: CombatInspectorTarget } | null {
  // Inspector precedence stays explicit: pinned beats hover, and hand-card focus beats entity focus.
  if (input.pinnedHandCard) {
    return {
      source: 'pinned',
      target: {
        kind: 'handCard',
        instanceId: input.pinnedHandCard,
      },
    }
  }

  if (input.pinnedEntity) {
    if (input.pinnedEntity.kind === 'summon') {
      return null
    }

    return {
      source: 'pinned',
      target:
        input.pinnedEntity.kind === 'player'
          ? { kind: 'player', playerId: input.pinnedEntity.id }
          : { kind: 'enemy', enemyId: input.pinnedEntity.id },
    }
  }

  if (input.hoveredHandCard) {
    return {
      source: 'hovered',
      target: {
        kind: 'handCard',
        instanceId: input.hoveredHandCard,
      },
    }
  }

  if (input.hoveredEntity) {
    if (input.hoveredEntity.kind === 'summon') {
      return null
    }

    return {
      source: 'hovered',
      target:
        input.hoveredEntity.kind === 'player'
          ? { kind: 'player', playerId: input.hoveredEntity.id }
          : { kind: 'enemy', enemyId: input.hoveredEntity.id },
    }
  }

  return null
}

export function buildCombatInspectorViewModel(
  input: BuildCombatInspectorViewModelInput,
): CombatInspectorViewModel | null {
  const { screen, target, source, selectedCardId, selectedDiscardIds } = input
  if (!screen) {
    return null
  }

  if (target.kind === 'player') {
    const player = screen.actors.players.find((entry) => entry.playerId === target.playerId)
    if (!player) {
      return null
    }

    const statusTags: CombatTag[] = [
      { label: player.stateLabel, tone: 'accent' },
      ...player.statusTags.map((tag) => ({
        label: tag.label,
        tone: normalizeTagTone(tag.tone),
      })),
      ...(player.passives.length > 0
        ? player.passives.map((passive) => ({ label: passive, tone: 'accent' as const }))
        : [{ label: 'No passives', tone: 'muted' as const }]),
    ]

    return {
      kind: 'entity',
      source,
      target,
      title: player.playerId,
      subtitle: player.summaryLines[0] ?? 'Player combat unit',
      categoryLabel: 'Player entity',
      portraitLabel: portraitLabel(player.playerId),
      portraitVariant: 'default',
      metrics: toInspectorMetrics(player.metrics),
      statusTags,
      summaryLines: player.summaryLines,
      detailLines: formatPlayerDetailLines(player),
    }
  }

  if (target.kind === 'enemy') {
    const enemy = screen.actors.enemies.find((entry) => entry.enemyId === target.enemyId)
    if (!enemy) {
      return null
    }

    const statusTags: CombatTag[] = [
      { label: enemy.stateLabel, tone: 'warning' },
      ...(enemy.statusEntries.length > 0
        ? enemy.statusEntries.map((status) => ({ label: status, tone: 'warning' as const }))
        : [{ label: 'No statuses', tone: 'muted' as const }]),
    ]

    return {
      kind: 'entity',
      source,
      target,
      title: enemy.enemyId,
      subtitle: enemy.summaryLines[0] ?? 'Enemy combat unit',
      categoryLabel: 'Enemy entity',
      portraitLabel: portraitLabel(enemy.enemyId),
      portraitVariant: 'enemy',
      metrics: toInspectorMetrics(enemy.metrics),
      statusTags,
      summaryLines: enemy.summaryLines,
      detailLines: formatEnemyDetailLines(enemy),
    }
  }

  const owners = screen.actors.players
    .map((player) => ({
      player,
      card: player.handCards.find((entry) => entry.instanceId === target.instanceId) ?? null,
    }))
    .filter((entry) => entry.card)

  const ownerEntry = owners[0]
  if (!ownerEntry?.card) {
    return null
  }

  const card = ownerEntry.card

  return {
    kind: 'handCard',
    source,
    target,
    title: card.title,
    subtitle: ownerEntry.player.playerId,
    categoryLabel: 'Hand card',
    portraitLabel: portraitLabel(card.title),
    costOrType: card.subtitle || 'Type unavailable',
    description: card.meta ?? card.subtitle ?? '카드 설명이 없습니다.',
    keywordTags: card.tags.map((tag) => ({
      label: tag.label,
      tone: normalizeTagTone(tag.tone),
    })),
    ruleLines: [
      `Owner: ${ownerEntry.player.playerId}`,
      card.unresolved ? '아직 카드 정보를 불러오지 못했습니다.' : '카드 정보를 확인할 수 있습니다.',
      ...(card.defId ? [`Definition id: ${card.defId}`] : []),
    ],
    selectionSummaries: buildSelectionSummaries({
      screen,
      instanceId: card.instanceId,
      selectedCardId,
      selectedDiscardIds,
    }),
  }
}
