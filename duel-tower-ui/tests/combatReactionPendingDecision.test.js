import assert from 'node:assert/strict'
import {
  createCombatPendingDecisionView,
  getReactionPendingLocalBlock,
} from '../src/lib/session/combatPendingDecision.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

function createReactionMetadata({
  candidateIds = ['reaction-1'],
  candidateCards,
  canSkip = true,
} = {}) {
  return {
    kind: 'pendingDecision',
    note: 'REACTION_CARD pending test metadata',
    supported: true,
    pendingDecisionType: 'REACTION_CARD',
    schema: {
      type: 'REACTION_CARD',
      reason: 'AFTER_ENEMY_ATTACK_DAMAGED_SELF',
      candidateIds,
      candidateCards,
      canSkip,
      selectedIdsField: 'cardId',
    },
  }
}

runTest('REACTION_CARD pending view preserves reaction candidates', () => {
  const candidateCards = [
    {
      instanceId: 'reaction-1',
      defId: 'C005',
      title: '긴급 공격',
      subtitle: 'Skill',
      unresolved: false,
      tags: [{ label: 'Skill', tone: 'accent' }],
      meta: 'Cost 1',
    },
  ]

  const view = createCombatPendingDecisionView(createReactionMetadata({
    candidateIds: ['reaction-1'],
    candidateCards,
  }))

  assert.ok(view)
  assert.equal(view.type, 'REACTION_CARD')
  assert.equal(view.reason, 'AFTER_ENEMY_ATTACK_DAMAGED_SELF')
  assert.deepEqual(view.candidateIds, ['reaction-1'])
  assert.equal(view.candidateCards, candidateCards)
  assert.equal(view.canSkip, true)
})

runTest('REACTION_CARD allows skip when canSkip is true', () => {
  assert.equal(getReactionPendingLocalBlock([], true), null)
})

runTest('REACTION_CARD blocks empty selection when skip is not allowed', () => {
  assert.equal(getReactionPendingLocalBlock([], false), 'Select a reaction card.')
})

runTest('REACTION_CARD blocks multiple selected ids', () => {
  assert.equal(getReactionPendingLocalBlock(['reaction-1', 'reaction-2'], true), 'Select only one reaction card.')
})
