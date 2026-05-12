/**
 * Screen API pending-decision helpers.
 *
 * SessionState pending decisions stay intentionally small. Combat screen actions
 * may add richer schema data for command UI, so keep those projections here.
 */

/**
 * @typedef {import('../api/sessionTypes').PendingDecisionDto & {
 *   candidateCards: import('../api/screenTypes').CombatCardDto[] | null
 * }} CombatPendingDecisionViewModel
 */

/**
 * @param {import('../api/screenTypes').CombatPendingActionMetadataDto | null | undefined} metadata
 * @returns {CombatPendingDecisionViewModel | null}
 */
export function createCombatPendingDecisionView(metadata) {
  if (!metadata?.pendingDecisionType) {
    return null
  }

  const schema = metadata.schema

  return {
    type: metadata.pendingDecisionType,
    reason: schema?.reason ?? null,
    playerId: schema?.playerId ?? null,
    statusId: schema?.statusId ?? null,
    limit: schema?.discardCount ?? null,
    pickCount: schema?.pickCount ?? null,
    candidateIds: schema?.candidateIds ?? [],
    choiceIds: schema?.choiceIds ?? schema?.candidateIds ?? [],
    candidateCards: schema?.candidateCards ?? null,
    canSkip: schema?.canSkip ?? null,
    destination: schema?.destination ?? null,
    shuffleAfterPick: schema?.shuffleAfterPick ?? null,
    groupIndex: schema?.groupIndex ?? null,
    actorKeys: schema?.actorKeys ?? [],
  }
}

/**
 * @param {readonly string[]} selectedPendingIds
 * @param {boolean | null | undefined} canSkip
 * @returns {string | null}
 */
export function getLastWordsPendingLocalBlock(selectedPendingIds, canSkip) {
  if (selectedPendingIds.length >= 2) {
    return '유언은 최대 1장만 선택할 수 있습니다.'
  }

  if (canSkip) {
    return null
  }

  if (selectedPendingIds.length !== 1) {
    return '유언을 발동할 카드 1장을 선택해 주세요.'
  }

  return null
}

/**
 * @param {readonly string[]} selectedPendingIds
 * @param {boolean | null | undefined} canSkip
 * @returns {string | null}
 */
export function getReactionPendingLocalBlock(selectedPendingIds, canSkip) {
  if (selectedPendingIds.length > 1) {
    return 'Select only one reaction card.'
  }

  if (selectedPendingIds.length === 0 && !canSkip) {
    return 'Select a reaction card.'
  }

  return null
}
