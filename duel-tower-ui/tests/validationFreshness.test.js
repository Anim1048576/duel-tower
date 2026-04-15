import assert from 'node:assert/strict'
import {
  createDeckEditorDraftSignature,
  isDeckEditorValidationStale,
} from '../src/lib/decks/validationFreshness.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

runTest('validate 직후 stale=false', () => {
  const state = {
    name: 'Starter',
    type: 'PLAYER',
    cards: [
      { key: 'deck-card-1', cardId: 'C001', count: 2 },
      { key: 'deck-card-2', cardId: 'C002', count: 1 },
    ],
  }

  const validation = {
    valid: true,
    normalizedTotalCards: 3,
    issues: [],
    isStale: false,
    validatedDraftSignature: createDeckEditorDraftSignature(state),
  }

  assert.equal(isDeckEditorValidationStale(state, validation), false)
})

runTest('validation 이후 카드 수 변경 stale=true', () => {
  const validatedState = {
    type: 'PLAYER',
    cards: [{ key: 'deck-card-1', cardId: 'C001', count: 2 }],
  }

  const currentState = {
    type: 'PLAYER',
    cards: [{ key: 'deck-card-1', cardId: 'C001', count: 3 }],
  }

  assert.equal(
    isDeckEditorValidationStale(currentState, {
      validatedDraftSignature: createDeckEditorDraftSignature(validatedState),
    }),
    true,
  )
})

runTest('validation 이후 카드 순서 변경 stale=true', () => {
  const validatedState = {
    type: 'PLAYER',
    cards: [
      { key: 'deck-card-1', cardId: 'C001', count: 1 },
      { key: 'deck-card-2', cardId: 'C002', count: 1 },
    ],
  }

  const reorderedState = {
    type: 'PLAYER',
    cards: [
      { key: 'deck-card-1', cardId: 'C002', count: 1 },
      { key: 'deck-card-2', cardId: 'C001', count: 1 },
    ],
  }

  assert.equal(
    isDeckEditorValidationStale(reorderedState, {
      validatedDraftSignature: createDeckEditorDraftSignature(validatedState),
    }),
    true,
  )
})

runTest('deck type도 signature에 포함', () => {
  const playerSignature = createDeckEditorDraftSignature({
    type: 'PLAYER',
    cards: [{ cardId: 'C001', count: 1 }],
  })
  const enemySignature = createDeckEditorDraftSignature({
    type: 'ENEMY',
    cards: [{ cardId: 'C001', count: 1 }],
  })

  assert.notEqual(playerSignature, enemySignature)
})
