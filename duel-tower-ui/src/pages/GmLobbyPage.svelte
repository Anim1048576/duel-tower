<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findGmLobbyAction,
    type GmLobbyActionId,
    type GmLobbyActionResponseById,
    type GmLobbyScreenAction,
    type GmLobbyScreenResponse,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    isStoredGmSessionAccess,
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
  import { resolveGmLobbyScreenRefreshPlan, resolveGmLobbySelections } from '../lib/session/gmLobbyScreenRefresh.js'
  import { resolveGmLobbyStartCombatFollowUp } from '../lib/session/gmLobbyStartCombatAction.js'
  import { readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'
  import { startTimedPolling, type TimedPollingHandle } from '../lib/session/liveSessionPolling'

  const POLLING_INTERVAL_MS = 4000

  /**
   * GmLobby responsibility boundary:
   * - Backend owns participantCards, startCombat blocked/recommended state,
   *   and start-combat procedure/action contracts.
   * - Frontend keeps only lightweight local inputs such as selected player,
   *   reset options, and refresh/action feedback around the current screen.
   */

  let loading = $state(true)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let refreshErrorMessage = $state<string | null>(null)
  let invalidAccessMessage = $state<string | null>(null)
  let actionErrorTitle = $state('GM action failed')
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)
  let screen = $state<GmLobbyScreenResponse | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let pendingActionId = $state<GmLobbyActionId | null>(null)
  let kickReason = $state('')
  let selectedKickPlayerId = $state('')
  let selectedStartPlayerId = $state('')
  let resetKeepPlayers = $state(true)
  let resetKeepLoadouts = $state(true)
  let resetSeedInput = $state('')
  let requestSequence = 0
  let pollingHandle: TimedPollingHandle | null = null

  function getRouteSessionCode() {
    return readSessionCodeFromRoute('gm-lobby')
  }

  function getInvalidAccessMessage(nextRouteCode: string | null) {
    if (!nextRouteCode) {
      return 'No session code is present in the current GM lobby URL.'
    }
    return null
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

  function clearRefreshError() {
    refreshErrorMessage = null
  }

  function stopPolling() {
    pollingHandle?.stop()
    pollingHandle = null
  }

  function applyScreen(
    nextScreen: GmLobbyScreenResponse,
    options: {
      preserveStartPlayerSelection: boolean
    },
  ) {
    screen = nextScreen
    syncSessionSelectionHandoff(nextScreen.sessionCode)

    const nextSelections = resolveGmLobbySelections(nextScreen, {
      selectedKickPlayerId,
      selectedStartPlayerId,
      preserveStartPlayerSelection: options.preserveStartPlayerSelection,
    })

    selectedKickPlayerId = nextSelections.selectedKickPlayerId
    selectedStartPlayerId = nextSelections.selectedStartPlayerId
  }

  function updateStoredGmAccess(nextScreen: GmLobbyScreenResponse | null, restoredGmToken?: string | null) {
    const routeCode = nextScreen?.sessionCode ?? getRouteSessionCode()
    const normalizedToken = restoredGmToken?.trim()
    if (!routeCode || !normalizedToken) return

    runtimeAccess = setStoredSessionAccess({
      code: routeCode,
      role: 'gm',
      gmToken: normalizedToken,
    })
  }

  async function refreshGmLobbyScreen(
    reason:
      | 'initial-load'
      | 'retry-load'
      | 'route-change'
      | 'polling'
      | 'action-kick'
      | 'action-reset'
      | 'action-start-combat-success'
      | 'action-start-combat-failed',
  ) {
    const plan = resolveGmLobbyScreenRefreshPlan(reason)
    const requestId = ++requestSequence
    const nextRouteCode = getRouteSessionCode()
    const nextAccess = readStoredSessionAccess()
    const nextInvalidAccessMessage = getInvalidAccessMessage(nextRouteCode)

    runtimeAccess = nextAccess
    invalidAccessMessage = nextInvalidAccessMessage

    if (plan.showLoading) {
      loading = true
      notFound = false
      errorMessage = null
      clearRefreshError()
      clearActionFeedback()
    }

    if (!nextRouteCode || nextInvalidAccessMessage) {
      stopPolling()
      screen = null
      notFound = false
      clearRefreshError()
      if (plan.showLoading) loading = false
      return
    }

    try {
      const response = await getScreen<GmLobbyScreenResponse>('GmLobby', { code: nextRouteCode })
      if (requestId !== requestSequence) return
      notFound = false
      errorMessage = null
      clearRefreshError()
      applyScreen(response, {
        preserveStartPlayerSelection: plan.preserveStartPlayerSelection,
      })
      startPolling()
    } catch (error) {
      if (requestId !== requestSequence) return
      stopPolling()

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFound = true
        screen = null
        clearRefreshError()
      } else {
        const message = getApiErrorMessage(error, 'Unable to restore the current GM lobby screen.')
        if (plan.showLoading || !screen) {
          errorMessage = message
          clearRefreshError()
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
    if (typeof window === 'undefined' || !screen) return

    pollingHandle = startTimedPolling({
      intervalMs: POLLING_INTERVAL_MS,
      onPoll: async () => {
        if (!pendingActionId) {
          await refreshGmLobbyScreen('polling')
        }
      },
      onError: (error) => {
        refreshErrorMessage = getApiErrorMessage(error, 'Unable to refresh the current GM lobby screen.')
      },
    })
  }

  function retryLoad() {
    void refreshGmLobbyScreen('retry-load')
  }

  function findAction<TActionId extends GmLobbyActionId>(actionId: TActionId) {
    return screen ? findGmLobbyAction(screen, actionId) : null
  }

  function getPendingActionLabel(actionId: GmLobbyActionId) {
    switch (actionId) {
      case 'gmLobby.kick':
        return 'Removing player...'
      case 'gmLobby.reset':
        return 'Resetting session...'
      case 'gmLobby.startCombat':
        return 'Starting combat...'
    }
  }

  function toNullableInteger(value: string) {
    const normalized = value.trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    return Number.isInteger(parsed) ? parsed : null
  }

  function buildKickAction(selectedAction: GmLobbyScreenAction, playerId: string) {
    const nextHref = selectedAction.href.replace(/\/players\/[^/]+\/kick$/, `/players/${encodeURIComponent(playerId)}/kick`)
    return {
      ...selectedAction,
      href: nextHref,
    }
  }

  function readStartCombatTemplatePlayerId(action: GmLobbyScreenAction | null) {
    if (!action?.payloadTemplate || !('playerId' in action.payloadTemplate)) {
      return ''
    }

    const value = action.payloadTemplate.playerId
    return typeof value === 'string' ? value : ''
  }

  async function refreshAfterAction(
    reason: 'action-kick' | 'action-reset' | 'action-start-combat-success' | 'action-start-combat-failed',
  ) {
    await refreshGmLobbyScreen(reason)
  }

  async function runKick() {
    if (!screen) return
    const action = findAction('gmLobby.kick')
    if (!action?.enabled || pendingActionId || !selectedKickPlayerId) return

    pendingActionId = action.id
    actionErrorTitle = 'Kick failed'
    clearActionFeedback()

    try {
      const resolvedAction = buildKickAction(action, selectedKickPlayerId)
      await invokeScreenAction<GmLobbyScreenResponse, GmLobbyActionResponseById['gmLobby.kick']>(
        resolvedAction,
        {
          body: buildScreenActionPayload(action, {
            playerId: selectedKickPlayerId,
            reason: kickReason.trim() || null,
          }),
        },
      )
      kickReason = ''
      await refreshAfterAction('action-kick')
      actionSuccessMessage = `${gmLobbyStateCopy.playerRemovedFeedback.message} (${selectedKickPlayerId})`
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to remove the selected player.')
    } finally {
      pendingActionId = null
    }
  }

  async function runReset() {
    if (!screen) return
    const action = findAction('gmLobby.reset')
    if (!action?.enabled || pendingActionId) return

    const parsedSeed = toNullableInteger(resetSeedInput)
    if (resetSeedInput.trim() && parsedSeed === null) {
      actionErrorTitle = 'Reset failed'
      actionErrorMessage = 'New seed must be an integer when provided.'
      actionSuccessMessage = null
      return
    }

    pendingActionId = action.id
    actionErrorTitle = 'Reset failed'
    clearActionFeedback()

    try {
      await invokeScreenAction<GmLobbyScreenResponse, GmLobbyActionResponseById['gmLobby.reset']>(action, {
        body: buildScreenActionPayload(action, {
          keepPlayers: resetKeepPlayers,
          keepLoadouts: resetKeepLoadouts,
          newSeed: parsedSeed,
        }),
      })
      await refreshAfterAction('action-reset')
      actionSuccessMessage = gmLobbyStateCopy.sessionResetFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to reset the current session.')
    } finally {
      pendingActionId = null
    }
  }

  async function runStartCombat() {
    if (!screen) return
    const action = findAction('gmLobby.startCombat')
    if (!action?.enabled || pendingActionId) return

    pendingActionId = action.id
    actionErrorTitle = 'Combat start failed'
    clearActionFeedback()

    const requestedPlayerId =
      selectedStartPlayerId ||
      screen.startCombat.recommendedStartPlayerId ||
      readStartCombatTemplatePlayerId(action) ||
      ''

    try {
      const response = await invokeScreenAction<
        GmLobbyScreenResponse,
        GmLobbyActionResponseById['gmLobby.startCombat']
      >(action, {
        body: buildScreenActionPayload(action, {
          expectedVersion: screen.version,
          playerId: requestedPlayerId,
        }),
      })

      updateStoredGmAccess(response.latestScreen, response.restoredGmToken)

      const followUp = resolveGmLobbyStartCombatFollowUp(response)

      if (followUp.shouldApplyLatestScreen && response.latestScreen) {
        clearRefreshError()
        applyScreen(response.latestScreen, {
          preserveStartPlayerSelection: followUp.preserveStartPlayerSelection,
        })
      }

      if (followUp.shouldNavigate && followUp.nextRoute) {
        navigateTo(followUp.nextRoute)
        return
      }

      if (followUp.refreshReason) {
        await refreshAfterAction(followUp.refreshReason)
      }

      if (followUp.successMessage) {
        actionSuccessMessage = followUp.successMessage
        return
      }

      actionErrorTitle = followUp.errorTitle ?? 'Combat start failed'
      actionErrorMessage = followUp.errorMessage
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to start combat from the current GM lobby.')
    } finally {
      pendingActionId = null
    }
  }

  function handlePopState() {
    void refreshGmLobbyScreen('route-change')
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void refreshGmLobbyScreen('initial-load')
    window.addEventListener('popstate', handlePopState)
  })

  onDestroy(() => {
    stopPolling()
    window.removeEventListener('popstate', handlePopState)
  })

  const routeSessionCode = $derived.by(() => getRouteSessionCode())
  const participantCount = $derived.by(() => screen?.participantCards.length ?? 0)
  const readyCount = $derived.by(() =>
    screen?.participantCards.filter((participant) => participant.readyLabel === 'Ready').length ?? 0,
  )
  const kickAction = $derived.by(() => (screen ? findGmLobbyAction(screen, 'gmLobby.kick') : null))
  const resetAction = $derived.by(() => (screen ? findGmLobbyAction(screen, 'gmLobby.reset') : null))
  const startCombatAction = $derived.by(() => (screen ? findGmLobbyAction(screen, 'gmLobby.startCombat') : null))
  const gmAccessLabel = $derived.by(() => {
    if (startCombatAction?.auth === 'loginCookie' && startCombatAction.enabled) return 'GM restore available'
    if (isStoredGmSessionAccess(runtimeAccess)) return 'GM token ready'
    return 'GM token missing'
  })
  const startBlockedMessage = $derived.by(() => screen?.startCombat.blockedReason?.userMessage ?? null)
  const selectedStartPlayerLabel = $derived.by(() =>
    screen?.startCombat.selectableStartPlayers.find((player) => player.playerId === selectedStartPlayerId)?.label ??
    '',
  )
</script>

<div class="gm-lobby-page">
  {#if loading}
    <SectionFrame
      eyebrow="Session Summary"
      title="Loading GM lobby"
      description="Resolving the current GM lobby screen from the URL."
    >
      <ContentStatePanel
        title={sessionPageStateCopy.loading.title}
        message="Fetching the current GM lobby screen by session code."
      />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame
      eyebrow="Session Route"
      title="GM lobby route is unavailable"
      description="This page needs a valid session code in the URL before it can restore the GM lobby."
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
      description="The session code was valid, but the current GM lobby screen could not be restored."
    >
      <ContentStatePanel
        title="Unable to load GM lobby"
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={retryLoad}
      />

      <div class="gm-lobby-page__actions">
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Back to session entry
        </a>
      </div>
    </SectionFrame>
  {:else if screen}
    <SectionFrame
      eyebrow="Session Summary"
      title={`Session ${screen.sessionCode}`}
      description="GM lobby now renders the server-provided screen model and keeps only selection inputs and action feedback in the browser."
    >
      <div class="gm-lobby-page__summary">
        <div class="gm-lobby-page__summary-copy">
          <p>GM lobby</p>
          <h3>Code: {screen.sessionCode}</h3>
        </div>

        <div class="gm-lobby-page__summary-tags">
          <TagChip label="GM View" tone="warning" />
          <TagChip label={gmAccessLabel} tone={gmAccessLabel === 'GM token ready' ? 'success' : 'accent'} />
          <TagChip label={`${readyCount} / ${participantCount} ready`} tone="accent" />
        </div>
      </div>

      <div class="gm-lobby-page__stats">
        <StatBlock value={participantCount} label="Joined" note="Current participant cards from the GM lobby screen" />
        <StatBlock value={readyCount} label="Ready" note="Players marked ready in the current screen response" />
        <StatBlock value={screen.version} label="Version" note="Current GM lobby screen version" />
      </div>

      {#if feedback}
        <ContentStatePanel title={feedback.title} message={feedback.message} />
      {/if}

      {#if screen.uiNotices.length > 0}
        <ContentStatePanel title="Screen notices" message={screen.uiNotices.join(' ')} />
      {/if}

      {#if refreshErrorMessage}
        <ContentStatePanel title="Refresh delayed" message={refreshErrorMessage} tone="error" />
      {/if}

      {#if actionErrorMessage}
        <ContentStatePanel title={actionErrorTitle} message={actionErrorMessage} tone="error" />
      {:else if actionSuccessMessage}
        <ContentStatePanel title="GM action completed" message={actionSuccessMessage} />
      {/if}
    </SectionFrame>

    <div class="gm-lobby-page__main">
      <SectionFrame
        title="Participant slots"
        description="Participant cards render directly from the server-curated GM lobby screen."
      >
        {#if screen.participantCards.length > 0}
          <div class="gm-lobby-page__slots">
            {#each screen.participantCards as participant}
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
        description="Kick and reset use the server-declared action contract with only minimal local form state."
      >
        <div class="gm-lobby-page__guide">
          <p>Current participant count: {participantCount}</p>
          <p>Current ready count: {readyCount}</p>
          <p>Use kick for a single participant, or reset the current session state with the options below.</p>
        </div>

        {#if kickAction && !kickAction.enabled}
          <ContentStatePanel
            title="Kick unavailable"
            message={kickAction.disabledReason?.userMessage ?? 'Kick is currently unavailable.'}
            tone="error"
          />
        {/if}

        <div class="gm-lobby-page__control-group">
          <label class="gm-lobby-page__field">
            <span>Player to remove</span>
            <select
              bind:value={selectedKickPlayerId}
              disabled={loading || pendingActionId !== null || !kickAction?.enabled || participantCount === 0}
            >
              <option value="">Select player</option>
              {#each screen.participantCards as participant}
                <option value={participant.name}>
                  {participant.slot} | {participant.name}
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
              disabled={loading || pendingActionId !== null || !kickAction?.enabled}
            />
          </label>

          <div class="gm-lobby-page__controls">
            <button
              type="button"
              disabled={loading || pendingActionId !== null || !kickAction?.enabled || !selectedKickPlayerId}
              onclick={() => void runKick()}
            >
              {pendingActionId === 'gmLobby.kick' ? getPendingActionLabel('gmLobby.kick') : kickAction?.label ?? 'Kick player'}
            </button>
          </div>
        </div>

        {#if resetAction && !resetAction.enabled}
          <ContentStatePanel
            title="Reset unavailable"
            message={resetAction.disabledReason?.userMessage ?? 'Reset is currently unavailable.'}
            tone="error"
          />
        {/if}

        <div class="gm-lobby-page__control-group gm-lobby-page__control-group--bordered">
          <label class="gm-lobby-page__toggle">
            <input
              bind:checked={resetKeepPlayers}
              type="checkbox"
              disabled={loading || pendingActionId !== null || !resetAction?.enabled}
            />
            <span>Keep current players</span>
          </label>

          <label class="gm-lobby-page__toggle">
            <input
              bind:checked={resetKeepLoadouts}
              type="checkbox"
              disabled={loading || pendingActionId !== null || !resetAction?.enabled}
            />
            <span>Keep current loadouts</span>
          </label>

          <label class="gm-lobby-page__field">
            <span>New seed</span>
            <input
              bind:value={resetSeedInput}
              type="text"
              placeholder="Optional integer seed"
              disabled={loading || pendingActionId !== null || !resetAction?.enabled}
            />
          </label>

          <div class="gm-lobby-page__controls">
            <button
              type="button"
              disabled={loading || pendingActionId !== null || !resetAction?.enabled}
              onclick={() => void runReset()}
            >
              {pendingActionId === 'gmLobby.reset' ? getPendingActionLabel('gmLobby.reset') : resetAction?.label ?? 'Reset session'}
            </button>
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Action zone"
      description="Combat start now follows the server-declared screen action instead of a frontend retry script."
    >
      <div class="gm-lobby-page__action-stack">
        <label class="gm-lobby-page__field">
          <span>Start as player</span>
          <select
            bind:value={selectedStartPlayerId}
            disabled={loading || pendingActionId !== null || !startCombatAction?.enabled || !screen.startCombat.selectableStartPlayers.length}
          >
            <option value="">Use recommended player</option>
            {#each screen.startCombat.selectableStartPlayers as player}
              <option value={player.playerId}>
                {player.label}
              </option>
            {/each}
          </select>
        </label>

        <p class="gm-lobby-page__action-note">
          START_COMBAT uses screen version {screen.version} with player `{selectedStartPlayerId || screen.startCombat.recommendedStartPlayerId || 'unselected'}`.
        </p>

        {#if selectedStartPlayerLabel}
          <p class="gm-lobby-page__action-note">
            Selected start player: {selectedStartPlayerLabel}
          </p>
        {/if}

        {#if startBlockedMessage}
          <p class="gm-lobby-page__action-note gm-lobby-page__action-note--warning">
            {startBlockedMessage}
          </p>
        {/if}

        {#if startCombatAction && !startCombatAction.enabled}
          <ContentStatePanel
            title="Combat start unavailable"
            message={startCombatAction.disabledReason?.userMessage ?? 'Combat start is currently unavailable.'}
            tone="error"
          />
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
        <a class="gm-lobby-page__link-action" data-nav href={pathBuilders.combat(screen.sessionCode)}>
          Open combat command
        </a>
        <button
          type="button"
          disabled={loading || pendingActionId !== null || !startCombatAction?.enabled}
          onclick={() => void runStartCombat()}
        >
          {pendingActionId === 'gmLobby.startCombat'
            ? getPendingActionLabel('gmLobby.startCombat')
            : startCombatAction?.label ?? 'Start combat'}
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
  .gm-lobby-page__guide p,
  .gm-lobby-page__summary-meta {
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

  .gm-lobby-page__summary-meta,
  .gm-lobby-page__guide p,
  .gm-lobby-page__action-note {
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
  .gm-lobby-page__participant-details dt,
  .gm-lobby-page__field span {
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

  .gm-lobby-page__action-note {
    margin: 0;
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
