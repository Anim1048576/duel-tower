<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { previewSessionLoadout } from '../lib/api/sessions'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findPlayerLobbyAction,
    type PlayerLobbyActionId,
    type PlayerLobbyActionResponseById,
    type PlayerLobbyLocalPresentationState,
    type PlayerLobbyScreenResponse,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    clearStoredSessionAccess,
    hasStoredSessionCode,
    isStoredPlayerSessionAccess,
    readStoredSessionAccess,
    updateStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    areSessionLoadoutDraftsEqual,
    buildSessionLoadoutActionPatch,
    buildSessionLoadoutPreviewRequest,
    cloneSessionLoadoutDraft,
    createEmptySessionLoadoutDraft,
    createEmptySessionLoadoutDraftEditFlags,
    createSessionLoadoutDraftFromScreen,
    isPreviewSessionLoadoutDraftCurrent,
    isSessionLoadoutDraftDirty,
    normalizeSessionLoadoutDraft,
    type SessionLoadoutDraft,
    type SessionLoadoutDraftEditFlags,
  } from '../lib/session/loadoutEditor'
  import {
    playerLobbyStateCopy,
    readSessionPageFeedback,
    sessionEntryStateCopy,
    sessionPageStateCopy,
    setSessionPageFeedback,
    type SessionPageFeedback,
  } from '../lib/session/pageState'
  import PlayerLobbyDeckEditorPanel from '../lib/session/components/PlayerLobbyDeckEditorPanel.svelte'
  import {
    addOwnedCardIdToLoadoutDraft,
    buildPlayerLobbyCurrentDeckEntries,
    buildPlayerLobbyDeckPoolGroups,
    removeOwnedCardIdFromLoadoutDraft,
    resolvePlayerLobbyActiveDeckEditor,
    resolvePlayerLobbyPreviewState,
    shouldAcceptPlayerLobbyPreviewResponse,
  } from '../lib/session/playerLobbyDeckEditor.js'
  import { createPlayerLobbyLocalPresentation } from '../lib/session/playerLobbyPresentation.js'
  import { resolvePlayerLobbyScreenRefreshPlan } from '../lib/session/playerLobbyScreenRefresh.js'
  import { readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'
  import { startTimedPolling, type TimedPollingHandle } from '../lib/session/liveSessionPolling'
  import { removeSelectionHandoff, selectionHandoffKeys } from '../lib/selectionHandoff'

  const POLLING_INTERVAL_MS = 4000

  let loading = $state(true)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let invalidAccessMessage = $state<string | null>(null)
  let actionErrorTitle = $state('Player action failed')
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessTitle = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)
  let screen = $state<PlayerLobbyScreenResponse | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let pendingActionId = $state<PlayerLobbyActionId | null>(null)
  let loadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let savedLoadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let editFlags = $state<SessionLoadoutDraftEditFlags>(createEmptySessionLoadoutDraftEditFlags())
  let passiveCandidate = $state('')
  let requestedSessionCode = $state<string | null>(null)
  let requestSequence = 0
  let deckPreviewResponse = $state<Awaited<ReturnType<typeof previewSessionLoadout>> | null>(null)
  let lastSuccessfulDeckPreviewResponse = $state<Awaited<ReturnType<typeof previewSessionLoadout>> | null>(null)
  let deckPreviewPending = $state(false)
  let deckPreviewErrorMessage = $state<string | null>(null)
  let deckPreviewRequestSequence = 0
  let latestDeckPreviewRequestId = 0
  let pendingDeckPreviewDraft = $state<SessionLoadoutDraft | null>(null)
  let pollingHandle: TimedPollingHandle | null = null

  function getRouteSessionCode() {
    return readSessionCodeFromRoute('player-lobby')
  }

  function getInvalidAccessMessage(nextRouteCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextRouteCode) return '현재 플레이어 로비 URL에 세션 코드가 없습니다.'
    if (!isStoredPlayerSessionAccess(nextAccess)) {
      return '플레이어 권한이 없습니다. 세션 입장에서 다시 들어가 주세요.'
    }
    if (!hasStoredSessionCode(nextAccess, nextRouteCode)) {
      return '저장된 세션 권한이 요청 코드와 다릅니다.'
    }
    return null
  }

  function parseIdentifierText(value: string) {
    return value.split(/\r?\n|,/).map((entry) => entry.trim()).filter(Boolean)
  }

  function formatIdentifierText(values: readonly string[]) {
    return values.join('\n')
  }

  function normalizeLoadoutIdentifier(value: string | null | undefined) {
    return typeof value === 'string' ? value.trim() : ''
  }

  function addLoadoutIdentifier(values: readonly string[], value: string) {
    const normalizedValue = normalizeLoadoutIdentifier(value)
    if (!normalizedValue || values.includes(normalizedValue)) {
      return [...values]
    }
    return [...values, normalizedValue]
  }

  function toNullablePositiveNumber(value: string) {
    const normalized = value.trim()
    const parsed = normalized ? Number(normalized) : null
    return parsed !== null && Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') return
    window.history[replace ? 'replaceState' : 'pushState']({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function resetEditState() {
    editFlags = createEmptySessionLoadoutDraftEditFlags()
    passiveCandidate = ''
  }

  function stopPolling() {
    pollingHandle?.stop()
    pollingHandle = null
  }

  function syncStoredCharacterId(characterId: number | null) {
    const nextAccess = updateStoredSessionAccess({ characterId: characterId ?? undefined })
    if (nextAccess) runtimeAccess = nextAccess
  }

  function applyScreen(nextScreen: PlayerLobbyScreenResponse, options: { forceDraftSync?: boolean } = {}) {
    const nextSavedDraft = createSessionLoadoutDraftFromScreen(nextScreen.me.draft)
    const currentLocalDirty = isSessionLoadoutDraftDirty(
      savedLoadoutDraft,
      normalizeSessionLoadoutDraft(loadoutDraft),
    )

    screen = nextScreen
    syncSessionSelectionHandoff(nextScreen.sessionCode)
    syncStoredCharacterId(nextScreen.me.loadout.characterId)

    if (options.forceDraftSync || !currentLocalDirty) {
      loadoutDraft = cloneSessionLoadoutDraft(nextSavedDraft)
      resetEditState()
    }

    savedLoadoutDraft = nextSavedDraft
  }

  function clearPlayerLobbyRuntimeState() {
    stopPolling()
    requestSequence += 1
    deckPreviewRequestSequence += 1
    latestDeckPreviewRequestId = 0
    screen = null
    runtimeAccess = null
    invalidAccessMessage = null
    loadoutDraft = createEmptySessionLoadoutDraft()
    savedLoadoutDraft = createEmptySessionLoadoutDraft()
    deckPreviewResponse = null
    lastSuccessfulDeckPreviewResponse = null
    deckPreviewPending = false
    deckPreviewErrorMessage = null
    pendingDeckPreviewDraft = null
    resetEditState()
  }

  function startPolling() {
    stopPolling()
    if (typeof window === 'undefined' || !screen || !isStoredPlayerSessionAccess(runtimeAccess)) return

    pollingHandle = startTimedPolling({
      intervalMs: POLLING_INTERVAL_MS,
      onPoll: async () => {
        if (!pendingActionId) {
          await refreshPlayerLobbyScreen('polling')
        }
      },
      onError: (error) => {
        errorMessage = getApiErrorMessage(error, '플레이어 로비를 새로고침하지 못했습니다.')
      },
    })
  }

  async function refreshPlayerLobbyScreen(
    reason:
      | 'initial-load'
      | 'retry-load'
      | 'route-change'
      | 'polling'
      | 'action-toggle-ready'
      | 'action-save-loadout',
  ) {
    const plan = resolvePlayerLobbyScreenRefreshPlan(reason)
    const requestId = ++requestSequence
    const nextRouteCode = getRouteSessionCode()
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidAccessMessage(nextRouteCode, nextAccess)

    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage
    requestedSessionCode = nextRouteCode

    if (plan.showLoading) {
      loading = true
      notFound = false
      errorMessage = null
      clearActionFeedback()
    }

    if (!nextRouteCode || nextInvalidAccessMessage) {
      stopPolling()
      if (plan.showLoading) loading = false
      return
    }

    try {
      const response = await getScreen<PlayerLobbyScreenResponse>('PlayerLobby', { code: nextRouteCode })
      if (requestId !== requestSequence) return
      notFound = false
      errorMessage = null
      applyScreen(response, { forceDraftSync: plan.forceDraftSync })
      startPolling()
    } catch (error) {
      if (requestId !== requestSequence) return
      stopPolling()

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFound = true
        screen = null
      } else {
        errorMessage = getApiErrorMessage(error, 'Unable to restore the current player lobby.')
      }
    } finally {
      if (requestId === requestSequence && plan.showLoading) loading = false
    }
  }

  function retryLoad() {
    void refreshPlayerLobbyScreen('retry-load')
  }

  function updateCharacterId(value: string) {
    const nextCharacterId = toNullablePositiveNumber(value)
    if (nextCharacterId === loadoutDraft.characterId) return
    if (nextCharacterId === savedLoadoutDraft.characterId) {
      loadoutDraft = cloneSessionLoadoutDraft(savedLoadoutDraft)
      resetEditState()
      return
    }

    loadoutDraft = { characterId: nextCharacterId, deckOwnedCardIds: [], exCardId: '', passiveIds: [] }
    resetEditState()
  }

  function updateExCardId(value: string) {
    loadoutDraft = { ...loadoutDraft, exCardId: value }
    editFlags = { ...editFlags, exCardEdited: true }
  }

  function updatePassiveIds(value: string) {
    loadoutDraft = { ...loadoutDraft, passiveIds: parseIdentifierText(value) }
    editFlags = { ...editFlags, passiveIdsEdited: true }
  }

  function addOwnedCardToDeck(ownedCardId: string) {
    if (!ownedCardId || localPresentation?.deckEditingLocked) return

    loadoutDraft = addOwnedCardIdToLoadoutDraft(loadoutDraft, ownedCardId)
    editFlags = { ...editFlags, deckOwnedCardIdsEdited: true }
  }

  function removeOwnedCardFromDeck(ownedCardId: string) {
    if (!ownedCardId || localPresentation?.deckEditingLocked) return

    loadoutDraft = removeOwnedCardIdFromLoadoutDraft(loadoutDraft, ownedCardId)
    editFlags = { ...editFlags, deckOwnedCardIdsEdited: true }
  }

  function addPassiveCandidate() {
    const nextIdentifier = normalizeLoadoutIdentifier(passiveCandidate)
    if (!nextIdentifier) return

    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: addLoadoutIdentifier(loadoutDraft.passiveIds, nextIdentifier),
    }
    editFlags = { ...editFlags, passiveIdsEdited: true }
    passiveCandidate = ''
  }

  function getPendingActionLabel(actionId: PlayerLobbyActionId) {
    switch (actionId) {
      case 'playerLobby.toggleReady': return 'Updating ready state...'
      case 'playerLobby.leave': return 'Leaving session...'
      case 'playerLobby.saveLoadout': return 'Saving loadout...'
    }
  }

  async function runToggleReady() {
    if (!screen) return
    const action = findPlayerLobbyAction(screen, 'playerLobby.toggleReady')
    if (!action?.enabled || pendingActionId) return

    pendingActionId = action.id
    actionErrorTitle = 'Ready update failed'
    clearActionFeedback()

    try {
      const requestedReady = !screen.me.ready
      await invokeScreenAction<PlayerLobbyScreenResponse, PlayerLobbyActionResponseById['playerLobby.toggleReady']>(
        action,
        { body: buildScreenActionPayload(action, { ready: requestedReady }) },
      )
      await refreshPlayerLobbyScreen('action-toggle-ready')
      actionSuccessTitle = 'Ready updated'
      actionSuccessMessage = requestedReady
        ? '준비 상태입니다.'
        : '준비 상태를 해제했습니다.'
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to update the current ready state.')
    } finally {
      pendingActionId = null
    }
  }

  async function runLeave() {
    if (!screen) return
    const action = findPlayerLobbyAction(screen, 'playerLobby.leave')
    if (!action?.enabled || pendingActionId) return

    pendingActionId = action.id
    actionErrorTitle = 'Leave failed'
    clearActionFeedback()

    try {
      await invokeScreenAction<PlayerLobbyScreenResponse, PlayerLobbyActionResponseById['playerLobby.leave']>(action)
      clearStoredSessionAccess()
      removeSelectionHandoff(selectionHandoffKeys.sessionId)
      removeSelectionHandoff(selectionHandoffKeys.sessionCode)
      clearPlayerLobbyRuntimeState()
      setSessionPageFeedback(sessionEntryStateCopy.leftFeedback)
      navigateTo(pathBuilders.sessionEntry(), true)
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to leave the current session.')
      pendingActionId = null
    }
  }

  async function runSaveLoadout() {
    if (!screen) return
    const action = findPlayerLobbyAction(screen, 'playerLobby.saveLoadout')
    if (!action?.enabled || pendingActionId) return

    const normalizedDraft = normalizeSessionLoadoutDraft(loadoutDraft)
    loadoutDraft = normalizedDraft

    if (normalizedDraft.characterId === null) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = '로드아웃 저장을 위해 캐릭터를 선택해 주세요.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    const savePayload = buildSessionLoadoutActionPatch(normalizedDraft, savedLoadoutDraft, editFlags)
    const requiresExCard = 'exCardId' in savePayload && typeof savePayload.exCardId === 'string'
    if (requiresExCard && !normalizedDraft.exCardId) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = '로드아웃 저장을 위해 EX 카드를 선택해 주세요.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    pendingActionId = action.id
    actionErrorTitle = 'Loadout save failed'
    clearActionFeedback()

    try {
      await invokeScreenAction<PlayerLobbyScreenResponse, PlayerLobbyActionResponseById['playerLobby.saveLoadout']>(
        action,
        { body: buildScreenActionPayload(action, savePayload) },
      )
      await refreshPlayerLobbyScreen('action-save-loadout')
      actionSuccessTitle = playerLobbyStateCopy.loadoutSavedFeedback.title
      actionSuccessMessage = playerLobbyStateCopy.loadoutSavedFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to save the current loadout.')
    } finally {
      pendingActionId = null
    }
  }

  function handlePopState() {
    void refreshPlayerLobbyScreen('route-change')
  }

  async function refreshDeckPreview(
    nextScreen: PlayerLobbyScreenResponse,
    nextDraft: SessionLoadoutDraft,
    playerToken: string,
  ) {
    const requestId = ++deckPreviewRequestSequence
    const clientRequestId = `player-lobby-preview-${requestId}`
    latestDeckPreviewRequestId = requestId
    pendingDeckPreviewDraft = cloneSessionLoadoutDraft(nextDraft)
    deckPreviewPending = true
    deckPreviewErrorMessage = null

    try {
      const response = await previewSessionLoadout(
        nextScreen.sessionCode,
        nextScreen.me.playerId,
        buildSessionLoadoutPreviewRequest(nextDraft, clientRequestId),
        playerToken,
      )
      if (!shouldAcceptPlayerLobbyPreviewResponse({
        requestId,
        latestRequestId: latestDeckPreviewRequestId,
        clientRequestId,
        response,
        isPreviewDraftCurrent: isPreviewSessionLoadoutDraftCurrent,
        draft: nextDraft,
      })) {
        return
      }
      deckPreviewResponse = response
      lastSuccessfulDeckPreviewResponse = response
      deckPreviewErrorMessage = null
    } catch (error) {
      if (
        requestId !== latestDeckPreviewRequestId ||
        !areSessionLoadoutDraftsEqual(normalizeSessionLoadoutDraft(loadoutDraft), nextDraft)
      ) {
        return
      }
      deckPreviewErrorMessage = getApiErrorMessage(error, '덱 미리보기를 새로고침하지 못했습니다.')
    } finally {
      if (
        requestId === latestDeckPreviewRequestId &&
        areSessionLoadoutDraftsEqual(normalizeSessionLoadoutDraft(loadoutDraft), nextDraft)
      ) {
        deckPreviewPending = false
        pendingDeckPreviewDraft = null
      }
    }
  }

  function retryDeckPreview() {
    if (!screen || !isStoredPlayerSessionAccess(runtimeAccess)) return
    void refreshDeckPreview(
      screen,
      normalizedLoadoutDraft,
      runtimeAccess.playerToken,
    )
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void refreshPlayerLobbyScreen('initial-load')
    window.addEventListener('popstate', handlePopState)
  })

  onDestroy(() => {
    stopPolling()
    window.removeEventListener('popstate', handlePopState)
  })

  // server screen owns slot/reference snapshots; localPresentation owns current draft UX only.
  const localPresentation = $derived.by(() =>
    screen
      ? (createPlayerLobbyLocalPresentation(
          screen,
          normalizeSessionLoadoutDraft(loadoutDraft),
        ) as PlayerLobbyLocalPresentationState)
      : null,
  )
  const normalizedLoadoutDraft = $derived.by(() => normalizeSessionLoadoutDraft(loadoutDraft))
  const screenDraftMatchesLoadoutDraft = $derived.by(() =>
    screen
      ? areSessionLoadoutDraftsEqual(createSessionLoadoutDraftFromScreen(screen.me.draft), normalizedLoadoutDraft)
      : false,
  )
  const currentPlayerId = $derived.by(() => screen?.me.playerId ?? runtimeAccess?.playerId ?? null)
  const participantCount = $derived.by(() => screen?.participantSlots.length ?? 0)
  const readyCount = $derived.by(() => screen?.participantSlots.filter((slot) => slot.state.includes('Ready')).length ?? 0)
  const toggleReadyAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.toggleReady') : null))
  const leaveAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.leave') : null))
  const saveLoadoutAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.saveLoadout') : null))
  const loadoutEditGuardMessage = $derived.by(() => !saveLoadoutAction?.enabled ? saveLoadoutAction?.disabledReason?.userMessage ?? 'Current loadout editing is unavailable.' : null)
  const deckPreviewState = $derived.by(() =>
    resolvePlayerLobbyPreviewState({
      previewResponse: deckPreviewResponse,
      fallbackPreviewResponse: lastSuccessfulDeckPreviewResponse,
      isPreviewDraftCurrent: isPreviewSessionLoadoutDraftCurrent,
      draft: normalizedLoadoutDraft,
    }),
  )
  const currentDeckPreviewMatchesDraft = $derived.by(() => deckPreviewState.matchingPreviewResponse !== null)
  const staleDeckPreviewVisible = $derived.by(() => deckPreviewState.hasStaleFallback)
  const effectiveDeckEditor = $derived.by(() =>
    resolvePlayerLobbyActiveDeckEditor({
      screenDeckEditor: screen?.deckEditor ?? null,
      matchingPreviewResponse: deckPreviewState.matchingPreviewResponse,
      fallbackPreviewResponse: deckPreviewState.fallbackPreviewResponse,
      draftDirty: localPresentation?.dirty ?? false,
    }),
  )
  const currentDeckEntries = $derived.by(() =>
    screen
      ? buildPlayerLobbyCurrentDeckEntries({
          draftDeckOwnedCardIds: normalizedLoadoutDraft.deckOwnedCardIds,
          screenDeckEditor: screen.deckEditor,
          matchingPreviewResponse: deckPreviewState.matchingPreviewResponse,
          fallbackPreviewResponse: deckPreviewState.fallbackPreviewResponse,
          draftDirty: localPresentation?.dirty ?? false,
          ownedCardOptions: screen.references.ownedCardOptions,
        })
      : [],
  )
  const deckPoolGroups = $derived.by(() =>
    screen
      ? buildPlayerLobbyDeckPoolGroups({
          screenDeckEditor: screen.deckEditor,
          matchingPreviewResponse: deckPreviewState.matchingPreviewResponse,
          fallbackPreviewResponse: deckPreviewState.fallbackPreviewResponse,
          draftDirty: localPresentation?.dirty ?? false,
          ownedCardOptions: screen.references.ownedCardOptions,
        })
      : [],
  )
  const deckPreviewInteractionLocked = $derived.by(() =>
    loading ||
    pendingActionId !== null ||
    Boolean(loadoutEditGuardMessage) ||
    Boolean(localPresentation?.deckEditingLocked) ||
    (Boolean(localPresentation?.dirty) && deckPreviewPending && !staleDeckPreviewVisible),
  )

  $effect(() => {
    if (!screen || !localPresentation || !isStoredPlayerSessionAccess(runtimeAccess)) {
      deckPreviewResponse = null
      lastSuccessfulDeckPreviewResponse = null
      deckPreviewPending = false
      deckPreviewErrorMessage = null
      pendingDeckPreviewDraft = null
      return
    }

    if (!localPresentation.dirty || screenDraftMatchesLoadoutDraft) {
      deckPreviewResponse = null
      lastSuccessfulDeckPreviewResponse = null
      deckPreviewPending = false
      deckPreviewErrorMessage = null
      pendingDeckPreviewDraft = null
      return
    }

    if (
      currentDeckPreviewMatchesDraft ||
      (pendingDeckPreviewDraft && areSessionLoadoutDraftsEqual(pendingDeckPreviewDraft, normalizedLoadoutDraft))
    ) {
      return
    }

    void refreshDeckPreview(
      screen,
      normalizedLoadoutDraft,
      runtimeAccess.playerToken,
    )
  })
