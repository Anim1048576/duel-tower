<script lang="ts">
  import { onMount } from 'svelte'
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
    CardInstanceDto,
    CombatEnemyDto,
    CombatSummonDto,
    CommandRequest,
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

  type CombatTone = 'accent' | 'muted' | 'success' | 'warning'

  type CombatMetric = {
    label: string
    value: string | number
    note: string
  }

  type CombatTag = {
    label: string
    tone?: CombatTone
  }

  type CombatActorSummary = {
    raw: string | null
    kind: 'player' | 'enemy' | 'unknown' | 'none'
    id: string | null
    label: string
    note: string
    tone: CombatTone
  }

  type CombatStatusViewModel = {
    sessionCode: string
    version: number
    round: number | null
    currentTurnPlayer: string | null
    phase: string | null
    currentTurnLabel: string
    currentTurnTone: CombatTone
    currentTurnNote: string
    turnOrderSummary: string
    battlefieldSummary: string
    runSummary: string
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
    tags: CombatTag[]
  }

  type CombatPlayTargetType =
    | 'NONE'
    | 'SELF'
    | 'ALLY_ONE'
    | 'ALLY_ALL'
    | 'ALLY_SIDE'
    | 'ENEMY_ONE'
    | 'ENEMY_ALL'
    | 'ENEMY_SIDE'
    | 'ANY_ONE'
    | string

  type CombatPlayTargetSpec = {
    target: CombatPlayTargetType
    requiredSelection: boolean
  }

  type CombatChoiceOption = {
    id: string
    label: string
    description: string | null
  }

  type CombatExtraPlayRequirement =
    | {
        type: 'discard_from_hand'
        count: number
        excludeSourceCard: boolean
        filter: string
      }
    | {
        type: 'select_field_cards'
        minSelections: number
        maxSelections: number
        scope: string
        filter: string
        excludeSourceCard: boolean
      }
    | {
        type: 'choice'
        id: string
        label: string
        minSelections: number
        maxSelections: number
        options: CombatChoiceOption[]
      }

  type CombatResolvedPlaySpec = {
    target: CombatPlayTargetSpec
    extraRequirements: CombatExtraPlayRequirement[]
  }

  type CombatCommandRequirementViewModel = {
    sourceLabel: string
    targetSummary: string
    discardSummary: string
    fieldSelectionSummary: string
    choiceSummary: string
  }

  type CombatPlayerViewModel = {
    playerId: string
    ready: boolean
    stateLabel: string
    stateTone: CombatTone
    metrics: CombatMetric[]
    summaryLines: string[]
    statusTags: CombatTag[]
    passives: string[]
    handCards: ResolvedCombatCardViewModel[]
    fieldCards: ResolvedCombatCardViewModel[]
    graveCards: ResolvedCombatCardViewModel[]
    excludedCards: ResolvedCombatCardViewModel[]
  }

  type CombatEnemyViewModel = {
    enemyId: string
    stateLabel: string
    stateTone: CombatTone
    metrics: CombatMetric[]
    summaryLines: string[]
    statusEntries: string[]
  }

  type CombatSummonViewModel = {
    summonId: string
    owner: string
    stateLabel: string
    stateTone: CombatTone
    metrics: CombatMetric[]
    summaryLines: string[]
  }

  type CommandOptionViewModel = {
    id: CombatCommandType
    title: string
    note: string
    disabled: boolean
  }

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
  let requestSequence = 0
  let sidebarSessionCode = $state<string | null>(null)
  let hadSidebarReadAccess = $state(false)

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

  async function loadCombatState() {
    const nextCode = requestedSessionCode
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidCombatAccessMessage(nextCode)
    const nextHasSidebarReadAccess = hasCombatReadAccess(nextCode, nextAccess)
    const requestId = ++requestSequence

    if (sidebarSessionCode !== nextCode) {
      invalidateCombatSidebarRequests()
      resetCombatSidebarState()
      sidebarSessionCode = nextCode
    } else if (hadSidebarReadAccess && !nextHasSidebarReadAccess) {
      invalidateCombatSidebarRequests()
      resetCombatSidebarState()
    }

    hadSidebarReadAccess = nextHasSidebarReadAccess

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

  function asRecord(value: unknown) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null
    }

    return value as Record<string, unknown>
  }

  function readString(value: unknown) {
    return typeof value === 'string' ? value.trim() : ''
  }

  function readBoolean(value: unknown, fallback = false) {
    return typeof value === 'boolean' ? value : fallback
  }

  function readInteger(value: unknown, fallback = 0) {
    return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : fallback
  }

  function normalizePlaySpec(playSpec: unknown): CombatResolvedPlaySpec {
    const playSpecRecord = asRecord(playSpec)
    const targetRecord = asRecord(playSpecRecord?.target)
    const target = readString(targetRecord?.target) || 'NONE'
    const requiredSelection = readBoolean(targetRecord?.requiredSelection)
    const extraRequirementValues = Array.isArray(playSpecRecord?.extraRequirements)
      ? playSpecRecord.extraRequirements
      : []

    const extraRequirements = extraRequirementValues
      .map((value) => {
        const record = asRecord(value)
        const type = readString(record?.type)

        if (type === 'discard_from_hand') {
          return {
            type,
            count: readInteger(record?.count, 1),
            excludeSourceCard: readBoolean(record?.excludeSourceCard, true),
            filter: readString(record?.filter) || 'ANY',
          } satisfies CombatExtraPlayRequirement
        }

        if (type === 'select_field_cards') {
          return {
            type,
            minSelections: readInteger(record?.minSelections),
            maxSelections: readInteger(record?.maxSelections),
            scope: readString(record?.scope) || 'ALL_PLAYER_FIELDS',
            filter: readString(record?.filter) || 'INSTALLED_ONLY',
            excludeSourceCard: readBoolean(record?.excludeSourceCard),
          } satisfies CombatExtraPlayRequirement
        }

        if (type === 'choice') {
          const options = Array.isArray(record?.options)
            ? record.options
                .map((optionValue) => {
                  const optionRecord = asRecord(optionValue)
                  const id = readString(optionRecord?.id)

                  if (!id) {
                    return null
                  }

                  return {
                    id,
                    label: readString(optionRecord?.label) || id,
                    description: readString(optionRecord?.description) || null,
                  } satisfies CombatChoiceOption
                })
                .filter((option): option is CombatChoiceOption => option !== null)
            : []

          return {
            type,
            id: readString(record?.id) || 'choice',
            label: readString(record?.label) || 'Choice input',
            minSelections: readInteger(record?.minSelections),
            maxSelections: readInteger(record?.maxSelections),
            options,
          } satisfies CombatExtraPlayRequirement
        }

        return null
      })
      .filter((requirement): requirement is CombatExtraPlayRequirement => requirement !== null)

    return {
      target: {
        target,
        requiredSelection,
      },
      extraRequirements,
    } satisfies CombatResolvedPlaySpec
  }

  function getCardDefIdFromInstanceId(instanceId: string | null | undefined) {
    const normalizedId = instanceId?.trim() ?? ''

    if (!normalizedId) {
      return null
    }

    return session?.cards[normalizedId]?.defId ?? null
  }

  function getSelectedDiscardIdsFromHand(player: PlayerStateDto | null) {
    const handIds = new Set(player?.hand ?? [])
    return commandDraft.selectedDiscardIds.filter((instanceId) => handIds.has(instanceId))
  }

  function getSelectedFieldIds(player: PlayerStateDto | null) {
    const fieldIds = new Set(player?.field ?? [])
    return commandDraft.selectedIds.filter((instanceId) => fieldIds.has(instanceId))
  }

  function getPendingCandidateIds(pendingDecision: PendingDecisionDto | null) {
    const candidateIds = new Set(pendingDecision?.candidateIds ?? [])
    return commandDraft.selectedIds.filter((value) => candidateIds.has(value))
  }

  function getOrderedTieActorKeys(pendingDecision: PendingDecisionDto | null) {
    const actorKeys = new Set(pendingDecision?.actorKeys ?? [])
    return commandDraft.orderedActorKeys.filter((actorKey) => actorKeys.has(actorKey))
  }

  function formatTargetSelectionLabel(target: CombatCommandDraft['selectedTargets'][number]) {
    if (target.enemyId) {
      return `Enemy ${target.enemyId}`
    }

    if (target.playerId) {
      return `Player ${target.playerId}`
    }

    if (target.summonOwnerPlayerId && target.summonInstanceId) {
      return `Summon ${target.summonInstanceId}`
    }

    return 'Unknown target'
  }

  function describeTargetRequirement(targetSpec: CombatPlayTargetSpec) {
    if (!targetSpec.requiredSelection || targetSpec.target === 'NONE') {
      return 'No manual target required'
    }

    switch (targetSpec.target) {
      case 'ENEMY_ONE':
        return 'Select exactly one enemy or summon target'
      case 'ALLY_ONE':
        return 'Select exactly one ally player or summon target'
      case 'ANY_ONE':
        return 'Select exactly one target'
      case 'SELF':
        return 'Self-targeted automatically'
      case 'ENEMY_ALL':
      case 'ENEMY_SIDE':
        return 'Enemy-side target is resolved automatically'
      case 'ALLY_ALL':
      case 'ALLY_SIDE':
        return 'Ally-side target is resolved automatically'
      default:
        return `Target rule: ${targetSpec.target}`
    }
  }

  function getPlaySpecRequirement(
    playSpec: CombatResolvedPlaySpec,
    type: CombatExtraPlayRequirement['type'],
  ) {
    return playSpec.extraRequirements.find((requirement) => requirement.type === type) ?? null
  }

  function buildCommandRequirementViewModel(
    sourceLabel: string,
    playSpec: CombatResolvedPlaySpec,
  ): CombatCommandRequirementViewModel {
    const discardRequirement = getPlaySpecRequirement(playSpec, 'discard_from_hand')
    const fieldRequirement = getPlaySpecRequirement(playSpec, 'select_field_cards')
    const choiceRequirement = getPlaySpecRequirement(playSpec, 'choice')

    return {
      sourceLabel,
      targetSummary: describeTargetRequirement(playSpec.target),
      discardSummary:
        discardRequirement?.type === 'discard_from_hand'
          ? `Select ${discardRequirement.count} hand discard${discardRequirement.count > 1 ? 's' : ''}${discardRequirement.excludeSourceCard ? ' excluding the source card' : ''}`
          : 'No extra hand discard required',
      fieldSelectionSummary:
        fieldRequirement?.type === 'select_field_cards'
          ? `Select ${fieldRequirement.minSelections}-${fieldRequirement.maxSelections} field card ids`
          : 'No extra field selection required',
      choiceSummary:
        choiceRequirement?.type === 'choice'
          ? `${choiceRequirement.label} (${choiceRequirement.options.map((option) => option.label).join(', ') || 'choice options'})`
          : 'No explicit choice requirement',
    }
  }

  function getTargetSelectionError(
    commandLabel: string,
    targetSpec: CombatPlayTargetSpec,
    selectedTargets: CombatCommandDraft['selectedTargets'],
  ) {
    if (!targetSpec.requiredSelection || targetSpec.target === 'NONE') {
      return null
    }

    if (targetSpec.target === 'SELF') {
      return null
    }

    if (targetSpec.target === 'ALLY_ALL' || targetSpec.target === 'ALLY_SIDE' || targetSpec.target === 'ENEMY_ALL' || targetSpec.target === 'ENEMY_SIDE') {
      return null
    }

    if (selectedTargets.length !== 1) {
      return `${commandLabel} requires exactly one target selection.`
    }

    const [target] = selectedTargets

    switch (targetSpec.target) {
      case 'ENEMY_ONE':
        return target.enemyId || target.summonInstanceId
          ? null
          : `${commandLabel} requires one enemy or summon target.`
      case 'ALLY_ONE':
        return target.playerId || target.summonInstanceId
          ? null
          : `${commandLabel} requires one ally player or summon target.`
      case 'ANY_ONE':
        return target.enemyId || target.playerId || target.summonInstanceId
          ? null
          : `${commandLabel} requires one target.`
      default:
        return selectedTargets.length > 0 ? null : `${commandLabel} requires a target selection.`
    }
  }

  function getPlayCardRequirementError(
    commandLabel: string,
    playSpec: CombatResolvedPlaySpec | null,
    sourceCardId: string,
    selectedTargets: CombatCommandDraft['selectedTargets'],
    selectedDiscardIds: string[],
    selectedFieldIds: string[],
  ) {
    if (!playSpec) {
      return null
    }

    const targetError = getTargetSelectionError(commandLabel, playSpec.target, selectedTargets)

    if (targetError) {
      return targetError
    }

    const discardRequirement = getPlaySpecRequirement(playSpec, 'discard_from_hand')

    if (discardRequirement?.type === 'discard_from_hand') {
      if (selectedDiscardIds.length !== discardRequirement.count) {
        return `${commandLabel} requires ${discardRequirement.count} hand discard selection${discardRequirement.count > 1 ? 's' : ''}.`
      }

      if (discardRequirement.excludeSourceCard && selectedDiscardIds.includes(sourceCardId)) {
        return 'The source card cannot be selected as an extra discard.'
      }
    }

    const fieldRequirement = getPlaySpecRequirement(playSpec, 'select_field_cards')

    if (fieldRequirement?.type === 'select_field_cards') {
      if (
        selectedFieldIds.length < fieldRequirement.minSelections ||
        selectedFieldIds.length > fieldRequirement.maxSelections
      ) {
        return `${commandLabel} requires ${fieldRequirement.minSelections}-${fieldRequirement.maxSelections} selected field ids.`
      }

      if (fieldRequirement.excludeSourceCard && selectedFieldIds.includes(sourceCardId)) {
        return 'The source card cannot be selected as a field helper id.'
      }
    }

    if (getPlaySpecRequirement(playSpec, 'choice')) {
      return `${commandLabel} has a choice-based follow-up that is not wired in this combat step yet.`
    }

    return null
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

  function formatTargetRefSummary(targets: CombatCommandDraft['selectedTargets']) {
    const labels = targets
      .map((target) => target.enemyId ?? target.playerId ?? target.summonInstanceId ?? null)
      .filter(Boolean)

    return labels.length > 0 ? labels.join(', ') : 'None'
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

    for (const item of [...bufferedItems, ...fetchedItems]) {
      const key = item.cursor || `${item.version}:${item.type}:${item.timestamp ?? ''}`

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

        return (right.cursor ?? '').localeCompare(left.cursor ?? '')
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
    }

    resetCommandDraftAfterSuccess(commandType, nextSession)
    recentCommandEvents = nextEvents
    commandSuccessMessage = `${commandType} command was accepted and the combat shell synced to the latest session state.`
    void loadCombatEvents()
    void loadCombatLogs()
    void loadCombatRecentResults()
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
    }

    recentCommandEvents = nextEvents
    commandRejectedMessage =
      normalizedErrors.length > 0 ? normalizedErrors.join(', ') : fallbackMessage

    if (sawVersionMismatch && nextSession) {
      commandRejectedMessage = `${commandRejectedMessage} Synced to the latest session state. Try again.`
    }

    void loadCombatEvents()
    void loadCombatLogs()
    void loadCombatRecentResults()
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

  function getUnsupportedCardCommandMessage() {
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
    const filteredDiscardIds = getSelectedDiscardIdsFromHand(runtimePlayer)
    const filteredSelectedIds = getSelectedFieldIds(runtimePlayer)
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

    let payload: CommandRequest | null = null

    switch (runtimePendingDecision.type) {
      case 'HAND_SWAP': {
        const discardIds = getSelectedDiscardIdsFromHand(runtimePlayerState)

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
        const discardIds = getSelectedDiscardIdsFromHand(runtimePlayerState)

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
  const selectedDiscardIdsFromHand = $derived.by(() => getSelectedDiscardIdsFromHand(runtimePlayerState))
  const selectedFieldIds = $derived.by(() => getSelectedFieldIds(runtimePlayerState))
  const pendingCandidateIds = $derived.by(() => getPendingCandidateIds(runtimePendingDecision))
  const orderedTieActorKeys = $derived.by(() => getOrderedTieActorKeys(runtimePendingDecision))
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
    <SectionFrame
      eyebrow="Combat Status"
      title="Combat Command"
      description="The combat shell now emphasizes the current turn owner, battlefield pressure, and recent combat feedback before deeper command controls."
    >
      <div class="combat-page__status-bar">
        <div class="combat-page__status-stats">
          <StatBlock
            value={statusView.round ?? 'Pending'}
            label="Round"
            note={statusView.round !== null ? 'Current combat round' : 'Combat state not active yet'}
          />
          <StatBlock
            value={statusView.currentTurnLabel}
            label="Current Turn"
            note={statusView.currentTurnNote}
          />
          <StatBlock
            value={statusView.phase ?? 'Pending'}
            label="Phase"
            note={statusView.turnOrderSummary}
          />
          <StatBlock
            value={statusView.version}
            label="Version"
            note={statusView.battlefieldSummary}
          />
        </div>

        <div class="combat-page__status-tags">
          <TagChip label={statusView.sessionCode} tone="accent" />
          <TagChip label={accessRoleLabel} tone="success" />
          <TagChip
            label={combatState ? 'Combat state live' : 'Pre-combat state'}
            tone={combatState ? 'warning' : 'muted'}
          />
          <TagChip label={currentTurnActor.kind === 'enemy' ? 'Enemy acting' : currentTurnActor.kind === 'player' ? 'Player acting' : 'Turn pending'} tone={currentTurnActor.tone} />
        </div>
      </div>

      <div class="combat-page__overview-grid">
        <article class={`combat-page__spotlight-card combat-page__spotlight-card--${statusView.currentTurnTone}`}>
          <strong>Current turn</strong>
          <h3>{statusView.currentTurnLabel}</h3>
          <p>{statusView.currentTurnNote}</p>
          <p>Turn order: {statusView.turnOrderSummary}</p>
        </article>

        <article class="combat-page__spotlight-card">
          <strong>Battlefield</strong>
          <h3>{statusView.battlefieldSummary}</h3>
          <p>{statusView.initiativeSummary} | {statusView.tieGroupSummary}</p>
          <p>{statusView.runSummary}</p>
        </article>

        <article class="combat-page__spotlight-card">
          <strong>Recent feedback</strong>
          {#if recentResultsLoading}
            <h3>Loading recent results</h3>
            <p>Restoring the latest combat-facing result summary.</p>
          {:else if recentResultsErrorMessage}
            <h3>Recent result unavailable</h3>
            <p>{recentResultsErrorMessage}</p>
          {:else if latestRecentResult}
            <h3>{latestRecentResult.title}</h3>
            <p>{latestRecentResult.summary}</p>
            <p>{latestRecentResult.type} | {latestRecentResult.at ?? 'Time unavailable'}</p>
          {:else}
            <h3>No recent result yet</h3>
            <p>The current combat flow has not produced a recent-result summary yet.</p>
          {/if}
        </article>
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
          description="Player cards highlight live zones, EX state, passives, and pending decisions. Player HP/AP is not exposed in the current player session payload."
        >
          {#if playerViews.length > 0}
            <div class="combat-page__unit-list">
              {#each playerViews as player}
                <article
                  class="combat-page__unit-card"
                  class:combat-page__unit-card--active-turn={currentTurnActor.kind === 'player' && currentTurnActor.id === player.playerId}
                >
                  <div class="combat-page__unit-head">
                    <div>
                      <h3>{player.playerId}</h3>
                      <p>{player.ready ? 'Ready participant' : 'Joined participant'} | Hand and zone state</p>
                    </div>
                    <div class="combat-page__tag-row">
                      {#if currentTurnActor.kind === 'player' && currentTurnActor.id === player.playerId}
                        <TagChip label="Current turn" tone="success" />
                      {/if}
                      {#if visiblePlayerView?.playerId === player.playerId}
                        <TagChip label="Visible hand" tone="accent" />
                      {/if}
                      <TagChip label={player.stateLabel} tone={player.stateTone} />
                    </div>
                  </div>

                  <div class="combat-page__metric-grid">
                    {#each player.metrics as metric}
                      <div class="combat-page__metric-card">
                        <strong>{metric.value}</strong>
                        <span>{metric.label}</span>
                        <p>{metric.note}</p>
                      </div>
                    {/each}
                  </div>

                  {#each player.summaryLines as line}
                    <p class="combat-page__unit-note">{line}</p>
                  {/each}

                  <div class="combat-page__tag-row">
                    {#each player.statusTags as tag}
                      <TagChip label={tag.label} tone={tag.tone} />
                    {/each}
                  </div>

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
          description="Enemy cards surface combat HP/AP and status pressure first, with summons grouped below the main enemy roster."
        >
          {#if enemyViews.length > 0}
            <div class="combat-page__unit-list">
              {#each enemyViews as enemy}
                <article
                  class="combat-page__unit-card combat-page__unit-card--enemy"
                  class:combat-page__unit-card--active-turn={currentEnemyView?.enemyId === enemy.enemyId}
                >
                  <div class="combat-page__unit-head">
                    <div>
                      <h3>{enemy.enemyId}</h3>
                      <p>Combat enemy | Live battlefield unit</p>
                    </div>
                    <div class="combat-page__tag-row">
                      {#if currentEnemyView?.enemyId === enemy.enemyId}
                        <TagChip label="Current turn" tone="warning" />
                      {/if}
                      <TagChip label={enemy.stateLabel} tone={enemy.stateTone} />
                    </div>
                  </div>

                  <div class="combat-page__metric-grid">
                    {#each enemy.metrics as metric}
                      <div class="combat-page__metric-card">
                        <strong>{metric.value}</strong>
                        <span>{metric.label}</span>
                        <p>{metric.note}</p>
                      </div>
                    {/each}
                  </div>

                  {#each enemy.summaryLines as line}
                    <p class="combat-page__unit-note">{line}</p>
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
                        <p>{summon.owner} | Support unit</p>
                      </div>
                      <TagChip label={summon.stateLabel} tone={summon.stateTone} />
                    </div>

                    <div class="combat-page__metric-grid combat-page__metric-grid--compact">
                      {#each summon.metrics as metric}
                        <div class="combat-page__metric-card">
                          <strong>{metric.value}</strong>
                          <span>{metric.label}</span>
                          <p>{metric.note}</p>
                        </div>
                      {/each}
                    </div>

                    {#each summon.summaryLines as line}
                      <p class="combat-page__unit-note">{line}</p>
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
        title="Combat context and command"
        description="The sidebar keeps pending decisions, visible zones, and combat history close to the live battlefield summary without changing the command flow."
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
              message={`Expected version ${commandGuards.expectedVersion ?? 'N/A'} | Current actor ${statusView.currentTurnLabel} | Runtime role ${commandGuards.role}`}
            >
              <p>Current turn matches runtime player: {commandGuards.isCurrentTurnPlayer ? 'Yes' : 'No'}</p>
              <p>Pending decision: {commandGuards.hasPendingDecision ? 'Present' : 'None'}</p>
              <p>EX available: {commandGuards.exAvailable ? 'Yes' : 'No'}</p>
              <p>Recent command events buffered: {recentCommandEvents.length}</p>
            </ContentStatePanel>

            <div class="combat-page__zone-panel">
              <strong>Selected command input</strong>
              <p>Command source: {selectedCommandRequirementView?.sourceLabel ?? selectedCommandSourceLabel ?? 'Select a card or EX first'}</p>
              <p>Target rule: {selectedCommandRequirementView?.targetSummary ?? 'No command-specific target rule loaded yet.'}</p>
              <p>Discard rule: {selectedCommandRequirementView?.discardSummary ?? 'No extra hand discard required'}</p>
              <p>Field selection rule: {selectedCommandRequirementView?.fieldSelectionSummary ?? 'No extra field selection required'}</p>
              <p>Choice rule: {selectedCommandRequirementView?.choiceSummary ?? 'No explicit choice requirement'}</p>
              {#if selectedCommandDetailLoading}
                <p>Loading card detail for the selected command source.</p>
              {:else if selectedCommandDetailError}
                <p>{selectedCommandDetailError}</p>
              {/if}

              <div class="combat-page__tag-row">
                {#if commandDraft.selectedTargets.length > 0}
                  {#each commandDraft.selectedTargets as target}
                    <TagChip label={formatTargetSelectionLabel(target)} tone="warning" />
                  {/each}
                {:else}
                  <TagChip label="No manual target" tone="muted" />
                {/if}
              </div>

              <div class="combat-page__tag-row">
                {#if selectedDiscardIdsFromHand.length > 0}
                  {#each selectedDiscardIdsFromHand as discardId}
                    <TagChip label={`Discard ${discardId}`} tone="accent" />
                  {/each}
                {:else}
                  <TagChip label="No discard ids" tone="muted" />
                {/if}
              </div>

              <div class="combat-page__tag-row">
                {#if selectedFieldIds.length > 0}
                  {#each selectedFieldIds as selectedId}
                    <TagChip label={`Field ${selectedId}`} tone="accent" />
                  {/each}
                {:else}
                  <TagChip label="No field ids" tone="muted" />
                {/if}
              </div>

              <div class="combat-page__action-buttons">
                <button type="button" onclick={() => handleClearSelectedTargets()}>
                  Clear targets
                </button>
                <button type="button" onclick={() => handleClearSelectionInputs()}>
                  Clear follow-up inputs
                </button>
              </div>
            </div>

            {#if runtimePendingDecision}
              <div class="combat-page__zone-panel">
                <strong>Pending decision</strong>
                <p>Type: {runtimePendingDecision.type ?? 'Unavailable'}</p>
                <p>Reason: {runtimePendingDecision.reason ?? 'None'}</p>
                <p>Limit: {runtimePendingDecision.limit ?? 'N/A'} | Pick count: {runtimePendingDecision.pickCount ?? 'N/A'}</p>
                <p>Destination: {runtimePendingDecision.destination ?? 'N/A'} | Shuffle after pick: {runtimePendingDecision.shuffleAfterPick ? 'Yes' : 'No'}</p>
                <p>Group index: {runtimePendingDecision.groupIndex ?? 'N/A'}</p>
                <p>Actor keys: {runtimePendingDecision.actorKeys.join(', ') || 'None'}</p>
                <p>Selected hand discards: {selectedDiscardIdsFromHand.length} | Selected candidate ids: {pendingCandidateIds.length} | Ordered tie actors: {orderedTieActorKeys.length}</p>

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
                      <article
                        class="combat-page__card-row"
                        class:selected={selectedFieldIds.includes(card.instanceId)}
                      >
                        <div>
                          <span>{card.title}</span>
                          <small>{card.subtitle}</small>
                        </div>
                        <div class="combat-page__tag-row">
                          <TagChip label={card.unresolved ? 'Unresolved' : 'Field'} tone={card.unresolved ? 'warning' : 'success'} />
                          <button type="button" class="combat-page__inline-button" onclick={() => handleToggleSelectedId(card.instanceId)}>
                            {selectedFieldIds.includes(card.instanceId) ? 'Unmark field id' : 'Select field id'}
                          </button>
                        </div>
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
                      <article class="combat-page__card-row">
                        <div>
                          <span>{card.title}</span>
                          <small>Grave | {card.subtitle}</small>
                        </div>
                        <TagChip label={card.unresolved ? 'Unresolved' : 'Grave'} tone={card.unresolved ? 'warning' : 'muted'} />
                      </article>
                    {/each}
                  {/if}
                  {#if visiblePlayerView.excludedCards.length > 0}
                    {#each visiblePlayerView.excludedCards as card}
                      <article class="combat-page__card-row">
                        <div>
                          <span>{card.title}</span>
                          <small>Excluded | {card.subtitle}</small>
                        </div>
                        <TagChip label={card.unresolved ? 'Unresolved' : 'Excluded'} tone={card.unresolved ? 'warning' : 'muted'} />
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
      description="The bottom strip keeps the visible hand and action context readable while command wiring stays otherwise unchanged."
    >
      <div class="combat-page__hand-bar">
        <div class="combat-page__hand-cards">
          {#if visiblePlayerView && visiblePlayerView.handCards.length > 0}
            {#each visiblePlayerView.handCards as card}
              <article
                class="combat-page__hand-card"
                class:selected={commandDraft.selectedCardId === card.instanceId || selectedDiscardIdsFromHand.includes(card.instanceId)}
              >
                <p>{card.subtitle}</p>
                <h4>{card.title}</h4>
                <span>{card.meta}</span>
                <div class="combat-page__tag-row">
                  {#each card.tags as tag}
                    <TagChip label={tag.label} tone={tag.tone} />
                  {/each}
                  {#if selectedDiscardIdsFromHand.includes(card.instanceId)}
                    <TagChip label="Discard selected" tone="warning" />
                  {/if}
                </div>
                <p>{card.description}</p>
                <div class="combat-page__action-buttons">
                  <button type="button" onclick={() => handleSelectHandCard(card.instanceId)}>
                    {commandDraft.selectedCardId === card.instanceId ? 'Selected card' : 'Select card'}
                  </button>
                  <button type="button" class:selected={selectedDiscardIdsFromHand.includes(card.instanceId)} onclick={() => handleToggleDiscard(card.instanceId)}>
                    {selectedDiscardIdsFromHand.includes(card.instanceId) ? 'Marked discard' : 'Mark discard'}
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
          <p>Current actor: {statusView.currentTurnLabel}</p>
          <p>Visible hand owner: {visiblePlayerView?.playerId ?? 'Unavailable'}</p>
          <p>Selected actor: {commandDraft.selectedPlayerId ?? 'Not selected'}</p>
          <p>Selected target: {selectedEnemyView?.enemyId ?? 'Target refs below'}</p>
          <p>Selected card: {selectedCardView?.title ?? commandDraft.selectedCardId ?? 'Not selected'}</p>
          <p>Pending decision: {runtimePendingDecision?.type ?? 'None'}</p>
          <p>Selected targets: {commandDraft.selectedTargets.length} | Selected ids: {commandDraft.selectedIds.length}</p>
          <p>Ordered actor keys: {commandDraft.orderedActorKeys.join(', ') || 'None'}</p>
          <p>Target refs: {formatTargetRefSummary(commandDraft.selectedTargets)}</p>
          <p>Discard ids from hand: {selectedDiscardIdsFromHand.length} | Field ids: {selectedFieldIds.length}</p>
          <p>Count: {commandDraft.selectedCount ?? 'N/A'} | Pending candidate ids: {pendingCandidateIds.length}</p>
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
            ></textarea>
          </label>
          <div class="combat-page__action-buttons">
            <button type="button" onclick={() => handleClearSelectedTargets()}>
              Clear targets
            </button>
            <button type="button" onclick={() => handleClearSelectionInputs()}>
              Clear helper inputs
            </button>
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
  .combat-page__overview-grid,
  .combat-page__zone-grid,
  .combat-page__zone-panel,
  .combat-page__summon-section,
  .combat-page__field-control,
  .combat-page__feed-list,
  .combat-page__command-list,
  .combat-page__metric-grid {
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
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1rem;
    flex: 1 1 38rem;
  }

  .combat-page__overview-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
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
  .combat-page__spotlight-card,
  .combat-page__zone-panel,
  .combat-page__card-row,
  .combat-page__metric-card,
  .combat-page__field-control input,
  .combat-page__field-control textarea,
  .combat-page__inline-button,
  .combat-page__feed-card,
  .combat-page__command-list button {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
  }

  .combat-page__unit-card,
  .combat-page__hand-card,
  .combat-page__zone-panel,
  .combat-page__spotlight-card {
    display: grid;
    gap: 0.75rem;
  }

  .combat-page__unit-card--enemy {
    border-color: rgba(199, 167, 125, 0.28);
  }

  .combat-page__unit-card--active-turn {
    border-color: rgba(226, 193, 155, 0.48);
    box-shadow: inset 0 0 0 1px rgba(226, 193, 155, 0.24);
  }

  .combat-page__spotlight-card--accent {
    border-color: rgba(113, 196, 255, 0.32);
  }

  .combat-page__spotlight-card--success {
    border-color: rgba(126, 214, 158, 0.36);
  }

  .combat-page__spotlight-card--warning {
    border-color: rgba(226, 193, 155, 0.48);
  }

  .combat-page__metric-grid {
    grid-template-columns: repeat(auto-fit, minmax(8.5rem, 1fr));
    gap: 0.75rem;
  }

  .combat-page__metric-grid--compact {
    grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  }

  .combat-page__metric-card {
    display: grid;
    gap: 0.25rem;
    align-content: start;
  }

  .combat-page__metric-card strong,
  .combat-page__metric-card span,
  .combat-page__metric-card p,
  .combat-page__spotlight-card strong,
  .combat-page__spotlight-card h3,
  .combat-page__spotlight-card p {
    margin: 0;
  }

  .combat-page__metric-card strong,
  .combat-page__spotlight-card h3 {
    font-family: var(--font-display);
    font-size: 1.05rem;
  }

  .combat-page__metric-card span,
  .combat-page__spotlight-card strong {
    font-size: 0.78rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .combat-page__metric-card p,
  .combat-page__spotlight-card p,
  .combat-page__unit-note {
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .combat-page__unit-note {
    margin: 0;
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

  .combat-page__command-list {
    gap: 0.75rem;
  }

  .combat-page__command-list button {
    display: grid;
    gap: 0.35rem;
    text-align: left;
    color: var(--color-text);
  }

  .combat-page__command-list button span,
  .combat-page__command-list button small {
    margin: 0;
  }

  .combat-page__command-list button small {
    color: var(--color-text-soft);
    line-height: 1.5;
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
    .combat-page__hand-bar,
    .combat-page__overview-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 960px) {
    .combat-page__status-stats,
    .combat-page__hand-cards,
    .combat-page__zone-grid,
    .combat-page__metric-grid,
    .combat-page__metric-grid--compact {
      grid-template-columns: 1fr;
    }
  }
</style>
