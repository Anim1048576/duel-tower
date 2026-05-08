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
 * @typedef {import('../api/screenTypes').CombatActionId | null} CombatSelectedActionId
 */
/** @type {Record<string, import('../api/screenTypes').CombatActionId>} */
const COMBAT_ACTION_ID_BY_COMMAND_TYPE = {
  DRAW: 'combat.draw',
  END_TURN: 'combat.endTurn',
  CLEAR_RECENT_RESULTS: 'combat.clearRecentResults',
  PLAY_CARD: 'combat.playCard',
  USE_EX: 'combat.useEx',
  HAND_SWAP: 'combat.handSwap',
  DISCARD_TO_HAND_LIMIT: 'combat.resolvePending',
  SEARCH_PICK: 'combat.resolvePending',
  LAST_WORDS: 'combat.resolvePending',
  INITIATIVE_TIE_ORDER: 'combat.resolvePending',
  RESOLVE_INITIATIVE_TIE: 'combat.resolvePending',
}

/** @type {Record<string, import('../api/screenTypes').CombatActionId>} */
const COMBAT_ACTION_ID_BY_CAMEL_CASE = {
  draw: 'combat.draw',
  endTurn: 'combat.endTurn',
  clearRecentResults: 'combat.clearRecentResults',
  playCard: 'combat.playCard',
  useEx: 'combat.useEx',
  handSwap: 'combat.handSwap',
  resolvePending: 'combat.resolvePending',
}

/**
 * @param {string | null | undefined} value
 * @returns {import('../api/screenTypes').CombatActionId | null}
 */
function normalizeCombatActionId(value) {
  const rawValue = String(value ?? '').trim()
  if (!rawValue) {
    return null
  }

  if (COMBAT_ACTION_ID_BY_CAMEL_CASE[rawValue]) {
    return COMBAT_ACTION_ID_BY_CAMEL_CASE[rawValue]
  }

  if (rawValue.startsWith('combat.') && COMBAT_ACTION_ID_BY_CAMEL_CASE[rawValue.slice('combat.'.length)]) {
    return COMBAT_ACTION_ID_BY_CAMEL_CASE[rawValue.slice('combat.'.length)]
  }

  return COMBAT_ACTION_ID_BY_COMMAND_TYPE[rawValue.toUpperCase()] ?? null
}

/**
 * @typedef {{
 *   selectedActionId: CombatSelectedActionId
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

/**
 * @param {string} playerId
 */
function targetKeyForPlayer(playerId) {
  return `player:${playerId}`
}

/**
 * @param {string} enemyId
 */
function targetKeyForEnemy(enemyId) {
  return `enemy:${enemyId}`
}

/**
 * @param {string} owner
 * @param {string} summonId
 */
function targetKeyForSummon(owner, summonId) {
  return `summon:${owner}:${summonId}`
}

/**
 * @param {import('../api/screenTypes').CombatScreenResponse | null | undefined} screen
 */
function getPendingMetadata(screen) {
  if (!screen?.possibleActions?.length) {
    return null
  }

  /** @param {import('../api/screenTypes').CombatScreenAction} candidate */
  const action = screen.possibleActions.find((candidate) => candidate.id === 'combat.resolvePending') ?? null
  if (!action?.metadata || action.metadata.kind !== 'pendingDecision') {
    return null
  }

  return action.metadata
}

/**
 * @param {import('../api/screenTypes').CombatScreenResponse | null | undefined} screen
 */
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

/**
 * @param {import('../api/screenTypes').CombatScreenResponse | null | undefined} screen
 */
export function getPlayCardSourceOptions(screen) {
  const action = (screen?.possibleActions ?? []).find((candidate) => candidate.id === 'combat.playCard') ?? null
  const metadata = action?.metadata
  return metadata?.kind === 'playCard' ? (metadata.sourceOptions ?? []) : []
}

/**
 * @template T
 * @param {T[]} values
 * @returns {T[]}
 */
function uniqueValues(values) {
  return Array.from(new Set(values))
}

/**
 * @param {import('../api/screenTypes').CombatScreenResponse} nextScreen
 * @param {CombatLocalSelectionState} currentState
 * @param {{
 *   reason: CombatScreenRefreshReason
 *   previousScreen?: import('../api/screenTypes').CombatScreenResponse | null
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
  const validHandIds = new Set((nextScreen?.zones?.hand ?? []).map((/** @type {import('../api/screenTypes').CombatCardDto} */ card) => card.instanceId))
  const playableHandIds = new Set(getPlayCardSourceOptions(nextScreen).map((source) => source.instanceId))
  const validFieldIds = new Set((nextScreen?.zones?.field ?? []).map((/** @type {import('../api/screenTypes').CombatCardDto} */ card) => card.instanceId))
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
    (nextScreen?.actors?.players ?? []).some((/** @type {import('../api/screenTypes').CombatPlayerDto} */ player) => player.playerId === nextState.selectedPlayerId)
      ? nextState.selectedPlayerId
      : nextScreen?.zones?.visiblePlayerId ?? null

  const normalizedSelectedActionId = normalizeCombatActionId(nextState.selectedActionId)
  nextState.selectedActionId =
    normalizedSelectedActionId &&
    (nextScreen?.possibleActions ?? []).some((/** @type {import('../api/screenTypes').CombatScreenAction} */ action) => action.id === normalizedSelectedActionId)
      ? normalizedSelectedActionId
      : null

  const nextSelectedCardId =
    nextState.selectedCardId && playableHandIds.has(nextState.selectedCardId) ? nextState.selectedCardId : null
  const selectedCardLost = nextState.selectedCardId !== null && nextSelectedCardId === null
  nextState.selectedCardId = nextSelectedCardId

  nextState.selectedTargetKeys = uniqueValues(nextState.selectedTargetKeys.filter((key) => validTargetKeys.has(key)))
  nextState.selectedDiscardIds = uniqueValues(nextState.selectedDiscardIds.filter((id) => validHandIds.has(id)))
  nextState.selectedFieldIds = uniqueValues(nextState.selectedFieldIds.filter((id) => validFieldIds.has(id)))

  if (nextState.selectedActionId === 'combat.handSwap') {
    nextState.selectedCardId = null
    nextState.selectedDiscardIds = nextState.selectedDiscardIds.slice(0, 1)
  }

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
    nextState.orderedActorKeys = (nextPendingSchema?.actorKeys ?? []).filter((/** @type {string} */ actorKey) =>
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
