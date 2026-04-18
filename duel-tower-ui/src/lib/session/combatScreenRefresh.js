/**
 * Combat refresh policy keeps the live screen update path readable.
 * The page decides why it is refreshing, and this helper decides:
 * - whether to show loading UI
 * - whether to use `afterVersion` screen polling
 * - which local selection/input state survives the next screen snapshot
 *
 * Combat keeps only lightweight local presentation/input state.
 * Server-owned combat rules and command availability still come from the
 * current combat screen model and action metadata.
 */

/**
 * @typedef {'initial-load' | 'retry-load' | 'route-change' | 'polling' | 'action-success' | 'action-failure'} CombatScreenRefreshReason
 */

/**
 * @typedef {{
 *   selectedActionId: string | null
 *   selectedPlayerId: string | null
 *   selectedCardId: string | null
 *   selectedTargetKeys: string[]
 *   selectedDiscardIds: string[]
 *   selectedFieldIds: string[]
 *   selectedPendingIds: string[]
 *   orderedActorKeys: string[]
 *   selectedCount: number | null
 *   selectedReason: string
 * }} CombatLocalSelectionState
 */

/**
 * @returns {CombatLocalSelectionState}
 */
export function createEmptyCombatLocalSelectionState() {
  return {
    selectedActionId: null,
    selectedPlayerId: null,
    selectedCardId: null,
    selectedTargetKeys: [],
    selectedDiscardIds: [],
    selectedFieldIds: [],
    selectedPendingIds: [],
    orderedActorKeys: [],
    selectedCount: 1,
    selectedReason: '',
  }
}

/**
 * @param {CombatScreenRefreshReason} reason
 */
export function resolveCombatScreenRefreshPlan(reason) {
  switch (reason) {
    case 'initial-load':
    case 'route-change':
      return {
        showLoading: true,
        useAfterVersion: false,
        resetTransientSelection: true,
        clearActionFeedback: true,
      }
    case 'retry-load':
      return {
        showLoading: true,
        useAfterVersion: false,
        resetTransientSelection: false,
        clearActionFeedback: false,
      }
    case 'polling':
      return {
        showLoading: false,
        useAfterVersion: true,
        resetTransientSelection: false,
        clearActionFeedback: false,
      }
    case 'action-success':
      return {
        showLoading: false,
        useAfterVersion: true,
        resetTransientSelection: true,
        clearActionFeedback: false,
      }
    case 'action-failure':
      return {
        showLoading: false,
        useAfterVersion: false,
        resetTransientSelection: false,
        clearActionFeedback: false,
      }
  }
}

function targetKeyForPlayer(playerId) {
  return `player:${playerId}`
}

function targetKeyForEnemy(enemyId) {
  return `enemy:${enemyId}`
}

function targetKeyForSummon(owner, summonId) {
  return `summon:${owner}:${summonId}`
}

function getPendingMetadata(screen) {
  if (!screen?.possibleActions?.length) {
    return null
  }

  const action = screen.possibleActions.find((candidate) => candidate.id === 'combat.resolvePending') ?? null
  if (!action?.metadata || action.metadata.kind !== 'pendingDecision') {
    return null
  }

  return action.metadata
}

function getPendingDecisionSignature(screen) {
  const metadata = getPendingMetadata(screen)
  if (!metadata?.pendingDecisionType) {
    return null
  }

  const schema = metadata.schema
  return JSON.stringify({
    type: metadata.pendingDecisionType,
    groupIndex: schema?.groupIndex ?? null,
    candidateIds: schema?.candidateIds ?? [],
    actorKeys: schema?.actorKeys ?? [],
  })
}

function uniqueValues(values) {
  return Array.from(new Set(values))
}

/**
 * @param {any} nextScreen
 * @param {CombatLocalSelectionState} currentState
 * @param {{
 *   reason: CombatScreenRefreshReason
 *   previousScreen?: any | null
 * }} options
 * @returns {CombatLocalSelectionState}
 */
