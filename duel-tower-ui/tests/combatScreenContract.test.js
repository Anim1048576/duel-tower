import assert from 'node:assert/strict'

function runTest(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

function createCombatScreenSample() {
  return {
    screenKey: 'Combat',
    generatedAt: '2026-04-18T19:10:30+09:00',
    uiNotices: ['combat contract sample'],
    sessionCode: 'ABCD1234',
    version: 27,
    changed: true,
    status: {
      round: 2,
      phase: 'PLAYER',
      currentActor: {
        raw: 'P:p1',
        kind: 'player',
        id: 'p1',
        label: 'p1',
        note: 'p1 is the current acting player.',
        tone: 'success',
      },
      turnOrderSummary: 'p1 -> e1',
      battlefieldSummary: '1 players | 1 enemies',
      runSummary: 'Current node: test encounter',
      tieGroupSummary: null,
    },
    access: {
      role: 'player',
      runtimePlayerId: 'p1',
      expectedVersion: 27,
      guards: {
        canIssuePlayerCommand: true,
        canResolvePendingCommand: false,
        canClearRecentResultsCommand: true,
        canIssueGmCommand: false,
        exAvailable: true,
        hasPendingDecision: false,
        isCurrentTurnPlayer: true,
        hasCombatState: true,
      },
    },
    actors: {
      players: [
        {
          playerId: 'p1',
          ready: true,
          stateLabel: 'Ready',
          stateTone: 'success',
          metrics: [{ label: 'Hand', value: 3, note: 'Limit 5' }],
          summaryLines: ['EX ready'],
          statusTags: [{ label: 'EX ready', tone: 'warning' }],
          passives: ['P001'],
          handCards: [
            {
              instanceId: 'card-1',
              defId: 'C001',
              title: 'Strike',
              subtitle: 'Attack',
              unresolved: false,
              tags: [{ label: 'Attack', tone: 'accent' }],
              meta: 'Instance card-1',
            },
          ],
          fieldCards: [],
          graveCards: [],
          excludedCards: [],
          exCard: {
            instanceId: 'ex-1',
            defId: 'EX901',
            title: 'Meteor',
            subtitle: 'EX',
            unresolved: false,
            tags: [{ label: 'EX', tone: 'warning' }],
            meta: 'Instance ex-1',
          },
        },
      ],
      enemies: [],
      summons: [],
    },
    zones: {
      visiblePlayerId: 'p1',
      hand: [
        {
          instanceId: 'card-1',
          defId: 'C001',
          title: 'Strike',
          subtitle: 'Attack',
          unresolved: false,
          tags: [{ label: 'Attack', tone: 'accent' }],
          meta: 'Instance card-1',
        },
      ],
      field: [],
      grave: [],
      excluded: [],
      ex: {
        instanceId: 'ex-1',
        defId: 'EX901',
        title: 'Meteor',
        subtitle: 'EX',
        unresolved: false,
        tags: [{ label: 'EX', tone: 'warning' }],
        meta: 'Instance ex-1',
      },
    },
    sidebar: {
      events: [{ title: 'CARD_PLAYED', lines: ['p1 used Strike'] }],
      logs: [{ title: 'INFO', lines: ['version 27'] }],
      recentResults: [{ title: 'Encounter clear', summary: 'Victory', meta: 'Combat | just now' }],
    },
    possibleActions: [
      {
        id: 'combat.playCard',
        label: 'Play selected card',
        method: 'POST',
        href: '/api/screens/sessions/ABCD1234/combat/actions/combat.playCard',
        auth: 'playerToken',
        enabled: true,
        disabledReason: null,
        payloadTemplate: {
          type: 'PLAY_CARD',
          expectedVersion: 27,
          playerId: 'p1',
          cardId: '',
          discardIds: [],
          selectedIds: [],
          targets: [],
        },
        metadata: {
          kind: 'playCard',
          note: 'Server-calculated command support and requirement views for each playable hand card.',
          localSelection: {
            requiresSelectedCard: true,
            sourceType: 'handCard',
          },
          sourceOptions: [
            {
              instanceId: 'card-1',
              title: 'Strike',
              sourceCard: {
                instanceId: 'card-1',
                defId: 'C001',
                title: 'Strike',
                subtitle: 'Attack',
                unresolved: false,
                tags: [{ label: 'Attack', tone: 'accent' }],
                meta: 'Instance card-1',
              },
              requirementView: {
                sourceLabel: 'Strike',
                targetSummary: 'Select exactly one enemy or summon target',
                discardSummary: 'No extra hand discard required',
                selectedIdsSummary: 'No extra field selection required',
                choiceSummary: 'No explicit choice requirement',
                targetRule: {
                  target: 'ENEMY_ONE',
                  requiredSelection: true,
                },
                discardRequirement: null,
                selectedIdsRequirement: null,
                pendingChoiceSchema: null,
                unsupportedReason: null,
              },
              supported: true,
              unsupportedReason: null,
            },
          ],
        },
      },
    ],
  }
}

runTest('combat screen sample exposes server-owned render slices for status/access/actors/zones/sidebar', () => {
  const screen = createCombatScreenSample()

  assert.equal(screen.screenKey, 'Combat')
  assert.equal(screen.access.role, 'player')
  assert.equal(screen.actors.players[0].handCards[0].title, 'Strike')
  assert.equal(screen.zones.visiblePlayerId, 'p1')
  assert.equal(screen.sidebar.recentResults[0].summary, 'Victory')
})

runTest('combat action sample exposes requirement metadata and latest-screen-friendly payload templates', () => {
  const action = createCombatScreenSample().possibleActions[0]

  assert.equal(action.id, 'combat.playCard')
  assert.equal(action.enabled, true)
  assert.equal(action.payloadTemplate.type, 'PLAY_CARD')
  assert.equal(action.metadata.kind, 'playCard')
  assert.equal(action.metadata.localSelection.requiresSelectedCard, true)
  assert.equal(action.metadata.sourceOptions[0].requirementView.targetRule.requiredSelection, true)
})
