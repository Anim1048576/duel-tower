import assert from 'node:assert/strict'
import {
  createDeckEditorDraftSignature,
  isDeckEditorValidationLocallyStale,
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

runTest('local freshness is false immediately after validating the same draft', () => {
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
    validatedDraftSignature: createDeckEditorDraftSignature(state),
    validatedAt: '2026-04-15T00:00:00Z',
  }

  assert.equal(isDeckEditorValidationLocallyStale(state, validation), false)
})

runTest('local freshness becomes true after a validated draft changes count', () => {
  const validatedState = {
    type: 'PLAYER',
    cards: [{ key: 'deck-card-1', cardId: 'C001', count: 2 }],
  }

  const currentState = {
    type: 'PLAYER',
    cards: [{ key: 'deck-card-1', cardId: 'C001', count: 3 }],
  }

  assert.equal(
    isDeckEditorValidationLocallyStale(currentState, {
      validatedDraftSignature: createDeckEditorDraftSignature(validatedState),
    }),
    true,
  )
})

runTest('local freshness becomes true after a validated draft changes order', () => {
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
    isDeckEditorValidationLocallyStale(reorderedState, {
      validatedDraftSignature: createDeckEditorDraftSignature(validatedState),
    }),
    true,
  )
})

runTest('validated draft signature includes deck type', () => {
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
