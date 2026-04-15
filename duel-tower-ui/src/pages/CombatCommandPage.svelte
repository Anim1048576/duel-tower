<script lang="ts">
  import { onMount } from 'svelte'
  import {
    buildCommandRequirementViewModel,
    getPlayCardRequirementError,
  } from '../features/session/combat/commandRequirements'
  import {
    formatTargetRefSummary,
    formatTargetSelectionLabel,
    getUnsupportedCardCommandMessage,
    getUnsupportedPendingDecisionMessage,
  } from '../features/session/combat/commandMessages'
  import {
    getOrderedTieActorKeys,
    getPendingCandidateIds,
    getSelectedDiscardIdsFromHand,
    getSelectedFieldIds,
  } from '../features/session/combat/selectionFilters'
  import {
    normalizePlaySpec,
  } from '../features/session/combat/playSpec'
  import { getCard, listCards } from '../lib/api/content'
  import type { CardDefinition, CardDetailResponse } from '../lib/api/contentTypes'
  import {
    executeSessionCommand,
    getSessionEvents,
    getSessionLogs,
    getSessionRecentResults,
    getSessionState,
  } from '../lib/api/sessions'
  import type {
    CombatEnemyDto,
    CombatSummonDto,
    CommandRequest,
    RecentResultsResponse,
    SessionEventItemDto,
    SessionLogItemDto,
    PlayerStateDto,
    SessionStateDto,
  } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import BattlefieldPanel from '../lib/components/combat/BattlefieldPanel.svelte'
  import CombatHeader from '../lib/components/combat/CombatHeader.svelte'
  import CombatLayout from '../lib/components/combat/CombatLayout.svelte'
  import CombatSidebar from '../lib/components/combat/CombatSidebar.svelte'
  import HandBar from '../lib/components/combat/HandBar.svelte'
  import type {
    CombatActorSummary,
    CombatEnemyViewModel,
    CombatFeedEntry,
    CombatPlayerViewModel,
    CombatRecentResultEntry,
    CombatStatusViewModel,
    CombatSummonViewModel,
    CombatTag,
    CombatTone,
    CombatMetric,
    CommandOptionViewModel,
    ResolvedCombatCardViewModel,
  } from '../lib/components/combat/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders } from '../lib/navigation'
  import {
    hasStoredSessionCode,
    isStoredGmSessionAccess,
    isStoredPlayerSessionAccess,
    readStoredSessionAccess,
    toSessionReadAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    buildCombatCommandGuards,
    createEmptyCombatCommandDraft,
    syncCombatCommandDraft,
    toggleCombatIdentifier,
    type CombatCommandDraft,
    type CombatCommandType,
  } from '../lib/session/combatCommandDraft'
  import {
    createLiveSessionPage,
  } from '../lib/session/liveSessionPage'
  import { sessionPageStateCopy } from '../lib/session/pageState'
  import { readRequestedSessionCodeFromAccessOrHandoff, readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'

  const combatSidebarEventLimit = 12

  let loading = $state(true)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let invalidAccessMessage = $state<string | null>(null)
  let accessNoticeMessage = $state<string | null>(null)
  let catalogLoading = $state(true)
  let catalogErrorMessage = $state<string | null>(null)
  let session = $state<SessionStateDto | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let cardCatalog = $state<CardDefinition[]>([])
  let cardDetails = $state<Record<string, CardDetailResponse>>({})
  let cardDetailLoadingIds = $state<string[]>([])
  let cardDetailErrors = $state<Record<string, string>>({})
  let commandDraft = $state<CombatCommandDraft>(createEmptyCombatCommandDraft())
  let commandPending = $state<CombatCommandType | null>(null)
  let commandErrorMessage = $state<string | null>(null)
  let commandRejectedMessage = $state<string | null>(null)
  let commandSuccessMessage = $state<string | null>(null)
  let recentCommandEvents = $state<SessionEventItemDto[]>([])
  let eventsLoading = $state(true)
  let eventsErrorMessage = $state<string | null>(null)
  let eventItems = $state<SessionEventItemDto[]>([])
  let eventsRequestSequence = 0
  let logsLoading = $state(true)
  let logsErrorMessage = $state<string | null>(null)
  let logItems = $state<SessionLogItemDto[]>([])
  let logsRequestSequence = 0
  let recentResultsLoading = $state(true)
  let recentResultsErrorMessage = $state<string | null>(null)
  let recentResults = $state<RecentResultsResponse | null>(null)
  let recentResultsRequestSequence = 0
  let sidebarSessionCode = $state<string | null>(null)
  let hadSidebarReadAccess = $state(false)

  function getInvalidCombatAccessMessage(nextCode: string | null) {
    if (!nextCode) {
      return 'No session code is available in the combat route or session handoff yet.'
    }

    return null
  }

  function getAccessNotice(nextCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextCode || !nextAccess) {
      return 'Session runtime access is unavailable. Combat state is restored in read-only mode by session code only.'
    }

    if (!hasStoredSessionCode(nextAccess, nextCode)) {
      return 'Stored session access does not match the requested combat code. The page is restored in code-first read-only mode.'
    }

    if (isStoredPlayerSessionAccess(nextAccess)) {
      return `Player access restored for ${nextAccess.playerId}. The current hand and player-side zones now follow that player when available.`
    }

    if (isStoredGmSessionAccess(nextAccess)) {
      return 'GM access restored for this combat code. The shell now resolves the live combat state and is ready for command wiring in the next step.'
    }

    return 'Session access is present but incomplete. Combat state is restored in read-only mode.'
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') {
      return
    }

    window.history[replace ? 'replaceState' : 'pushState']({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function syncCombatState(nextSession: SessionStateDto) {
    session = nextSession
    commandDraft = syncCombatCommandDraft(commandDraft, nextSession, runtimeAccess)
    syncSessionSelectionHandoff(nextSession.sessionCode)

    if (!routeSessionCode && nextSession.sessionCode) {
      navigateTo(pathBuilders.combat(nextSession.sessionCode), true)
    }
  }

  function hasCombatReadAccess(nextCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextCode || !nextAccess) {
      return false
    }

    if (!hasStoredSessionCode(nextAccess, nextCode)) {
      return false
    }

    if (isStoredPlayerSessionAccess(nextAccess)) {
      return Boolean(nextAccess.playerToken && nextAccess.playerId)
    }

    if (isStoredGmSessionAccess(nextAccess)) {
      return Boolean(nextAccess.gmToken)
    }

    return false
  }

  function invalidateCombatSidebarRequests() {
    eventsRequestSequence += 1
    logsRequestSequence += 1
    recentResultsRequestSequence += 1
  }

  function resetCombatSidebarState() {
    recentCommandEvents = []
    eventItems = []
    logItems = []
    recentResults = null
    eventsLoading = false
    logsLoading = false
    recentResultsLoading = false
    eventsErrorMessage = null
    logsErrorMessage = null
    recentResultsErrorMessage = null
  }

  const combatPage = createLiveSessionPage<StoredSessionAccess | null>({
    readCode: () =>
      readRequestedSessionCodeFromAccessOrHandoff({
        pageKey: 'combat',
        storedAccess: readStoredSessionAccess(),
        preferStoredAccess: false,
      }).code,
    readAccess: () => readStoredSessionAccess(),
    getInvalidMessage: getInvalidCombatAccessMessage,
    loadState: getSessionState,
    getPollingAccess: toSessionReadAccess,
    canPoll: ({ code, access, state }) => state.sessionCode === code && hasCombatReadAccess(code, access),
    onBeforeLoad: ({ code, access, invalidMessage }) => {
      const nextHasSidebarReadAccess = hasCombatReadAccess(code, access)

      if (sidebarSessionCode !== code) {
        invalidateCombatSidebarRequests()
        resetCombatSidebarState()
        sidebarSessionCode = code
      } else if (hadSidebarReadAccess && !nextHasSidebarReadAccess) {
        invalidateCombatSidebarRequests()
        resetCombatSidebarState()
      }

      hadSidebarReadAccess = nextHasSidebarReadAccess

      runtimeAccess = access
      invalidAccessMessage = invalidMessage
      accessNoticeMessage = getAccessNotice(code, access)
      loading = true
      notFound = false
      errorMessage = null
      session = null
    },
    onLoaded: (response) => {
      syncCombatState(response)
    },
    onPolled: (nextSession, { code, access }) => {
      runtimeAccess = access
      invalidAccessMessage = getInvalidCombatAccessMessage(code)
      accessNoticeMessage = getAccessNotice(code, access)
      hadSidebarReadAccess = true
      syncCombatState(nextSession)
      void loadCombatSidebarData()
    },
    onNotFound: () => {
      notFound = true
    },
    onError: (error) => {
      errorMessage = getApiErrorMessage(error, 'Unable to restore the current combat session shell.')
    },
    onLoadSettled: () => {
      loading = false
    },
  })

  function stopCombatPolling() {
    combatPage.stopPolling()
  }

  function updateCombatPollingVersion(nextSession: SessionStateDto) {
    combatPage.updatePollingVersion(nextSession.version)
  }

  function startCombatPolling(
    nextCode: string | null,
    nextAccess: StoredSessionAccess | null,
    nextSession: SessionStateDto,
  ) {
    combatPage.startPolling(nextSession, {
      code: nextCode,
      access: nextAccess,
    })
  }

  async function loadCombatState() {
    await combatPage.load()
  }

  async function loadCardCatalog() {
    catalogLoading = true
    catalogErrorMessage = null

    try {
      cardCatalog = await listCards()
    } catch (error) {
      cardCatalog = []
      catalogErrorMessage = getApiErrorMessage(
        error,
        'Unable to load the card archive for combat card resolution.',
      )
    } finally {
      catalogLoading = false
    }
  }

  function getCardDefinition(defId: string | null) {
    if (!defId) {
      return null
    }

    return cardCatalog.find((card) => card.id === defId) ?? null
  }

  function getCardDetail(defId: string | null) {
    return defId ? cardDetails[defId] ?? null : null
  }

  function deleteRecordEntry<T>(record: Record<string, T>, key: string) {
    const nextRecord = { ...record }
    delete nextRecord[key]
    return nextRecord
  }

  async function ensureCardDetail(defId: string | null) {
    const normalizedDefId = defId?.trim() ?? ''

    if (!normalizedDefId) {
      return null
    }

    const cached = cardDetails[normalizedDefId] ?? null

    if (cached) {
      return cached
    }

    if (cardDetailLoadingIds.includes(normalizedDefId)) {
      return null
    }

    cardDetailLoadingIds = [...cardDetailLoadingIds, normalizedDefId]
    cardDetailErrors = deleteRecordEntry(cardDetailErrors, normalizedDefId)

    try {
      const detail = await getCard(normalizedDefId)
      cardDetails = {
        ...cardDetails,
        [normalizedDefId]: detail,
      }
      return detail
    } catch (error) {
      cardDetailErrors = {
        ...cardDetailErrors,
        [normalizedDefId]: getApiErrorMessage(error, `Unable to load card detail for ${normalizedDefId}.`),
      }
      return null
    } finally {
      cardDetailLoadingIds = cardDetailLoadingIds.filter((id) => id !== normalizedDefId)
    }
  }

  function getCardDefIdFromInstanceId(instanceId: string | null | undefined) {
    const normalizedId = instanceId?.trim() ?? ''

    if (!normalizedId) {
      return null
    }

    return session?.cards[normalizedId]?.defId ?? null
  }

  function handleClearSelectedTargets() {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedEnemyId: null,
        selectedTargets: [],
      },
      session,
      runtimeAccess,
    )
  }

  function handleClearSelectionInputs() {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedDiscardIds: [],
        selectedIds: [],
        orderedActorKeys: [],
      },
      session,
      runtimeAccess,
    )
  }

  function createUnresolvedCardView(instanceId: string, defId: string | null) {
    return {
      instanceId,
      defId,
      title: defId ?? instanceId,
      subtitle: defId ? 'Unresolved card definition' : 'Unresolved card instance',
      meta: `Instance ${instanceId}`,
      description: defId
        ? 'The card instance exists, but its definition is not present in the current card archive.'
        : 'The requested card instance is not present in the current session card map.',
      unresolved: true,
      tags: [{ label: 'Unresolved', tone: 'warning' as const }],
    } satisfies ResolvedCombatCardViewModel
  }

  function resolveCombatCard(instanceId: string) {
    const instance = session?.cards[instanceId] ?? null

    if (!instance) {
      return createUnresolvedCardView(instanceId, null)
    }

    const definition = getCardDefinition(instance.defId)

    if (!definition) {
      return createUnresolvedCardView(instanceId, instance.defId)
    }

    return {
      instanceId,
      defId: instance.defId,
      title: definition.name,
      subtitle: getCardTypeLabel(definition.type),
      meta: `${buildCardArchiveMeta(definition)} | Instance ${instanceId}`,
      description: definition.description,
      unresolved: false,
      tags: buildCardDisplayTags(definition),
    } satisfies ResolvedCombatCardViewModel
  }

  function parseCombatActor(rawActor: string | null | undefined): CombatActorSummary {
    const normalized = rawActor?.trim() ?? ''

    if (!normalized) {
      return {
        raw: null,
        kind: 'none',
        id: null,
        label: 'No active turn',
        note: 'Combat turn owner is not available in the current state.',
        tone: 'muted',
      }
    }

    if (normalized.startsWith('P:')) {
      const playerId = normalized.slice(2).trim()

      return {
        raw: normalized,
        kind: 'player',
        id: playerId || null,
        label: playerId || normalized,
        note: playerId
          ? `${playerId} is the current acting player.`
          : 'A player turn is active, but the actor id is incomplete.',
        tone: 'success',
      }
    }

    if (normalized.startsWith('E:')) {
      const enemyId = normalized.slice(2).trim()

      return {
        raw: normalized,
        kind: 'enemy',
        id: enemyId || null,
        label: enemyId || normalized,
        note: enemyId
          ? `${enemyId} is the current acting enemy.`
          : 'An enemy turn is active, but the actor id is incomplete.',
        tone: 'warning',
      }
    }

    return {
      raw: normalized,
      kind: 'unknown',
      id: normalized,
      label: normalized,
      note: 'Current actor format is not recognized, so the raw value is shown.',
      tone: 'accent',
    }
  }

  function buildTurnOrderSummary(combat: SessionStateDto['combat']) {
    if (!combat?.turnOrder.length) {
      return 'Turn order is not available yet.'
    }

    const preview = combat.turnOrder.slice(0, 6).map((actorKey) => parseCombatActor(actorKey).label)
    const hiddenCount = combat.turnOrder.length - preview.length
    const summary = preview.join(' -> ')

    return hiddenCount > 0 ? `${summary} +${hiddenCount} more` : summary
  }

  function getPlayerStateLabel(player: PlayerStateDto) {
    if (player.pendingDecision?.type) {
      return player.pendingDecision.type
    }

    return player.ready ? 'Ready' : 'Joined'
  }

  function getPlayerStateTone(player: PlayerStateDto) {
    if (player.pendingDecision) {
      return 'warning' as const
    }

    return player.ready ? 'success' : 'accent'
  }

  function buildPlayerViewModel(player: PlayerStateDto) {
    const pendingLabel = player.pendingDecision?.type ?? 'None'
    const exLabel = player.exCard ?? 'None'

    return {
      playerId: player.playerId,
      ready: player.ready,
      stateLabel: getPlayerStateLabel(player),
      stateTone: getPlayerStateTone(player),
      metrics: [
        {
          label: 'Hand',
          value: player.hand.length,
          note: `Limit ${player.handLimit}`,
        },
        {
          label: 'Field',
          value: player.field.length,
          note: `Limit ${player.fieldLimit}`,
        },
        {
          label: 'Deck',
          value: player.deck.length,
          note: 'Cards remaining',
        },
        {
          label: 'Owned',
          value: `${player.ownedCardCount}/${player.maxOwnedCardCount}`,
          note: 'Owned pool',
        },
      ],
      summaryLines: [
        `EX ${exLabel} | Cooldown ${player.exOnCooldown ? 'Yes' : 'No'} | Passives ${player.passiveIds.length}`,
        `Pending ${pendingLabel} | Ready ${player.ready ? 'Yes' : 'No'} | Cards played ${player.cardsPlayedThisTurn}`,
        `Grave ${player.grave.length} | Excluded ${player.excluded.length} | Forgetting required ${player.forgettingRequired ? 'Yes' : 'No'}`,
      ],
      statusTags: [
        {
          label: player.exCard ? (player.exOnCooldown ? 'EX cooldown' : 'EX ready') : 'No EX',
          tone: player.exCard ? (player.exOnCooldown ? 'muted' : 'warning') : 'muted',
        },
        {
          label: player.pendingDecision?.type ?? 'No pending decision',
          tone: player.pendingDecision ? 'warning' : 'muted',
        },
        {
          label: `${player.grave.length} grave`,
          tone: player.grave.length > 0 ? 'accent' : 'muted',
        },
        {
          label: `${player.excluded.length} excluded`,
          tone: player.excluded.length > 0 ? 'accent' : 'muted',
        },
      ],
      passives: player.passiveIds,
      handCards: player.hand.map((instanceId) => resolveCombatCard(instanceId)),
      fieldCards: player.field.map((instanceId) => resolveCombatCard(instanceId)),
      graveCards: player.grave.map((instanceId) => resolveCombatCard(instanceId)),
      excludedCards: player.excluded.map((instanceId) => resolveCombatCard(instanceId)),
    } satisfies CombatPlayerViewModel
  }

  function buildEnemyViewModel(enemy: CombatEnemyDto) {
    const statusEntries = Object.entries(enemy.statuses).map(
      ([statusId, amount]) => `${statusId}: ${amount}`,
    )

    return {
      enemyId: enemy.enemyId,
      stateLabel: enemy.exActivatable ? 'EX ready' : enemy.exOnCooldown ? 'Cooldown' : 'Active',
      stateTone: enemy.exActivatable ? 'warning' : enemy.exOnCooldown ? 'muted' : 'accent',
      metrics: [
        {
          label: 'HP',
          value: `${enemy.hp}/${enemy.maxHp}`,
          note: 'Current / max',
        },
        {
          label: 'AP',
          value: enemy.ap,
          note: 'Current AP',
        },
        {
          label: 'ATK',
          value: enemy.attackPower,
          note: 'Attack power',
        },
        {
          label: 'HEAL',
          value: enemy.healPower,
          note: 'Heal power',
        },
      ],
      summaryLines: [
        `EX ${enemy.exCardId ?? 'None'} | EX ready ${enemy.exActivatable ? 'Yes' : 'No'} | Cooldown ${enemy.exOnCooldown ? 'Yes' : 'No'}`,
        `Statuses ${statusEntries.length > 0 ? statusEntries.length : 'None'} | Enemy id ${enemy.enemyId}`,
      ],
      statusEntries,
    } satisfies CombatEnemyViewModel
  }

  function buildSummonViewModel(summon: CombatSummonDto) {
    return {
      summonId: summon.summonId,
      owner: summon.owner,
      stateLabel: summon.actionAvailable ? 'Action ready' : 'Tapped',
      stateTone: summon.actionAvailable ? 'success' : 'muted',
      metrics: [
        {
          label: 'HP',
          value: summon.hp,
          note: 'Current HP',
        },
        {
          label: 'ATK',
          value: summon.atk,
          note: 'Attack power',
        },
        {
          label: 'HEAL',
          value: summon.heal,
          note: 'Heal power',
        },
      ],
      summaryLines: [
        `Owner ${summon.owner}`,
        `Action available ${summon.actionAvailable ? 'Yes' : 'No'}`,
      ],
    } satisfies CombatSummonViewModel
  }

  function buildStatusViewModel(nextSession: SessionStateDto | null) {
    if (!nextSession) {
      return null
    }

    const combat = nextSession.combat
    const currentActor = parseCombatActor(combat?.currentTurnPlayer ?? null)
    const tieGroupCount = combat?.initiativeTieGroups.filter((group) => group.length > 1).length ?? 0

    return {
      sessionCode: nextSession.sessionCode,
      version: nextSession.version,
      round: combat?.round ?? null,
      currentTurnPlayer: combat?.currentTurnPlayer ?? null,
      phase: combat?.phase ?? null,
      currentTurnLabel: currentActor.label,
      currentTurnTone: currentActor.tone,
      currentTurnNote: currentActor.note,
      turnOrderSummary: buildTurnOrderSummary(combat),
      battlefieldSummary: `${Object.keys(nextSession.players).length} players | ${combat?.enemies.length ?? 0} enemies | ${combat?.summons.length ?? 0} summons`,
      runSummary: nextSession.run?.currentNode
        ? `${nextSession.run.currentNode.name} | ${nextSession.run.currentNode.typeLabel}`
        : nextSession.run?.resultPending
          ? 'A run result is pending resolution.'
          : 'Run node unavailable',
      initiativeSummary: combat ? `${Object.keys(combat.initiatives).length} initiative entries` : 'No initiative state',
      tieGroupSummary: tieGroupCount > 0 ? `${tieGroupCount} tie groups` : 'No tie groups',
    } satisfies CombatStatusViewModel
  }

  function formatSidebarTimestamp(value: string | null) {
    return value?.trim() || 'Timestamp unavailable'
  }

  function mergeEventItems(bufferedItems: readonly SessionEventItemDto[], fetchedItems: readonly SessionEventItemDto[]) {
    const seen = new Set<string>()
    const merged: SessionEventItemDto[] = []
    const toCursorNumber = (cursor: number | string | null | undefined) => {
      const normalized = Number(cursor)
      return Number.isFinite(normalized) ? normalized : -1
    }

    for (const item of [...bufferedItems, ...fetchedItems]) {
      const key =
        Number.isFinite(Number(item.cursor))
          ? String(Number(item.cursor))
          : `${item.version}:${item.type}:${item.timestamp ?? ''}`

      if (seen.has(key)) {
        continue
      }

      seen.add(key)
      merged.push(item)
    }

      return merged
        .sort((left, right) => {
          if (left.version !== right.version) {
            return right.version - left.version
          }

          return toCursorNumber(right.cursor) - toCursorNumber(left.cursor)
        })
        .slice(0, combatSidebarEventLimit)
    }

  function handleWindowStateChange() {
    void loadCombatState()
    void loadCombatSidebarData()
  }

  function handleSelectCommand(commandType: CombatCommandType) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedCommandType: commandType,
        selectedDiscardIds:
          commandType === 'PLAY_CARD' ? commandDraft.selectedDiscardIds : [],
        selectedIds:
          commandType === 'PLAY_CARD' ? commandDraft.selectedIds : [],
      },
      session,
      runtimeAccess,
    )
  }

  function handleSelectEnemy(enemyId: string) {
    const alreadySelected = commandDraft.selectedTargets.some((target) => target.enemyId === enemyId)

    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedEnemyId: alreadySelected ? null : enemyId,
        selectedTargets: alreadySelected
          ? commandDraft.selectedTargets.filter((target) => target.enemyId !== enemyId)
          : [...commandDraft.selectedTargets, { enemyId }],
      },
      session,
      runtimeAccess,
    )
  }

  function handleSelectPlayer(playerId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedPlayerId: playerId,
      },
      session,
      runtimeAccess,
    )
  }

  function handleToggleTargetPlayer(playerId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedTargets: commandDraft.selectedTargets.some((target) => target.playerId === playerId)
          ? commandDraft.selectedTargets.filter((target) => target.playerId !== playerId)
          : [...commandDraft.selectedTargets, { playerId }],
      },
      session,
      runtimeAccess,
    )
  }

  function handleToggleTargetSummon(owner: string, summonId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedTargets: commandDraft.selectedTargets.some(
          (target) =>
            target.summonOwnerPlayerId === owner && target.summonInstanceId === summonId,
        )
          ? commandDraft.selectedTargets.filter(
              (target) =>
                !(
                  target.summonOwnerPlayerId === owner && target.summonInstanceId === summonId
                ),
            )
          : [
              ...commandDraft.selectedTargets,
              { summonOwnerPlayerId: owner, summonInstanceId: summonId },
            ],
      },
      session,
      runtimeAccess,
    )
  }

  function handleSelectHandCard(instanceId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedCommandType: 'PLAY_CARD',
        selectedCardId: instanceId,
        selectedDiscardIds: [],
        selectedIds: [],
      },
      session,
      runtimeAccess,
    )
  }

  function handleToggleDiscard(instanceId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedDiscardIds: toggleCombatIdentifier(commandDraft.selectedDiscardIds, instanceId),
      },
      session,
      runtimeAccess,
    )
  }

  function handleToggleSelectedId(instanceId: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedIds: toggleCombatIdentifier(commandDraft.selectedIds, instanceId),
      },
      session,
      runtimeAccess,
    )
  }

  function handleTogglePendingSelectedId(value: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedIds: toggleCombatIdentifier(commandDraft.selectedIds, value),
      },
      session,
      runtimeAccess,
    )
  }

  function handleToggleOrderedActorKey(actorKey: string) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        orderedActorKeys: toggleCombatIdentifier(commandDraft.orderedActorKeys, actorKey),
      },
      session,
      runtimeAccess,
    )
  }

  function handleSelectedCountChange(value: string) {
    const parsed = Number(value)

    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedCount: Number.isFinite(parsed) && parsed > 0 ? parsed : 1,
      },
      session,
      runtimeAccess,
    )
  }

  function handleSelectedReasonChange(value: string) {
    commandDraft = {
      ...commandDraft,
      selectedReason: value,
    }
  }

  function clearCommandMessages() {
    commandErrorMessage = null
    commandRejectedMessage = null
    commandSuccessMessage = null
  }

  function resetCommandDraftAfterSuccess(commandType: CombatCommandType, nextSession: SessionStateDto | null) {
    commandDraft = syncCombatCommandDraft(
      {
        ...commandDraft,
        selectedCommandType: commandType,
        selectedCardId: null,
        selectedTargets: [],
        selectedDiscardIds: [],
        selectedIds: [],
        orderedActorKeys: [],
        selectedReason: '',
      },
      nextSession ?? session,
      runtimeAccess,
    )
  }

  function getPlayerCommandAccess() {
    if (!isStoredPlayerSessionAccess(runtimeAccess)) {
      return null
    }

    return {
      role: 'player' as const,
      playerToken: runtimeAccess.playerToken,
      playerId: runtimeAccess.playerId,
    }
  }

  async function loadCombatEvents() {
    const requestId = ++eventsRequestSequence

    if (!requestedSessionCode) {
      eventsLoading = false
      eventsErrorMessage = 'Session code is required before events can be restored.'
      eventItems = []
      return
    }

    const access = toSessionReadAccess(runtimeAccess)

    if (!access) {
      eventsLoading = false
      eventsErrorMessage = 'Session access token is required before events can be restored.'
      eventItems = []
      return
    }

    eventsLoading = true
    eventsErrorMessage = null

    try {
      const response = await getSessionEvents(
        requestedSessionCode,
        { limit: combatSidebarEventLimit },
        access,
      )

      if (requestId !== eventsRequestSequence) {
        return
      }

      eventItems = response.items
    } catch (error) {
      if (requestId !== eventsRequestSequence) {
        return
      }

      eventItems = []
      eventsErrorMessage = getApiErrorMessage(error, 'Unable to load combat events.')
    } finally {
      if (requestId === eventsRequestSequence) {
        eventsLoading = false
      }
    }
  }

  async function loadCombatLogs() {
    const requestId = ++logsRequestSequence

    if (!requestedSessionCode) {
      logsLoading = false
      logsErrorMessage = 'Session code is required before logs can be restored.'
      logItems = []
      return
    }

    const access = toSessionReadAccess(runtimeAccess)

    if (!access) {
      logsLoading = false
      logsErrorMessage = 'Session access token is required before logs can be restored.'
      logItems = []
      return
    }

    logsLoading = true
    logsErrorMessage = null

    try {
      const response = await getSessionLogs(requestedSessionCode, { limit: 12 }, access)

      if (requestId !== logsRequestSequence) {
        return
      }

      logItems = response.items
    } catch (error) {
      if (requestId !== logsRequestSequence) {
        return
      }

      logItems = []
      logsErrorMessage = getApiErrorMessage(error, 'Unable to load combat logs.')
    } finally {
      if (requestId === logsRequestSequence) {
        logsLoading = false
      }
    }
  }

  async function loadCombatRecentResults() {
    const requestId = ++recentResultsRequestSequence

    if (!requestedSessionCode) {
      recentResultsLoading = false
      recentResultsErrorMessage = 'Session code is required before recent results can be restored.'
      recentResults = null
      return
    }

    const access = toSessionReadAccess(runtimeAccess)

    if (!access) {
      recentResultsLoading = false
      recentResultsErrorMessage = 'Session access token is required before recent results can be restored.'
      recentResults = null
      return
    }

    recentResultsLoading = true
    recentResultsErrorMessage = null

    try {
      const response = await getSessionRecentResults(requestedSessionCode, access)

      if (requestId !== recentResultsRequestSequence) {
        return
      }

      recentResults = response
    } catch (error) {
      if (requestId !== recentResultsRequestSequence) {
        return
      }

      recentResults = null
      recentResultsErrorMessage = getApiErrorMessage(error, 'Unable to load recent results.')
    } finally {
      if (requestId === recentResultsRequestSequence) {
        recentResultsLoading = false
      }
    }
  }

  async function loadCombatSidebarData() {
    await Promise.all([loadCombatEvents(), loadCombatLogs(), loadCombatRecentResults()])
  }

  function syncEngineResponseSuccess(commandType: CombatCommandType, nextSession: SessionStateDto | null, nextEvents: SessionEventItemDto[]) {
    if (nextSession) {
      syncCombatState(nextSession)
      updateCombatPollingVersion(nextSession)
    }

    resetCommandDraftAfterSuccess(commandType, nextSession)
    recentCommandEvents = nextEvents
    commandSuccessMessage = `${commandType} command was accepted and the combat shell synced to the latest session state.`
    void loadCombatSidebarData()
  }

  function handleRejectedCommandResponse(
    commandType: CombatCommandType,
    fallbackMessage: string,
    errors: readonly string[],
    nextSession: SessionStateDto | null,
    nextEvents: SessionEventItemDto[],
  ) {
    const normalizedErrors = errors
      .map((error) => error.trim())
      .filter((error) => error.length > 0)
    const sawVersionMismatch = normalizedErrors.some((error) =>
      error.toLowerCase().includes('version mismatch'),
    )

    if (nextSession) {
      syncCombatState(nextSession)
      updateCombatPollingVersion(nextSession)
    }

    recentCommandEvents = nextEvents
    commandRejectedMessage =
      normalizedErrors.length > 0 ? normalizedErrors.join(', ') : fallbackMessage

    if (sawVersionMismatch && nextSession) {
      commandRejectedMessage = `${commandRejectedMessage} Synced to the latest session state. Try again.`
    }

    void loadCombatSidebarData()
  }

  async function handleSimpleCommand(commandType: 'END_TURN' | 'DRAW' | 'CLEAR_RECENT_RESULTS') {
    if (!requestedSessionCode || !session || commandPending) {
      return
    }

    clearCommandMessages()

    const playerAccess = getPlayerCommandAccess()

    if (!playerAccess) {
      commandErrorMessage = 'Player token access is required before a command can be sent.'
      return
    }

    if (commandType === 'CLEAR_RECENT_RESULTS') {
      if (!commandGuards.canClearRecentResultsCommand) {
        commandErrorMessage = 'Player token access is required before clearing recent results.'
        return
      }
    } else if (!commandGuards.canIssuePlayerCommand) {
      commandErrorMessage = 'The runtime player must own the current turn before issuing this command.'
      return
    }

    commandPending = commandType
    commandDraft = {
      ...commandDraft,
      selectedCommandType: commandType,
      selectedPlayerId: playerAccess.playerId,
    }

    try {
      const payload: CommandRequest = {
        type: commandType,
        expectedVersion: session.version,
        playerId: playerAccess.playerId,
        count:
          commandType === 'DRAW'
            ? typeof commandDraft.selectedCount === 'number' && commandDraft.selectedCount > 0
              ? commandDraft.selectedCount
              : 1
            : undefined,
      }

      const response = await executeSessionCommand(
        requestedSessionCode,
        payload,
        playerAccess,
      )

      if (!response.accepted) {
        handleRejectedCommandResponse(
          commandType,
          `${commandType} was rejected by the engine.`,
          response.errors,
          response.state,
          response.events,
        )
        return
      }

      syncEngineResponseSuccess(commandType, response.state, response.events)
    } catch (error) {
      commandErrorMessage = getApiErrorMessage(error, `Unable to execute the ${commandType} command.`)
    } finally {
      commandPending = null
    }
  }

  async function handlePlayerCardCommand(commandType: 'PLAY_CARD' | 'USE_EX') {
    if (!requestedSessionCode || !session || commandPending) {
      return
    }

    clearCommandMessages()

    const playerAccess = getPlayerCommandAccess()

    if (!playerAccess) {
      commandErrorMessage = 'Player token access is required before this command can be sent.'
      return
    }

    if (!commandGuards.canIssuePlayerCommand) {
      commandErrorMessage = 'The runtime player must own the current turn before issuing this command.'
      return
    }

    const unsupportedMessage = getUnsupportedCardCommandMessage()

    if (unsupportedMessage) {
      commandRejectedMessage = unsupportedMessage
      commandErrorMessage = null
      commandSuccessMessage = null
      return
    }

    const cardId =
      commandType === 'PLAY_CARD'
        ? commandDraft.selectedCardId
        : session.players[playerAccess.playerId]?.exCard ?? null

    if (!cardId) {
      commandErrorMessage =
        commandType === 'PLAY_CARD'
          ? 'Select a hand card before issuing PLAY_CARD.'
          : 'EX card is not available for the current runtime player.'
      return
    }

    const runtimePlayer = session.players[playerAccess.playerId] ?? null

    if (commandType === 'PLAY_CARD' && !runtimePlayer?.hand.includes(cardId)) {
      commandErrorMessage = 'Select a card from the runtime player hand before issuing PLAY_CARD.'
      return
    }

    const commandDefId = getCardDefIdFromInstanceId(cardId)
    const commandDetail = commandDefId
      ? getCardDetail(commandDefId) ?? (await ensureCardDetail(commandDefId))
      : null
    const playSpec = normalizePlaySpec(commandDetail?.playSpec ?? null)
    const filteredTargets = commandDraft.selectedTargets
    const filteredDiscardIds = selectedDiscardIdsFromHand
    const filteredSelectedIds = selectedFieldIds
    const requirementError = getPlayCardRequirementError(
      commandType === 'PLAY_CARD' ? 'PLAY_CARD' : 'USE_EX',
      playSpec,
      cardId,
      filteredTargets,
      filteredDiscardIds,
      filteredSelectedIds,
    )

    if (requirementError) {
      commandErrorMessage = requirementError
      return
    }

    commandPending = commandType
    commandDraft = {
      ...commandDraft,
      selectedCommandType: commandType,
      selectedPlayerId: playerAccess.playerId,
    }

    try {
      const payload: CommandRequest =
        commandType === 'PLAY_CARD'
          ? {
              type: commandType,
              expectedVersion: session.version,
              playerId: playerAccess.playerId,
              cardId,
              targets: filteredTargets.length > 0 ? filteredTargets : undefined,
              discardIds: filteredDiscardIds.length > 0 ? filteredDiscardIds : undefined,
              selectedIds: filteredSelectedIds.length > 0 ? filteredSelectedIds : undefined,
            }
          : {
              type: commandType,
              expectedVersion: session.version,
              playerId: playerAccess.playerId,
              targets: filteredTargets.length > 0 ? filteredTargets : undefined,
            }

      const response = await executeSessionCommand(
        requestedSessionCode,
        payload,
        playerAccess,
      )

      if (!response.accepted) {
        handleRejectedCommandResponse(
          commandType,
          `${commandType} was rejected by the engine.`,
          response.errors,
          response.state,
          response.events,
        )
        return
      }

      syncEngineResponseSuccess(commandType, response.state, response.events)
    } catch (error) {
      commandErrorMessage = getApiErrorMessage(error, `Unable to execute the ${commandType} command.`)
    } finally {
      commandPending = null
    }
  }

  async function handlePendingDecisionCommand() {
    if (!requestedSessionCode || !session || commandPending) {
      return
    }

    if (!isStoredPlayerSessionAccess(runtimeAccess)) {
      commandErrorMessage = 'Player token access is required before a pending decision can be sent.'
      return
    }

    if (!commandGuards.canResolvePendingCommand || !runtimePendingDecision?.type) {
      commandErrorMessage = 'A supported pending decision is required before this command can be sent.'
      return
    }

    const unsupportedMessage = unsupportedPendingDecisionMessage

    if (unsupportedMessage) {
      commandRejectedMessage = unsupportedMessage
      commandErrorMessage = null
      commandSuccessMessage = null
      return
    }

    const payloadBase = {
      type: runtimePendingDecision.type,
      expectedVersion: session.version,
      playerId: runtimeAccess.playerId,
      reason: commandDraft.selectedReason || runtimePendingDecision.reason,
    } as const

    let payload: CommandRequest | null = null

    switch (runtimePendingDecision.type) {
      case 'HAND_SWAP': {
        const discardIds = selectedDiscardIdsFromHand

        if (discardIds.length !== 1) {
          commandErrorMessage = 'Select exactly one hand card before resolving HAND_SWAP.'
          return
        }
        payload = {
          ...payloadBase,
          discardIds,
        }
        break
      }
      case 'DISCARD_TO_HAND_LIMIT': {
        const discardIds = selectedDiscardIdsFromHand

        if (discardIds.length === 0) {
          commandErrorMessage = 'Select hand cards to discard before resolving DISCARD_TO_HAND_LIMIT.'
          return
        }
        payload = {
          ...payloadBase,
          discardIds,
        }
        break
      }
      case 'SEARCH_PICK':
      case 'RESOLVE_SEARCH_PICK':
        if (pendingCandidateIds.length === 0) {
          commandErrorMessage = 'Select candidate ids before resolving this decision.'
          return
        }
        if (
          typeof runtimePendingDecision.pickCount === 'number' &&
          runtimePendingDecision.pickCount > 0 &&
          pendingCandidateIds.length !== runtimePendingDecision.pickCount
        ) {
          commandErrorMessage = `Select exactly ${runtimePendingDecision.pickCount} candidate ids before resolving this decision.`
          return
        }
        payload = {
          ...payloadBase,
          selectedIds: pendingCandidateIds,
        }
        break
      case 'RESOLVE_INITIATIVE_TIE': {
        const orderedActorKeys =
          orderedTieActorKeys.length > 0
            ? orderedTieActorKeys
            : runtimePendingDecision.actorKeys

        if (orderedActorKeys.length !== runtimePendingDecision.actorKeys.length) {
          commandErrorMessage = 'Order all actor keys in the tie group before resolving the initiative tie.'
          return
        }

        payload = {
          ...payloadBase,
          tieGroupIndex: runtimePendingDecision.groupIndex,
          orderedActorKeys,
        }
        break
      }
      default:
        commandRejectedMessage = `${runtimePendingDecision.type} is not supported in this step yet.`
        return
    }

    clearCommandMessages()
    commandPending = runtimePendingDecision.type
    commandDraft = {
      ...commandDraft,
      selectedCommandType: runtimePendingDecision.type,
      selectedPlayerId: runtimeAccess.playerId,
    }

    try {
      if (!payload) {
        commandErrorMessage = 'Pending decision payload could not be built.'
        return
      }

      const response = await executeSessionCommand(
        requestedSessionCode,
        payload,
        {
          role: 'player',
          playerToken: runtimeAccess.playerToken,
          playerId: runtimeAccess.playerId,
        },
      )

      if (!response.accepted) {
        handleRejectedCommandResponse(
          runtimePendingDecision.type,
          `${runtimePendingDecision.type} was rejected by the engine.`,
          response.errors,
          response.state,
          response.events,
        )
        return
      }

      syncEngineResponseSuccess(runtimePendingDecision.type, response.state, response.events)
    } catch (error) {
      commandErrorMessage = getApiErrorMessage(
        error,
        `Unable to resolve ${runtimePendingDecision.type}.`,
      )
    } finally {
      commandPending = null
    }
  }

  onMount(() => {
    void loadCombatState()
    void loadCardCatalog()
    void loadCombatSidebarData()
    window.addEventListener('popstate', handleWindowStateChange)

    return () => {
      combatPage.dispose()
      invalidateCombatSidebarRequests()
      window.removeEventListener('popstate', handleWindowStateChange)
    }
  })

  const routeSessionCode = $derived.by(() => readSessionCodeFromRoute('combat'))
  const requestedSessionCode = $derived.by(() =>
    readRequestedSessionCodeFromAccessOrHandoff({
      pageKey: 'combat',
      storedAccess: runtimeAccess,
      preferStoredAccess: false,
    }).code,
  )
  const combatState = $derived.by(() => session?.combat ?? null)
  const runState = $derived.by(() => session?.run ?? null)
  const statusView = $derived.by(() => buildStatusViewModel(session))
  const commandGuards = $derived.by(() => buildCombatCommandGuards(session, runtimeAccess))
  const accessRoleLabel = $derived.by(() => {
    if (isStoredGmSessionAccess(runtimeAccess)) {
      return 'GM access'
    }

    if (isStoredPlayerSessionAccess(runtimeAccess)) {
      return `Player ${runtimeAccess.playerId}`
    }

    return 'Read-only shell'
  })
  const playerViews = $derived.by(() =>
    session ? Object.values(session.players).map((player) => buildPlayerViewModel(player)) : [],
  )
  const enemyViews = $derived.by(() =>
    combatState ? combatState.enemies.map((enemy) => buildEnemyViewModel(enemy)) : [],
  )
  const summonViews = $derived.by(() =>
    combatState ? combatState.summons.map((summon) => buildSummonViewModel(summon)) : [],
  )
  const visiblePlayerView = $derived.by(() => {
    const playerAccess = isStoredPlayerSessionAccess(runtimeAccess) ? runtimeAccess : null

    if (playerAccess) {
      return playerViews.find((player) => player.playerId === playerAccess.playerId) ?? playerViews[0] ?? null
    }

    return playerViews[0] ?? null
  })
  const runtimePendingDecision = $derived.by(() => {
    if (isStoredPlayerSessionAccess(runtimeAccess) && session) {
      return session.players[runtimeAccess.playerId]?.pendingDecision ?? null
    }

    return null
  })
  const unsupportedPendingDecisionMessage = $derived.by(() =>
    getUnsupportedPendingDecisionMessage(runtimePendingDecision),
  )
  const currentTurnActor = $derived.by(() => parseCombatActor(combatState?.currentTurnPlayer ?? null))
  const currentEnemyView = $derived.by(() =>
    currentTurnActor.kind === 'enemy' && currentTurnActor.id
      ? enemyViews.find((enemy) => enemy.enemyId === currentTurnActor.id) ?? null
      : null,
  )
  const latestRecentResult = $derived.by(() => recentResults?.recentResults[0] ?? null)
  const selectedEnemyView = $derived.by(() =>
    commandDraft.selectedEnemyId
      ? enemyViews.find((enemy) => enemy.enemyId === commandDraft.selectedEnemyId) ?? null
      : null,
  )
  const selectedCardView = $derived.by(() =>
    commandDraft.selectedCardId && visiblePlayerView
      ? visiblePlayerView.handCards.find((card) => card.instanceId === commandDraft.selectedCardId) ?? null
      : null,
  )
  const runtimePlayerState = $derived.by(() => {
    if (isStoredPlayerSessionAccess(runtimeAccess) && session) {
      return session.players[runtimeAccess.playerId] ?? null
    }

    return null
  })
  const runtimeExCardView = $derived.by(() =>
    runtimePlayerState?.exCard ? resolveCombatCard(runtimePlayerState.exCard) : null,
  )
  const selectedCommandDefId = $derived.by(() => {
    if (commandDraft.selectedCommandType === 'PLAY_CARD') {
      return getCardDefIdFromInstanceId(commandDraft.selectedCardId)
    }

    if (commandDraft.selectedCommandType === 'USE_EX') {
      return getCardDefIdFromInstanceId(runtimePlayerState?.exCard ?? null)
    }

    return null
  })
  const selectedCommandDetail = $derived.by(() => getCardDetail(selectedCommandDefId))
  const selectedCommandDetailLoading = $derived.by(() =>
    selectedCommandDefId ? cardDetailLoadingIds.includes(selectedCommandDefId) : false,
  )
  const selectedCommandDetailError = $derived.by(() =>
    selectedCommandDefId ? cardDetailErrors[selectedCommandDefId] ?? null : null,
  )
  const selectedCommandPlaySpec = $derived.by(() =>
    normalizePlaySpec(selectedCommandDetail?.playSpec ?? null),
  )
  const selectedCommandSourceLabel = $derived.by(() => {
    if (commandDraft.selectedCommandType === 'PLAY_CARD') {
      return selectedCardView?.title ?? null
    }

    if (commandDraft.selectedCommandType === 'USE_EX') {
      return runtimeExCardView?.title ?? null
    }

    return null
  })
  const selectedDiscardIdsFromHand = $derived.by(() =>
    getSelectedDiscardIdsFromHand(runtimePlayerState, commandDraft.selectedDiscardIds),
  )
  const selectedFieldIds = $derived.by(() =>
    getSelectedFieldIds(runtimePlayerState, commandDraft.selectedIds),
  )
  const pendingCandidateIds = $derived.by(() =>
    getPendingCandidateIds(runtimePendingDecision, commandDraft.selectedIds),
  )
  const orderedTieActorKeys = $derived.by(() =>
    getOrderedTieActorKeys(runtimePendingDecision, commandDraft.orderedActorKeys),
  )
  const selectedCommandRequirementView = $derived.by(() => {
    if (!commandDraft.selectedCommandType || !selectedCommandSourceLabel) {
      return null
    }

    return buildCommandRequirementViewModel(
      selectedCommandSourceLabel,
      selectedCommandPlaySpec,
    )
  })
  const mergedEventItems = $derived.by(() => mergeEventItems(recentCommandEvents, eventItems))
  const commandOptions = $derived.by(
    () =>
      [
        {
          id: 'DRAW',
          title: 'Draw',
          note: commandGuards.canIssuePlayerCommand
            ? 'Draw is available for the runtime player on the current turn.'
            : 'Requires the runtime player to own the current turn.',
          disabled: !commandGuards.canIssuePlayerCommand,
        },
        {
          id: 'END_TURN',
          title: 'End turn',
          note: commandGuards.canIssuePlayerCommand
            ? 'Available when the runtime player owns the current turn.'
            : 'Requires the runtime player to own the current turn.',
          disabled: !commandGuards.canIssuePlayerCommand,
        },
        {
          id: 'CLEAR_RECENT_RESULTS',
          title: 'Clear recent results',
          note: commandGuards.canClearRecentResultsCommand
            ? 'Connected as a player-side utility command that clears the recent result stack.'
            : 'Requires player token access for the current session.',
          disabled: !commandGuards.canClearRecentResultsCommand,
        },
        {
          id: 'PLAY_CARD',
          title: 'Play selected card',
          note: commandDraft.selectedCardId
            ? 'Uses the selected hand card instance as the next command source.'
            : 'Select a hand card first to prepare a play-card command.',
          disabled: !commandGuards.canIssuePlayerCommand || !commandDraft.selectedCardId,
        },
        {
          id: 'USE_EX',
          title: 'Use EX',
          note: commandGuards.exAvailable
            ? 'EX is available for the runtime player.'
            : 'Requires a runtime player with EX available and not on cooldown.',
          disabled: !commandGuards.canIssuePlayerCommand || !commandGuards.exAvailable,
        },
        {
          id: 'RESOLVE_PENDING',
          title: 'Resolve pending decision',
          note: commandGuards.hasPendingDecision
            ? 'A pending decision is present for the runtime player.'
            : 'Requires a pending decision on the runtime player state.',
          disabled: !commandGuards.canIssuePlayerCommand || !commandGuards.hasPendingDecision,
        },
        {
          id: 'GM_REVIEW',
          title: 'GM review',
          note: 'Reserved for a later GM-only command step.',
          disabled: true,
        },
      ] satisfies CommandOptionViewModel[],
  )

  const currentTurnStateLabel = $derived.by(() =>
    currentTurnActor.kind === 'enemy'
      ? 'Enemy acting'
      : currentTurnActor.kind === 'player'
        ? 'Player acting'
        : 'Turn pending',
  )
  const selectedTargetLabels = $derived.by(() =>
    commandDraft.selectedTargets.map((target) => formatTargetSelectionLabel(target)),
  )
  const commandGuardMessage = $derived.by(
    () =>
      `Expected version ${commandGuards.expectedVersion ?? 'N/A'} | Current actor ${statusView?.currentTurnLabel ?? 'Unavailable'} | Runtime role ${commandGuards.role}`,
  )
  const eventFeedEntries = $derived.by(
    () =>
      mergedEventItems.map((event) => ({
        title: event.type,
        lines: [
          `Version ${event.version} | Cursor ${event.cursor}`,
          formatSidebarTimestamp(event.timestamp),
        ],
      })) satisfies CombatFeedEntry[],
  )
  const logFeedEntries = $derived.by(
    () =>
      logItems.map((log) => ({
        title: log.type,
        lines: [log.message, `Version ${log.version} | ${formatSidebarTimestamp(log.timestamp)}`],
      })) satisfies CombatFeedEntry[],
  )
  const recentResultEntries = $derived.by(
    () =>
      (recentResults?.recentResults ?? []).map((result) => ({
        title: result.title,
        summary: result.summary,
        meta: `${result.type} | ${result.at ?? 'Time unavailable'}`,
      })) satisfies CombatRecentResultEntry[],
  )
  const runNodeSummary = $derived.by(
    () =>
      `Run node: ${runState?.currentNode?.name ?? 'Unavailable'} | Result pending: ${runState?.resultPending ? 'Yes' : 'No'}`,
  )

  function handleCommandButtonClick(commandType: string) {
    const nextCommandType = commandType as CombatCommandType

    handleSelectCommand(nextCommandType)

    if (
      nextCommandType === 'END_TURN' ||
      nextCommandType === 'DRAW' ||
      nextCommandType === 'CLEAR_RECENT_RESULTS'
    ) {
      void handleSimpleCommand(nextCommandType)
    } else if (nextCommandType === 'PLAY_CARD' || nextCommandType === 'USE_EX') {
      void handlePlayerCardCommand(nextCommandType)
    }
  }

  $effect(() => {
    if (selectedCommandDefId) {
      void ensureCardDetail(selectedCommandDefId)
    }
  })
