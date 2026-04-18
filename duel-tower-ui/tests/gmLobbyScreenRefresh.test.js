import assert from 'node:assert/strict'
import {
  resolveGmLobbyScreenRefreshPlan,
  resolveGmLobbySelections,
} from '../src/lib/session/gmLobbyScreenRefresh.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

const sampleScreen = {
  participantCards: [
    { name: 'p1' },
    { name: 'p2' },
  ],
  startCombat: {
    recommendedStartPlayerId: 'p1',
    selectableStartPlayers: [
      { playerId: 'p1' },
      { playerId: 'p2' },
    ],
  },
}

runTest('route-driven GmLobby refresh shows loading and resets start-player selection to server recommendation', () => {
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('initial-load'), {
    showLoading: true,
    preserveStartPlayerSelection: false,
  })
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('route-change'), {
    showLoading: true,
    preserveStartPlayerSelection: false,
  })
})

runTest('polling and non-start actions keep lightweight local selection state when still valid', () => {
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('polling'), {
    showLoading: false,
    preserveStartPlayerSelection: true,
  })
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('action-kick'), {
    showLoading: false,
    preserveStartPlayerSelection: true,
  })
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('action-reset'), {
    showLoading: false,
    preserveStartPlayerSelection: true,
  })

  assert.deepEqual(
    resolveGmLobbySelections(sampleScreen, {
      selectedKickPlayerId: 'p2',
      selectedStartPlayerId: 'p2',
      preserveStartPlayerSelection: true,
    }),
    {
      selectedKickPlayerId: 'p2',
      selectedStartPlayerId: 'p2',
    },
  )
})

runTest('invalid local selections fall back to current server data', () => {
  assert.deepEqual(
    resolveGmLobbySelections(sampleScreen, {
      selectedKickPlayerId: 'missing',
      selectedStartPlayerId: 'missing',
      preserveStartPlayerSelection: true,
    }),
    {
      selectedKickPlayerId: 'p1',
      selectedStartPlayerId: 'p1',
    },
  )
})

runTest('start-combat success refresh resets start-player selection back to the latest server recommendation', () => {
  assert.deepEqual(resolveGmLobbyScreenRefreshPlan('action-start-combat-success'), {
    showLoading: false,
    preserveStartPlayerSelection: false,
  })

  assert.deepEqual(
    resolveGmLobbySelections(sampleScreen, {
      selectedKickPlayerId: 'p1',
      selectedStartPlayerId: 'p2',
      preserveStartPlayerSelection: false,
    }),
    {
      selectedKickPlayerId: 'p1',
      selectedStartPlayerId: 'p1',
    },
  )
})
