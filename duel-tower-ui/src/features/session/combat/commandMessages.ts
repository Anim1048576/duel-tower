import type { PendingDecisionDto, TargetRefDto } from '../../../lib/api/sessionTypes'

export function formatTargetSelectionLabel(target: TargetRefDto) {
  if (target.enemyId) {
    return `Enemy ${target.enemyId}`
  }

  if (target.playerId) {
    return `Player ${target.playerId}`
  }

  if (target.summonOwnerPlayerId && target.summonInstanceId) {
    return `Summon ${target.summonInstanceId}`
  }

  return 'Unknown target'
}

export function formatTargetRefSummary(targets: readonly TargetRefDto[]) {
  const labels = targets
    .map((target) => target.enemyId ?? target.playerId ?? target.summonInstanceId ?? null)
    .filter(Boolean)

  return labels.length > 0 ? labels.join(', ') : 'None'
}

export function getUnsupportedCardCommandMessage() {
  return null
}

export function getUnsupportedPendingDecisionMessage(pendingDecision: PendingDecisionDto | null) {
  if (!pendingDecision?.type) {
    return 'Pending decision type is missing.'
  }

  switch (pendingDecision.type) {
    case 'DISCARD_TO_HAND_LIMIT':
    case 'SEARCH_PICK':
    case 'INITIATIVE_TIE_ORDER':
    case 'LAST_WORDS':
      return null
    default:
      return `${pendingDecision.type} is not supported in this step yet.`
  }
}
