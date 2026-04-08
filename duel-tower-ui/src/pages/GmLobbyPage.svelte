<script lang="ts">
  import { onMount } from 'svelte'
  import { getSessionState, kickPlayer, resetSession } from '../lib/api/sessions'
  import type { PlayerStateDto, SessionStateDto } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    hasStoredSessionCode,
    isStoredGmSessionAccess,
    normalizeSessionCode,
    readStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    gmLobbyStateCopy,
    readSessionPageFeedback,
    sessionPageStateCopy,
    type SessionPageFeedback,
  } from '../lib/session/pageState'
  import {
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type LobbyParticipantItem = {
    id: string
    slot: string
    name: string
    state: string
    tone: 'accent' | 'muted' | 'success' | 'warning'
    note: string
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
  let actionPending = $state<'kick' | 'reset' | null>(null)
  let kickReason = $state('')
  let selectedKickPlayerId = $state('')
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

  function getInvalidAccessMessage(nextRouteCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextRouteCode) {
      return 'No session code is present in the current GM lobby URL.'
    }

    if (!isStoredGmSessionAccess(nextAccess)) {
      return 'GM session access is not available. Re-enter through the session entry page first.'
    }

    if (!hasStoredSessionCode(nextAccess, nextRouteCode)) {
      return 'The stored GM session access does not match the requested session code.'
    }

    return null
  }

  function buildParticipantStateLabel(player: PlayerStateDto) {
    return player.ready ? 'Ready' : 'Joined'
  }

  function buildParticipantTone(player: PlayerStateDto) {
    return player.ready ? 'success' : 'muted'
  }

  function buildParticipantNote(player: PlayerStateDto) {
    const passiveSummary =
      player.passiveIds.length > 0
        ? `${player.passiveIds.length} passives`
        : 'No passives'

    const exSummary = player.exCard ? `EX ${player.exCard}` : 'No EX card'

    return `Deck ${player.deckOwnedCardIds.length} cards | ${passiveSummary} | ${exSummary}`
  }

  function buildParticipantItems(nextSession: SessionStateDto | null) {
    if (!nextSession) {
      return [] as LobbyParticipantItem[]
    }

    return Object.values(nextSession.players)
      .sort((left, right) => left.playerId.localeCompare(right.playerId))
      .map((player, index) => ({
        id: player.playerId,
        slot: `P${index + 1}`,
        name: player.playerId,
        state: buildParticipantStateLabel(player),
        tone: buildParticipantTone(player),
        note: buildParticipantNote(player),
      }))
  }

  function syncLobbyState(nextSession: SessionStateDto) {
    session = nextSession
    setSelectionHandoff(selectionHandoffKeys.sessionCode, nextSession.sessionCode)
    removeSelectionHandoff(selectionHandoffKeys.sessionId)

    const remainingPlayerIds = Object.keys(nextSession.players)

    if (!remainingPlayerIds.includes(selectedKickPlayerId)) {
      selectedKickPlayerId = remainingPlayerIds[0] ?? ''
    }
  }

  async function loadGmLobbyState() {
    const nextRouteCode = routeSessionCode
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidAccessMessage(nextRouteCode, nextAccess)
    const requestId = ++requestSequence

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
      actionSuccessMessage = gmLobbyStateCopy.sessionResetFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to reset the current session.')
    } finally {
      actionPending = null
    }
  }

  function handleWindowStateChange() {
    void loadGmLobbyState()
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void loadGmLobbyState()
    window.addEventListener('popstate', handleWindowStateChange)

    return () => {
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
      eyebrow="GM Access"
      title="GM lobby access is unavailable"
      description="This page expects GM runtime access that matches the current session code."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.invalidGmAccess.title}
        message={invalidAccessMessage}
        tone="error"
      >
        <p>Requested code: {routeSessionCode ?? 'Unavailable'}</p>
        <p>Open the session entry screen and create a GM session again to restore GM access.</p>
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
        </div>

        <div class="gm-lobby-page__summary-tags">
          <TagChip label="GM View" tone="warning" />
          <TagChip label={gmAccessLabel} tone="success" />
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
        description="The GM slot grid now reflects the live participant list and ready states from the session API."
      >
        {#if participantItems.length > 0}
          <div class="gm-lobby-page__slots">
            {#each participantItems as participant}
              <ParticipantSlot
                slot={participant.slot}
                name={participant.name}
                state={participant.state}
                tone={participant.tone}
                note={participant.note}
              />
            {/each}
          </div>
        {:else}
          <ContentStatePanel
            title="No participants yet"
            message="No players have joined this live session yet."
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
      description="Bottom action strip keeps navigation ready for the next combat step while GM state management stays in this page."
    >
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
        <button type="button" disabled>Start session (TODO)</button>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .gm-lobby-page,
  .gm-lobby-page__main,
  .gm-lobby-page__guide,
  .gm-lobby-page__control-group {
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

  .gm-lobby-page__summary-tags,
  .gm-lobby-page__controls,
  .gm-lobby-page__actions {
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

  .gm-lobby-page__guide p {
    color: var(--color-text-soft);
    line-height: 1.65;
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