export function reconcileCombatLocalSelectionState(nextScreen, currentState, { reason, previousScreen = null }) {
  const plan = resolveCombatScreenRefreshPlan(reason)
  const previousPendingSignature = getPendingDecisionSignature(previousScreen)
  const nextPendingMetadata = getPendingMetadata(nextScreen)
  const nextPendingSchema = nextPendingMetadata?.schema ?? null
  const nextPendingSignature = getPendingDecisionSignature(nextScreen)
  const pendingChanged = previousPendingSignature !== nextPendingSignature
  const validHandIds = new Set((nextScreen?.zones?.hand ?? []).map((card) => card.instanceId))
  const validFieldIds = new Set((nextScreen?.zones?.field ?? []).map((card) => card.instanceId))
  const validTargetKeys = new Set()
  const validCandidateIds = new Set(nextPendingSchema?.candidateIds ?? [])
  const validActorKeys = new Set(nextPendingSchema?.actorKeys ?? [])

  for (const player of nextScreen?.actors?.players ?? []) {
    validTargetKeys.add(targetKeyForPlayer(player.playerId))
  }

  for (const enemy of nextScreen?.actors?.enemies ?? []) {
    validTargetKeys.add(targetKeyForEnemy(enemy.enemyId))
  }

  for (const summon of nextScreen?.actors?.summons ?? []) {
    validTargetKeys.add(targetKeyForSummon(summon.owner, summon.summonId))
  }

  const nextState = plan.resetTransientSelection
    ? {
        ...createEmptyCombatLocalSelectionState(),
        selectedPlayerId: currentState.selectedPlayerId,
      }
    : {
        ...createEmptyCombatLocalSelectionState(),
        ...currentState,
        selectedTargetKeys: [...currentState.selectedTargetKeys],
        selectedDiscardIds: [...currentState.selectedDiscardIds],
        selectedFieldIds: [...currentState.selectedFieldIds],
        selectedPendingIds: [...currentState.selectedPendingIds],
        orderedActorKeys: [...currentState.orderedActorKeys],
      }

  nextState.selectedPlayerId =
    nextState.selectedPlayerId &&
    (nextScreen?.actors?.players ?? []).some((player) => player.playerId === nextState.selectedPlayerId)
      ? nextState.selectedPlayerId
      : nextScreen?.zones?.visiblePlayerId ?? null

  nextState.selectedActionId =
    nextState.selectedActionId &&
    (nextScreen?.possibleActions ?? []).some((action) => action.id === nextState.selectedActionId)
      ? nextState.selectedActionId
      : null

  const nextSelectedCardId =
    nextState.selectedCardId && validHandIds.has(nextState.selectedCardId) ? nextState.selectedCardId : null
  const selectedCardLost = nextState.selectedCardId !== null && nextSelectedCardId === null
  nextState.selectedCardId = nextSelectedCardId

  nextState.selectedTargetKeys = uniqueValues(nextState.selectedTargetKeys.filter((key) => validTargetKeys.has(key)))
  nextState.selectedDiscardIds = uniqueValues(nextState.selectedDiscardIds.filter((id) => validHandIds.has(id)))
  nextState.selectedFieldIds = uniqueValues(nextState.selectedFieldIds.filter((id) => validFieldIds.has(id)))

  if (selectedCardLost && nextState.selectedActionId === 'combat.playCard') {
    nextState.selectedTargetKeys = []
    nextState.selectedDiscardIds = []
    nextState.selectedFieldIds = []
  }

  if (!nextPendingMetadata?.pendingDecisionType) {
    nextState.selectedPendingIds = []
    nextState.orderedActorKeys = []
  } else if (pendingChanged) {
    nextState.selectedPendingIds = []
    nextState.orderedActorKeys = (nextPendingSchema?.actorKeys ?? []).filter((actorKey) =>
      currentState.orderedActorKeys.includes(actorKey),
    )
  } else {
    nextState.selectedPendingIds = uniqueValues(
      nextState.selectedPendingIds.filter((id) => validCandidateIds.has(id)),
    )
    nextState.orderedActorKeys = uniqueValues(
      nextState.orderedActorKeys.filter((actorKey) => validActorKeys.has(actorKey)),
    )
  }

  nextState.selectedCount =
    typeof nextState.selectedCount === 'number' &&
    Number.isInteger(nextState.selectedCount) &&
    nextState.selectedCount > 0
      ? nextState.selectedCount
      : 1

  nextState.selectedReason = typeof nextState.selectedReason === 'string' ? nextState.selectedReason : ''

  return nextState
}
