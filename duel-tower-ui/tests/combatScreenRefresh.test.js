import assert from 'node:assert/strict'
import {
  createEmptyCombatLocalSelectionState,
  reconcileCombatLocalSelectionState,
  resolveCombatScreenRefreshPlan,
} from '../src/lib/session/combatScreenRefresh.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

function createScreen({
  version = 7,
  handIds = ['card-1', 'card-2'],
  fieldIds = ['field-1', 'field-2'],
  candidateIds = ['candidate-1', 'candidate-2'],
  actorKeys = ['P:p1', 'E:e1'],
  visiblePlayerId = 'p1',
  pendingType = 'SEARCH_PICK',
} = {}) {
  return {
    version,
    zones: {
      visiblePlayerId,
      hand: handIds.map((instanceId) => ({ instanceId })),
      field: fieldIds.map((instanceId) => ({ instanceId })),
    },
    actors: {
      players: [{ playerId: 'p1' }, { playerId: 'p2' }],
      enemies: [{ enemyId: 'e1' }],
      summons: [{ owner: 'p1', summonId: 's1' }],
    },
    possibleActions: [
      { id: 'combat.playCard' },
      {
        id: 'combat.resolvePending',
        metadata: {
          kind: 'pendingDecision',
          pendingDecisionType: pendingType,
          schema: {
            candidateIds,
            actorKeys,
            groupIndex: 0,
          },
        },
      },
    ],
  }
}

runTest('combat refresh plan uses afterVersion only for polling and action-success follow-up', () => {
  assert.deepEqual(resolveCombatScreenRefreshPlan('initial-load'), {
    showLoading: true,
    useAfterVersion: false,
    resetTransientSelection: true,
    clearActionFeedback: true,
  })
  assert.deepEqual(resolveCombatScreenRefreshPlan('polling'), {
    showLoading: false,
    useAfterVersion: true,
    resetTransientSelection: false,
    clearActionFeedback: false,
  })
  assert.deepEqual(resolveCombatScreenRefreshPlan('action-success'), {
    showLoading: false,
    useAfterVersion: true,
    resetTransientSelection: true,
    clearActionFeedback: false,
  })
})

runTest('polling keeps valid local selection state', () => {
  const currentState = {
    ...createEmptyCombatLocalSelectionState(),
    selectedActionId: 'combat.playCard',
    selectedPlayerId: 'p2',
    selectedCardId: 'card-2',
    selectedTargetKeys: ['enemy:e1', 'summon:p1:s1'],
    selectedDiscardIds: ['card-1'],
    selectedFieldIds: ['field-2'],
    selectedPendingIds: ['candidate-2'],
    orderedActorKeys: ['E:e1'],
    selectedCount: 3,
    selectedReason: 'keep me',
  }

  assert.deepEqual(
    reconcileCombatLocalSelectionState(createScreen(), currentState, {
      reason: 'polling',
      previousScreen: createScreen(),
    }),
    currentState,
  )
})

runTest('selected card loss clears play-card-specific helper input while preserving visible player focus', () => {
  const currentState = {
    ...createEmptyCombatLocalSelectionState(),
    selectedActionId: 'combat.playCard',
    selectedPlayerId: 'p2',
    selectedCardId: 'missing-card',
    selectedTargetKeys: ['enemy:e1'],
    selectedDiscardIds: ['card-1'],
    selectedFieldIds: ['field-1'],
  }

  assert.deepEqual(
    reconcileCombatLocalSelectionState(createScreen(), currentState, {
      reason: 'polling',
      previousScreen: createScreen(),
    }),
    {
      ...createEmptyCombatLocalSelectionState(),
      selectedActionId: 'combat.playCard',
      selectedPlayerId: 'p2',
      selectedCardId: null,
    },
  )
})

runTest('action-success clears transient command input before applying the latest screen', () => {
  const currentState = {
    ...createEmptyCombatLocalSelectionState(),
    selectedActionId: 'combat.resolvePending',
    selectedPlayerId: 'p2',
    selectedCardId: 'card-1',
    selectedTargetKeys: ['enemy:e1'],
    selectedDiscardIds: ['card-1'],
    selectedFieldIds: ['field-1'],
    selectedPendingIds: ['candidate-1'],
    orderedActorKeys: ['P:p1'],
    selectedCount: 4,
    selectedReason: 'temporary',
  }

  assert.deepEqual(
    reconcileCombatLocalSelectionState(createScreen(), currentState, {
      reason: 'action-success',
      previousScreen: createScreen({ version: 6 }),
    }),
    {
      ...createEmptyCombatLocalSelectionState(),
      selectedPlayerId: 'p2',
    },
  )
})

runTest('pending decision transition clears candidate picks and reorders tie input to the new server actor order', () => {
  const previousScreen = createScreen({
    pendingType: 'INITIATIVE_TIE_ORDER',
    actorKeys: ['P:p1', 'E:e1'],
    candidateIds: [],
  })
  const nextScreen = createScreen({
    version: 8,
    pendingType: 'INITIATIVE_TIE_ORDER',
    actorKeys: ['E:e1', 'P:p1', 'P:p2'],
    candidateIds: [],
  })
  const currentState = {
    ...createEmptyCombatLocalSelectionState(),
    orderedActorKeys: ['P:p1', 'E:e1'],
    selectedPendingIds: ['candidate-1'],
  }

  assert.deepEqual(
    reconcileCombatLocalSelectionState(nextScreen, currentState, {
      reason: 'polling',
      previousScreen,
    }),
    {
      ...createEmptyCombatLocalSelectionState(),
      selectedPlayerId: 'p1',
      orderedActorKeys: ['E:e1', 'P:p1'],
    },
  )
})
