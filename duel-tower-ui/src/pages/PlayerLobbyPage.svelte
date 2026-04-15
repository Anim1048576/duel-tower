<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findPlayerLobbyAction,
    type PlayerLobbyActionId,
    type PlayerLobbyActionResponseById,
    type PlayerLobbyScreenResponse,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import { addPresetIdentifier, normalizePresetIdentifier } from '../lib/presets/editorModel'
  import {
    clearStoredSessionAccess,
    hasStoredSessionCode,
    isStoredPlayerSessionAccess,
    readStoredSessionAccess,
    updateStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    buildSessionLoadoutActionPatch,
    cloneSessionLoadoutDraft,
    createEmptySessionLoadoutDraft,
    createEmptySessionLoadoutDraftEditFlags,
    createSessionLoadoutDraftFromScreen,
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
  import { createPlayerLobbyLocalPresentation } from '../lib/session/playerLobbyPresentation.js'
  import { readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'
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
  let selectedPresetId = $state('')
  let lastAppliedPresetLabel = $state<string | null>(null)
  let loadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let savedLoadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let editFlags = $state<SessionLoadoutDraftEditFlags>(createEmptySessionLoadoutDraftEditFlags())
  let ownedCardCandidate = $state('')
  let passiveCandidate = $state('')
  let requestedSessionCode = $state<string | null>(null)
  let requestSequence = 0
  let pollTimer: ReturnType<typeof window.setInterval> | null = null

  function getRouteSessionCode() {
    return readSessionCodeFromRoute('player-lobby')
  }

  function getInvalidAccessMessage(nextRouteCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextRouteCode) return 'No session code is present in the current player lobby URL.'
    if (!isStoredPlayerSessionAccess(nextAccess)) {
      return 'Player session access is not available. Re-enter through the session entry page first.'
    }
    if (!hasStoredSessionCode(nextAccess, nextRouteCode)) {
      return 'The stored player session access does not match the requested session code.'
    }
    return null
  }

  function parseIdentifierText(value: string) {
    return value.split(/\r?\n|,/).map((entry) => entry.trim()).filter(Boolean)
  }

  function formatIdentifierText(values: readonly string[]) {
    return values.join('\n')
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
    ownedCardCandidate = ''
    passiveCandidate = ''
  }

  function stopPolling() {
    if (pollTimer !== null) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function syncStoredCharacterId(characterId: number | null) {
    const nextAccess = updateStoredSessionAccess({ characterId: characterId ?? undefined })
    if (nextAccess) runtimeAccess = nextAccess
  }

  function syncSelectedPreset(nextScreen: PlayerLobbyScreenResponse, forceReset = false) {
    const currentSelection = forceReset ? '' : normalizePresetIdentifier(selectedPresetId)
    const hasCurrentSelection = nextScreen.presets.items.some(
      (item) => String(item.presetId) === currentSelection,
    )
    selectedPresetId = hasCurrentSelection
      ? currentSelection
      : nextScreen.presets.selectedId == null
        ? ''
        : String(nextScreen.presets.selectedId)
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
    syncSelectedPreset(nextScreen, options.forceDraftSync)

    if (options.forceDraftSync || !currentLocalDirty) {
      loadoutDraft = cloneSessionLoadoutDraft(nextSavedDraft)
      resetEditState()
    }

    savedLoadoutDraft = nextSavedDraft
  }

  function clearPlayerLobbyRuntimeState() {
    stopPolling()
    requestSequence += 1
    screen = null
    runtimeAccess = null
    invalidAccessMessage = null
    loadoutDraft = createEmptySessionLoadoutDraft()
    savedLoadoutDraft = createEmptySessionLoadoutDraft()
    selectedPresetId = ''
    lastAppliedPresetLabel = null
    resetEditState()
  }

  function startPolling() {
    stopPolling()
    if (typeof window === 'undefined' || !screen || !isStoredPlayerSessionAccess(runtimeAccess)) return

    pollTimer = window.setInterval(() => {
      if (!pendingActionId) {
        void loadPlayerLobbyScreen({ showLoading: false, forceDraftSync: false })
      }
    }, POLLING_INTERVAL_MS)
  }

  async function loadPlayerLobbyScreen(options: { showLoading?: boolean; forceDraftSync?: boolean } = {}) {
    const { showLoading = true, forceDraftSync = true } = options
    const requestId = ++requestSequence
    const nextRouteCode = getRouteSessionCode()
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidAccessMessage(nextRouteCode, nextAccess)

    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage
    requestedSessionCode = nextRouteCode

    if (showLoading) {
      loading = true
      notFound = false
      errorMessage = null
      clearActionFeedback()
      if (!screen) lastAppliedPresetLabel = null
    }

    if (!nextRouteCode || nextInvalidAccessMessage) {
      stopPolling()
      if (showLoading) loading = false
      return
    }

    try {
      const response = await getScreen<PlayerLobbyScreenResponse>('PlayerLobby', { code: nextRouteCode })
      if (requestId !== requestSequence) return
      notFound = false
      errorMessage = null
      applyScreen(response, { forceDraftSync })
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
      if (requestId === requestSequence && showLoading) loading = false
    }
  }

  function retryLoad() {
    void loadPlayerLobbyScreen({ showLoading: true, forceDraftSync: true })
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

  function updateDeckOwnedCardIds(value: string) {
    loadoutDraft = { ...loadoutDraft, deckOwnedCardIds: parseIdentifierText(value) }
    editFlags = { ...editFlags, deckOwnedCardIdsEdited: true }
  }

  function updatePassiveIds(value: string) {
    loadoutDraft = { ...loadoutDraft, passiveIds: parseIdentifierText(value) }
    editFlags = { ...editFlags, passiveIdsEdited: true }
  }

  function addOwnedCardCandidate() {
    const nextIdentifier = normalizePresetIdentifier(ownedCardCandidate)
    if (!nextIdentifier || localPresentation?.deckEditingLocked) return

    loadoutDraft = {
      ...loadoutDraft,
      deckOwnedCardIds: addPresetIdentifier(loadoutDraft.deckOwnedCardIds, nextIdentifier),
    }
    editFlags = { ...editFlags, deckOwnedCardIdsEdited: true }
    ownedCardCandidate = ''
  }

  function addPassiveCandidate() {
    const nextIdentifier = normalizePresetIdentifier(passiveCandidate)
    if (!nextIdentifier) return

    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: addPresetIdentifier(loadoutDraft.passiveIds, nextIdentifier),
    }
    editFlags = { ...editFlags, passiveIdsEdited: true }
    passiveCandidate = ''
  }

  function getPendingActionLabel(actionId: PlayerLobbyActionId) {
    switch (actionId) {
      case 'playerLobby.toggleReady': return 'Updating ready state...'
      case 'playerLobby.leave': return 'Leaving session...'
      case 'playerLobby.saveLoadout': return 'Saving loadout...'
      case 'playerLobby.applyPreset': return 'Applying preset...'
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
      await loadPlayerLobbyScreen({ showLoading: false, forceDraftSync: false })
      actionSuccessTitle = 'Ready updated'
      actionSuccessMessage = requestedReady
        ? 'You are marked ready in the current session.'
        : 'You are no longer marked ready in the current session.'
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
      actionErrorMessage = 'Character selection is required before saving the current loadout.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    const savePayload = buildSessionLoadoutActionPatch(normalizedDraft, savedLoadoutDraft, editFlags)
    const requiresExCard = 'exCardId' in savePayload && typeof savePayload.exCardId === 'string'
    if (requiresExCard && !normalizedDraft.exCardId) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = 'EX card selection is required before saving the current loadout.'
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
      await loadPlayerLobbyScreen({ showLoading: false, forceDraftSync: true })
      actionSuccessTitle = playerLobbyStateCopy.loadoutSavedFeedback.title
      actionSuccessMessage = playerLobbyStateCopy.loadoutSavedFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to save the current loadout.')
    } finally {
      pendingActionId = null
    }
  }

  async function runApplyPreset() {
    if (!screen) return
    const action = findPlayerLobbyAction(screen, 'playerLobby.applyPreset')
    if (!action?.enabled || pendingActionId) return

    const presetId = Number(selectedPresetId)
    const presetItem = screen.presets.items.find((item) => String(item.presetId) === selectedPresetId) ?? null
    if (!Number.isInteger(presetId) || presetId <= 0 || !presetItem) {
      actionErrorTitle = 'Preset apply failed'
      actionErrorMessage = 'Choose a saved preset before applying it to the current session.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    pendingActionId = action.id
    actionErrorTitle = 'Preset apply failed'
    clearActionFeedback()

    try {
      await invokeScreenAction<PlayerLobbyScreenResponse, PlayerLobbyActionResponseById['playerLobby.applyPreset']>(
        action,
        { body: buildScreenActionPayload(action, { presetId }) },
      )
      lastAppliedPresetLabel = presetItem.label
      await loadPlayerLobbyScreen({ showLoading: false, forceDraftSync: true })
      actionSuccessTitle = playerLobbyStateCopy.presetAppliedFeedback.title
      actionSuccessMessage = `${playerLobbyStateCopy.presetAppliedFeedback.message} (${presetItem.label})`
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to apply the selected preset.')
    } finally {
      pendingActionId = null
    }
  }

  function handlePopState() {
    void loadPlayerLobbyScreen({ showLoading: true, forceDraftSync: true })
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void loadPlayerLobbyScreen({ showLoading: true, forceDraftSync: true })
    window.addEventListener('popstate', handlePopState)
  })

  onDestroy(() => {
    stopPolling()
    window.removeEventListener('popstate', handlePopState)
  })

  const localPresentation = $derived.by(() =>
    screen ? createPlayerLobbyLocalPresentation(screen, normalizeSessionLoadoutDraft(loadoutDraft), selectedPresetId) : null,
  )
  const currentPlayerId = $derived.by(() => screen?.me.playerId ?? runtimeAccess?.playerId ?? null)
  const participantCount = $derived.by(() => screen?.participantSlots.length ?? 0)
  const readyCount = $derived.by(() => screen?.participantSlots.filter((slot) => slot.state.includes('Ready')).length ?? 0)
  const toggleReadyAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.toggleReady') : null))
  const leaveAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.leave') : null))
  const saveLoadoutAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.saveLoadout') : null))
  const applyPresetAction = $derived.by(() => (screen ? findPlayerLobbyAction(screen, 'playerLobby.applyPreset') : null))
  const loadoutDirty = $derived.by(() => screen !== null && isSessionLoadoutDraftDirty(savedLoadoutDraft, normalizeSessionLoadoutDraft(loadoutDraft)))
  const loadoutEditGuardMessage = $derived.by(() => !saveLoadoutAction?.enabled ? saveLoadoutAction?.disabledReason?.userMessage ?? 'Current loadout editing is unavailable.' : null)
  const presetApplyGuardMessage = $derived.by(() => !applyPresetAction?.enabled ? applyPresetAction?.disabledReason?.userMessage ?? 'Preset apply is unavailable.' : null)
</script>

<div class="player-lobby-page">
  {#if loading}
    <SectionFrame eyebrow="Player Lobby" title="Loading player lobby" description="Resolving the current player lobby screen from the URL.">
      <ContentStatePanel title={sessionPageStateCopy.loading.title} message={sessionPageStateCopy.loading.message} />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame eyebrow="Player Access" title={sessionPageStateCopy.invalidPlayerAccess.title} description="Player lobby requires stored player session access for the requested code.">
      <ContentStatePanel title={sessionPageStateCopy.invalidPlayerAccess.title} message={invalidAccessMessage} tone="error" />
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if notFound}
    <SectionFrame eyebrow="Session Missing" title="Session not found" description="The requested player lobby code did not resolve to a live session.">
      <ContentStatePanel title={sessionPageStateCopy.notFound.title} message={sessionPageStateCopy.notFound.message} tone="error">
        <p>Requested code: {requestedSessionCode ?? 'Unavailable'}</p>
        <p>Check the code from the session entry page and try again.</p>
      </ContentStatePanel>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame eyebrow="Session Summary" title="Player lobby could not be loaded" description="The session code was valid, but the current lobby screen could not be restored.">
      <ContentStatePanel title="Unable to load player lobby" message={errorMessage} tone="error" actionLabel="Retry load" onAction={retryLoad} />
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if screen && localPresentation}
    <SectionFrame eyebrow="Session Summary" title={`Session ${screen.sessionCode}`} description="Player lobby now renders the server-provided screen model and keeps only local loadout input in the browser.">
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
        </div>
      </div>
      <div class="player-lobby-page__stats">
        <StatBlock value={participantCount} label="Joined" note="Current participant slots from the lobby screen" />
        <StatBlock value={readyCount} label="Ready" note="Players marked ready in the current screen response" />
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
      <SectionFrame title="Participant slots" description="The participant grid now renders directly from the server-curated lobby screen.">
        {#if screen.participantSlots.length > 0}
          <div class="player-lobby-page__slots">
            {#each screen.participantSlots as participant}
              <ParticipantSlot slot={participant.slot} name={participant.name} state={participant.state} tone={participant.tone} note={participant.note} />
            {/each}
          </div>
        {:else}
          <ContentStatePanel title="No participants yet" message="The current session does not have any joined player slots to show yet." />
        {/if}
      </SectionFrame>

      <SectionFrame title="Current loadout" description="The server snapshot and the local draft stay separate so the form can reflect unsaved changes immediately.">
        <div class="player-lobby-page__guide">
          <p>Current player id: {screen.me.playerId}</p>
          <p>Ready state: {screen.me.summary.readyLabel}</p>
          <p>{screen.me.summary.loadoutSummary}</p>
        </div>
        <div class="player-lobby-page__reference-summary">
          <div>
            <strong>Last synced</strong>
            <p>Character: {screen.me.loadout.characterLabel}</p>
            <p>EX: {screen.me.loadout.exLabel}</p>
            <p>Deck: {screen.me.loadout.deckCount} owned cards</p>
            <p>Passives: {screen.me.loadout.passiveCount}</p>
          </div>
          <div>
            <strong>Draft to save</strong>
            <p>Character: {localPresentation.character.label}</p>
            <p>EX: {localPresentation.ex.label}</p>
            <p>Deck: {localPresentation.deckCount} owned cards</p>
            <p>Passives: {localPresentation.passiveCount}</p>
          </div>
        </div>
        <div class="player-lobby-page__todo">
          <p>{loadoutDirty ? 'Current draft has unsaved loadout changes.' : 'Current draft matches the last synced loadout state.'}</p>
          <p>
            {#if localPresentation.characterChangePending}
              Save the new character first to refresh server-owned cards and character defaults before editing the deck again.
            {:else if localPresentation.previewNeedsResolveRefresh}
              Local preview uses the latest server reference options, and unresolved ids will refresh after the next successful save.
            {:else}
              Current draft preview is aligned with the latest server reference options.
            {/if}
          </p>
          {#if lastAppliedPresetLabel}
            <p>Last preset applied to this live session: {lastAppliedPresetLabel}</p>
          {/if}
        </div>
      </SectionFrame>
    </div>

    <div class="player-lobby-page__main">
      <SectionFrame title="Direct loadout save" description="Edit the current player loadout locally and invoke the server-declared save action.">
        {#if loadoutEditGuardMessage}
          <ContentStatePanel title="Loadout editing unavailable" message={loadoutEditGuardMessage} tone="error" />
        {/if}
        {#if localPresentation.characterChangePending}
          <ContentStatePanel title="Character change pending" message="Save this character selection first. The next screen response will refresh owned cards, deck defaults, and the synced loadout summary." />
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
            <span>Deck owned card ids</span>
            <div class="player-lobby-page__picker-row">
              <select bind:value={ownedCardCandidate} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || localPresentation.deckEditingLocked}>
                <option value="">Quick add owned card</option>
                {#each screen.references.ownedCardOptions as option}
                  <option value={option.ownedCardId}>{option.label}</option>
                {/each}
              </select>
              <button type="button" disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || localPresentation.deckEditingLocked || !ownedCardCandidate} onclick={addOwnedCardCandidate}>Add card</button>
            </div>
            <textarea rows="6" value={formatIdentifierText(loadoutDraft.deckOwnedCardIds)} placeholder={localPresentation.deckEditingLocked ? 'Save the selected character first to refresh owned card ids.' : 'One owned card id per line'} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || localPresentation.deckEditingLocked} oninput={(event) => updateDeckOwnedCardIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
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

        <div class="player-lobby-page__actions">
          <button type="button" onclick={() => void runSaveLoadout()} disabled={loading || pendingActionId !== null || Boolean(loadoutEditGuardMessage) || !loadoutDirty || !saveLoadoutAction?.enabled}>
            {pendingActionId === 'playerLobby.saveLoadout' ? getPendingActionLabel('playerLobby.saveLoadout') : saveLoadoutAction?.label ?? 'Save loadout'}
          </button>
        </div>

        <div class="player-lobby-page__grid">
          <EntityListPane items={localPresentation.deckItems} emptyMessage="No deck owned card ids are currently assigned to the local draft." />
          <EntityListPane items={localPresentation.passiveItems} emptyMessage="No passive ids are currently assigned to the local draft." />
        </div>
      </SectionFrame>

      <SectionFrame title="Apply saved preset" description="Choose a server-provided preset item, review the latest preview snapshot, and invoke the apply action.">
        <div class="player-lobby-page__guide">
          <p>{localPresentation.preset.summary}</p>
          {#if lastAppliedPresetLabel}
            <p>Current live session was last updated from preset: {lastAppliedPresetLabel}</p>
          {/if}
        </div>
        {#if presetApplyGuardMessage}
          <ContentStatePanel title="Preset apply unavailable" message={presetApplyGuardMessage} tone="error" />
        {/if}
        {#if loadoutDirty}
          <ContentStatePanel title="Preset apply replaces the current draft" message="Applying a preset updates the live session loadout and replaces the current unsaved draft with the next server screen response." />
        {/if}
        <label class="player-lobby-page__field">
          <span>Preset</span>
          <select bind:value={selectedPresetId} disabled={loading || pendingActionId !== null || !screen.presets.items.length}>
            <option value="">Select preset</option>
            {#each screen.presets.items as preset}
              <option value={String(preset.presetId)}>{preset.label}</option>
            {/each}
          </select>
        </label>

        {#if !screen.presets.items.length}
          <ContentStatePanel title="No saved presets yet" message="Create a preset from the preset archive first, then return here to apply it to the current session." />
        {:else if selectedPresetId}
          <div class="player-lobby-page__guide">
            <p>{localPresentation.preset.label}</p>
            <p>{localPresentation.preset.subtitle}</p>
            {#if localPresentation.preset.previewSynced}
              <p>Character: {localPresentation.preset.characterLabel}</p>
              <p>EX: {localPresentation.preset.exLabel}</p>
            {:else}
              <p>Resolved preview list refreshes after the next server-selected preset snapshot is loaded.</p>
            {/if}
          </div>
          <div class="player-lobby-page__actions">
            <button type="button" onclick={() => void runApplyPreset()} disabled={loading || pendingActionId !== null || !applyPresetAction?.enabled}>
              {pendingActionId === 'playerLobby.applyPreset' ? getPendingActionLabel('playerLobby.applyPreset') : applyPresetAction?.label ?? 'Apply preset'}
            </button>
            <a class="player-lobby-page__link-action" data-nav href={pathBuilders.presetList()}>Open preset archive</a>
          </div>
          {#if localPresentation.preset.previewSynced}
            <div class="player-lobby-page__grid">
              <EntityListPane items={localPresentation.preset.deckItems} emptyMessage="This preset has no saved deck card entries." />
              <EntityListPane items={localPresentation.preset.passiveItems} emptyMessage="This preset has no saved passive entries." />
            </div>
          {:else}
            <ContentStatePanel title="Preview pending refresh" message={localPresentation.preset.summary} />
          {/if}
        {:else}
          <ContentStatePanel message="No saved preset is currently selected." />
        {/if}
      </SectionFrame>
    </div>

    <SectionFrame title="Action zone" description="Bottom action strip keeps readiness and membership actions separate from loadout editing.">
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
