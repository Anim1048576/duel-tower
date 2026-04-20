import assert from 'node:assert/strict'
import {
  addOwnedCardIdToLoadoutDraft,
  buildPlayerLobbyCurrentDeckEntries,
  buildPlayerLobbyDeckPoolGroups,
  isPlayerLobbyPreviewResponseCurrent,
  removeOwnedCardIdFromLoadoutDraft,
  resolvePlayerLobbyActiveDeckEditor,
  resolvePlayerLobbyPreviewState,
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
const currentDraft = {
  characterId: 101,
  deckOwnedCardIds: ['oc-1', 'oc-2', 'oc-3'],
  exCardId: 'EX901',
  passiveIds: ['P001'],
}

function isPreviewDraftCurrent(previewDraft, draft) {
  return JSON.stringify(previewDraft) === JSON.stringify({
    characterId: draft.characterId,
    deckOwnedCardIds: draft.deckOwnedCardIds,
    exCardId: draft.exCardId,
    passiveIds: draft.passiveIds,
  })
}

const previewResponse = {
  clientRequestId: 'preview-request-2',
  draft: {
    characterId: 101,
    deckOwnedCardIds: ['oc-1', 'oc-2', 'oc-3'],
    exCardId: 'EX901',
    passiveIds: ['P001'],
  },
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

runTest('preview response becomes current when echoed client request id or normalized draft matches', () => {
  assert.equal(
    isPlayerLobbyPreviewResponseCurrent(previewResponse, 'preview-request-2', isPreviewDraftCurrent, currentDraft),
    true,
  )
  assert.equal(
    isPlayerLobbyPreviewResponseCurrent(previewResponse, 'preview-request-9', isPreviewDraftCurrent, currentDraft),
    true,
  )
  assert.equal(
    isPlayerLobbyPreviewResponseCurrent(
      { ...previewResponse, draft: { ...previewResponse.draft, deckOwnedCardIds: ['oc-1'] } },
      'preview-request-9',
      isPreviewDraftCurrent,
      currentDraft,
    ),
    false,
  )
})

runTest('stale preview responses are ignored by request id and client request echo', () => {
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 2,
      latestRequestId: 2,
      clientRequestId: 'preview-request-2',
      response: previewResponse,
      isPreviewDraftCurrent,
      draft: currentDraft,
    }),
    true,
  )
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 1,
      latestRequestId: 2,
      clientRequestId: 'preview-request-2',
      response: previewResponse,
      isPreviewDraftCurrent,
      draft: currentDraft,
    }),
    false,
  )
  assert.equal(
    shouldAcceptPlayerLobbyPreviewResponse({
      requestId: 2,
      latestRequestId: 2,
      clientRequestId: 'preview-request-999',
      response: previewResponse,
      isPreviewDraftCurrent,
      draft: { ...currentDraft, deckOwnedCardIds: ['oc-1'] },
    }),
    false,
  )
})

runTest('unresolved ownedCardId still produces stable current deck entries', () => {
  const entries = buildPlayerLobbyCurrentDeckEntries({
    draftDeckOwnedCardIds: ['oc-1', 'missing-owned-card'],
    screenDeckEditor: null,
    matchingPreviewResponse: null,
    fallbackPreviewResponse: null,
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
    matchingPreviewResponse: previewResponse,
    fallbackPreviewResponse: null,
    draftDirty: true,
    ownedCardOptions,
  })
  const poolGroups = buildPlayerLobbyDeckPoolGroups({
    screenDeckEditor: null,
    matchingPreviewResponse: previewResponse,
    fallbackPreviewResponse: null,
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

runTest('last successful preview stays visible as stale fallback after a newer preview mismatch', () => {
  const staleState = resolvePlayerLobbyPreviewState({
    previewResponse: {
      ...previewResponse,
      clientRequestId: 'preview-request-3',
      draft: { ...previewResponse.draft, deckOwnedCardIds: ['oc-1'] },
    },
    fallbackPreviewResponse: previewResponse,
    isPreviewDraftCurrent,
    draft: currentDraft,
  })

  assert.equal(staleState.matchingPreviewResponse, null)
  assert.equal(staleState.hasStaleFallback, true)
  assert.equal(staleState.fallbackPreviewResponse?.clientRequestId, 'preview-request-2')
  assert.equal(
    resolvePlayerLobbyActiveDeckEditor({
      screenDeckEditor: null,
      matchingPreviewResponse: staleState.matchingPreviewResponse,
      fallbackPreviewResponse: staleState.fallbackPreviewResponse,
      draftDirty: true,
    })?.deck.changedCardCount,
    1,
  )
})
