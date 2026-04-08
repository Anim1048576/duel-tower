<script lang="ts">
  import { onMount } from 'svelte'
  import { listCards } from '../lib/api/content'
  import type { CardDefinition } from '../lib/api/contentTypes'
  import {
    executeSessionCommand,
    getSessionEvents,
    getSessionLogs,
    getSessionRecentResults,
    getSessionState,
  } from '../lib/api/sessions'
  import type {
    CardInstanceDto,
    CombatEnemyDto,
    CombatSummonDto,
    PendingDecisionDto,
    RecentResultsResponse,
    SessionEventItemDto,
    SessionLogItemDto,
    PlayerStateDto,
    SessionStateDto,
  } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    hasStoredSessionCode,
    isStoredGmSessionAccess,
    isStoredPlayerSessionAccess,
    normalizeSessionCode,
    readStoredSessionAccess,
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
  import { sessionPageStateCopy } from '../lib/session/pageState'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type CombatStatusViewModel = {
    sessionCode: string
    version: number
    round: number | null
    currentTurnPlayer: string | null
    phase: string | null
    initiativeSummary: string
    tieGroupSummary: string
  }

  type ResolvedCombatCardViewModel = {
    instanceId: string
    defId: string | null
    title: string
    subtitle: string
    meta: string
    description: string
    unresolved: boolean
    tags: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  type CombatPlayerViewModel = {
    playerId: string
    ready: boolean
    stateLabel: string
    stateTone: 'accent' | 'muted' | 'success' | 'warning'
    summaryLines: string[]
    passives: string[]
    handCards: ResolvedCombatCardViewModel[]
    fieldCards: ResolvedCombatCardViewModel[]
    graveCards: ResolvedCombatCardViewModel[]
    excludedCards: ResolvedCombatCardViewModel[]
  }

  type CombatEnemyViewModel = {
    enemyId: string
    stateLabel: string
    stateTone: 'accent' | 'muted' | 'success' | 'warning'
    summaryLines: string[]
    statusEntries: string[]
  }

  type CombatSummonViewModel = {
    summonId: string
    owner: string
    stateLabel: string
    stateTone: 'accent' | 'muted' | 'success' | 'warning'
    summaryLines: string[]
  }

  type CommandOptionViewModel = {
    id: CombatCommandType
    title: string
    note: string
    disabled: boolean
  }

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
  let commandDraft = $state<CombatCommandDraft>(createEmptyCombatCommandDraft())
  let commandPending = $state<CombatCommandType | null>(null)
  let commandErrorMessage = $state<string | null>(null)
  let commandRejectedMessage = $state<string | null>(null)
  let commandSuccessMessage = $state<string | null>(null)
  let recentCommandEvents = $state<SessionEventItemDto[]>([])
  let eventsLoading = $state(true)
  let eventsErrorMessage = $state<string | null>(null)
  let eventItems = $state<SessionEventItemDto[]>([])
  let logsLoading = $state(true)
  let logsErrorMessage = $state<string | null>(null)
  let logItems = $state<SessionLogItemDto[]>([])
  let recentResultsLoading = $state(true)
  let recentResultsErrorMessage = $state<string | null>(null)
  let recentResults = $state<RecentResultsResponse | null>(null)
  let requestSequence = 0

  function getRouteSessionCode() {
    if (typeof window === 'undefined') {
      return null
    }

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key !== 'combat') {
      return null
    }

    const code = match.params.code?.trim()
    return code ? normalizeSessionCode(code) : null
  }

  function getRequestedSessionCode() {
    const routeCode = routeSessionCode

    if (routeCode) {
      return routeCode
    }

    const handoffCode = readSelectionHandoff(selectionHandoffKeys.sessionCode)
    return handoffCode ? normalizeSessionCode(handoffCode) : null
  }

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
    setSelectionHandoff(selectionHandoffKeys.sessionCode, nextSession.sessionCode)
    removeSelectionHandoff(selectionHandoffKeys.sessionId)

    if (!routeSessionCode && nextSession.sessionCode) {
      navigateTo(pathBuilders.combat(nextSession.sessionCode), true)
    }
  }

  async function loadCombatState() {
    const nextCode = requestedSessionCode
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidCombatAccessMessage(nextCode)
    const requestId = ++requestSequence

    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage
    accessNoticeMessage = getAccessNotice(nextCode, nextAccess)
    loading = true
    notFound = false
    errorMessage = null
    session = null

    if (!nextCode || nextInvalidAccessMessage) {
      loading = false
      return
    }

    try {
      const response = await getSessionState(nextCode)

      if (requestId !== requestSequence) {
        return
      }

      syncCombatState(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (typeof error === 'object' && error && 'status' in error && error.status === 404) {
        notFound = true
      } else {
        errorMessage = getApiErrorMessage(error, 'Unable to restore the current combat session shell.')
      }
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
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
    return {
      playerId: player.playerId,
      ready: player.ready,
      stateLabel: getPlayerStateLabel(player),
      stateTone: getPlayerStateTone(player),
      summaryLines: [
        `Hand ${player.hand.length} | Grave ${player.grave.length} | Field ${player.field.length} | Excluded ${player.excluded.length}`,
        `EX ${player.exCard ?? 'None'} | Cooldown ${player.exOnCooldown ? 'Yes' : 'No'} | Passives ${player.passiveIds.length}`,
        `Pending decision ${player.pendingDecision ? 'Present' : 'None'} | Ready ${player.ready ? 'Yes' : 'No'}`,
      ],
      passives: player.passiveIds,
      handCards: player.hand.map((instanceId) => resolveCombatCard(instanceId)),
      fieldCards: player.field.map((instanceId) => resolveCombatCard(instanceId)),
      graveCards: player.grave.map((instanceId) => resolveCombatCard(instanceId)),
      excludedCards: player.excluded.map((instanceId) => resolveCombatCard(instanceId)),
    } satisfies CombatPlayerViewModel
  }

  function buildEnemyViewModel(enemy: CombatEnemyDto) {
    return {
      enemyId: enemy.enemyId,
      stateLabel: enemy.exActivatable ? 'EX ready' : enemy.exOnCooldown ? 'Cooldown' : 'Active',
      stateTone: enemy.exActivatable ? 'warning' : enemy.exOnCooldown ? 'muted' : 'accent',
      summaryLines: [
        `HP ${enemy.hp} / ${enemy.maxHp} | AP ${enemy.ap}`,
        `ATK ${enemy.attackPower} | HEAL ${enemy.healPower}`,
        `EX ${enemy.exCardId ?? 'None'} | EX ready ${enemy.exActivatable ? 'Yes' : 'No'}`,
      ],
      statusEntries: Object.entries(enemy.statuses).map(
        ([statusId, amount]) => `${statusId}: ${amount}`,
      ),
    } satisfies CombatEnemyViewModel
  }

  function buildSummonViewModel(summon: CombatSummonDto) {
    return {
      summonId: summon.summonId,
      owner: summon.owner,
      stateLabel: summon.actionAvailable ? 'Action ready' : 'Tapped',
      stateTone: summon.actionAvailable ? 'success' : 'muted',
      summaryLines: [
        `Owner ${summon.owner}`,
        `HP ${summon.hp} | ATK ${summon.atk} | HEAL ${summon.heal}`,
      ],
    } satisfies CombatSummonViewModel
  }

  function buildStatusViewModel(nextSession: SessionStateDto | null) {
    if (!nextSession) {
      return null
    }

    const combat = nextSession.combat
    const tieGroupCount = combat?.initiativeTieGroups.filter((group) => group.length > 1).length ?? 0

    return {
      sessionCode: nextSession.sessionCode,
      version: nextSession.version,
      round: combat?.round ?? null,
      currentTurnPlayer: combat?.currentTurnPlayer ?? null,
      phase: combat?.phase ?? null,
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

    for (const item of [...bufferedItems, ...fetchedItems]) {
      const key = item.cursor || `${item.version}:${item.type}:${item.timestamp ?? ''}`

      if (seen.has(key)) {
        continue
      }

      seen.add(key)
      merged.push(item)
    }

    return merged
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

  function clearCommandMessages() {
    commandErrorMessage = null
    commandRejectedMessage = null
    commandSuccessMessage = null
  }

  function getSessionReadAccess() {
    if (isStoredPlayerSessionAccess(runtimeAccess)) {
      return {
        role: 'player' as const,
        playerToken: runtimeAccess.playerToken,
        playerId: runtimeAccess.playerId,
      }
    }

    if (isStoredGmSessionAccess(runtimeAccess)) {
      return {
        role: 'gm' as const,
        gmToken: runtimeAccess.gmToken,
      }
    }

    return null
  }

  async function loadCombatEvents() {
    if (!requestedSessionCode) {
      eventsLoading = false
      eventsErrorMessage = 'Session code is required before events can be restored.'
      eventItems = []
      return
    }

    const access = getSessionReadAccess()

    if (!access) {
      eventsLoading = false
      eventsErrorMessage = 'Session access token is required before events can be restored.'
      eventItems = []
      return
    }

    eventsLoading = true
    eventsErrorMessage = null

    try {
      const response = await getSessionEvents(requestedSessionCode, { limit: 12 }, access)
      eventItems = response.items
    } catch (error) {
      eventItems = []
      eventsErrorMessage = getApiErrorMessage(error, 'Unable to load combat events.')
    } finally {
      eventsLoading = false
    }
  }

  async function loadCombatLogs() {
    if (!requestedSessionCode) {
      logsLoading = false
      logsErrorMessage = 'Session code is required before logs can be restored.'
      logItems = []
      return
    }

    const access = getSessionReadAccess()

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
      logItems = response.items
    } catch (error) {
      logItems = []
      logsErrorMessage = getApiErrorMessage(error, 'Unable to load combat logs.')
    } finally {
      logsLoading = false
    }
  }

  async function loadCombatRecentResults() {
    if (!requestedSessionCode) {
      recentResultsLoading = false
      recentResultsErrorMessage = 'Session code is required before recent results can be restored.'
      recentResults = null
      return
    }

    const access = getSessionReadAccess()

    if (!access) {
      recentResultsLoading = false
      recentResultsErrorMessage = 'Session access token is required before recent results can be restored.'
      recentResults = null
      return
    }

    recentResultsLoading = true
    recentResultsErrorMessage = null

    try {
      recentResults = await getSessionRecentResults(requestedSessionCode, access)
    } catch (error) {
      recentResults = null
      recentResultsErrorMessage = getApiErrorMessage(error, 'Unable to load recent results.')
    } finally {
      recentResultsLoading = false
    }
  }

  async function loadCombatSidebarData() {
    await Promise.all([loadCombatEvents(), loadCombatLogs(), loadCombatRecentResults()])
  }

  function syncEngineResponseSuccess(commandType: CombatCommandType, nextSession: SessionStateDto | null, nextEvents: SessionEventItemDto[]) {
    if (nextSession) {
      syncCombatState(nextSession)
    }

    recentCommandEvents = nextEvents
    commandSuccessMessage = `${commandType} command was accepted and the combat shell synced to the latest session state.`
    void loadCombatEvents()
    void loadCombatLogs()
    void loadCombatRecentResults()
  }

  async function handleSimpleCommand(commandType: 'END_TURN' | 'DRAW' | 'CLEAR_RECENT_RESULTS') {
    if (!requestedSessionCode || !session || commandPending) {
      return
    }

    const access = getSessionReadAccess()

    if (!access) {
      commandErrorMessage = 'Session access token is required before a command can be sent.'
      return
    }

    clearCommandMessages()
    commandPending = commandType
    commandDraft = {
      ...commandDraft,
      selectedCommandType: commandType,
      selectedPlayerId: access.role === 'player' ? access.playerId : commandDraft.selectedPlayerId,
    }

    try {
      const response = await executeSessionCommand(
        requestedSessionCode,
        {
          type: commandType,
          expectedVersion: session.version,
          playerId: access.role === 'player' ? access.playerId : commandDraft.selectedPlayerId,
        },
        access,
      )

      if (!response.accepted) {
        const errorParts = response.errors.length > 0 ? response.errors.join(', ') : 'The command was rejected by the engine.'
        commandRejectedMessage = errorParts
        recentCommandEvents = response.events
        return
      }

      syncEngineResponseSuccess(commandType, response.state, response.events)
    } catch (error) {
      commandErrorMessage = getApiErrorMessage(error, `Unable to execute the ${commandType} command.`)
    } finally {
      commandPending = null
    }
  }

  function getUnsupportedCardCommandMessage() {
    if (commandDraft.selectedDiscardIds.length > 0) {
      return 'Cards that require discardIds are not supported in this step yet.'
    }

    if (commandDraft.selectedIds.length > 0) {
      return 'Cards that require selectedIds are not supported in this step yet.'
    }

    return null
  }

  function getUnsupportedPendingDecisionMessage(pendingDecision: PendingDecisionDto | null) {
    if (!pendingDecision?.type) {
      return 'Pending decision type is missing.'
    }

    switch (pendingDecision.type) {
      case 'HAND_SWAP':
      case 'DISCARD_TO_HAND_LIMIT':
      case 'SEARCH_PICK':
      case 'RESOLVE_SEARCH_PICK':
      case 'RESOLVE_INITIATIVE_TIE':
        return null
      default:
        return `${pendingDecision.type} is not supported in this step yet.`
    }
  }

  async function handlePlayerCardCommand(commandType: 'PLAY_CARD' | 'USE_EX') {
    if (!requestedSessionCode || !session || commandPending) {
      return
    }

    if (!isStoredPlayerSessionAccess(runtimeAccess)) {
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
        : session.players[runtimeAccess.playerId]?.exCard ?? null

    if (!cardId) {
      commandErrorMessage =
        commandType === 'PLAY_CARD'
          ? 'Select a hand card before issuing PLAY_CARD.'
          : 'EX card is not available for the current runtime player.'
      return
    }

    clearCommandMessages()
    commandPending = commandType
    commandDraft = {
      ...commandDraft,
      selectedCommandType: commandType,
      selectedPlayerId: runtimeAccess.playerId,
    }

    try {
      const response = await executeSessionCommand(
        requestedSessionCode,
        {
          type: commandType,
          expectedVersion: session.version,
          playerId: runtimeAccess.playerId,
          cardId,
          targets: commandDraft.selectedTargets,
        },
        {
          role: 'player',
          playerToken: runtimeAccess.playerToken,
          playerId: runtimeAccess.playerId,
        },
      )

      if (!response.accepted) {
        commandRejectedMessage =
          response.errors.length > 0
            ? response.errors.join(', ')
            : `${commandType} was rejected by the engine.`
        recentCommandEvents = response.events
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

    const unsupportedMessage = getUnsupportedPendingDecisionMessage(runtimePendingDecision)

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

    let payload: Record<string, unknown> | null = null

    switch (runtimePendingDecision.type) {
      case 'HAND_SWAP':
      case 'DISCARD_TO_HAND_LIMIT':
        if (commandDraft.selectedDiscardIds.length === 0) {
          commandErrorMessage = 'Select at least one discard card before resolving this decision.'
          return
        }
        payload = {
          ...payloadBase,
          discardIds: commandDraft.selectedDiscardIds,
        }
        break
      case 'SEARCH_PICK':
      case 'RESOLVE_SEARCH_PICK':
        if (commandDraft.selectedIds.length === 0) {
          commandErrorMessage = 'Select at least one candidate id before resolving this decision.'
          return
        }
        payload = {
          ...payloadBase,
          selectedIds: commandDraft.selectedIds,
        }
        break
      case 'RESOLVE_INITIATIVE_TIE': {
        const orderedActorKeys =
          commandDraft.orderedActorKeys.length > 0
            ? commandDraft.orderedActorKeys
            : runtimePendingDecision.actorKeys

        if (orderedActorKeys.length === 0) {
          commandErrorMessage = 'Actor order is required before resolving the initiative tie.'
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
        commandRejectedMessage =
          response.errors.length > 0
            ? response.errors.join(', ')
            : `${runtimePendingDecision.type} was rejected by the engine.`
        recentCommandEvents = response.events
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
      window.removeEventListener('popstate', handleWindowStateChange)
    }
  })

  const routeSessionCode = $derived.by(() => getRouteSessionCode())
  const requestedSessionCode = $derived.by(() => getRequestedSessionCode())
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
    if (isStoredPlayerSessionAccess(runtimeAccess)) {
      return playerViews.find((player) => player.playerId === runtimeAccess.playerId) ?? playerViews[0] ?? null
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
  const currentActorView = $derived.by(() =>
    commandGuards.currentActorPlayerId
      ? playerViews.find((player) => player.playerId === commandGuards.currentActorPlayerId) ?? null
      : null,
  )
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
          note: commandGuards.canIssueGmCommand
            ? 'Connected as the first GM-side command shell action.'
            : 'Requires GM access for the current session.',
          disabled: !commandGuards.canIssueGmCommand,
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
    <SectionFrame
      eyebrow="Combat Status"
      title="Combat Command"
      description="Battle state, command planning, and action review now use live combat and card state while command execution stays for the next step."
    >
      <div class="combat-page__status-bar">
        <div class="combat-page__status-stats">
          <StatBlock
            value={statusView.round ?? 'Pending'}
            label="Round"
            note={statusView.round !== null ? 'Current combat round' : 'Combat state not active yet'}
          />
          <StatBlock
            value={statusView.currentTurnPlayer ?? 'Unavailable'}
            label="Turn"
            note={statusView.phase ?? 'Waiting for combat phase'}
          />
          <StatBlock
            value={statusView.version}
            label="Version"
            note={`${statusView.initiativeSummary} | ${statusView.tieGroupSummary}`}
          />
        </div>

        <div class="combat-page__status-tags">
          <TagChip label={statusView.sessionCode} tone="accent" />
          <TagChip label={accessRoleLabel} tone="success" />
          <TagChip
            label={combatState ? 'Combat state live' : 'Pre-combat state'}
            tone={combatState ? 'warning' : 'muted'}
          />
        </div>
      </div>

      {#if accessNoticeMessage}
        <ContentStatePanel title="Combat access status" message={accessNoticeMessage} />
      {/if}

      {#if catalogErrorMessage}
        <ContentStatePanel title="Card archive unavailable" message={catalogErrorMessage} />
      {/if}

      {#if commandErrorMessage}
        <ContentStatePanel title="Command request failed" message={commandErrorMessage} tone="error" />
      {:else if commandRejectedMessage}
        <ContentStatePanel title="Command rejected" message={commandRejectedMessage} tone="error" />
      {:else if commandSuccessMessage}
        <ContentStatePanel title="Command accepted" message={commandSuccessMessage} />
      {/if}
    </SectionFrame>

    <div class="combat-page__main">
      <div class="combat-page__field">
        <SectionFrame
          title="Player side"
          description="Player units now derive from live player state, including zones, EX state, passives, and pending decisions."
        >
          {#if playerViews.length > 0}
            <div class="combat-page__unit-list">
              {#each playerViews as player}
                <article class="combat-page__unit-card">
                  <div class="combat-page__unit-head">
                    <div>
                      <h3>{player.playerId}</h3>
                      <p>{player.ready ? 'Ready participant' : 'Joined participant'}</p>
                    </div>
                    <TagChip label={player.stateLabel} tone={player.stateTone} />
                  </div>

                  {#each player.summaryLines as line}
                    <p>{line}</p>
                  {/each}

                  <div class="combat-page__tag-row">
                    {#if player.passives.length > 0}
                      {#each player.passives as passiveId}
                        <TagChip label={passiveId} tone="accent" />
                      {/each}
                    {:else}
                      <TagChip label="No passives" tone="muted" />
                    {/if}
                  </div>

                  <div class="combat-page__action-buttons">
                    <button
                      type="button"
                      class:selected={commandDraft.selectedPlayerId === player.playerId}
                      onclick={() => handleSelectPlayer(player.playerId)}
                    >
                      {commandDraft.selectedPlayerId === player.playerId ? 'Selected actor' : 'Select actor'}
                    </button>
                    <button
                      type="button"
                      class:selected={commandDraft.selectedTargets.some((target) => target.playerId === player.playerId)}
                      onclick={() => handleToggleTargetPlayer(player.playerId)}
                    >
                      {commandDraft.selectedTargets.some((target) => target.playerId === player.playerId)
                        ? 'Targeted player'
                        : 'Target player'}
                    </button>
                  </div>
                </article>
              {/each}
            </div>
          {:else}
            <ContentStatePanel
              title="No player roster yet"
              message="No player state is available for this session yet."
            />
          {/if}
        </SectionFrame>

        <SectionFrame
          title="Enemy side"
          description="Enemy units and summons now come from the live combat state instead of mock battlefield data."
        >
          {#if enemyViews.length > 0}
            <div class="combat-page__unit-list">
              {#each enemyViews as enemy}
                <article class="combat-page__unit-card combat-page__unit-card--enemy">
                  <div class="combat-page__unit-head">
                    <div>
                      <h3>{enemy.enemyId}</h3>
                      <p>Combat enemy</p>
                    </div>
                    <TagChip label={enemy.stateLabel} tone={enemy.stateTone} />
                  </div>

                  {#each enemy.summaryLines as line}
                    <p>{line}</p>
                  {/each}

                  <div class="combat-page__tag-row">
                    {#if enemy.statusEntries.length > 0}
                      {#each enemy.statusEntries as status}
                        <TagChip label={status} tone="warning" />
                      {/each}
                    {:else}
                      <TagChip label="No statuses" tone="muted" />
                    {/if}
                  </div>

                  <div class="combat-page__action-buttons">
                    <button
                      type="button"
                      class:selected={commandDraft.selectedTargets.some((target) => target.enemyId === enemy.enemyId)}
                      onclick={() => handleSelectEnemy(enemy.enemyId)}
                    >
                      {commandDraft.selectedTargets.some((target) => target.enemyId === enemy.enemyId)
                        ? 'Targeted enemy'
                        : 'Target enemy'}
                    </button>
                  </div>
                </article>
              {/each}
            </div>
          {:else}
            <ContentStatePanel
              title="Enemy state not active yet"
              message="Combat enemies are not present in the current session state yet."
            />
          {/if}

          {#if summonViews.length > 0}
            <div class="combat-page__summon-section">
              <strong>Summons</strong>
              <div class="combat-page__unit-list">
                {#each summonViews as summon}
                  <article class="combat-page__unit-card">
                    <div class="combat-page__unit-head">
                      <div>
                        <h3>{summon.summonId}</h3>
                        <p>{summon.owner}</p>
                      </div>
                      <TagChip label={summon.stateLabel} tone={summon.stateTone} />
                    </div>

                    {#each summon.summaryLines as line}
                      <p>{line}</p>
                    {/each}

                    <div class="combat-page__action-buttons">
                      <button
                        type="button"
                        class:selected={commandDraft.selectedTargets.some((target) => target.summonOwnerPlayerId === summon.owner && target.summonInstanceId === summon.summonId)}
                        onclick={() => handleToggleTargetSummon(summon.owner, summon.summonId)}
                      >
                        {commandDraft.selectedTargets.some((target) => target.summonOwnerPlayerId === summon.owner && target.summonInstanceId === summon.summonId)
                          ? 'Targeted summon'
                          : 'Target summon'}
                      </button>
                    </div>
                  </article>
                {/each}
              </div>
            </div>
          {/if}
        </SectionFrame>
      </div>

      <SectionFrame
        title="Command and log"
        description="The sidebar now uses live state summaries so command and event panels can replace these blocks without moving the layout."
      >
        <div class="combat-page__sidebar">
          <div class="combat-page__command-panel">
            <strong>Command foundation</strong>
            <div class="combat-page__command-list">
              {#each commandOptions as option}
                <button
                  type="button"
                  disabled={option.disabled || commandPending !== null}
                  class:selected={commandDraft.selectedCommandType === option.id}
                  onclick={() => {
                    handleSelectCommand(option.id)

                    if (
                      option.id === 'END_TURN' ||
                      option.id === 'DRAW' ||
                      option.id === 'CLEAR_RECENT_RESULTS'
                    ) {
                      void handleSimpleCommand(option.id)
                    } else if (option.id === 'PLAY_CARD' || option.id === 'USE_EX') {
                      void handlePlayerCardCommand(option.id)
                    }
                  }}
                >
                  <span>
                    {commandPending === option.id
                      ? `${option.title}...`
                      : option.title}
                  </span>
                  <small>{option.note}</small>
                </button>
              {/each}
            </div>

            <ContentStatePanel
              title="Current command guards"
              message={`Expected version ${commandGuards.expectedVersion ?? 'N/A'} | Current actor ${commandGuards.currentActorPlayerId ?? 'Unavailable'} | Runtime role ${commandGuards.role}`}
            >
              <p>Current turn matches runtime player: {commandGuards.isCurrentTurnPlayer ? 'Yes' : 'No'}</p>
              <p>Pending decision: {commandGuards.hasPendingDecision ? 'Present' : 'None'}</p>
              <p>EX available: {commandGuards.exAvailable ? 'Yes' : 'No'}</p>
              <p>Recent command events buffered: {recentCommandEvents.length}</p>
            </ContentStatePanel>

            {#if runtimePendingDecision}
              <div class="combat-page__zone-panel">
                <strong>Pending decision</strong>
                <p>Type: {runtimePendingDecision.type ?? 'Unavailable'}</p>
                <p>Reason: {runtimePendingDecision.reason ?? 'None'}</p>
                <p>Limit: {runtimePendingDecision.limit ?? 'N/A'} | Pick count: {runtimePendingDecision.pickCount ?? 'N/A'}</p>
                <p>Destination: {runtimePendingDecision.destination ?? 'N/A'} | Shuffle after pick: {runtimePendingDecision.shuffleAfterPick ? 'Yes' : 'No'}</p>
                <p>Group index: {runtimePendingDecision.groupIndex ?? 'N/A'}</p>
                <p>Actor keys: {runtimePendingDecision.actorKeys.join(', ') || 'None'}</p>

                {#if unsupportedPendingDecisionMessage}
                  <ContentStatePanel
                    title="Pending decision is read-only"
                    message={unsupportedPendingDecisionMessage}
                  />
                {:else}
                  {#if runtimePendingDecision.candidateIds.length > 0}
                    <div class="combat-page__tag-row">
                      {#each runtimePendingDecision.candidateIds as candidateId}
                        <button
                          type="button"
                          class="combat-page__inline-button"
                          class:selected={commandDraft.selectedIds.includes(candidateId)}
                          onclick={() => handleTogglePendingSelectedId(candidateId)}
                        >
                          {commandDraft.selectedIds.includes(candidateId) ? `Selected ${candidateId}` : candidateId}
                        </button>
                      {/each}
                    </div>
                  {/if}

                  {#if runtimePendingDecision.actorKeys.length > 0}
                    <div class="combat-page__tag-row">
                      {#each runtimePendingDecision.actorKeys as actorKey}
                        <button
                          type="button"
                          class="combat-page__inline-button"
                          class:selected={commandDraft.orderedActorKeys.includes(actorKey)}
                          onclick={() => handleToggleOrderedActorKey(actorKey)}
                        >
                          {commandDraft.orderedActorKeys.includes(actorKey) ? `Ordered ${actorKey}` : actorKey}
                        </button>
                      {/each}
                    </div>
                  {/if}

                  <div class="combat-page__action-buttons">
                    <button
                      type="button"
                      disabled={!commandGuards.canResolvePendingCommand || commandPending !== null}
                      onclick={() => void handlePendingDecisionCommand()}
                    >
                      {commandPending && commandDraft.selectedCommandType === runtimePendingDecision.type
                        ? 'Resolving pending decision...'
                        : 'Resolve pending decision'}
                    </button>
                  </div>
                {/if}
              </div>
            {/if}
          </div>

          <div class="combat-page__log-panel">
            <strong>Current player zones</strong>
            {#if visiblePlayerView}
              <div class="combat-page__zone-grid">
                <div class="combat-page__zone-panel">
                  <strong>Field</strong>
                  {#if visiblePlayerView.fieldCards.length > 0}
                    {#each visiblePlayerView.fieldCards as card}
                      <article class="combat-page__card-row">
                        <div>
                          <span>{card.title}</span>
                          <small>{card.subtitle}</small>
                        </div>
                        <TagChip label={card.unresolved ? 'Unresolved' : 'Field'} tone={card.unresolved ? 'warning' : 'success'} />
                      </article>
                    {/each}
                  {:else}
                    <p>No field cards are active for this player.</p>
                  {/if}
                </div>

                <div class="combat-page__zone-panel">
                  <strong>Grave and excluded</strong>
                  {#if visiblePlayerView.graveCards.length > 0}
                    {#each visiblePlayerView.graveCards as card}
                      <article
                        class="combat-page__card-row"
                        class:selected={commandDraft.selectedDiscardIds.includes(card.instanceId)}
                      >
                        <div>
                          <span>{card.title}</span>
                          <small>Grave | {card.subtitle}</small>
                        </div>
                        <div class="combat-page__tag-row">
                          <TagChip label={card.unresolved ? 'Unresolved' : 'Grave'} tone={card.unresolved ? 'warning' : 'muted'} />
                          <button type="button" class="combat-page__inline-button" onclick={() => handleToggleDiscard(card.instanceId)}>
                            {commandDraft.selectedDiscardIds.includes(card.instanceId) ? 'Unmark discard' : 'Mark discard'}
                          </button>
                        </div>
                      </article>
                    {/each}
                  {/if}
                  {#if visiblePlayerView.excludedCards.length > 0}
                    {#each visiblePlayerView.excludedCards as card}
                      <article
                        class="combat-page__card-row"
                        class:selected={commandDraft.selectedIds.includes(card.instanceId)}
                      >
                        <div>
                          <span>{card.title}</span>
                          <small>Excluded | {card.subtitle}</small>
                        </div>
                        <div class="combat-page__tag-row">
                          <TagChip label={card.unresolved ? 'Unresolved' : 'Excluded'} tone={card.unresolved ? 'warning' : 'muted'} />
                          <button type="button" class="combat-page__inline-button" onclick={() => handleToggleSelectedId(card.instanceId)}>
                            {commandDraft.selectedIds.includes(card.instanceId) ? 'Unmark' : 'Select'}
                          </button>
                        </div>
                      </article>
                    {/each}
                  {/if}
                  {#if visiblePlayerView.graveCards.length === 0 && visiblePlayerView.excludedCards.length === 0}
                    <p>No grave or excluded cards are present for this player.</p>
                  {/if}
                </div>
              </div>
            {:else}
              <ContentStatePanel
                title="Zone summary unavailable"
                message="A current player zone summary will render here once a player state is present."
              />
            {/if}
          </div>

          <div class="combat-page__log-panel">
            <strong>Recent events</strong>
            {#if eventsLoading}
              <ContentStatePanel title="Loading events" message="Restoring recent combat events for the current session." />
            {:else if eventsErrorMessage}
              <ContentStatePanel
                title="Events unavailable"
                message={eventsErrorMessage}
                tone="error"
                actionLabel="Retry events"
                onAction={() => void loadCombatEvents()}
              />
            {:else if mergedEventItems.length > 0}
              <div class="combat-page__feed-list">
                {#each mergedEventItems as event}
                  <article class="combat-page__feed-card">
                    <strong>{event.type}</strong>
                    <p>Version {event.version} | Cursor {event.cursor}</p>
                    <p>{formatSidebarTimestamp(event.timestamp)}</p>
                  </article>
                {/each}
              </div>
            {:else}
              <ContentStatePanel title="No recent events" message="No combat events have been restored for this session yet." />
            {/if}
          </div>

          <div class="combat-page__log-panel">
            <strong>Recent logs</strong>
            {#if logsLoading}
              <ContentStatePanel title="Loading logs" message="Restoring recent combat logs for the current session." />
            {:else if logsErrorMessage}
              <ContentStatePanel
                title="Logs unavailable"
                message={logsErrorMessage}
                tone="error"
                actionLabel="Retry logs"
                onAction={() => void loadCombatLogs()}
              />
            {:else if logItems.length > 0}
              <div class="combat-page__feed-list">
                {#each logItems as log}
                  <article class="combat-page__feed-card">
                    <strong>{log.type}</strong>
                    <p>{log.message}</p>
                    <p>Version {log.version} | {formatSidebarTimestamp(log.timestamp)}</p>
                  </article>
                {/each}
              </div>
            {:else}
              <ContentStatePanel title="No recent logs" message="No combat log messages have been restored for this session yet." />
            {/if}
          </div>

          <div class="combat-page__log-panel">
            <strong>Recent results</strong>
            {#if recentResultsLoading}
              <ContentStatePanel title="Loading recent results" message="Restoring the latest recent-result summary for this session." />
            {:else if recentResultsErrorMessage}
              <ContentStatePanel
                title="Recent results unavailable"
                message={recentResultsErrorMessage}
                tone="error"
                actionLabel="Retry results"
                onAction={() => void loadCombatRecentResults()}
              />
            {:else if recentResults && recentResults.recentResults.length > 0}
              <div class="combat-page__feed-list">
                {#each recentResults.recentResults as result}
                  <article class="combat-page__feed-card">
                    <strong>{result.title}</strong>
                    <p>{result.summary}</p>
                    <p>{result.type} | {result.at ?? 'Time unavailable'}</p>
                  </article>
                {/each}
              </div>
            {:else}
              <ContentStatePanel
                title="No recent results"
                message="No recent result summary is available for this session yet."
              />
            {/if}
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Hand and action bar"
      description="The bottom strip now renders the current player hand from live card instances and the card catalog while keeping the same layout."
    >
      <div class="combat-page__hand-bar">
        <div class="combat-page__hand-cards">
          {#if visiblePlayerView && visiblePlayerView.handCards.length > 0}
            {#each visiblePlayerView.handCards as card}
              <article
                class="combat-page__hand-card"
                class:selected={commandDraft.selectedCardId === card.instanceId}
              >
                <p>{card.subtitle}</p>
                <h4>{card.title}</h4>
                <span>{card.meta}</span>
                <div class="combat-page__tag-row">
                  {#each card.tags as tag}
                    <TagChip label={tag.label} tone={tag.tone} />
                  {/each}
                </div>
                <p>{card.description}</p>
                <div class="combat-page__action-buttons">
                  <button type="button" onclick={() => handleSelectHandCard(card.instanceId)}>
                    {commandDraft.selectedCardId === card.instanceId ? 'Selected card' : 'Select card'}
                  </button>
                  <button type="button" onclick={() => handleToggleSelectedId(card.instanceId)}>
                    {commandDraft.selectedIds.includes(card.instanceId) ? 'Unmark' : 'Select id'}
                  </button>
                </div>
              </article>
            {/each}
          {:else}
            <ContentStatePanel
              title="No visible hand yet"
              message={catalogLoading
                ? 'Loading the card archive before resolving live hand cards.'
                : 'Visible hand cards will render here once the current player has hand instances in the live state.'}
            />
          {/if}
        </div>

        <div class="combat-page__action-summary">
          <strong>Selected action</strong>
          <p>Command: {commandDraft.selectedCommandType ?? 'Not selected'}</p>
          <p>Expected version: {commandGuards.expectedVersion ?? 'Unavailable'}</p>
          <p>Current actor: {currentActorView?.playerId ?? commandGuards.currentActorPlayerId ?? 'Unavailable'}</p>
          <p>Selected actor: {commandDraft.selectedPlayerId ?? 'Not selected'}</p>
          <p>Selected target: {selectedEnemyView?.enemyId ?? 'Target refs below'}</p>
          <p>Selected card: {selectedCardView?.title ?? commandDraft.selectedCardId ?? 'Not selected'}</p>
          <p>Pending decision: {runtimePendingDecision?.type ?? 'None'}</p>
          <p>Selected targets: {commandDraft.selectedTargets.length} | Selected ids: {commandDraft.selectedIds.length}</p>
          <p>Ordered actor keys: {commandDraft.orderedActorKeys.join(', ') || 'None'}</p>
          <p>Target refs: {commandDraft.selectedTargets.map((target) => target.enemyId ?? target.playerId ?? target.summonInstanceId ?? 'Unknown').join(', ') || 'None'}</p>
          <p>Discard ids: {commandDraft.selectedDiscardIds.length} | Count: {commandDraft.selectedCount ?? 'N/A'}</p>
          <p>Buffered events after command: {recentCommandEvents.length}</p>
          <p>Run node: {runState?.currentNode?.name ?? 'Unavailable'} | Result pending: {runState?.resultPending ? 'Yes' : 'No'}</p>
          <label class="combat-page__field-control">
            <span>Selected count</span>
            <input
              type="number"
              min="1"
              value={commandDraft.selectedCount ?? 1}
              oninput={(event) => handleSelectedCountChange((event.currentTarget as HTMLInputElement).value)}
            />
          </label>
          <label class="combat-page__field-control">
            <span>Selected reason</span>
            <textarea
              rows="3"
              bind:value={commandDraft.selectedReason}
              placeholder="Reason for the next command or pending resolution"
            />
          </label>
          <div class="combat-page__action-buttons">
            <button type="button" disabled={!commandGuards.canIssuePlayerCommand && !commandGuards.canIssueGmCommand}>
              Command execution pending
            </button>
            <button type="button" disabled>Payload build pending</button>
          </div>
        </div>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .combat-page,
  .combat-page__field,
  .combat-page__sidebar,
  .combat-page__unit-list,
  .combat-page__hand-bar,
  .combat-page__action-summary,
  .combat-page__zone-grid,
  .combat-page__zone-panel,
  .combat-page__summon-section,
  .combat-page__field-control,
  .combat-page__feed-list {
    display: grid;
    gap: 1.5rem;
  }

  .combat-page__status-bar {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .combat-page__status-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
    flex: 1 1 38rem;
  }

  .combat-page__status-tags,
  .combat-page__action-buttons,
  .combat-page__tag-row {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .combat-page__main {
    display: grid;
    grid-template-columns: minmax(0, 1.35fr) minmax(20rem, 0.65fr);
    gap: 1.5rem;
    align-items: start;
  }

  .combat-page__unit-card,
  .combat-page__hand-card,
  .combat-page__zone-panel,
  .combat-page__card-row,
  .combat-page__field-control input,
  .combat-page__field-control textarea,
  .combat-page__inline-button,
  .combat-page__feed-card {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
  }

  .combat-page__unit-card,
  .combat-page__hand-card,
  .combat-page__zone-panel {
    display: grid;
    gap: 0.75rem;
  }

  .combat-page__unit-card--enemy {
    border-color: rgba(199, 167, 125, 0.28);
  }

  .combat-page__unit-head,
  .combat-page__card-row {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: flex-start;
  }

  .combat-page__unit-head h3,
  .combat-page__unit-head p,
  .combat-page__unit-card > p,
  .combat-page__action-summary p,
  .combat-page__hand-card p,
  .combat-page__hand-card h4,
  .combat-page__hand-card span,
  .combat-page__zone-panel p,
  .combat-page__card-row span,
  .combat-page__card-row small {
    margin: 0;
  }

  .combat-page__unit-head h3,
  .combat-page__hand-card h4 {
    font-family: var(--font-display);
    font-size: 1.1rem;
  }

  .combat-page__unit-head p,
  .combat-page__unit-card > p,
  .combat-page__action-summary p,
  .combat-page__hand-card p,
  .combat-page__hand-card span,
  .combat-page__zone-panel p,
  .combat-page__card-row small,
  .combat-page__feed-card p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .combat-page__feed-card {
    display: grid;
    gap: 0.5rem;
  }

  .combat-page__feed-card strong,
  .combat-page__feed-card p {
    margin: 0;
  }

  .combat-page__command-panel,
  .combat-page__log-panel {
    display: grid;
    gap: 1rem;
  }

  .combat-page__command-panel strong,
  .combat-page__log-panel strong,
  .combat-page__action-summary strong,
  .combat-page__zone-panel strong,
  .combat-page__summon-section > strong,
  .combat-page__field-control span {
    font-size: 0.82rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .combat-page__hand-bar {
    grid-template-columns: minmax(0, 1.3fr) minmax(19rem, 0.7fr);
    align-items: start;
  }

  .combat-page__hand-cards {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .combat-page__nav-link,
  .combat-page__action-buttons button,
  .combat-page__inline-button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .combat-page__field-control {
    gap: 0.5rem;
  }

  .combat-page__field-control input,
  .combat-page__field-control textarea {
    width: 100%;
    color: var(--color-text);
    font: inherit;
  }

  .combat-page :global(.selected) {
    box-shadow: inset 0 0 0 1px rgba(226, 193, 155, 0.48);
  }

  .combat-page__nav-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  @media (max-width: 1080px) {
    .combat-page__main,
    .combat-page__hand-bar {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 960px) {
    .combat-page__status-stats,
    .combat-page__hand-cards,
    .combat-page__zone-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
