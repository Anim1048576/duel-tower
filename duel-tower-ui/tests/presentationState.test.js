import assert from 'node:assert/strict'
import {
  buildDeckEditorLocalSummary,
  getDeckEditorDeckTypeLabel,
  getDeckEditorLocalTitle,
  getDeckEditorLocalTotalCards,
  isDeckEditorLocalDirty,
} from '../src/lib/decks/presentationState.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

runTest('카드 count 변경 직후 totalCards가 로컬 입력을 따른다', () => {
  assert.equal(
    getDeckEditorLocalTotalCards({
      name: 'Deck',
      type: 'PLAYER',
      cards: [
        { key: 'deck-card-1', cardId: 'C001', count: 2 },
        { key: 'deck-card-2', cardId: 'C002', count: 3 },
      ],
    }),
    5,
  )
})

runTest('deck name 변경 직후 dirty=true', () => {
  assert.equal(
    isDeckEditorLocalDirty(
      {
        name: 'Changed name',
        type: 'PLAYER',
        cards: [{ key: 'deck-card-1', cardId: 'C001', count: 2 }],
      },
      {
        name: 'Original name',
        type: 'PLAYER',
        cards: [{ key: 'deck-card-1', cardId: 'C001', count: 2, position: 1 }],
      },
    ),
    true,
  )
})

runTest('카드 순서 변경 직후 dirty=true', () => {
  assert.equal(
    isDeckEditorLocalDirty(
      {
        name: 'Deck',
        type: 'PLAYER',
        cards: [
          { key: 'deck-card-1', cardId: 'C002', count: 1 },
          { key: 'deck-card-2', cardId: 'C001', count: 1 },
        ],
      },
      {
        name: 'Deck',
        type: 'PLAYER',
        cards: [
          { key: 'deck-card-1', cardId: 'C001', count: 1, position: 1 },
          { key: 'deck-card-2', cardId: 'C002', count: 1, position: 2 },
        ],
      },
    ),
    true,
  )
})

runTest('로컬 title과 type label이 입력 상태를 따른다', () => {
  assert.equal(
    getDeckEditorLocalTitle(
      { name: '  Local Draft Name  ', type: 'ENEMY', cards: [] },
      { name: 'Server Name', type: 'PLAYER', cards: [] },
      'edit',
    ),
    'Local Draft Name',
  )
  assert.equal(getDeckEditorDeckTypeLabel('ENEMY'), 'Enemy')
})

runTest('summary가 로컬 카드 수를 반영한다', () => {
  assert.equal(
    buildDeckEditorLocalSummary({
      name: 'Deck',
      type: 'PLAYER',
      cards: [{ key: 'deck-card-1', cardId: 'C001', count: 4 }],
    }),
    '4 total cards are currently distributed across 1 entry.',
  )
})