</script>

<div class="player-lobby-page">
  {#if loading}
    <SectionFrame eyebrow="Player Lobby" title="Loading player lobby" description="플레이어 로비를 불러오는 중입니다.">
      <ContentStatePanel title={sessionPageStateCopy.loading.title} message={sessionPageStateCopy.loading.message} />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame eyebrow="Player Access" title={sessionPageStateCopy.invalidPlayerAccess.title} description="플레이어 권한이 필요합니다.">
      <ContentStatePanel title={sessionPageStateCopy.invalidPlayerAccess.title} message={invalidAccessMessage} tone="error" />
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if notFound}
    <SectionFrame eyebrow="Session Missing" title="Session not found" description="요청한 세션을 찾을 수 없습니다.">
      <ContentStatePanel title={sessionPageStateCopy.notFound.title} message={sessionPageStateCopy.notFound.message} tone="error">
        <p>Requested code: {requestedSessionCode ?? 'Unavailable'}</p>
        <p>세션 코드를 확인하고 다시 시도해 주세요.</p>
      </ContentStatePanel>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame eyebrow="Session Summary" title="Player lobby could not be loaded" description="플레이어 로비를 불러오지 못했습니다.">
      <ContentStatePanel title="Unable to load player lobby" message={errorMessage} tone="error" actionLabel="Retry load" onAction={retryLoad} />
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if screen && localPresentation}
    <SectionFrame eyebrow="Session Summary" title={`Session ${screen.sessionCode}`} description="플레이어 로비 상태를 확인합니다.">
      <div class="player-lobby-page__summary">
        <div class="player-lobby-page__summary-copy">
          <p>Player lobby</p>
          <h3>Code: {screen.sessionCode}</h3>
        </div>
      <div class="player-lobby-page__summary-tags">
          <TagChip label="Player View" tone="accent" />
          <TagChip label={`Me: ${currentPlayerId ?? 'Unknown'}`} tone="success" />
          <TagChip label={`Ready: ${screen.me.summary.readyLabel}`} tone={screen.me.summary.readyTone} />
          <TagChip label={localPresentation.dirty ? 'Draft Changed' : 'Draft Synced'} tone={localPresentation.dirty ? 'warning' : 'success'} />
          <TagChip label={localPresentation.deckEditingLocked ? 'Deck Locked' : 'Deck Editable'} tone={localPresentation.deckEditingLocked ? 'warning' : 'success'} />
        </div>
      </div>
      <div class="player-lobby-page__stats">
        <StatBlock value={participantCount} label="Joined" note="Joined players" />
        <StatBlock value={readyCount} label="Ready" note="Ready players" />
        <StatBlock value={screen.me.summary.readyLabel} label="My state" note={screen.me.playerId} />
      </div>
      {#if feedback}
        <ContentStatePanel title={feedback.title} message={feedback.message} />
      {/if}
      {#if actionErrorMessage}
        <ContentStatePanel title={actionErrorTitle} message={actionErrorMessage} tone="error" />
      {:else if actionSuccessMessage}
        <ContentStatePanel title={actionSuccessTitle ?? 'Player action completed'} message={actionSuccessMessage} />
      {/if}
    </SectionFrame>

    <div class="player-lobby-page__main">
      <SectionFrame title="Participant slots" description="참가자 목록입니다.">
        {#if screen.participantSlots.length > 0}
          <div class="player-lobby-page__slots">
            {#each screen.participantSlots as participant}
              <ParticipantSlot slot={participant.slot} name={participant.name} state={participant.state} tone={participant.tone} note={participant.note} />
            {/each}
          </div>
        {:else}
          <ContentStatePanel title="No participants yet" message="아직 참가자가 없습니다." />
        {/if}
      </SectionFrame>

      <SectionFrame title="Current loadout" description="현재 로드아웃을 편집합니다.">
        <div class="player-lobby-page__guide">
          <p>Current player id: {screen.me.playerId}</p>
          <p>Ready state: {screen.me.summary.readyLabel}</p>
          <p>Current draft summary: {localPresentation.summary}</p>
          <p>Last synced summary: {localPresentation.syncedSummary}</p>
        </div>
        <div class="player-lobby-page__reference-summary">
          <div>
            <strong>Last synced</strong>
            <p>Character: {screen.me.loadout.characterLabel}</p>
            <p>EX: {screen.me.loadout.exLabel}</p>
            <p>Deck: {screen.me.loadout.deckCount} owned cards</p>
            <p>Passives: {screen.me.loadout.passiveCount}</p>
            <p>Summary: {localPresentation.syncedSummary}</p>
          </div>
          <div>
            <strong>Draft to save</strong>
            <p>Character: {localPresentation.character.label}</p>
            <p>EX: {localPresentation.ex.label}</p>
            <p>Deck: {localPresentation.deckCount} owned cards</p>
            <p>Passives: {localPresentation.passiveCount}</p>
            <p>Summary: {localPresentation.summary}</p>
          </div>
        </div>
        <div class="player-lobby-page__todo">
          <p>{localPresentation.dirty ? '저장되지 않은 변경사항이 있습니다.' : '변경사항이 없습니다.'}</p>
          <p>
            {#if localPresentation.characterChangePending}
              덱을 수정하기 전에 캐릭터 선택을 저장해 주세요.
            {:else if deckPreviewErrorMessage && staleDeckPreviewVisible}
              미리보기를 불러오지 못해 이전 결과를 표시합니다.
            {:else if localPresentation.dirty && !currentDeckPreviewMatchesDraft}
              미리보기를 불러오는 중입니다.
            {:else}
              현재 편집 상태입니다.
            {/if}
          </p>
          {#if localPresentation.deckEditingLocked}
            <p>{localPresentation.deckEditingLockReason}</p>
          {/if}
        </div>
      </SectionFrame>
    </div>

    <div class="player-lobby-page__main">
      <SectionFrame title="Direct loadout save" description="로드아웃을 저장합니다.">
        {#if loadoutEditGuardMessage}
          <ContentStatePanel title="Loadout editing unavailable" message={loadoutEditGuardMessage} tone="error" />
        {/if}
        {#if localPresentation.characterChangePending}
          <ContentStatePanel title="Character change pending" message="캐릭터 선택을 먼저 저장해 주세요." />
        {/if}
        {#if localPresentation.deckEditingLocked}
          <ContentStatePanel title="Deck editing locked" message={localPresentation.deckEditingLockReason} />
        {/if}
        <div class="player-lobby-page__form-grid">
          <label class="player-lobby-page__field">
            <span>Character</span>
            <select value={loadoutDraft.characterId === null ? '' : String(loadoutDraft.characterId)} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage)} onchange={(event) => updateCharacterId((event.currentTarget as HTMLSelectElement).value)}>
              <option value="">Select character</option>
              {#if loadoutDraft.characterId !== null && !screen.references.characterOptions.some((character) => character.id === String(loadoutDraft.characterId))}
                <option value={String(loadoutDraft.characterId)}>Character #{loadoutDraft.characterId} (unresolved)</option>
              {/if}
              {#each screen.references.characterOptions as character}
                <option value={character.id}>{character.label}</option>
              {/each}
            </select>
          </label>

          <label class="player-lobby-page__field">
            <span>EX card</span>
            <select value={loadoutDraft.exCardId} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage)} onchange={(event) => updateExCardId((event.currentTarget as HTMLSelectElement).value)}>
              <option value="">Select EX card</option>
              {#if loadoutDraft.exCardId && !screen.references.exCardOptions.some((card) => card.id === loadoutDraft.exCardId)}
                <option value={loadoutDraft.exCardId}>{loadoutDraft.exCardId} (unresolved)</option>
              {/if}
              {#each screen.references.exCardOptions as card}
                <option value={card.id}>{card.label}</option>
              {/each}
            </select>
          </label>

          <label class="player-lobby-page__field player-lobby-page__field--span-2">
            <span>Passive ids</span>
            <div class="player-lobby-page__picker-row">
              <select bind:value={passiveCandidate} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage)}>
                <option value="">Quick add passive</option>
                {#each screen.references.passiveOptions as passive}
                  <option value={passive.id}>{passive.label}</option>
                {/each}
              </select>
              <button type="button" disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || !passiveCandidate} onclick={addPassiveCandidate}>Add passive</button>
            </div>
            <textarea rows="4" value={formatIdentifierText(loadoutDraft.passiveIds)} placeholder="One passive id per line" disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage)} oninput={(event) => updatePassiveIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
          </label>
        </div>

        <PlayerLobbyDeckEditorPanel
          deckEditor={effectiveDeckEditor}
          currentDeckEntries={currentDeckEntries}
          cardPoolGroups={deckPoolGroups}
          controlsDisabled={deckPreviewInteractionLocked}
          previewPending={Boolean(localPresentation.dirty) && deckPreviewPending}
          previewErrorMessage={deckPreviewErrorMessage}
          previewStale={staleDeckPreviewVisible}
          onRetryPreview={screen && isStoredPlayerSessionAccess(runtimeAccess) ? retryDeckPreview : null}
          onAddOwnedCard={addOwnedCardToDeck}
          onRemoveOwnedCard={removeOwnedCardFromDeck}
        />

        <div class="player-lobby-page__actions">
          <button type="button" onclick={() => void runSaveLoadout()} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || !localPresentation.dirty || !saveLoadoutAction?.enabled}>
            {pendingActionId === 'playerLobby.saveLoadout' ? getPendingActionLabel('playerLobby.saveLoadout') : saveLoadoutAction?.label ?? 'Save loadout'}
          </button>
        </div>

        <div class="player-lobby-page__grid">
          <ContentStatePanel
            title="Draft passive summary"
            message={localPresentation.passiveItems.length
              ? String(localPresentation.passiveItems.length) + ' passives selected'
              : 'No passives selected.'}
          >
            {#each localPresentation.passiveItems as passiveItem}
              <p>{passiveItem.title}{passiveItem.subtitle ? ' - ' + passiveItem.subtitle : ''}</p>
            {/each}
          </ContentStatePanel>
          <ContentStatePanel
            title="Deck preview source"
            message={localPresentation.dirty
              ? deckPreviewErrorMessage && staleDeckPreviewVisible
                ? '미리보기를 불러오지 못해 이전 결과를 표시합니다.'
                : currentDeckPreviewMatchesDraft
                ? '최신 미리보기를 표시합니다.'
                : '미리보기를 불러오는 중입니다.'
              : '현재 로비 상태를 표시합니다.'}
          />
        </div>
      </SectionFrame>

    </div>

    <SectionFrame title="Action zone" description="준비 상태와 퇴장을 처리합니다.">
      <div class="player-lobby-page__guide">
        <p>Current ready state: {screen.me.summary.readyLabel}</p>
        <p>{screen.me.summary.membershipSummary}</p>
      </div>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
        <button type="button" onclick={() => void runToggleReady()} disabled={loading || pendingActionId !== null || !toggleReadyAction?.enabled}>
          {pendingActionId === 'playerLobby.toggleReady' ? getPendingActionLabel('playerLobby.toggleReady') : toggleReadyAction?.label ?? 'Toggle ready'}
        </button>
        <button type="button" onclick={() => void runLeave()} disabled={loading || pendingActionId !== null || !leaveAction?.enabled}>
          {pendingActionId === 'playerLobby.leave' ? getPendingActionLabel('playerLobby.leave') : leaveAction?.label ?? 'Leave session'}
        </button>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .player-lobby-page,
  .player-lobby-page__main,
  .player-lobby-page__guide,
  .player-lobby-page__todo { display: grid; gap: 1.5rem; }
  .player-lobby-page__summary { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; flex-wrap: wrap; }
  .player-lobby-page__summary-copy { display: grid; gap: 0.5rem; }
  .player-lobby-page__summary-copy p,
  .player-lobby-page__summary-copy h3,
  .player-lobby-page__guide p,
  .player-lobby-page__todo p,
  .player-lobby-page__reference-summary p { margin: 0; }
  .player-lobby-page__summary-copy p { color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.12em; font-size: 0.78rem; }
  .player-lobby-page__summary-copy h3 { font-family: var(--font-display); font-size: clamp(1.8rem, 2.6vw, 2.4rem); line-height: 1.1; }
  .player-lobby-page__summary-tags { display: flex; gap: 0.5rem; flex-wrap: wrap; }
  .player-lobby-page__stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
  .player-lobby-page__main { grid-template-columns: minmax(0, 1.35fr) minmax(18rem, 0.65fr); align-items: start; }
  .player-lobby-page__slots,
  .player-lobby-page__grid,
  .player-lobby-page__form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
  .player-lobby-page__field { display: grid; gap: 0.5rem; }
  .player-lobby-page__field--span-2 { grid-column: span 2; }
  .player-lobby-page__field span { color: var(--color-text-muted); font-size: 0.82rem; text-transform: uppercase; letter-spacing: 0.08em; }
  .player-lobby-page__field select,
  .player-lobby-page__field textarea,
  .player-lobby-page__picker-row button { min-height: 3rem; width: 100%; border: 1px solid var(--color-border); background: rgba(12, 11, 10, 0.3); color: var(--color-text); padding: 0.75rem 0.9rem; font: inherit; }
  .player-lobby-page__field textarea { min-height: 7rem; resize: vertical; }
  .player-lobby-page__guide p,
  .player-lobby-page__todo p,
  .player-lobby-page__reference-summary p { color: var(--color-text-soft); line-height: 1.65; }
  .player-lobby-page__reference-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; padding: 0.9rem 1rem; border: 1px solid var(--color-border); background: rgba(12, 11, 10, 0.18); }
  .player-lobby-page__reference-summary strong { display: block; margin-bottom: 0.35rem; color: var(--color-text-muted); font-size: 0.82rem; text-transform: uppercase; letter-spacing: 0.08em; }
  .player-lobby-page__todo { border-top: 1px solid var(--color-border); padding-top: 1rem; }
  .player-lobby-page__actions { display: flex; flex-wrap: wrap; gap: 0.75rem; }
  .player-lobby-page__picker-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.75rem; }
  .player-lobby-page__picker-row button { width: fit-content; min-width: 8rem; }
  .player-lobby-page__link-action,
  .player-lobby-page__actions button { min-height: 3rem; padding: 0.75rem 1rem; border: 1px solid var(--color-border); display: inline-flex; align-items: center; justify-content: center; background: rgba(12, 11, 10, 0.28); color: var(--color-text); }
  .player-lobby-page__link-action { border-color: rgba(226, 193, 155, 0.42); background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08)); }
  @media (max-width: 960px) {
    .player-lobby-page__stats,
    .player-lobby-page__main,
    .player-lobby-page__slots,
    .player-lobby-page__grid,
    .player-lobby-page__form-grid,
    .player-lobby-page__reference-summary { grid-template-columns: 1fr; }
    .player-lobby-page__field--span-2 { grid-column: span 1; }
    .player-lobby-page__picker-row { grid-template-columns: 1fr; }
    .player-lobby-page__picker-row button { width: 100%; }
  }
</style>
