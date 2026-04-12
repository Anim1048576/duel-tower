<script lang="ts">
  import { onMount } from 'svelte'
  import { listCharacters } from '../lib/api/characters'
  import type { CharacterProfileResponse } from '../lib/api/characterTypes'
  import { listCards, listPassives } from '../lib/api/content'
  import type { CardDefinition, PassiveDefinition } from '../lib/api/contentTypes'
  import {
    executeSessionCommand,
    getSessionState,
    kickPlayer,
    resetSession,
    restoreGmAccess,
  } from '../lib/api/sessions'
  import type { PlayerStateDto, SessionRequestAccess, SessionStateDto } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    hasStoredSessionCode,
    isStoredGmSessionAccess,
    normalizeSessionCode,
    readStoredSessionAccess,
    setStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    gmLobbyStateCopy,
    readSessionPageFeedback,
    sessionPageStateCopy,
    type SessionPageFeedback,
  } from '../lib/session/pageState'
  import {
    startLiveSessionPolling,
    type LiveSessionPollingHandle,
  } from '../lib/session/liveSessionPolling'
  import {
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'
  import { resolveSessionLoadoutExCardId } from '../lib/session/loadoutEditor'

  type TagTone = 'accent' | 'muted' | 'success' | 'warning'

  type LobbyParticipantItem = {
    id: string
    slot: string
    name: string
    readyLabel: string
    readyTone: TagTone
    characterSummary: string
    exSummary: string
    passiveSummary: string
    deckSummary: string
    detailTags: {
      label: string
      tone: TagTone
    }[]
  }

  let loading = $state(true)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let invalidAccessMessage = $state<string | null>(null)
  let actionErrorTitle = $state('GM action failed')
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)
  let session = $state<SessionStateDto | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let requestSequence = 0
  let lobbyPolling: LiveSessionPollingHandle | null = null
  let actionPending = $state<'kick' | 'reset' | 'start' | null>(null)
  let referenceLoading = $state(true)
  let referenceErrorMessage = $state<string | null>(null)
  let characters = $state<CharacterProfileResponse[]>([])
  let cards = $state<CardDefinition[]>([])
  let passives = $state<PassiveDefinition[]>([])
  let kickReason = $state('')
  let selectedKickPlayerId = $state('')
  let selectedStartPlayerId = $state('')
  let resetKeepPlayers = $state(true)
  let resetKeepLoadouts = $state(true)
  let resetSeedInput = $state('')

  function getSessionCodeFromRoute() {
    if (typeof window === 'undefined') return null

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key !== 'gm-lobby') {
      return null
    }

    const code = match.params.code?.trim()
    return code ? normalizeSessionCode(code) : null
  }

  function getInvalidAccessMessage(nextRouteCode: string | null) {
    if (!nextRouteCode) {
      return 'No session code is present in the current GM lobby URL.'
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

  async function loadReferenceCatalogs() {
    referenceLoading = true
    referenceErrorMessage = null

    const [characterResult, cardResult, passiveResult] = await Promise.allSettled([
      listCharacters(),
      listCards(),
      listPassives(),
    ])

    const errors: string[] = []

    if (characterResult.status === 'fulfilled') {
      characters = characterResult.value
    } else {
      characters = []
      errors.push('character roster')
    }

    if (cardResult.status === 'fulfilled') {
      cards = cardResult.value
    } else {
      cards = []
      errors.push('card archive')
    }

    if (passiveResult.status === 'fulfilled') {
      passives = passiveResult.value
    } else {
      passives = []
      errors.push('passive archive')
    }

    referenceErrorMessage =
      errors.length > 0
        ? `Some reference data could not be restored: ${errors.join(', ')}. Participant summaries will fall back to ids.`
        : null

    referenceLoading = false
  }

  function normalizeContentId(value: string | null | undefined) {
    const normalized = value?.trim()
    return normalized ? normalized : ''
  }

  function getResolvedCard(cardId: string | null | undefined) {
    const normalized = normalizeContentId(cardId)
    return normalized ? cards.find((card) => card.id === normalized) ?? null : null
  }

  function getResolvedPassive(passiveId: string | null | undefined) {
    const normalized = normalizeContentId(passiveId)
    return normalized ? passives.find((passive) => passive.id === normalized) ?? null : null
  }

  function getDeckCardEntries(player: PlayerStateDto) {
    return player.deckOwnedCardIds.map((ownedCardId) => {
      const ownedCard = player.ownedCards.find((entry) => entry.ownedCardId === ownedCardId) ?? null
      const cardId = normalizeContentId(ownedCard?.cardId)
      const resolvedCard = cardId ? getResolvedCard(cardId) : null

      return {
        key: cardId || ownedCardId,
        cardId,
        label: resolvedCard?.name ?? (cardId || ownedCardId),
      }
    })
  }

  function getPlayerDeckCardIds(player: PlayerStateDto) {
    return getDeckCardEntries(player)
      .map((entry) => entry.cardId)
      .filter(Boolean)
  }

  function formatPreviewList(values: readonly string[], totalCount = values.length) {
    const preview = values.filter(Boolean)

    if (preview.length === 0) {
      return ''
    }

    const hiddenCount = totalCount - preview.length
    return hiddenCount > 0 ? `${preview.join(', ')} +${hiddenCount} more` : preview.join(', ')
  }

  function buildParticipantStateLabel(player: PlayerStateDto) {
    return player.ready ? 'Ready' : 'Not ready'
  }

  function buildParticipantTone(player: PlayerStateDto): TagTone {
    return player.ready ? 'success' : 'muted'
  }

  function buildCharacterSummary(player: PlayerStateDto, nextSession: SessionStateDto | null) {
    if (referenceLoading) {
      return 'Loading character summary...'
    }

    const resolvedExCardId = resolveSessionLoadoutExCardId(player, nextSession)
    const deckCardIds = new Set(getPlayerDeckCardIds(player))
    let bestMatch:
      | {
          character: CharacterProfileResponse
          score: number
          exMatched: boolean
        }
      | null = null
    let ambiguous = false

    for (const character of characters) {
      const exMatched =
        !!resolvedExCardId && normalizeContentId(character.exCard) === normalizeContentId(resolvedExCardId)
      const deckOverlap = (character.currentSkillDeck ?? []).reduce(
        (count, cardId) => count + (deckCardIds.has(normalizeContentId(cardId)) ? 1 : 0),
        0,
      )
      const score = (exMatched ? 100 : 0) + deckOverlap

      if (score <= 0) {
        continue
      }

      if (!bestMatch || score > bestMatch.score) {
        bestMatch = { character, score, exMatched }
        ambiguous = false
        continue
      }

      if (score === bestMatch.score) {
        ambiguous = true
      }
    }

    if (!bestMatch) {
      return 'Unavailable from current session data'
    }

    if (bestMatch.exMatched && !ambiguous) {
      return `${bestMatch.character.name} #${bestMatch.character.id}`
    }

    if (!ambiguous) {
      return `Likely ${bestMatch.character.name} #${bestMatch.character.id}`
    }

    return 'Multiple character candidates'
  }

  function buildExSummary(player: PlayerStateDto, nextSession: SessionStateDto | null) {
    const resolvedExCardId = resolveSessionLoadoutExCardId(player, nextSession)

    if (!resolvedExCardId) {
      return 'No EX configured'
    }

    const resolvedCard = getResolvedCard(resolvedExCardId)
    return resolvedCard ? `${resolvedCard.name} (${resolvedCard.id})` : resolvedExCardId
  }

  function buildPassiveSummary(player: PlayerStateDto) {
    if (player.passiveIds.length === 0) {
      return 'No passives equipped'
    }

    const labels = [...new Set(
      player.passiveIds
        .map((passiveId) => getResolvedPassive(passiveId)?.name ?? normalizeContentId(passiveId))
        .filter(Boolean),
    )]

    return `${player.passiveIds.length} equipped | ${formatPreviewList(labels.slice(0, 3), labels.length)}`
  }

  function buildDeckSummary(player: PlayerStateDto) {
    const deckEntries = getDeckCardEntries(player)

    if (deckEntries.length === 0) {
      return 'No deck cards selected'
    }

    const uniqueEntries = deckEntries.filter((entry, index, entries) => {
      return entries.findIndex((candidate) => candidate.key === entry.key) === index
    })
    const previewLabels = uniqueEntries.slice(0, 3).map((entry) => entry.label)

    return [
      `${deckEntries.length} cards`,
      `${uniqueEntries.length} unique`,
      formatPreviewList(previewLabels, uniqueEntries.length),
    ]
      .filter(Boolean)
      .join(' | ')
  }

  function buildParticipantTags(
    player: PlayerStateDto,
    nextSession: SessionStateDto | null,
  ): LobbyParticipantItem['detailTags'] {
    const resolvedExCardId = resolveSessionLoadoutExCardId(player, nextSession)

    return [
      {
        label: `${player.deckOwnedCardIds.length} deck cards`,
        tone: player.deckOwnedCardIds.length > 0 ? 'accent' : 'muted',
      },
      {
        label: `${player.passiveIds.length} passives`,
        tone: player.passiveIds.length > 0 ? 'success' : 'muted',
      },
      {
        label: resolvedExCardId ? 'EX linked' : 'No EX',
        tone: resolvedExCardId ? 'warning' : 'muted',
      },
    ]
  }

  function getSortedPlayers(nextSession: SessionStateDto | null) {
    if (!nextSession) {
      return [] as PlayerStateDto[]
    }

    return Object.values(nextSession.players).sort((left, right) => {
      if (left.ready !== right.ready) {
        return left.ready ? -1 : 1
      }

      return left.playerId.localeCompare(right.playerId)
    })
  }

  function getPreferredStartPlayerId(nextSession: SessionStateDto | null) {
    const players = getSortedPlayers(nextSession)
    return players.find((player) => player.ready)?.playerId ?? players[0]?.playerId ?? ''
  }

  function buildParticipantItems(nextSession: SessionStateDto | null) {
    if (!nextSession) {
      return [] as LobbyParticipantItem[]
    }

    return getSortedPlayers(nextSession)
      .map((player, index) => ({
        id: player.playerId,
        slot: `P${index + 1}`,
        name: player.playerId,
        readyLabel: buildParticipantStateLabel(player),
        readyTone: buildParticipantTone(player),
        characterSummary: buildCharacterSummary(player, nextSession),
        exSummary: buildExSummary(player, nextSession),
        passiveSummary: buildPassiveSummary(player),
        deckSummary: buildDeckSummary(player),
        detailTags: buildParticipantTags(player, nextSession),
      }))
  }

  function syncLobbyState(nextSession: SessionStateDto) {
    session = nextSession
    setSelectionHandoff(selectionHandoffKeys.sessionCode, nextSession.sessionCode)
    removeSelectionHandoff(selectionHandoffKeys.sessionId)

    const remainingPlayerIds = Object.keys(nextSession.players)
    const preferredStartPlayerId = getPreferredStartPlayerId(nextSession)

    if (!remainingPlayerIds.includes(selectedKickPlayerId)) {
      selectedKickPlayerId = remainingPlayerIds[0] ?? ''
    }

    if (!remainingPlayerIds.includes(selectedStartPlayerId)) {
      selectedStartPlayerId = preferredStartPlayerId
    }
  }

  function getGmSessionReadAccess(nextAccess: StoredSessionAccess | null): SessionRequestAccess | null {
    if (!isStoredGmSessionAccess(nextAccess)) {
      return null
    }

    return {
      role: 'gm',
      gmToken: nextAccess.gmToken,
    }
  }

  function stopGmLobbyPolling() {
    lobbyPolling?.stop()
    lobbyPolling = null
  }

  function updateGmLobbyPollingVersion(nextSession: SessionStateDto) {
    lobbyPolling?.updateVersion(nextSession.version)
  }

  function syncPolledGmLobbyState(nextSession: SessionStateDto) {
    const nextRouteCode = routeSessionCode
    const nextAccess = readStoredSessionAccess()

    if (
      !nextRouteCode ||
      nextSession.sessionCode !== nextRouteCode ||
      !isStoredGmSessionAccess(nextAccess) ||
      !hasStoredSessionCode(nextAccess, nextRouteCode)
    ) {
      stopGmLobbyPolling()
      return
    }

    runtimeAccess = nextAccess
    syncLobbyState(nextSession)
  }

  function startGmLobbyPolling(
    nextCode: string | null,
    nextAccess: StoredSessionAccess | null,
    nextSession: SessionStateDto,
  ) {
    stopGmLobbyPolling()

    const access = getGmSessionReadAccess(nextAccess)

    if (!nextCode || !access || !hasStoredSessionCode(nextAccess, nextCode)) {
      return
    }

    lobbyPolling = startLiveSessionPolling({
      code: nextCode,
      access,
      initialVersion: nextSession.version,
      onState: syncPolledGmLobbyState,
    })
  }

  async function loadGmLobbyState() {
    const nextRouteCode = routeSessionCode
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidAccessMessage(nextRouteCode)
    const requestId = ++requestSequence

    stopGmLobbyPolling()
    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage
    loading = true
    notFound = false
    errorMessage = null
    actionErrorMessage = null
    actionSuccessMessage = null
    session = null

    if (!nextRouteCode || nextInvalidAccessMessage) {
      loading = false
      return
    }

    try {
      const response = await getSessionState(nextRouteCode)

      if (requestId !== requestSequence) {
        return
      }

      syncLobbyState(response)
      startGmLobbyPolling(nextRouteCode, nextAccess, response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (typeof error === 'object' && error && 'status' in error && error.status === 404) {
        notFound = true
      } else {
        errorMessage = getApiErrorMessage(error, 'Unable to restore the current GM lobby.')
      }
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  async function handleKickPlayer() {
    if (
      loading ||
      actionPending ||
      !routeSessionCode ||
      !selectedKickPlayerId ||
      !isStoredGmSessionAccess(runtimeAccess)
    ) {
      return
    }

    actionPending = 'kick'
    actionErrorTitle = 'Kick failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await kickPlayer(
        routeSessionCode,
        selectedKickPlayerId,
        {
          reason: kickReason.trim() || null,
        },
        runtimeAccess.gmToken,
      )

      syncLobbyState(response)
      updateGmLobbyPollingVersion(response)
      kickReason = ''
      actionSuccessMessage = `${gmLobbyStateCopy.playerRemovedFeedback.message} (${selectedKickPlayerId})`
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to remove the selected player.')
    } finally {
      actionPending = null
    }
  }

  async function handleResetSession() {
    if (loading || actionPending || !routeSessionCode || !isStoredGmSessionAccess(runtimeAccess)) {
      return
    }

    const normalizedSeed = resetSeedInput.trim()
    const parsedSeed = normalizedSeed ? Number(normalizedSeed) : null

    if (normalizedSeed && !Number.isInteger(parsedSeed)) {
      actionErrorTitle = 'Reset failed'
      actionErrorMessage = 'New seed must be an integer when provided.'
      actionSuccessMessage = null
      return
    }

    actionPending = 'reset'
    actionErrorTitle = 'Reset failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await resetSession(
        routeSessionCode,
        {
          keepPlayers: resetKeepPlayers,
          keepLoadouts: resetKeepLoadouts,
          newSeed: parsedSeed,
        },
        runtimeAccess.gmToken,
      )

      syncLobbyState(response)
      updateGmLobbyPollingVersion(response)
      actionSuccessMessage = gmLobbyStateCopy.sessionResetFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to reset the current session.')
    } finally {
      actionPending = null
    }
  }

  async function handleStartCombat() {
    if (loading || actionPending || !routeSessionCode || !session || !selectedStartPlayerId) {
      return
    }

    if (session.combat && session.combat.phase !== 'END') {
      actionErrorTitle = 'Combat start unavailable'
      actionErrorMessage = 'Combat is already active for this session.'
      actionSuccessMessage = null
      return
    }

    actionPending = 'start'
    actionErrorTitle = 'Combat start failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    let activeSession = session
    let activeGmToken = isStoredGmSessionAccess(runtimeAccess) ? runtimeAccess.gmToken : null
    let restoredGmAccess = false

    const syncResponseState = (nextState: SessionStateDto | null) => {
      if (!nextState) {
        return null
      }

      syncLobbyState(nextState)
      updateGmLobbyPollingVersion(nextState)
      activeSession = nextState
      return nextState
    }

    const navigateToCombat = (nextState: SessionStateDto | null) => {
      navigateTo(pathBuilders.combat(nextState?.sessionCode ?? routeSessionCode))
    }

    const isVersionMismatch = (message: string) => message.toLowerCase().includes('version mismatch')
    const isCombatAlreadyStarted = (message: string) => message.toLowerCase().includes('combat already started')
    const isGmAuthorizationFailure = (message: string) =>
      message.toLowerCase().includes('gm authorization required')
    const getRejectedMessage = (errors: string[]) =>
      errors.length > 0 ? errors.join(', ') : 'START_COMBAT was rejected by the engine.'

    const syncRestoredGmAccess = (response: Awaited<ReturnType<typeof restoreGmAccess>>) => {
      const nextAccess = setStoredSessionAccess({
        code: response.code,
        role: 'gm',
        gmToken: response.gmToken,
      })

      runtimeAccess = nextAccess
      syncLobbyState(response.state)
      updateGmLobbyPollingVersion(response.state)
      startGmLobbyPolling(routeSessionCode, nextAccess, response.state)
      activeSession = response.state
      return response
    }

    const restoreGmSessionAccess = async () => {
      const restored = syncRestoredGmAccess(await restoreGmAccess(routeSessionCode))
      activeGmToken = restored.gmToken
      restoredGmAccess = true
      return restored
    }

    const executeStartCombat = (expectedVersion: number, gmToken: string) =>
      executeSessionCommand(
        routeSessionCode,
        {
          type: 'START_COMBAT',
          playerId: selectedStartPlayerId,
          expectedVersion,
        },
        {
          role: 'gm' as const,
          gmToken,
        },
      )

    const handleRejectedStartCombat = async (
      response: Awaited<ReturnType<typeof executeStartCombat>>,
      retried: boolean,
      gmToken: string,
    ) => {
      const syncedState = syncResponseState(response.state)
      const rejectionMessage = getRejectedMessage(response.errors)

        if (isCombatAlreadyStarted(rejectionMessage)) {
          actionErrorTitle = 'Combat already in progress'
          actionErrorMessage =
            'Combat had already started in this session, so you were moved to the combat screen.'
          navigateToCombat(syncedState)
          return true
        }

      if (!retried && syncedState && isVersionMismatch(rejectionMessage)) {
        const retryResponse = await executeStartCombat(syncedState.version, gmToken)
        const retryState = syncResponseState(retryResponse.state)

        if (retryResponse.accepted) {
          actionSuccessMessage = restoredGmAccess
            ? 'GM access was restored, the lobby synced to the latest session state, and combat started.'
            : 'The GM lobby synced to the latest session state and retried START_COMBAT once before combat started.'
          navigateToCombat(retryState)
          return true
        }

        const retryMessage = getRejectedMessage(retryResponse.errors)

        if (isCombatAlreadyStarted(retryMessage)) {
          actionErrorTitle = 'Combat already in progress'
          actionErrorMessage = restoredGmAccess
            ? 'GM access was restored, but combat had already started, so you were moved to the combat screen.'
            : 'After syncing the latest session state, the lobby detected that combat had already started and moved you to the combat screen.'
          navigateToCombat(retryState)
          return true
        }

        actionErrorTitle = 'Combat start rejected'
        actionErrorMessage = isVersionMismatch(retryMessage)
          ? 'The GM lobby synced to the latest session state and retried START_COMBAT once, but the version changed again before combat could start. Try once more after the lobby settles.'
          : retryMessage
        return true
      }

      actionErrorTitle = 'Combat start rejected'
      actionErrorMessage = isVersionMismatch(rejectionMessage)
        ? 'The GM lobby synced to the latest session state, but START_COMBAT still could not be applied with the refreshed version.'
        : rejectionMessage
      return true
    }

    try {
      if (!activeGmToken) {
        try {
          await restoreGmSessionAccess()
        } catch (restoreError) {
          actionErrorTitle = 'GM access restore failed'
          actionErrorMessage = getApiErrorMessage(
            restoreError,
            'Unable to restore GM access for this session. Sign in as the original GM again or return to session entry and reopen the session.',
          )
          return
        }
      }

      if (!activeGmToken) {
        actionErrorTitle = 'GM access restore failed'
        actionErrorMessage = 'GM access could not be restored for the current session.'
        return
      }

      const response = await executeStartCombat(activeSession.version, activeGmToken)

      if (!response.accepted) {
        await handleRejectedStartCombat(response, false, activeGmToken)
        return
      }

      const nextState = syncResponseState(response.state)
      if (restoredGmAccess) {
        actionSuccessMessage = 'GM access was restored from the logged-in account and combat started.'
      }
      navigateToCombat(nextState)
    } catch (error) {
      const status =
        typeof error === 'object' && error && 'status' in error && typeof error.status === 'number'
          ? error.status
          : null
      const errorMessage = getApiErrorMessage(error, 'Unable to start combat from the current GM lobby.')

      if ((status === 401 || isGmAuthorizationFailure(errorMessage)) && !restoredGmAccess) {
        try {
          const restored = await restoreGmSessionAccess()
          const retryResponse = await executeStartCombat(restored.state.version, restored.gmToken)

          if (!retryResponse.accepted) {
            await handleRejectedStartCombat(retryResponse, true, restored.gmToken)
            return
          }

          const retryState = syncResponseState(retryResponse.state)
          actionSuccessMessage =
            'GM access was restored from the logged-in account and START_COMBAT succeeded with the refreshed session state.'
          navigateToCombat(retryState)
          return
        } catch (restoreError) {
          actionErrorTitle = 'GM access restore failed'
          actionErrorMessage = getApiErrorMessage(
            restoreError,
            'Unable to restore GM access for this session. Sign in as the original GM again or return to session entry and reopen the session.',
          )
          return
        }
      }

      actionErrorMessage = errorMessage
    } finally {
      actionPending = null
    }
  }

  function handleWindowStateChange() {
    void loadGmLobbyState()
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void loadReferenceCatalogs()
    void loadGmLobbyState()
    window.addEventListener('popstate', handleWindowStateChange)

    return () => {
      stopGmLobbyPolling()
      window.removeEventListener('popstate', handleWindowStateChange)
    }
  })

  const routeSessionCode = $derived.by(() => getSessionCodeFromRoute())
  const participantItems = $derived.by(() => buildParticipantItems(session))
  const participantCount = $derived.by(() => (session ? Object.keys(session.players).length : 0))
  const readyCount = $derived.by(() =>
    session ? Object.values(session.players).filter((player) => player.ready).length : 0,
  )
  const gmAccessLabel = $derived.by(() =>
    isStoredGmSessionAccess(runtimeAccess) ? 'GM token ready' : 'GM token missing',
  )
  const kickActionLabel = $derived.by(() =>
    actionPending === 'kick' ? 'Removing player...' : 'Remove selected player',
  )
  const resetActionLabel = $derived.by(() =>
    actionPending === 'reset' ? 'Resetting session...' : 'Reset session',
  )
  const startActionLabel = $derived.by(() =>
    actionPending === 'start' ? 'Starting combat...' : 'Start combat',
  )
  const startBlockedMessage = $derived.by(() => {
    if (!session) {
      return 'Session state is unavailable.'
    }

    if (participantCount === 0) {
      return 'At least one participant must join before combat can start.'
    }

    if (!selectedStartPlayerId) {
      return 'Select the playerId to attach to START_COMBAT.'
    }

    if (session.combat && session.combat.phase !== 'END') {
      return 'Combat is already active for this session.'
    }

    return null
  })
