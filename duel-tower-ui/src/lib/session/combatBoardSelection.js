/**
 * @typedef {import('../api/screenTypes').CombatRequirementViewDto} CombatRequirementViewDto
 * @typedef {import('../api/screenTypes').CombatScreenResponse} CombatScreenResponse
 * @typedef {import('../api/sessionTypes').TargetRefDto} TargetRefDto
 * @typedef {{
 *   allyPlayerId: string | null
 *   fieldOwnerById: Map<string, string>
 * }} BoardSelectionContext
 */

/** @param {string} playerId */
export function targetKeyForPlayer(playerId) {
  return `player:${playerId}`
}

/** @param {string} enemyId */
export function targetKeyForEnemy(enemyId) {
  return `enemy:${enemyId}`
}

/** @param {string} owner
 *  @param {string} summonId
 */
export function targetKeyForSummon(owner, summonId) {
  return `summon:${owner}:${summonId}`
}

/** @param {readonly string[]} targetKeys
 *  @returns {TargetRefDto[]}
 */
export function buildTargetRefs(targetKeys) {
  return /** @type {TargetRefDto[]} */ (targetKeys
    .map(/** @returns {TargetRefDto | null} */ (key) => {
      if (key.startsWith('player:')) {
        return {
          playerId: key.slice('player:'.length),
        }
      }

      if (key.startsWith('enemy:')) {
        return {
          enemyId: key.slice('enemy:'.length),
        }
      }

      if (key.startsWith('summon:')) {
        const [, owner, summonId] = key.split(':')
        if (!owner || !summonId) {
          return null
        }

        return {
          summonOwnerPlayerId: owner,
          summonInstanceId: summonId,
        }
      }

      return null
    })
    .filter((value) => Boolean(value)))
}

/** @param {string} instanceId */
export function fieldSelectionKey(instanceId) {
  return `field:${instanceId}`
}

/** @param {string} key */
export function isFieldSelectionKey(key) {
  return key.startsWith('field:')
}

/** @param {string} key */
export function fieldIdFromSelectionKey(key) {
  return key.startsWith('field:') ? key.slice('field:'.length) : null
}

/** @param {readonly string[]} values */
export function uniqueIdentifiers(values) {
  return [...new Set(values)]
}

/** @param {readonly string[]} targetKeys
 *  @param {readonly string[]} fieldIds
 */
export function buildSelectedBoardObjectKeys(targetKeys, fieldIds) {
  return uniqueIdentifiers([...targetKeys, ...fieldIds.map((instanceId) => fieldSelectionKey(instanceId))])
}

/** @param {CombatScreenResponse | null | undefined} screenModel
 *  @param {string | null | undefined} allyPlayerId
 *  @returns {BoardSelectionContext}
 */
export function createBoardSelectionContext(screenModel, allyPlayerId) {
  /** @type {Map<string, string>} */
  const fieldOwnerById = new Map()

  for (const player of screenModel?.actors?.players ?? []) {
    for (const card of player.fieldCards ?? []) {
      fieldOwnerById.set(card.instanceId, player.playerId)
    }
  }

  return {
    allyPlayerId: allyPlayerId ?? null,
    fieldOwnerById,
  }
}

/** @param {string} key
 *  @param {BoardSelectionContext} context
 */
