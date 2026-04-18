<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findCombatAction,
    type CombatActionId,
    type CombatCardDto,
    type CombatPendingActionMetadataDto,
    type CombatRequirementViewDto,
    type CombatScreenAction,
    type CombatScreenActionResponse,
    type CombatScreenResponse,
  } from '../lib/api/screenTypes'
  import type { PendingDecisionDto, RunRecentResultDto, TargetRefDto } from '../lib/api/sessionTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import BattlefieldPanel from '../lib/components/combat/BattlefieldPanel.svelte'
  import CombatHeader from '../lib/components/combat/CombatHeader.svelte'
  import {
    buildCombatInspectorViewModel,
    resolveCombatInspectorTarget,
  } from '../lib/components/combat/combatInspector'
  import CombatLayout from '../lib/components/combat/CombatLayout.svelte'
  import CombatSidebar from '../lib/components/combat/CombatSidebar.svelte'
  import HandBar from '../lib/components/combat/HandBar.svelte'
  import type {
    CombatInspectorEntityReference,
    CombatPresentationState,
    CombatEnemyViewModel,
    CombatFeedEntry,
    CombatPlayerViewModel,
    CombatRecentResultEntry,
    CombatSidebarTab,
    CombatStatusViewModel,
    CombatSummonViewModel,
    CombatTag,
    CombatTone,
    CommandOptionViewModel,
    ResolvedCombatCardViewModel,
  } from '../lib/components/combat/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import { pathBuilders } from '../lib/navigation'
  import { readStoredSessionAccess, type StoredSessionAccess } from '../lib/session/access'
  import {
    reconcileCombatLocalSelectionState,
    resolveCombatScreenRefreshPlan,
  } from '../lib/session/combatScreenRefresh.js'
  import { startTimedPolling, type TimedPollingHandle } from '../lib/session/liveSessionPolling'
  import { readRequestedSessionCodeFromAccessOrHandoff, readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'

  const POLLING_INTERVAL_MS = 4000
  const COMBAT_SIDEBAR_EVENT_LIMIT = 12
  type CombatRefreshReason =
    | 'initial-load'
    | 'retry-load'
    | 'route-change'
    | 'polling'
    | 'action-success'
    | 'action-failure'

  /**
   * Combat page boundary:
   * - server screen owns card/status/sidebar/action metadata and command/action results
   * - frontend keeps only local selection and transient presentation state
   * - polling and action follow-up both re-enter through the same screen refresh path
   */

  let loading = $state(true)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let refreshErrorMessage = $state<string | null>(null)
  let invalidAccessMessage = $state<string | null>(null)
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let screen = $state<CombatScreenResponse | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let pendingActionId = $state<CombatActionId | null>(null)
  let selectedActionId = $state<CombatActionId | null>(null)
  let selectedPlayerId = $state<string | null>(null)
  let selectedCardId = $state<string | null>(null)
  let selectedTargetKeys = $state<string[]>([])
  let selectedDiscardIds = $state<string[]>([])
  let selectedFieldIds = $state<string[]>([])
  let selectedPendingIds = $state<string[]>([])
  let orderedActorKeys = $state<string[]>([])
  let selectedCount = $state<number | null>(1)
  let selectedReason = $state('')
  let activeSidebarTab = $state<CombatSidebarTab>('command')
  let headerExpanded = $state(true)
  let hoveredEntity = $state<CombatInspectorEntityReference | null>(null)
  let pinnedEntity = $state<CombatInspectorEntityReference | null>(null)
  let hoveredHandCard = $state<string | null>(null)
  let pinnedHandCard = $state<string | null>(null)
  let handExpanded = $state(true)
  let requestSequence = 0
  let pollingHandle: TimedPollingHandle | null = null
  type CombatLocalSelectionState = ReturnType<typeof readLocalSelectionState>

  const combatPresentationState = $derived<CombatPresentationState>({
    activeSidebarTab,
    headerExpanded,
    hoveredEntity,
    pinnedEntity,
    hoveredHandCard,
    pinnedHandCard,
    handExpanded,
  })

  function normalizeTone(value: string | null | undefined): CombatTone {
    switch (value) {
      case 'accent':
      case 'muted':
      case 'success':
      case 'warning':
        return value
      default:
        return 'accent'
    }
  }

  function normalizeOptionalText(value: string | null | undefined) {
    const normalized = value?.trim()
    return normalized ? normalized : null
  }

  function normalizePositiveInteger(value: string) {
    const parsed = Number(value)
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null
  }

  function getRouteSessionCode() {
    return readSessionCodeFromRoute('combat')
  }

  function getRequestedSessionCode(nextAccess: StoredSessionAccess | null) {
    return readRequestedSessionCodeFromAccessOrHandoff({
      pageKey: 'combat',
      storedAccess: nextAccess,
      preferStoredAccess: false,
    }).code
  }

  function getInvalidCombatAccessMessage(nextCode: string | null) {
    if (!nextCode) {
      return 'No session code is available in the combat route or session handoff yet.'
    }

    return null
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') {
      return
    }

    window.history[replace ? 'replaceState' : 'pushState']({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function stopPolling() {
    pollingHandle?.stop()
    pollingHandle = null
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function setActiveSidebarTab(nextTab: CombatSidebarTab) {
    activeSidebarTab = nextTab
  }

  function toggleHeaderExpanded() {
    headerExpanded = !headerExpanded
  }

  function readLocalSelectionState() {
    return {
      selectedActionId,
      selectedPlayerId,
      selectedCardId,
      selectedTargetKeys: [...selectedTargetKeys],
      selectedDiscardIds: [...selectedDiscardIds],
      selectedFieldIds: [...selectedFieldIds],
      selectedPendingIds: [...selectedPendingIds],
      orderedActorKeys: [...orderedActorKeys],
      selectedCount,
      selectedReason,
    }
  }

  function writeLocalSelectionState(nextState: CombatLocalSelectionState) {
    selectedActionId = nextState.selectedActionId
    selectedPlayerId = nextState.selectedPlayerId
    selectedCardId = nextState.selectedCardId
    selectedTargetKeys = [...nextState.selectedTargetKeys]
    selectedDiscardIds = [...nextState.selectedDiscardIds]
    selectedFieldIds = [...nextState.selectedFieldIds]
    selectedPendingIds = [...nextState.selectedPendingIds]
    orderedActorKeys = [...nextState.orderedActorKeys]
    selectedCount = nextState.selectedCount
    selectedReason = nextState.selectedReason
  }

  function toCardView(card: CombatCardDto | null | undefined): ResolvedCombatCardViewModel | null {
    if (!card) {
      return null
    }

    return {
      instanceId: card.instanceId,
      defId: card.defId,
      title: card.title,
      subtitle: card.subtitle,
      meta: card.meta ?? '',
      description: card.meta ?? card.subtitle,
      unresolved: card.unresolved,
      tags: card.tags.map((tag) => ({
        label: tag.label,
        tone: normalizeTone(tag.tone),
      })),
    }
  }

  function toMetricValue(value: string | number | boolean | null) {
    if (typeof value === 'boolean') {
      return value ? 'Yes' : 'No'
    }

    return value ?? 'N/A'
  }

  function toPlayerView(player: CombatScreenResponse['actors']['players'][number]): CombatPlayerViewModel {
    return {
      playerId: player.playerId,
      ready: player.ready,
      stateLabel: player.stateLabel,
      stateTone: normalizeTone(player.stateTone),
      metrics: player.metrics.map((metric) => ({
        label: metric.label,
        value: toMetricValue(metric.value),
        note: metric.note ?? '',
      })),
      summaryLines: player.summaryLines,
      statusTags: player.statusTags.map((tag) => ({
        label: tag.label,
        tone: normalizeTone(tag.tone),
      })),
      passives: player.passives,
      handCards: player.handCards.map((card) => toCardView(card)).filter(Boolean) as ResolvedCombatCardViewModel[],
      fieldCards: player.fieldCards.map((card) => toCardView(card)).filter(Boolean) as ResolvedCombatCardViewModel[],
      graveCards: player.graveCards.map((card) => toCardView(card)).filter(Boolean) as ResolvedCombatCardViewModel[],
      excludedCards: player.excludedCards.map((card) => toCardView(card)).filter(Boolean) as ResolvedCombatCardViewModel[],
    }
  }

  function toEnemyView(enemy: CombatScreenResponse['actors']['enemies'][number]): CombatEnemyViewModel {
    return {
      enemyId: enemy.enemyId,
      stateLabel: enemy.stateLabel,
      stateTone: normalizeTone(enemy.stateTone),
      metrics: enemy.metrics.map((metric) => ({
        label: metric.label,
        value: toMetricValue(metric.value),
        note: metric.note ?? '',
      })),
      summaryLines: enemy.summaryLines,
      statusEntries: enemy.statusEntries,
    }
  }

  function toSummonView(summon: CombatScreenResponse['actors']['summons'][number]): CombatSummonViewModel {
    return {
      summonId: summon.summonId,
      owner: summon.owner,
      stateLabel: summon.stateLabel,
      stateTone: normalizeTone(summon.stateTone),
      metrics: summon.metrics.map((metric) => ({
        label: metric.label,
        value: toMetricValue(metric.value),
        note: metric.note ?? '',
      })),
      summaryLines: summon.summaryLines,
    }
  }

  function toRequirementView(requirement: CombatRequirementViewDto | null | undefined) {
    if (!requirement) {
      return null
    }

    return {
      sourceLabel: requirement.sourceLabel,
      targetSummary: requirement.targetSummary,
      discardSummary: requirement.discardSummary,
      fieldSelectionSummary: requirement.selectedIdsSummary,
      choiceSummary: requirement.choiceSummary,
    }
  }

  function targetKeyForPlayer(playerId: string) {
    return `player:${playerId}`
  }

  function targetKeyForEnemy(enemyId: string) {
    return `enemy:${enemyId}`
  }

  function targetKeyForSummon(owner: string, summonId: string) {
    return `summon:${owner}:${summonId}`
  }

  function buildTargetRefs(targetKeys: readonly string[]): TargetRefDto[] {
    return targetKeys
      .map((key) => {
        if (key.startsWith('player:')) {
          return {
            playerId: key.slice('player:'.length),
          }
        }

        if (key.startsWith('enemy:')) {
          return {
            enemyId: key.slice('enemy:'.length),
          }
        }

        if (key.startsWith('summon:')) {
          const [, owner, summonId] = key.split(':')
          if (!owner || !summonId) {
            return null
          }

          return {
            summonOwnerPlayerId: owner,
            summonInstanceId: summonId,
          }
        }

        return null
      })
      .filter(Boolean) as TargetRefDto[]
  }

  function toggleIdentifier(values: readonly string[], value: string) {
    return values.includes(value) ? values.filter((entry) => entry !== value) : [...values, value]
  }

  function findSelectedPlayCardSource(screenModel: CombatScreenResponse | null, instanceId: string | null) {
    if (!screenModel || !instanceId) {
      return null
    }

    const action = findCombatAction(screenModel, 'combat.playCard')
    const metadata = action?.metadata
    if (!metadata || metadata.kind !== 'playCard') {
      return null
    }

    return metadata.sourceOptions.find((option) => option.instanceId === instanceId) ?? null
  }

  function getPendingMetadata(screenModel: CombatScreenResponse | null) {
    const action = screenModel ? findCombatAction(screenModel, 'combat.resolvePending') : null
    const metadata = action?.metadata
    if (!metadata || metadata.kind !== 'pendingDecision') {
      return null
    }

    return metadata
  }

  function applyScreen(nextScreen: CombatScreenResponse, reason: CombatRefreshReason) {
    const previousScreen = screen
    screen = nextScreen
    syncSessionSelectionHandoff(nextScreen.sessionCode)
    writeLocalSelectionState(
      reconcileCombatLocalSelectionState(nextScreen, readLocalSelectionState(), {
        reason,
        previousScreen,
      }) as CombatLocalSelectionState,
    )

    if (!getRouteSessionCode() && nextScreen.sessionCode) {
      navigateTo(pathBuilders.combat(nextScreen.sessionCode), true)
    }
  }

  async function requestCombatScreen(reason: CombatRefreshReason) {
    const plan = resolveCombatScreenRefreshPlan(reason)
    const nextAccess = readStoredSessionAccess()
    const nextRequestedCode = getRequestedSessionCode(nextAccess)
    const nextInvalidAccessMessage = getInvalidCombatAccessMessage(nextRequestedCode)

    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage

    if (!nextRequestedCode || nextInvalidAccessMessage) {
      return {
        kind: 'invalid-access' as const,
      }
    }

    const query: Record<string, number> = {
      eventLimit: COMBAT_SIDEBAR_EVENT_LIMIT,
    }

    if (plan.useAfterVersion && screen?.version != null) {
      query.afterVersion = screen.version
    }

    const response = await getScreen<CombatScreenResponse>('Combat', { code: nextRequestedCode }, {
      query,
    })

    if (plan.useAfterVersion && response.changed === false) {
      if (reason === 'action-success') {
        return {
          kind: 'screen' as const,
          screen: await getScreen<CombatScreenResponse>('Combat', { code: nextRequestedCode }, {
            query: {
              eventLimit: COMBAT_SIDEBAR_EVENT_LIMIT,
            },
          }),
        }
      }

      return {
        kind: 'unchanged' as const,
      }
    }

    return {
      kind: 'screen' as const,
      screen: response,
    }
  }

  async function refreshCombatScreen(reason: CombatRefreshReason) {
    const requestId = ++requestSequence
    const plan = resolveCombatScreenRefreshPlan(reason)

    if (plan.showLoading) {
      loading = true
      notFound = false
      errorMessage = null
      refreshErrorMessage = null
      if (plan.clearActionFeedback) {
        clearActionFeedback()
      }
    }

    try {
      const result = await requestCombatScreen(reason)

      if (requestId !== requestSequence) {
        return
      }

      if (result.kind === 'invalid-access') {
        stopPolling()
        screen = null
        notFound = false
        refreshErrorMessage = null
        if (plan.showLoading) {
          loading = false
        }
        return
      }

      if (result.kind === 'unchanged') {
        refreshErrorMessage = null
        return
      }

      notFound = false
      errorMessage = null
      refreshErrorMessage = null
      applyScreen(result.screen, reason)
      if (reason !== 'polling') {
        startPolling()
      }
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (reason === 'polling' && error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        stopPolling()
      }

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFound = true
        screen = null
        refreshErrorMessage = null
      } else {
        const message = getApiErrorMessage(error, 'Unable to restore the current combat screen.')
        if (plan.showLoading || !screen) {
          errorMessage = message
        } else {
          refreshErrorMessage = message
        }
      }
    } finally {
      if (requestId === requestSequence && plan.showLoading) {
        loading = false
      }
    }
  }

  function startPolling() {
    stopPolling()

    if (typeof window === 'undefined' || !screen) {
      return
    }

    pollingHandle = startTimedPolling({
      intervalMs: POLLING_INTERVAL_MS,
      onPoll: async () => {
        if (pendingActionId || !screen) {
          return
        }

        await refreshCombatScreen('polling')
      },
      onError: (error) => {
        refreshErrorMessage = getApiErrorMessage(error, 'Unable to refresh the current combat screen.')
      },
    })
  }

  function getAccessRoleLabel(screenModel: CombatScreenResponse | null) {
    if (!screenModel) {
      return 'Read-only shell'
    }

    if (screenModel.access.role === 'gm') {
      return 'GM access'
    }

    if (screenModel.access.role === 'player' && screenModel.access.runtimePlayerId) {
      return `Player ${screenModel.access.runtimePlayerId}`
    }

    return 'Read-only shell'
  }

  function getCurrentTurnStateLabel(screenModel: CombatScreenResponse | null) {
    const kind = screenModel?.status.currentActor?.kind

    if (kind === 'enemy') {
      return 'Enemy acting'
    }

    if (kind === 'player') {
      return 'Player acting'
    }

    return 'Turn pending'
  }

  function getRequirementLocalBlock(requirement: CombatRequirementViewDto | null | undefined) {
    if (!requirement) {
      return null
    }

    if (requirement.unsupportedReason) {
      return requirement.unsupportedReason
    }

    if (requirement.targetRule?.requiredSelection && selectedTargetKeys.length === 0) {
      return requirement.targetSummary
    }

    if (requirement.discardRequirement) {
      const expectedCount = requirement.discardRequirement.count
      if (selectedDiscardIds.length < expectedCount) {
        return requirement.discardSummary
      }
      if (selectedDiscardIds.length > expectedCount) {
        return `Select only ${expectedCount} discard card${expectedCount === 1 ? '' : 's'}.`
      }
    }

    if (requirement.selectedIdsRequirement) {
      const { minSelections, maxSelections } = requirement.selectedIdsRequirement
      if (selectedFieldIds.length < minSelections) {
        return requirement.selectedIdsSummary
      }
      if (selectedFieldIds.length > maxSelections) {
        return `Select at most ${maxSelections} field card id${maxSelections === 1 ? '' : 's'}.`
      }
    }

    if (requirement.pendingChoiceSchema) {
      return requirement.choiceSummary
    }

    return null
  }

  function getPendingLocalBlock(metadata: CombatPendingActionMetadataDto | null) {
    if (!metadata) {
      return null
    }

    if (metadata.unsupportedReason) {
      return metadata.unsupportedReason
    }

    const schema = metadata.schema
    if (!schema) {
      return metadata.blocked ? 'Pending decision schema is unavailable.' : null
    }

    switch (schema.type) {
      case 'DISCARD_TO_HAND_LIMIT':
        if ((schema.discardCount ?? 0) !== selectedDiscardIds.length) {
          return `Select ${schema.discardCount ?? 0} discard card${schema.discardCount === 1 ? '' : 's'} to resolve the pending decision.`
        }
        return null
      case 'SEARCH_PICK':
        if ((schema.pickCount ?? 0) !== selectedPendingIds.length) {
          return `Select ${schema.pickCount ?? 0} candidate id${schema.pickCount === 1 ? '' : 's'} to resolve the pending decision.`
        }
        return null
      case 'INITIATIVE_TIE_ORDER':
        if ((schema.actorKeys?.length ?? 0) !== orderedActorKeys.length) {
          return 'Order all tie-group actor keys before resolving the pending decision.'
        }
        return null
      default:
        return null
    }
  }

  function getActionPresentationBlock(action: CombatScreenAction | null) {
    if (!action) {
      return 'Combat action is unavailable.'
    }

    if (!action.enabled) {
      return action.disabledReason?.userMessage ?? `${action.label} is currently disabled.`
    }

    if (!action.metadata) {
      return null
    }

    if (action.metadata.kind === 'playCard') {
      if (!selectedCardId) {
        return 'Select a hand card first.'
      }

      const selectedSource = action.metadata.sourceOptions.find((option) => option.instanceId === selectedCardId)
      if (!selectedSource) {
        return 'Selected hand card is no longer available.'
      }

      if (!selectedSource.supported) {
        return selectedSource.unsupportedReason ?? 'Selected card is not supported in this combat step yet.'
      }

      return getRequirementLocalBlock(selectedSource.requirementView)
    }

    if (action.metadata.kind === 'useEx') {
      if (!action.metadata.supported) {
        return action.metadata.unsupportedReason ?? 'EX action is not supported in this combat step yet.'
      }

      return getRequirementLocalBlock(action.metadata.requirementView)
    }

    if (action.metadata.kind === 'pendingDecision') {
      return getPendingLocalBlock(action.metadata)
    }

    return null
  }

  function getPendingDecisionView(metadata: CombatPendingActionMetadataDto | null): PendingDecisionDto | null {
    if (!metadata?.pendingDecisionType) {
      return null
    }

    const schema = metadata.schema

    return {
      type: metadata.pendingDecisionType,
      reason: schema?.reason ?? null,
      limit: schema?.discardCount ?? null,
      pickCount: schema?.pickCount ?? null,
      candidateIds: schema?.candidateIds ?? [],
      destination: schema?.destination ?? null,
      shuffleAfterPick: schema?.shuffleAfterPick ?? null,
      groupIndex: schema?.groupIndex ?? null,
      actorKeys: schema?.actorKeys ?? [],
    }
  }

  function buildActionBody(action: CombatScreenAction) {
    switch (action.id) {
      case 'combat.draw': {
        const basePayload = buildScreenActionPayload(action, {})
        return {
          ...basePayload,
          count: selectedCount ?? 1,
          reason: normalizeOptionalText(selectedReason),
        }
      }
      case 'combat.endTurn': {
        const basePayload = buildScreenActionPayload(action, {})
        return {
          ...basePayload,
          reason: normalizeOptionalText(selectedReason),
        }
      }
      case 'combat.clearRecentResults': {
        const basePayload = buildScreenActionPayload(action, {})
        return {
          ...basePayload,
          reason: normalizeOptionalText(selectedReason),
        }
      }
      case 'combat.playCard': {
        const basePayload = buildScreenActionPayload(action, {})
        return {
          ...basePayload,
          cardId: selectedCardId ?? '',
          discardIds: selectedDiscardIds,
          selectedIds: selectedFieldIds,
          targets: buildTargetRefs(selectedTargetKeys),
          reason: normalizeOptionalText(selectedReason),
        }
      }
      case 'combat.useEx': {
        const basePayload = buildScreenActionPayload(action, {})
        return {
          ...basePayload,
          targets: buildTargetRefs(selectedTargetKeys),
          reason: normalizeOptionalText(selectedReason),
        }
      }
      case 'combat.resolvePending': {
        const basePayload = buildScreenActionPayload(action, {})
        const metadata = action.metadata
        const schema = metadata && metadata.kind === 'pendingDecision' ? metadata.schema : null
        const selectedIdsField = schema?.selectedIdsField
        return {
          ...basePayload,
          discardIds: selectedIdsField === 'discardIds' ? selectedDiscardIds : [],
          selectedIds: selectedIdsField === 'selectedIds' ? selectedPendingIds : [],
          orderedActorKeys: selectedIdsField === 'orderedActorKeys' ? orderedActorKeys : [],
          tieGroupIndex: schema?.groupIndex ?? null,
          reason: normalizeOptionalText(selectedReason),
        }
      }
    }
  }

  async function executeAction(actionId: CombatActionId) {
    if (!screen) {
      return
    }

    const action = findCombatAction(screen, actionId)
    const blockedMessage = getActionPresentationBlock(action)

    if (!action || blockedMessage) {
      actionErrorMessage = blockedMessage
      actionSuccessMessage = null
      return
    }

    pendingActionId = action.id
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await invokeScreenAction<CombatScreenResponse, CombatScreenActionResponse>(action, {
        body: buildActionBody(action),
      })

      if (response.latestScreen) {
        applyScreen(response.latestScreen, response.success ? 'action-success' : 'action-failure')
      } else if (response.latestVersion && screen.version !== response.latestVersion) {
        await refreshCombatScreen(response.success ? 'action-success' : 'action-failure')
      }

      if (response.success) {
        actionSuccessMessage = response.message ?? `${action.label} completed.`
        actionErrorMessage = null
      } else {
        actionErrorMessage =
          response.message ??
          response.disabledReason?.userMessage ??
          `${action.label} could not be completed.`
        actionSuccessMessage = null
      }
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, `Unable to execute ${action.label}.`)
      actionSuccessMessage = null
    } finally {
      pendingActionId = null
    }
  }

  function handleCommandButtonClick(actionId: string) {
    if (!screen) {
      return
    }

    const normalizedActionId = actionId as CombatActionId
    selectedActionId = normalizedActionId

    if (
      normalizedActionId === 'combat.draw' ||
      normalizedActionId === 'combat.endTurn' ||
      normalizedActionId === 'combat.clearRecentResults'
    ) {
      void executeAction(normalizedActionId)
      return
    }

    if (normalizedActionId === 'combat.useEx' && !getActionPresentationBlock(findCombatAction(screen, normalizedActionId))) {
      void executeAction(normalizedActionId)
    }
  }

  function handleSelectPlayer(playerId: string) {
    selectedPlayerId = playerId
  }

  function handleToggleTargetPlayer(playerId: string) {
    selectedTargetKeys = toggleIdentifier(selectedTargetKeys, targetKeyForPlayer(playerId))
  }

  function handleToggleTargetEnemy(enemyId: string) {
    selectedTargetKeys = toggleIdentifier(selectedTargetKeys, targetKeyForEnemy(enemyId))
  }

  function handleToggleTargetSummon(owner: string, summonId: string) {
    selectedTargetKeys = toggleIdentifier(selectedTargetKeys, targetKeyForSummon(owner, summonId))
  }

  function handleSelectHandCard(instanceId: string) {
    selectedCardId = selectedCardId === instanceId ? null : instanceId
    selectedActionId = 'combat.playCard'
  }

  function handleToggleDiscard(instanceId: string) {
    selectedDiscardIds = toggleIdentifier(selectedDiscardIds, instanceId)
  }

  function handleToggleFieldId(instanceId: string) {
    selectedFieldIds = toggleIdentifier(selectedFieldIds, instanceId)
  }

  function handleTogglePendingSelectedId(value: string) {
    selectedPendingIds = toggleIdentifier(selectedPendingIds, value)
  }

  function handleToggleOrderedActorKey(actorKey: string) {
    if (orderedActorKeys.includes(actorKey)) {
      orderedActorKeys = orderedActorKeys.filter((value) => value !== actorKey)
      return
    }

    orderedActorKeys = [...orderedActorKeys, actorKey]
  }

  function handleSelectedCountChange(value: string) {
    selectedCount = normalizePositiveInteger(value) ?? 1
  }

  function handleSelectedReasonChange(value: string) {
    selectedReason = value
  }

  function handleClearSelectedTargets() {
    selectedTargetKeys = []
  }

  function handleClearSelectionInputs() {
    selectedDiscardIds = []
    selectedFieldIds = []
    selectedPendingIds = []
    orderedActorKeys = []
  }

  function handleResolvePendingDecision() {
    selectedActionId = 'combat.resolvePending'
    void executeAction('combat.resolvePending')
  }

  function handlePopState() {
    void refreshCombatScreen('route-change')
  }

  onMount(() => {
    void refreshCombatScreen('initial-load')
    window.addEventListener('popstate', handlePopState)
  })

  onDestroy(() => {
    stopPolling()
    window.removeEventListener('popstate', handlePopState)
  })

  const requestedSessionCode = $derived.by(() => getRequestedSessionCode(runtimeAccess))
  const playerViews = $derived.by(() => (screen ? screen.actors.players.map((player) => toPlayerView(player)) : []))
  const enemyViews = $derived.by(() => (screen ? screen.actors.enemies.map((enemy) => toEnemyView(enemy)) : []))
  const summonViews = $derived.by(() => (screen ? screen.actors.summons.map((summon) => toSummonView(summon)) : []))
  const visiblePlayerView = $derived.by(() => {
    const screenModel = screen

    if (!screenModel) {
      return null
    }

    return (
      playerViews.find((player) => player.playerId === screenModel.zones.visiblePlayerId) ??
      playerViews[0] ??
      null
    )
  })
  const selectedCardView = $derived.by(() =>
    selectedCardId ? (visiblePlayerView?.handCards.find((card) => card.instanceId === selectedCardId) ?? null) : null,
  )
  const selectedPlayCardSource = $derived.by(() => findSelectedPlayCardSource(screen, selectedCardId))
  const pendingActionMetadata = $derived.by(() => getPendingMetadata(screen))
  const pendingDecision = $derived.by(() => getPendingDecisionView(pendingActionMetadata))
  const pendingLocalBlock = $derived.by(() => getPendingLocalBlock(pendingActionMetadata))
  const selectedAction = $derived.by(() =>
    screen && selectedActionId ? findCombatAction(screen, selectedActionId) : null,
  )
  const selectedActionLocalBlock = $derived.by(() => getActionPresentationBlock(selectedAction))
  const selectedRequirementView = $derived.by(() => {
    if (!selectedAction?.metadata) {
      return null
    }

    if (selectedAction.metadata.kind === 'playCard') {
      return toRequirementView(selectedPlayCardSource?.requirementView ?? null)
    }

    if (selectedAction.metadata.kind === 'useEx') {
      return toRequirementView(selectedAction.metadata.requirementView)
    }

    return null
  })
  const selectedSourceLabel = $derived.by(() => {
    if (!selectedAction?.metadata) {
      return null
    }

    if (selectedAction.metadata.kind === 'playCard') {
      return selectedPlayCardSource?.sourceCard?.title ?? selectedCardView?.title ?? null
    }

    if (selectedAction.metadata.kind === 'useEx') {
      return selectedAction.metadata.sourceCard?.title ?? null
    }

    return null
  })
  const statusView = $derived.by(() => {
    if (!screen) {
      return null
    }

    return {
      sessionCode: screen.sessionCode,
      version: screen.version,
      round: screen.status.round,
      currentTurnPlayer: screen.status.currentActor?.raw ?? null,
      phase: screen.status.phase,
      currentTurnLabel: screen.status.currentActor?.label ?? 'No active turn',
      currentTurnTone: normalizeTone(screen.status.currentActor?.tone),
      currentTurnNote: screen.status.currentActor?.note ?? 'Combat turn owner is not available in the current state.',
      turnOrderSummary: screen.status.turnOrderSummary,
      battlefieldSummary: screen.status.battlefieldSummary,
      runSummary: screen.status.runSummary,
      initiativeSummary: screen.status.currentActor?.note ?? 'Initiative summary unavailable.',
      tieGroupSummary: screen.status.tieGroupSummary ?? 'No initiative tie group is active.',
    } satisfies CombatStatusViewModel
  })
  const accessRoleLabel = $derived.by(() => getAccessRoleLabel(screen))
  const currentTurnStateLabel = $derived.by(() => getCurrentTurnStateLabel(screen))
  const currentEnemyView = $derived.by(() =>
    screen?.status.currentActor?.kind === 'enemy' && screen?.status.currentActor.id
      ? (enemyViews.find((enemy) => enemy.enemyId === screen?.status.currentActor?.id) ?? null)
      : null,
  )
  const selectedEnemyView = $derived.by(() => {
    const selectedEnemyKey = selectedTargetKeys.find((key) => key.startsWith('enemy:'))
    if (!selectedEnemyKey) {
      return null
    }

    const enemyId = selectedEnemyKey.slice('enemy:'.length)
    return enemyViews.find((enemy) => enemy.enemyId === enemyId) ?? null
  })
  const selectedTargets = $derived.by(() => buildTargetRefs(selectedTargetKeys))
  const selectedTargetLabels = $derived.by(() => {
    if (!screen) {
      return []
    }

    const labels: string[] = []
    for (const key of selectedTargetKeys) {
      if (key.startsWith('player:')) {
        const playerId = key.slice('player:'.length)
        const player = screen.actors.players.find((entry) => entry.playerId === playerId)
        labels.push(player ? `Player ${player.playerId}` : playerId)
        continue
      }

      if (key.startsWith('enemy:')) {
        const enemyId = key.slice('enemy:'.length)
        const enemy = screen.actors.enemies.find((entry) => entry.enemyId === enemyId)
        labels.push(enemy ? `Enemy ${enemy.enemyId}` : enemyId)
        continue
      }

      if (key.startsWith('summon:')) {
        const [, owner, summonId] = key.split(':')
        labels.push(owner && summonId ? `Summon ${summonId} (${owner})` : key)
      }
    }

    return labels
  })
  const activeInspectorTarget = $derived.by(() =>
    resolveCombatInspectorTarget({
      pinnedEntity,
      pinnedHandCard,
      hoveredEntity,
      hoveredHandCard,
    }),
  )
  const inspectorView = $derived.by(() =>
    activeInspectorTarget
      ? buildCombatInspectorViewModel({
          screen,
          target: activeInspectorTarget.target,
          source: activeInspectorTarget.source,
          selectedCardId,
          selectedDiscardIds,
        })
      : null,
  )
  const commandOptions = $derived.by(() => {
    if (!screen) {
      return [] as CommandOptionViewModel[]
    }

    return screen.possibleActions.map((action) => {
      const presentationBlock = getActionPresentationBlock(action)
      const metadataNote = action.metadata && 'note' in action.metadata ? action.metadata.note : null
      return {
        id: action.id,
        title: action.label,
        note: presentationBlock ?? metadataNote ?? action.disabledReason?.userMessage ?? action.label,
        disabled: Boolean(presentationBlock),
      }
    }) satisfies CommandOptionViewModel[]
  })
  const commandGuardMessage = $derived.by(() => {
    if (!screen) {
      return 'Combat access is unavailable.'
    }

    return `Expected version ${screen.access.expectedVersion} | Runtime role ${screen.access.role} | Current actor ${screen.status.currentActor?.label ?? 'Unavailable'}`
  })
  const eventFeedEntries = $derived.by(() => (screen?.sidebar.events ?? []) satisfies CombatFeedEntry[])
  const logFeedEntries = $derived.by(() => (screen?.sidebar.logs ?? []) satisfies CombatFeedEntry[])
  const recentResultEntries = $derived.by(
    () => (screen?.sidebar.recentResults ?? []) satisfies CombatRecentResultEntry[],
  )
  const latestRecentResult = $derived.by(() => {
    const latest = screen?.sidebar.recentResults[0]
    if (!latest) {
      return null
    }

    return {
      id: `${screen?.sessionCode ?? 'combat'}:${latest.title}`,
      type: 'Combat',
      title: latest.title,
      summary: latest.summary,
      detail: null,
      source: null,
      at: latest.meta,
    } satisfies RunRecentResultDto
  })
  const accessNoticeMessage = $derived.by(() =>
    screen?.uiNotices.length ? screen.uiNotices.join(' ') : null,
  )
  const pendingCandidateIds = $derived.by(() => selectedPendingIds)
  const canResolvePendingCommand = $derived.by(() => {
    const action = screen ? findCombatAction(screen, 'combat.resolvePending') : null
    return Boolean(action?.enabled) && !pendingLocalBlock
  })
</script>

<div class="combat-page">
  {#if loading}
    <SectionFrame
      eyebrow="Combat Status"
      title="Loading combat screen"
      description="Restoring the current combat screen model from the Screen API."
    >
      <ContentStatePanel
        title="Loading combat"
        message="Fetching the current combat screen by session code."
      />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame
      eyebrow="Combat Access"
      title="Combat screen is unavailable"
      description="This page needs a session code first, then it can restore the combat screen model."
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
      description="The requested combat code did not resolve to a live combat screen."
    >
      <ContentStatePanel
        title="Combat session not found"
        message="Check the current session code and reopen the combat route."
        tone="error"
      >
        <p>Requested code: {requestedSessionCode ?? 'Unavailable'}</p>
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
      title="Combat screen could not be loaded"
      description="The session code was valid, but the combat screen could not be restored."
    >
      <ContentStatePanel
        title="Unable to load combat screen"
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={() => void refreshCombatScreen('retry-load')}
      />
      <div class="combat-page__action-buttons">
        <a class="combat-page__nav-link" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if screen && statusView}
    <CombatLayout>
      {#snippet header()}
        <CombatHeader
          {statusView}
          {accessRoleLabel}
          headerExpanded={combatPresentationState.headerExpanded}
          combatStateLive={screen?.access.guards.hasCombatState ?? false}
          currentTurnStateLabel={currentTurnStateLabel}
          recentResultsLoading={false}
          recentResultsErrorMessage={null}
          {latestRecentResult}
          {accessNoticeMessage}
          catalogErrorMessage={null}
          commandErrorMessage={actionErrorMessage ?? refreshErrorMessage ?? selectedActionLocalBlock}
          commandRejectedMessage={null}
          commandSuccessMessage={actionSuccessMessage}
          onToggleExpanded={toggleHeaderExpanded}
        />
      {/snippet}

      {#snippet field()}
        <BattlefieldPanel
          {playerViews}
          {enemyViews}
          {summonViews}
          currentTurnPlayerId={screen?.status.currentActor?.kind === 'player' ? screen.status.currentActor.id : null}
          currentEnemyId={currentEnemyView?.enemyId ?? null}
          visiblePlayerId={visiblePlayerView?.playerId ?? null}
          {selectedPlayerId}
          {selectedTargets}
          onSelectPlayer={handleSelectPlayer}
          onToggleTargetPlayer={handleToggleTargetPlayer}
          onToggleTargetEnemy={handleToggleTargetEnemy}
          onToggleTargetSummon={handleToggleTargetSummon}
        />
      {/snippet}

      {#snippet sidebar()}
        <CombatSidebar
          activeTab={combatPresentationState.activeSidebarTab}
          {commandOptions}
          commandPending={pendingActionId}
          selectedCommandType={selectedActionId}
          {commandGuardMessage}
          isCurrentTurnPlayer={screen?.access.guards.isCurrentTurnPlayer ?? false}
          hasPendingDecision={screen?.access.guards.hasPendingDecision ?? false}
          exAvailable={screen?.access.guards.exAvailable ?? false}
          recentCommandEventCount={0}
          requirementView={selectedRequirementView}
          sourceLabel={selectedSourceLabel}
          detailLoading={false}
          detailError={null}
          {selectedTargetLabels}
          {selectedDiscardIds}
          {selectedFieldIds}
          {pendingDecision}
          unsupportedPendingDecisionMessage={pendingActionMetadata?.unsupportedReason ?? pendingLocalBlock}
          {pendingCandidateIds}
          orderedTieActorKeys={orderedActorKeys}
          {canResolvePendingCommand}
          {visiblePlayerView}
          eventEntries={eventFeedEntries}
          eventsLoading={false}
          eventsErrorMessage={null}
          logEntries={logFeedEntries}
          logsLoading={false}
          logsErrorMessage={null}
          recentResultEntries={recentResultEntries}
          recentResultsLoading={false}
          recentResultsErrorMessage={null}
          {inspectorView}
          onTabChange={setActiveSidebarTab}
          onCommandButtonClick={handleCommandButtonClick}
          onClearTargets={handleClearSelectedTargets}
          onClearSelectionInputs={handleClearSelectionInputs}
          onTogglePendingSelectedId={handleTogglePendingSelectedId}
          onToggleOrderedActorKey={handleToggleOrderedActorKey}
          onResolvePendingDecision={handleResolvePendingDecision}
          onToggleSelectedId={handleToggleFieldId}
          onRetryEvents={() => void refreshCombatScreen('action-failure')}
          onRetryLogs={() => void refreshCombatScreen('action-failure')}
          onRetryResults={() => void refreshCombatScreen('action-failure')}
        />
      {/snippet}

      {#snippet hand()}
        <HandBar
          handCards={visiblePlayerView?.handCards ?? []}
          {selectedCardId}
          {selectedDiscardIds}
          selectedCommandType={selectedActionId}
          expectedVersion={screen?.access.expectedVersion ?? null}
          currentActorLabel={statusView.currentTurnLabel}
          visibleHandOwner={visiblePlayerView?.playerId ?? null}
          selectedActor={selectedPlayerId}
          selectedEnemyId={selectedEnemyView?.enemyId ?? null}
          selectedCardLabel={selectedCardView?.title ?? selectedCardId}
          pendingDecisionType={pendingDecision?.type ?? null}
          selectedTargetCount={selectedTargetKeys.length}
          selectedIdCount={selectedFieldIds.length}
          orderedActorKeysSummary={orderedActorKeys.join(', ') || 'None'}
          targetRefSummary={selectedTargetLabels.join(', ') || 'None'}
          selectedDiscardCount={selectedDiscardIds.length}
          selectedFieldCount={selectedFieldIds.length}
          {selectedCount}
          pendingCandidateCount={selectedPendingIds.length}
          bufferedEventCount={screen?.sidebar.events.length ?? 0}
          runNodeSummary={screen?.status.runSummary ?? 'Run summary unavailable.'}
          {selectedReason}
          catalogLoading={false}
          emptyMessage="Visible hand cards will render here once the current visible player has hand instances in the combat screen."
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
