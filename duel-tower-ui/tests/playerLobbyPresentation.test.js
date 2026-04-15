import assert from 'node:assert/strict'
import { createPlayerLobbyLocalPresentation } from '../src/lib/session/playerLobbyPresentation.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

const baseScreen = {
  sessionCode: 'ABCD',
  version: 3,
  screenKey: 'PlayerLobby',
  generatedAt: '2026-04-16T10:00:00Z',
  uiNotices: [],
  routeTemplate: '/sessions/{code}/player-lobby',
  policyGroup: 'player-lobby',
  auth: 'sessionReadable',
  participantSlots: [],
  me: {
    playerId: 'p-1',
    ready: false,
    loadout: {
      characterId: 101,
      characterLabel: 'Alice #101',
      deckOwnedCardIds: ['oc-1', 'oc-2'],
      exCardId: 'ex-1',
      exLabel: 'Meteor EX',
      passiveIds: ['ps-1'],
      deckCount: 2,
      passiveCount: 1,
    },
    summary: {
      readyLabel: 'Joined',
      readyTone: 'muted',
      loadoutSummary: 'Deck 2 cards | 1 passives | EX ex-1',
      draftSummary: 'Deck 2 cards | 1 passives | EX ex-1',
      membershipSummary: '1 player joined',
    },
    draft: {
      characterId: 101,
      characterLabel: 'Alice #101',
      deckOwnedCardIds: ['oc-1', 'oc-2'],
      exCardId: 'ex-1',
      exLabel: 'Meteor EX',
      passiveIds: ['ps-1'],
      deckCount: 2,
      passiveCount: 1,
    },
    draftFlags: {
      dirty: false,
      deckEditingLocked: false,
      requiredFieldsMissing: false,
    },
  },
  references: {
    characterOptions: [
      { id: '101', label: 'Alice #101', subtitle: 'Starter', tags: [{ label: 'Starter', tone: 'accent' }] },
      { id: '202', label: 'Basil #202', subtitle: 'Alt', tags: [{ label: 'Alt', tone: 'muted' }] },
    ],
    exCardOptions: [
      { id: 'ex-1', label: 'Meteor EX', subtitle: 'Burst', tags: [{ label: 'Burst', tone: 'warning' }] },
      { id: 'ex-2', label: 'Shield EX', subtitle: 'Guard', tags: [{ label: 'Guard', tone: 'success' }] },
    ],
    passiveOptions: [
      { id: 'ps-1', label: 'Quick Step', subtitle: 'Passive', tags: [{ label: 'Passive', tone: 'success' }] },
      { id: 'ps-2', label: 'Heavy Step', subtitle: 'Passive', tags: [{ label: 'Passive', tone: 'muted' }] },
    ],
    ownedCardOptions: [
      { ownedCardId: 'oc-1', cardId: 'c-1', label: 'Strike', subtitle: 'Attack', tags: [{ label: 'Attack', tone: 'accent' }] },
      { ownedCardId: 'oc-2', cardId: 'c-2', label: 'Guard', subtitle: 'Skill', tags: [{ label: 'Skill', tone: 'muted' }] },
      { ownedCardId: 'oc-3', cardId: 'c-3', label: 'Burst', subtitle: 'Attack', tags: [{ label: 'Attack', tone: 'warning' }] },
    ],
  },
  presets: {
    items: [
      { presetId: 11, label: 'Starter Preset', subtitle: 'Alice loadout' },
      { presetId: 12, label: 'Alt Preset', subtitle: 'Basil loadout' },
    ],
    selectedId: 11,
    preview: {
      name: 'Starter Preset',
      summary: 'Deck 2 cards | 1 passives | EX Meteor EX',
      characterLabel: 'Alice #101',
      exLabel: 'Meteor EX',
      deckItems: [{ id: 'c-1', label: 'Strike', subtitle: 'Attack', tags: [{ label: 'Attack', tone: 'accent' }] }],
      passiveItems: [{ id: 'ps-1', label: 'Quick Step', subtitle: 'Passive', tags: [{ label: 'Passive', tone: 'success' }] }],
    },
  },
  possibleActions: [],
}

runTest('local dirty reacts immediately to loadout draft changes', () => {
  const presentation = createPlayerLobbyLocalPresentation(
    baseScreen,
    {
      characterId: 101,
      deckOwnedCardIds: ['oc-1', 'oc-3'],
      exCardId: 'ex-1',
      passiveIds: ['ps-1'],
    },
    '11',
  )

  assert.equal(presentation.dirty, true)
  assert.equal(presentation.summary, 'Deck 2 cards | 1 passives | EX ex-1')
  assert.equal(presentation.deckItems[1].title, 'Burst')
  assert.equal(presentation.previewNeedsResolveRefresh, true)
})

runTest('local preview falls back cleanly for unresolved ids', () => {
  const presentation = createPlayerLobbyLocalPresentation(
    baseScreen,
    {
      characterId: 202,
      deckOwnedCardIds: ['oc-9'],
      exCardId: 'ex-9',
      passiveIds: ['ps-2'],
    },
    '11',
  )

  assert.equal(presentation.character.label, 'Basil #202')
  assert.equal(presentation.ex.label, 'EX card (unresolved)')
  assert.equal(
    presentation.deckEditingLockReason,
    'Save the new character first to refresh owned card options and unlock deck editing.',
  )
  assert.equal(presentation.deckItems[0].title, 'oc-9')
  assert.equal(presentation.deckItems[0].note, 'This owned card id is not available in the latest server reference options.')
})

runTest('preset preview only uses the latest server-selected preset snapshot', () => {
  const syncedPresentation = createPlayerLobbyLocalPresentation(baseScreen, baseScreen.me.draft, '11')
  const unsyncedPresentation = createPlayerLobbyLocalPresentation(baseScreen, baseScreen.me.draft, '12')

  assert.equal(syncedPresentation.preset.previewSynced, true)
  assert.equal(syncedPresentation.preset.previewStale, false)
  assert.equal(syncedPresentation.preset.characterLabel, 'Alice #101')
  assert.equal(unsyncedPresentation.preset.previewSynced, false)
  assert.equal(unsyncedPresentation.preset.previewStale, true)
  assert.equal(
    unsyncedPresentation.preset.summary,
    'Resolved preview is available only for the latest server-selected preset snapshot.',
  )
})