</script>

<div class="combat-page">
  {#if loading}
    <SectionFrame
      eyebrow="Combat Status"
      title="Loading combat shell"
      description="Restoring the current combat session state from the live session API."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.loading.title}
        message="Fetching the current combat shell by session code."
      />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame
      eyebrow="Combat Access"
      title="Combat shell is unavailable"
      description="This page needs a session code first, then it can restore the live combat shell."
    >
      <ContentStatePanel title="Combat code required" message={invalidAccessMessage} tone="error">
        <p>Open the session entry screen or a lobby route first, then return with a session code.</p>
      </ContentStatePanel>
      <div class="combat-page__action-buttons">
        <a class="combat-page__nav-link" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if notFound}
    <SectionFrame
      eyebrow="Session Missing"
      title="Combat session not found"
      description="The requested combat code did not resolve to a live session."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.notFound.title}
        message={sessionPageStateCopy.notFound.message}
        tone="error"
      >
        <p>Requested code: {requestedSessionCode ?? 'Unavailable'}</p>
        <p>Check the current session code and reopen the combat route.</p>
      </ContentStatePanel>
      <div class="combat-page__action-buttons">
        <a class="combat-page__nav-link" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame
      eyebrow="Combat Status"
      title="Combat shell could not be loaded"
      description="The session code was valid, but the live combat shell could not be restored."
    >
      <ContentStatePanel
        title="Unable to load combat shell"
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={() => void loadCombatState()}
      />
      <div class="combat-page__action-buttons">
        <a class="combat-page__nav-link" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if session && statusView}
    <CombatLayout>
      {#snippet header()}
        <CombatHeader
          {statusView}
          {accessRoleLabel}
          combatStateLive={Boolean(combatState)}
          currentTurnStateLabel={currentTurnStateLabel}
          {recentResultsLoading}
          {recentResultsErrorMessage}
          {latestRecentResult}
          {accessNoticeMessage}
          {catalogErrorMessage}
          {commandErrorMessage}
          {commandRejectedMessage}
          {commandSuccessMessage}
        />
      {/snippet}

      {#snippet field()}
        <BattlefieldPanel
          {playerViews}
          {enemyViews}
          {summonViews}
          currentTurnPlayerId={currentTurnActor.kind === 'player' ? currentTurnActor.id : null}
          currentEnemyId={currentEnemyView?.enemyId ?? null}
          visiblePlayerId={visiblePlayerView?.playerId ?? null}
          selectedPlayerId={commandDraft.selectedPlayerId}
          selectedTargets={commandDraft.selectedTargets}
          onSelectPlayer={handleSelectPlayer}
          onToggleTargetPlayer={handleToggleTargetPlayer}
          onToggleTargetEnemy={handleSelectEnemy}
          onToggleTargetSummon={handleToggleTargetSummon}
        />
      {/snippet}

      {#snippet sidebar()}
        <CombatSidebar
          {commandOptions}
          commandPending={commandPending}
          selectedCommandType={commandDraft.selectedCommandType}
          commandGuardMessage={commandGuardMessage}
          isCurrentTurnPlayer={commandGuards.isCurrentTurnPlayer}
          hasPendingDecision={commandGuards.hasPendingDecision}
          exAvailable={commandGuards.exAvailable}
          recentCommandEventCount={recentCommandEvents.length}
          requirementView={selectedCommandRequirementView}
          sourceLabel={selectedCommandSourceLabel}
          detailLoading={selectedCommandDetailLoading}
          detailError={selectedCommandDetailError}
          selectedTargetLabels={selectedTargetLabels}
          selectedDiscardIds={selectedDiscardIdsFromHand}
          selectedFieldIds={selectedFieldIds}
          pendingDecision={runtimePendingDecision}
          unsupportedPendingDecisionMessage={unsupportedPendingDecisionMessage}
          {pendingCandidateIds}
          {orderedTieActorKeys}
          canResolvePendingCommand={commandGuards.canResolvePendingCommand}
          {visiblePlayerView}
          eventEntries={eventFeedEntries}
          {eventsLoading}
          {eventsErrorMessage}
          logEntries={logFeedEntries}
          {logsLoading}
          {logsErrorMessage}
          recentResultEntries={recentResultEntries}
          {recentResultsLoading}
          {recentResultsErrorMessage}
          onCommandButtonClick={handleCommandButtonClick}
          onClearTargets={handleClearSelectedTargets}
          onClearSelectionInputs={handleClearSelectionInputs}
          onTogglePendingSelectedId={handleTogglePendingSelectedId}
          onToggleOrderedActorKey={handleToggleOrderedActorKey}
          onResolvePendingDecision={() => void handlePendingDecisionCommand()}
          onToggleSelectedId={handleToggleSelectedId}
          onRetryEvents={() => void loadCombatEvents()}
          onRetryLogs={() => void loadCombatLogs()}
          onRetryResults={() => void loadCombatRecentResults()}
        />
      {/snippet}

      {#snippet hand()}
        <HandBar
          handCards={visiblePlayerView?.handCards ?? []}
          selectedCardId={commandDraft.selectedCardId}
          selectedDiscardIds={selectedDiscardIdsFromHand}
          selectedCommandType={commandDraft.selectedCommandType}
          expectedVersion={commandGuards.expectedVersion}
          currentActorLabel={statusView.currentTurnLabel}
          visibleHandOwner={visiblePlayerView?.playerId ?? null}
          selectedActor={commandDraft.selectedPlayerId}
          selectedEnemyId={selectedEnemyView?.enemyId ?? null}
          selectedCardLabel={selectedCardView?.title ?? commandDraft.selectedCardId ?? null}
          pendingDecisionType={runtimePendingDecision?.type ?? null}
          selectedTargetCount={commandDraft.selectedTargets.length}
          selectedIdCount={commandDraft.selectedIds.length}
          orderedActorKeysSummary={commandDraft.orderedActorKeys.join(', ') || 'None'}
          targetRefSummary={formatTargetRefSummary(commandDraft.selectedTargets)}
          selectedDiscardCount={selectedDiscardIdsFromHand.length}
          selectedFieldCount={selectedFieldIds.length}
          selectedCount={commandDraft.selectedCount}
          pendingCandidateCount={pendingCandidateIds.length}
          bufferedEventCount={recentCommandEvents.length}
          runNodeSummary={runNodeSummary}
          selectedReason={commandDraft.selectedReason}
          {catalogLoading}
          emptyMessage="Visible hand cards will render here once the current player has hand instances in the live state."
          onSelectHandCard={handleSelectHandCard}
          onToggleDiscard={handleToggleDiscard}
          onSelectedCountChange={handleSelectedCountChange}
          onSelectedReasonChange={handleSelectedReasonChange}
          onClearTargets={handleClearSelectedTargets}
          onClearSelectionInputs={handleClearSelectionInputs}
        />
      {/snippet}
    </CombatLayout>
  {/if}
</div>

<style>
  .combat-page {
    display: grid;
    gap: 1rem;
  }

  .combat-page__action-buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .combat-page__nav-link {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
</style>
