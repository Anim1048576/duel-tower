import assert from 'node:assert/strict'
import { resolveGmLobbyStartCombatFollowUp } from '../src/lib/session/gmLobbyStartCombatAction.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

runTest('successful start-combat prefers navigation when nextRoute is provided', () => {
  const followUp = resolveGmLobbyStartCombatFollowUp({
    success: true,
    outcome: 'STARTED',
    message: 'Combat started.',
    disabledReason: null,
    nextRoute: '/sessions/ABCD1234/combat',
    latestScreen: null,
  })

  assert.deepEqual(followUp, {
    shouldNavigate: true,
    nextRoute: '/sessions/ABCD1234/combat',
    shouldApplyLatestScreen: false,
    preserveStartPlayerSelection: false,
    refreshReason: 'action-start-combat-success',
    successMessage: 'Combat started.',
    errorTitle: null,
    errorMessage: null,
  })
})

runTest('blocked start-combat keeps the local selection and uses the returned latest screen when present', () => {
  const followUp = resolveGmLobbyStartCombatFollowUp({
    success: false,
    outcome: 'BLOCKED',
    message: null,
    disabledReason: { userMessage: 'Mark at least one participant ready first.' },
    nextRoute: null,
    latestScreen: { screenKey: 'GmLobby' },
  })

  assert.deepEqual(followUp, {
    shouldNavigate: false,
    nextRoute: null,
    shouldApplyLatestScreen: true,
    preserveStartPlayerSelection: true,
    refreshReason: null,
    successMessage: null,
    errorTitle: 'Combat start unavailable',
    errorMessage: 'Mark at least one participant ready first.',
  })
})

runTest('GM access failure falls back to refresh when latest screen is absent', () => {
  const followUp = resolveGmLobbyStartCombatFollowUp({
    success: false,
    outcome: 'GM_ACCESS_REQUIRED',
    message: 'GM access could not be restored.',
    disabledReason: null,
    nextRoute: null,
    latestScreen: null,
  })

  assert.deepEqual(followUp, {
    shouldNavigate: false,
    nextRoute: null,
    shouldApplyLatestScreen: false,
    preserveStartPlayerSelection: true,
    refreshReason: 'action-start-combat-failed',
    successMessage: null,
    errorTitle: 'GM access restore failed',
    errorMessage: 'GM access could not be restored.',
  })
})
