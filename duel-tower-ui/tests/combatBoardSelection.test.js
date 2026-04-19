import assert from 'node:assert/strict'

import {
  acceptsBoardSelectionKey,
  buildBoardSelectionPayload,
  createBoardSelectionContext,
  fieldSelectionKey,
  requiresBoardCountChoice,
  resolveBoardSelectionLimit,
  targetKeyForEnemy,
  targetKeyForSummon,
} from '../src/lib/session/combatBoardSelection.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

function createScreenSample() {
  return {
    zones: {
      visiblePlayerId: 'p1',
    },
    actors: {
      players: [
        {
          playerId: 'p1',
          fieldCards: [{ instanceId: 'field-ally', title: 'Ally Ward' }],
        },
        {
          playerId: 'p2',
          fieldCards: [{ instanceId: 'field-hostile', title: 'Hostile Sigil' }],
        },
      ],
      enemies: [{ enemyId: 'e1' }],
      summons: [{ summonId: 's1', owner: 'p2' }],
    },
  }
}

runTest('Tig901_EX count choice is skipped when metadata exposes exactly one allowed count', () => {
  const requirement = {
    boardObjectRequirement: {
      minSelections: 1,
      maxSelections: 2,
      kinds: ['CHARACTER', 'SUMMON'],
      relation: 'HOSTILE',
    },
    boardObjectSelectionHints: {
      candidateCount: 1,
      allowedCounts: [1],
      skipCountChoice: true,
    },
  }

  assert.equal(requiresBoardCountChoice(requirement), false)
  assert.equal(resolveBoardSelectionLimit(requirement, null), 1)
})

runTest('Tig901_EX count choice is required when metadata exposes 1 or 2 hostile targets', () => {
  const requirement = {
    boardObjectRequirement: {
      minSelections: 1,
      maxSelections: 2,
      kinds: ['CHARACTER', 'SUMMON'],
      relation: 'HOSTILE',
    },
    boardObjectSelectionHints: {
      candidateCount: 2,
      allowedCounts: [1, 2],
      skipCountChoice: false,
    },
  }

  assert.equal(requiresBoardCountChoice(requirement), true)
  assert.equal(resolveBoardSelectionLimit(requirement, null), null)
  assert.equal(resolveBoardSelectionLimit(requirement, 2), 2)
})

runTest('field-card relation matching uses actual owner data instead of hardcoded ally relation', () => {
  const screen = createScreenSample()
  const context = createBoardSelectionContext(screen, 'p1')
  const requirement = {
    boardObjectRequirement: {
      minSelections: 1,
      maxSelections: 1,
      kinds: ['FIELD_CARD'],
      relation: 'HOSTILE',
    },
  }

  assert.equal(acceptsBoardSelectionKey(requirement, fieldSelectionKey('field-hostile'), context), true)
  assert.equal(acceptsBoardSelectionKey(requirement, fieldSelectionKey('field-ally'), context), false)
})

runTest('board-object payload keeps field-card selections in selectedIds and combat targets in targets', () => {
  const screen = createScreenSample()
  const context = createBoardSelectionContext(screen, 'p1')
  const fieldRequirement = {
    boardObjectRequirement: {
      minSelections: 0,
      maxSelections: 3,
      kinds: ['FIELD_CARD'],
      relation: 'ANY',
    },
    boardObjectSelectionHints: {
      candidateCount: 2,
      allowedCounts: [0, 1, 2],
      skipCountChoice: false,
    },
  }
  const hostileRequirement = {
    boardObjectRequirement: {
      minSelections: 1,
      maxSelections: 2,
      kinds: ['CHARACTER', 'SUMMON'],
      relation: 'HOSTILE',
    },
    boardObjectSelectionHints: {
      candidateCount: 2,
      allowedCounts: [1, 2],
      skipCountChoice: false,
    },
  }

  const fieldPayload = buildBoardSelectionPayload({
    requirement: fieldRequirement,
    selectedTargetKeys: [targetKeyForEnemy('e1')],
    selectedFieldIds: ['field-hostile'],
    context,
  })
  const targetPayload = buildBoardSelectionPayload({
    requirement: hostileRequirement,
    selectedTargetKeys: [targetKeyForEnemy('e1'), targetKeyForSummon('p2', 's1')],
    selectedFieldIds: ['field-hostile'],
    context,
  })

  assert.deepEqual(fieldPayload.selectedIds, ['field-hostile'])
  assert.deepEqual(fieldPayload.targets, [])
  assert.equal(targetPayload.selectedIds.length, 0)
  assert.deepEqual(targetPayload.targets, [
    { enemyId: 'e1' },
    { summonOwnerPlayerId: 'p2', summonInstanceId: 's1' },
  ])
})
