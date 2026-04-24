import assert from 'node:assert/strict'
import {
  createCombatPendingDecisionView,
  getLastWordsPendingLocalBlock,
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

function createLastWordsMetadata({
  candidateIds = ['last-words-1'],
  candidateCards,
  canSkip = true,
} = {}) {
  return {
    kind: 'pendingDecision',
    note: 'LAST_WORDS pending test metadata',
    supported: true,
    pendingDecisionType: 'LAST_WORDS',
    schema: {
      type: 'LAST_WORDS',
      reason: 'manual last words test',
      candidateIds,
      candidateCards,
      canSkip,
      selectedIdsField: 'selectedIds',
    },
  }
}

runTest('LAST_WORDS pending view preserves candidateCards display data', () => {
  const candidateCards = [
    {
      instanceId: 'last-words-1',
      defId: 'C001',
      title: '마지막 불꽃',
      subtitle: 'Attack',
      unresolved: false,
      tags: [{ label: '유언', tone: 'warning' }],
      meta: 'Cost 2 AP',
    },
    {
      instanceId: 'last-words-2',
      defId: 'C002',
      title: '남겨진 결의',
      subtitle: 'Skill',
      unresolved: false,
      tags: [{ label: 'Skill', tone: 'accent' }],
      meta: 'Cost 1 AP',
    },
  ]

  const view = createCombatPendingDecisionView(createLastWordsMetadata({
    candidateIds: candidateCards.map((card) => card.instanceId),
    candidateCards,
  }))

  assert.ok(view)
  assert.deepEqual(view.candidateIds, ['last-words-1', 'last-words-2'])
  assert.equal(view.candidateCards, candidateCards)
  assert.equal(view.candidateCards[0].title, '마지막 불꽃')
  assert.equal(view.candidateCards[0].subtitle, 'Attack')
  assert.deepEqual(view.candidateCards[0].tags, [{ label: '유언', tone: 'warning' }])
  assert.equal(view.candidateCards[0].meta, 'Cost 2 AP')
})

runTest('LAST_WORDS allows resolve without selectedPendingIds when canSkip is true', () => {
  assert.equal(getLastWordsPendingLocalBlock([], true), null)
})

runTest('LAST_WORDS blocks empty selectedPendingIds when canSkip is false', () => {
  assert.equal(
    getLastWordsPendingLocalBlock([], false),
    '유언을 발동할 카드 1장을 선택해 주세요.',
  )
})

runTest('LAST_WORDS blocks two or more selectedPendingIds', () => {
  assert.equal(
    getLastWordsPendingLocalBlock(['last-words-1', 'last-words-2'], true),
    '유언은 최대 1장만 선택할 수 있습니다.',
  )
})