</script>

<div class="gm-lobby-page">
  {#if loading}
    <SectionFrame
      eyebrow="Session Summary"
      title="Loading GM lobby"
      description="Restoring the current session state from the live session API."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.loading.title}
        message="Fetching the current GM lobby by session code."
      />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame
      eyebrow="Session Route"
      title="GM lobby route is unavailable"
      description="This page needs a valid session code in the URL before it can restore the current GM lobby."
    >
      <ContentStatePanel
        title="Session code is missing"
        message={invalidAccessMessage}
        tone="error"
      >
        <p>Requested code: {routeSessionCode ?? 'Unavailable'}</p>
        <p>Open the session entry screen and enter a valid session code to restore the GM lobby.</p>
      </ContentStatePanel>

      <div class="gm-lobby-page__actions">
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if notFound}
    <SectionFrame
      eyebrow="Session Missing"
      title="Session not found"
      description="The requested GM lobby code did not resolve to a live session."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.notFound.title}
        message={sessionPageStateCopy.notFound.message}
        tone="error"
      >
        <p>Requested code: {routeSessionCode ?? 'Unavailable'}</p>
        <p>Check the code from the session entry page and try again.</p>
      </ContentStatePanel>

      <div class="gm-lobby-page__actions">
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame
      eyebrow="Session Summary"
      title="GM lobby could not be loaded"
      description="The session code was valid, but the current GM lobby state could not be restored."
    >
      <ContentStatePanel
        title="Unable to load GM lobby"
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={() => void loadGmLobbyState()}
      />

      <div class="gm-lobby-page__actions">
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if session}
    <SectionFrame
      eyebrow="Session Summary"
      title={`Session ${session.sessionCode}`}
      description="GM lobby restores the current session from the URL code and uses stored GM access only for authorized actions."
    >
      <div class="gm-lobby-page__summary">
        <div class="gm-lobby-page__summary-copy">
          <p>GM lobby</p>
          <h3>Code: {session.sessionCode}</h3>
          <span class="gm-lobby-page__summary-meta">
            Session ID {session.sessionId} | Seed {session.seed}
          </span>
        </div>

        <div class="gm-lobby-page__summary-tags">
          <TagChip label="GM View" tone="warning" />
          <TagChip label={gmAccessLabel} tone="success" />
          <TagChip label={`${readyCount} / ${participantCount} ready`} tone="accent" />
        </div>
      </div>

      <div class="gm-lobby-page__stats">
        <StatBlock value={participantCount} label="Joined" note="Current live participants" />
        <StatBlock value={readyCount} label="Ready" note="Players marked ready in the current state" />
        <StatBlock value={session.version} label="Version" note="Current session state version" />
      </div>

      {#if feedback}
        <ContentStatePanel title={feedback.title} message={feedback.message} />
      {/if}

      {#if actionErrorMessage}
        <ContentStatePanel
          title={actionErrorTitle}
          message={actionErrorMessage}
          tone="error"
        />
      {:else if actionSuccessMessage}
        <ContentStatePanel
          title="GM action completed"
          message={actionSuccessMessage}
        />
      {/if}
    </SectionFrame>

    <div class="gm-lobby-page__main">
      <SectionFrame
        title="Participant slots"
        description="The live participant list now keeps ready state and loadout context readable without changing the surrounding GM lobby shell."
      >
        {#if referenceLoading}
          <ContentStatePanel
            title="Loading participant summaries"
            message="Restoring character, EX, and passive labels from the content catalog."
          />
        {:else if referenceErrorMessage}
          <ContentStatePanel
            title="Participant labels are partially restored"
            message={referenceErrorMessage}
          />
        {/if}

        {#if participantItems.length > 0}
          <div class="gm-lobby-page__slots">
            {#each participantItems as participant}
              <article class={`gm-lobby-page__participant-card gm-lobby-page__participant-card--${participant.readyTone}`}>
                <div class="gm-lobby-page__participant-head">
                  <div class="gm-lobby-page__participant-copy">
                    <p>{participant.slot}</p>
                    <h4>{participant.name}</h4>
                  </div>

                  <TagChip label={participant.readyLabel} tone={participant.readyTone} />
                </div>

                <div class="gm-lobby-page__participant-tags">
                  {#each participant.detailTags as tag}
                    <TagChip label={tag.label} tone={tag.tone} />
                  {/each}
                </div>

                <dl class="gm-lobby-page__participant-details">
                  <div>
                    <dt>Character</dt>
                    <dd>{participant.characterSummary}</dd>
                  </div>

                  <div>
                    <dt>EX</dt>
                    <dd>{participant.exSummary}</dd>
                  </div>

                  <div>
                    <dt>Passives</dt>
                    <dd>{participant.passiveSummary}</dd>
                  </div>

                  <div>
                    <dt>Deck</dt>
                    <dd>{participant.deckSummary}</dd>
                  </div>
                </dl>
              </article>
            {/each}
          </div>
        {:else}
          <ContentStatePanel
            title="No participants yet"
            message="This live session is waiting for its first player. Keep the lobby open and share the session code to collect joins."
          />
        {/if}
      </SectionFrame>

      <SectionFrame
        title="GM control panel"
        description="Kick and reset are connected here with minimal controls that fit the current lobby shell."
      >
        <div class="gm-lobby-page__guide">
          <p>Current participant count: {participantCount}</p>
          <p>Current ready count: {readyCount}</p>
          <p>Use kick for a single participant, or reset the current session state with the options below.</p>
        </div>

        <div class="gm-lobby-page__control-group">
          <label class="gm-lobby-page__field">
            <span>Player to remove</span>
            <select
              bind:value={selectedKickPlayerId}
              disabled={loading || actionPending !== null || participantCount === 0}
            >
              <option value="">Select player</option>
              {#each Object.values(session.players) as player}
                <option value={player.playerId}>
                  {player.playerId}
                </option>
              {/each}
            </select>
          </label>

          <label class="gm-lobby-page__field">
            <span>Kick reason</span>
            <input
              bind:value={kickReason}
              type="text"
              placeholder="Optional reason"
              disabled={loading || actionPending !== null}
            />
          </label>

          <div class="gm-lobby-page__controls">
            <button
              type="button"
              disabled={loading || actionPending !== null || !selectedKickPlayerId}
              onclick={() => void handleKickPlayer()}
            >
              {kickActionLabel}
            </button>
          </div>
        </div>

        <div class="gm-lobby-page__control-group gm-lobby-page__control-group--bordered">
          <label class="gm-lobby-page__toggle">
            <input
              bind:checked={resetKeepPlayers}
              type="checkbox"
              disabled={loading || actionPending !== null}
            />
            <span>Keep current players</span>
          </label>

          <label class="gm-lobby-page__toggle">
            <input
              bind:checked={resetKeepLoadouts}
              type="checkbox"
              disabled={loading || actionPending !== null}
            />
            <span>Keep current loadouts</span>
          </label>

          <label class="gm-lobby-page__field">
            <span>New seed</span>
            <input
              bind:value={resetSeedInput}
              type="text"
              placeholder="Optional integer seed"
              disabled={loading || actionPending !== null}
            />
          </label>

          <div class="gm-lobby-page__controls">
            <button
              type="button"
              disabled={loading || actionPending !== null}
              onclick={() => void handleResetSession()}
            >
              {resetActionLabel}
            </button>
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Action zone"
      description="Bottom action strip keeps navigation and live combat start in the GM lobby without changing the surrounding route structure."
    >
      <div class="gm-lobby-page__action-stack">
        <label class="gm-lobby-page__field">
          <span>Start as player</span>
          <select
            bind:value={selectedStartPlayerId}
            disabled={loading || actionPending !== null || participantCount === 0}
          >
            <option value="">Select player</option>
            {#each getSortedPlayers(session) as player}
              <option value={player.playerId}>
                {player.playerId}{player.ready ? ' | ready' : ' | not ready'}
              </option>
            {/each}
          </select>
        </label>

        <p class="gm-lobby-page__action-note">
          START_COMBAT sends `expectedVersion` {session.version} with playerId `{selectedStartPlayerId || 'unselected'}`.
        </p>

        {#if startBlockedMessage}
          <p class="gm-lobby-page__action-note gm-lobby-page__action-note--warning">
            {startBlockedMessage}
          </p>
        {/if}
      </div>

      <div class="gm-lobby-page__actions">
        <a
          class="gm-lobby-page__link-action gm-lobby-page__link-action--muted"
          data-nav
          href={pathBuilders.sessionEntry()}
        >
          Back to session entry
        </a>
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.combat(session.sessionCode)}>
          Open combat command
        </a>
        <button
          type="button"
          disabled={loading || actionPending !== null || !!startBlockedMessage}
          onclick={() => void handleStartCombat()}
        >
          {startActionLabel}
        </button>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .gm-lobby-page,
  .gm-lobby-page__main,
  .gm-lobby-page__guide,
  .gm-lobby-page__control-group,
  .gm-lobby-page__action-stack {
    display: grid;
    gap: 1.5rem;
  }

  .gm-lobby-page__summary {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .gm-lobby-page__summary-copy {
    display: grid;
    gap: 0.5rem;
  }

  .gm-lobby-page__summary-copy p,
  .gm-lobby-page__summary-copy h3,
  .gm-lobby-page__summary-meta,
  .gm-lobby-page__guide p {
    margin: 0;
  }

  .gm-lobby-page__summary-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .gm-lobby-page__summary-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .gm-lobby-page__summary-meta {
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .gm-lobby-page__summary-tags,
  .gm-lobby-page__controls,
  .gm-lobby-page__actions,
  .gm-lobby-page__participant-tags {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .gm-lobby-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .gm-lobby-page__main {
    grid-template-columns: minmax(0, 1.35fr) minmax(18rem, 0.65fr);
    align-items: start;
  }

  .gm-lobby-page__slots {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .gm-lobby-page__participant-card {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
    display: grid;
    gap: 0.9rem;
  }

  .gm-lobby-page__participant-card--success {
    border-color: rgba(188, 204, 173, 0.32);
  }

  .gm-lobby-page__participant-head {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
  }

  .gm-lobby-page__participant-copy {
    display: grid;
    gap: 0.35rem;
  }

  .gm-lobby-page__participant-copy p,
  .gm-lobby-page__participant-copy h4,
  .gm-lobby-page__participant-details dt,
  .gm-lobby-page__participant-details dd {
    margin: 0;
  }

  .gm-lobby-page__participant-copy p,
  .gm-lobby-page__participant-details dt {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .gm-lobby-page__participant-copy h4 {
    font-size: 1rem;
  }

  .gm-lobby-page__participant-details {
    display: grid;
    gap: 0.85rem;
  }

  .gm-lobby-page__participant-details > div {
    display: grid;
    gap: 0.3rem;
  }

  .gm-lobby-page__participant-details dd {
    color: var(--color-text-soft);
    line-height: 1.55;
  }

  .gm-lobby-page__guide p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .gm-lobby-page__action-note {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .gm-lobby-page__action-note--warning {
    color: var(--color-warning);
  }

  .gm-lobby-page__control-group--bordered {
    padding-top: 1rem;
    border-top: 1px solid var(--color-border);
  }

  .gm-lobby-page__field {
    display: grid;
    gap: 0.5rem;
  }

  .gm-lobby-page__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .gm-lobby-page__field input,
  .gm-lobby-page__field select {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .gm-lobby-page__toggle {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    color: var(--color-text-soft);
  }

  .gm-lobby-page__controls button,
  .gm-lobby-page__link-action,
  .gm-lobby-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .gm-lobby-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .gm-lobby-page__link-action--muted {
    border-color: var(--color-border);
    background: rgba(12, 11, 10, 0.28);
  }

  @media (max-width: 960px) {
    .gm-lobby-page__stats,
    .gm-lobby-page__main,
    .gm-lobby-page__slots {
      grid-template-columns: 1fr;
    }
  }
</style>
