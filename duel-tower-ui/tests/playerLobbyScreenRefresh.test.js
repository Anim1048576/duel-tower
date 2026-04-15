import assert from 'node:assert/strict'
import { resolvePlayerLobbyScreenRefreshPlan } from '../src/lib/session/playerLobbyScreenRefresh.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

runTest('initial and route-driven refreshes resync the local draft with loading UI', () => {
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('initial-load'), {
    showLoading: true,
    forceDraftSync: true,
  })
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('route-change'), {
    showLoading: true,
    forceDraftSync: true,
  })
})

runTest('polling and ready toggle refresh preserve the local draft', () => {
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('polling'), {
    showLoading: false,
    forceDraftSync: false,
  })
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('action-toggle-ready'), {
    showLoading: false,
    forceDraftSync: false,
  })
})

runTest('save and apply preset refreshes resync the local draft without full-page loading', () => {
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('action-save-loadout'), {
    showLoading: false,
    forceDraftSync: true,
  })
  assert.deepEqual(resolvePlayerLobbyScreenRefreshPlan('action-apply-preset'), {
    showLoading: false,
    forceDraftSync: true,
  })
})
