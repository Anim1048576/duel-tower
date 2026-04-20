import assert from 'node:assert/strict'
import {
  addOwnedCardIdToLoadoutDraft,
  buildPlayerLobbyCurrentDeckEntries,
  buildPlayerLobbyDeckPoolGroups,
  buildPlayerLobbyPreviewDraftSignature,
  isPlayerLobbyPreviewResponseCurrent,
  removeOwnedCardIdFromLoadoutDraft,
  shouldAcceptPlayerLobbyPreviewResponse,
} from '../src/lib/session/playerLobbyDeckEditor.js'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

const baseDraft = {
  characterId: 101,
  deckOwnedCardIds: ['oc-1', 'oc-2'],
  exCardId: 'EX901',
  passiveIds: ['P001'],
}

const ownedCardOptions = [
  {
    ownedCardId: 'oc-1',
    cardId: 'C001',
    label: 'Strike',
    subtitle: 'Attack',
    tags: [{ label: 'Attack', tone: 'accent' }],
  },
  {
    ownedCardId: 'oc-2',
    cardId: 'C002',
    label: 'Guard',
    subtitle: 'Block',
    tags: [{ label: 'Skill', tone: 'muted' }],
  },
  {
    ownedCardId: 'oc-3',
    cardId: 'C003',
    label: 'Burst',
    subtitle: 'Attack',
    tags: [{ label: 'Burst', tone: 'warning' }],
  },
]

const previewDraft = addOwnedCardIdToLoadoutDraft(baseDraft, 'oc-3')
const previewDraftSignature = buildPlayerLobbyPreviewDraftSignature(previewDraft)
const previewResponse = {
  draft: {
    characterId: 101,
    deckOwnedCardIds: ['oc-1', 'oc-2', 'oc-3'],
    exCardId: 'EX901',
    passiveIds: ['P001'],
  },
  draftSignature: previewDraftSignature,
  deckEditor: {
    deck: {
      requiredDeckSize: 12,
      draftDeckSize: 3,
      changedCardCount: 1,
      saveAllowed: false,
    },
    globalReasonCodes: ['INVALID_DECK_SIZE'],
    issues: [],
    draftEntries: [
      {
        ownedCardId: 'oc-1',
        cardId: 'C001',
        inSavedDeck: true,
        lockedInDeck: true,
        canRemove: false,
        reasonCodes: ['LOCKED_CARD'],
      },
      {
        ownedCardId: 'oc-2',
        cardId: 'C002',
        inSavedDeck: true,
        lockedInDeck: false,
        canRemove: true,
        reasonCodes: [],
      },
      {
        ownedCardId: 'oc-3',
        cardId: 'C003',
        inSavedDeck: false,
        lockedInDeck: false,
        canRemove: true,
        reasonCodes: [],
      },
    ],
    cardPoolGroups: [
      {
        cardId: 'C003',
        currentDeckCount: 1,
        totalOwnedCount: 1,
        availableOwnedCount: 0,
        canAdd: false,
        nextOwnedCardId: '',
        reasonCodes: ['ALREADY_IN_DECK'],
        ownedCards: [
          {
            ownedCardId: 'oc-3',
            cardId: 'C003',
            inDraftDeck: true,
            canAdd: false,
            reasonCodes: ['ALREADY_IN_DECK'],
          },
        ],
      },
    ],
  },
}

runTest('click-style add and remove update local draft ids', () => {
  const addedDraft = addOwnedCardIdToLoadoutDraft(baseDraft, 'oc-3')
  const removedDraft = removeOwnedCardIdFromLoadoutDraft(addedDraft, 'oc-2')

  assert.deepEqual(addedDraft.deckOwnedCardIds, ['oc-1', 'oc-2', 'oc-3'])
  assert.deepEqual(removedDraft.deckOwnedCardIds, ['oc-1', 'oc-3'])
})

runTest('preview response becomes current only when draft signature matches', () => {
  assert.equal(isPlayerLobbyPreviewResponseCurrent(previewResponse, previewDraftSignature), true)
  assert.equal(isPlayerLobbyPreviewResponseCurrent(previewResponse, 'characterId=101;passiveIds=P001;deckOwnedCardIds=oc-1;exCardId=EX901'), false)
})

runTest('stale preview responses are ignored by request id and signature', () => {
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 2,
      latestRequestId: 2,
      draftSignature: previewDraftSignature,
      response: previewResponse,
    }),
    true,
  )
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 1,
      latestRequestId: 2,
      draftSignature: previewDraftSignature,
      response: previewResponse,
    }),
    false,
  )
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 2,
      latestRequestId: 2,
      draftSignature: 'characterId=999',
      response: previewResponse,
    }),
    false,
  )
})

runTest('unresolved ownedCardId still produces stable current deck entries', () => {
  const entries = buildPlayerLobbyCurrentDeckEntries({
    draftDeckOwnedCardIds: ['oc-1', 'missing-owned-card'],
    screenDeckEditor: null,
    previewResponse: null,
    draftSignature: buildPlayerLobbyPreviewDraftSignature({
      ...baseDraft,
      deckOwnedCardIds: ['oc-1', 'missing-owned-card'],
    }),
    draftDirty: true,
    ownedCardOptions,
  })

  assert.equal(entries.length, 2)
  assert.equal(entries[1].title, 'missing-owned-card')
  assert.equal(entries[1].previewPending, true)
  assert.equal(entries[1].unresolved, true)
})

runTest('locked cards stay remove-blocked and card pool reflects server preview state', () => {
  const entries = buildPlayerLobbyCurrentDeckEntries({
    draftDeckOwnedCardIds: previewDraft.deckOwnedCardIds,
    screenDeckEditor: null,
    previewResponse,
    draftSignature: previewDraftSignature,
    draftDirty: true,
    ownedCardOptions,
  })
  const poolGroups = buildPlayerLobbyDeckPoolGroups({
    screenDeckEditor: null,
    previewResponse,
    draftSignature: previewDraftSignature,
    draftDirty: true,
    ownedCardOptions,
  })

  assert.equal(entries[0].ownedCardId, 'oc-1')
  assert.equal(entries[0].canRemove, false)
  assert.deepEqual(entries[0].reasonCodes, ['LOCKED_CARD'])
  assert.equal(poolGroups.length, 1)
  assert.equal(poolGroups[0].ownedCards[0].canAdd, false)
  assert.deepEqual(poolGroups[0].ownedCards[0].reasonCodes, ['ALREADY_IN_DECK'])
})
