import type { PendingDecisionDto, PlayerStateDto } from '../../../lib/api/sessionTypes'

export function getSelectedDiscardIdsFromHand(
  player: PlayerStateDto | null,
  selectedDiscardIds: readonly string[],
) {
  const handIds = new Set(player?.hand ?? [])
  return selectedDiscardIds.filter((instanceId) => handIds.has(instanceId))
}

export function getSelectedFieldIds(
  player: PlayerStateDto | null,
  selectedIds: readonly string[],
) {
  const fieldIds = new Set(player?.field ?? [])
  return selectedIds.filter((instanceId) => fieldIds.has(instanceId))
}

export function getPendingCandidateIds(
  pendingDecision: PendingDecisionDto | null,
  selectedIds: readonly string[],
) {
  const candidateIds = new Set(pendingDecision?.candidateIds ?? [])
  return selectedIds.filter((value) => candidateIds.has(value))
}

export function getOrderedTieActorKeys(
  pendingDecision: PendingDecisionDto | null,
  orderedActorKeys: readonly string[],
) {
  const actorKeys = new Set(pendingDecision?.actorKeys ?? [])
  return orderedActorKeys.filter((actorKey) => actorKeys.has(actorKey))
}