export function describeBoardSelectionKey(key, context) {
  if (key.startsWith('player:')) {
    return { key, kind: 'player', boardKind: 'CHARACTER', relation: 'ALLY', ownerPlayerId: null }
  }

  if (key.startsWith('enemy:')) {
    return { key, kind: 'enemy', boardKind: 'CHARACTER', relation: 'HOSTILE', ownerPlayerId: null }
  }

  if (key.startsWith('summon:')) {
    const [, owner] = key.split(':')
    return {
      key,
      kind: 'summon',
      boardKind: 'SUMMON',
      relation:
        owner && context?.allyPlayerId ? (owner === context.allyPlayerId ? 'ALLY' : 'HOSTILE') : null,
      ownerPlayerId: owner ?? null,
    }
  }

  if (isFieldSelectionKey(key)) {
    const instanceId = fieldIdFromSelectionKey(key)
    const ownerPlayerId = instanceId ? (context?.fieldOwnerById.get(instanceId) ?? null) : null
    return {
      key,
      kind: 'field',
      boardKind: 'FIELD_CARD',
      relation:
        ownerPlayerId && context?.allyPlayerId
          ? ownerPlayerId === context.allyPlayerId
            ? 'ALLY'
            : 'HOSTILE'
          : null,
      ownerPlayerId,
      instanceId,
    }
  }

  return null
}

/** @param {CombatRequirementViewDto | null | undefined} requirement */
export function getBoardCountChoiceOptions(requirement) {
  return uniqueIdentifiers((requirement?.boardObjectSelectionHints?.allowedCounts ?? []).map(String)).map(Number)
}

/** @param {CombatRequirementViewDto | null | undefined} requirement */
export function requiresBoardCountChoice(requirement) {
  const countOptions = getBoardCountChoiceOptions(requirement)
  return countOptions.length > 1 && !(requirement?.boardObjectSelectionHints?.skipCountChoice ?? false)
}

/** @param {CombatRequirementViewDto | null | undefined} requirement
 *  @param {number | null | undefined} selectedCount
 */
export function resolveBoardSelectionLimit(requirement, selectedCount) {
  const boardRequirement = requirement?.boardObjectRequirement
  if (!boardRequirement) {
    return null
  }

  const countOptions = getBoardCountChoiceOptions(requirement)
  if (countOptions.length === 0) {
    return boardRequirement.maxSelections
  }

  if (requirement?.boardObjectSelectionHints?.skipCountChoice) {
    return countOptions[0] ?? boardRequirement.maxSelections
  }

  return selectedCount != null && countOptions.includes(selectedCount) ? selectedCount : null
}

/** @param {CombatRequirementViewDto | null | undefined} requirement
 *  @param {string} key
 *  @param {BoardSelectionContext} context
 */
export function acceptsBoardSelectionKey(requirement, key, context) {
  const boardRequirement = requirement?.boardObjectRequirement
  if (!boardRequirement) {
    return true
  }

  const descriptor = describeBoardSelectionKey(key, context)
  if (!descriptor) {
    return false
  }

  if (!boardRequirement.kinds.includes(/** @type {import('../api/screenTypes').CombatBoardObjectKind} */ (descriptor.boardKind))) {
    return false
  }

  if (boardRequirement.relation === 'ANY') {
    return true
  }

  return descriptor.relation === boardRequirement.relation
}

/** @param {readonly string[]} keys
 *  @param {CombatRequirementViewDto | null | undefined} requirement
 *  @param {BoardSelectionContext} context
 */
export function filterBoardSelectionKeysForRequirement(keys, requirement, context) {
  return keys.filter((key) => acceptsBoardSelectionKey(requirement, key, context))
}

/** @param {{
 *   requirement: CombatRequirementViewDto | null | undefined
 *   selectedTargetKeys: readonly string[]
 *   selectedFieldIds: readonly string[]
 *   context: BoardSelectionContext
 * }} options
 */
export function buildBoardSelectionPayload({
  requirement,
  selectedTargetKeys,
  selectedFieldIds,
  context,
}) {
  const boardSelectionKeys = filterBoardSelectionKeysForRequirement(
    buildSelectedBoardObjectKeys(selectedTargetKeys, selectedFieldIds),
    requirement,
    context,
  )
  return {
    targets: buildTargetRefs(boardSelectionKeys.filter((key) => !isFieldSelectionKey(key))),
    selectedIds: boardSelectionKeys
      .map((key) => fieldIdFromSelectionKey(key))
      .filter((value) => Boolean(value)),
  }
}
