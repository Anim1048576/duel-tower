<script lang="ts">
  import { onMount } from 'svelte'
  import { listCharacters } from '../lib/api/characters'
  import type { CharacterProfileResponse } from '../lib/api/characterTypes'
  import { listCards, listPassives } from '../lib/api/content'
  import type { CardDefinition, PassiveDefinition } from '../lib/api/contentTypes'
  import { listPresets } from '../lib/api/presets'
  import type { PresetResponse } from '../lib/api/presetTypes'
  import {
    applyPresetToSession,
    getSessionState,
    leaveSession,
    updatePlayerReady,
    updateSessionLoadout,
  } from '../lib/api/sessions'
  import type { OwnedCardDto, PlayerStateDto, SessionStateDto } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    addPresetIdentifier,
    normalizePresetEditorState,
    normalizePresetIdentifier,
  } from '../lib/presets/editorModel'
  import {
    clearStoredSessionAccess,
    hasStoredSessionCode,
    isStoredPlayerSessionAccess,
    normalizeSessionCode,
    readStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    cloneSessionLoadoutDraft,
    createEmptySessionLoadoutDraft,
    createSessionLoadoutDraft,
    isSessionLoadoutDraftDirty,
    normalizeSessionLoadoutDraft,
    type SessionLoadoutDraft,
  } from '../lib/session/loadoutEditor'
  import {
    playerLobbyStateCopy,
    readSessionPageFeedback,
    sessionEntryStateCopy,
    sessionPageStateCopy,
    setSessionPageFeedback,
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
  let actionErrorTitle = $state('Player action failed')
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)
  let session = $state<SessionStateDto | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let requestSequence = 0
  let actionPending = $state<'ready' | 'leave' | 'save-loadout' | 'apply-preset' | null>(null)
  let referenceLoading = $state(true)
  let referenceErrorMessage = $state<string | null>(null)
  let characters = $state<CharacterProfileResponse[]>([])
  let cards = $state<CardDefinition[]>([])
  let passives = $state<PassiveDefinition[]>([])
  let presetsLoading = $state(true)
  let presetsErrorMessage = $state<string | null>(null)
  let presets = $state<PresetResponse[]>([])
  let selectedPresetId = $state('')
  let loadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let savedLoadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let ownedCardCandidate = $state('')
  let passiveCandidate = $state('')

  function getSessionCodeFromRoute() {
    if (typeof window === 'undefined') return null
    const match = resolveRouteMatch(window.location.pathname)
    if (match?.page.key !== 'player-lobby') return null
    const code = match.params.code?.trim()
    return code ? normalizeSessionCode(code) : null
  }

  function getInvalidAccessMessage(nextRouteCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextRouteCode) {
      return 'No session code is present in the current player lobby URL.'
    }
    if (!isStoredPlayerSessionAccess(nextAccess)) {
      return 'Player session access is not available. Re-enter through the session entry page first.'
    }
    if (!hasStoredSessionCode(nextAccess, nextRouteCode)) {
      return 'The stored player session access does not match the requested session code.'
    }
    return null
  }

  function parseIdentifierText(value: string) {
    return value
      .split(/\r?\n|,/)
      .map((entry) => entry.trim())
      .filter(Boolean)
  }

  function formatIdentifierText(values: readonly string[]) {
    return values.join('\n')
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') return
    window.history[replace ? 'replaceState' : 'pushState']({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function getResolvedCharacter(characterId: number | null) {
    return characterId === null ? null : characters.find((character) => character.id === characterId) ?? null
  }

  function getResolvedCard(cardId: string) {
    const normalized = normalizePresetIdentifier(cardId)
    return cards.find((card) => card.id === normalized) ?? null
  }

  function getResolvedPassive(passiveId: string) {
    const normalized = normalizePresetIdentifier(passiveId)
    return passives.find((passive) => passive.id === normalized) ?? null
  }

  function getOwnedCardDefinition(ownedCard: OwnedCardDto) {
    return getResolvedCard(ownedCard.cardId)
  }

  function getResolvedOwnedCard(ownedCardId: string) {
    const ownedCard = currentPlayer?.ownedCards.find((entry) => entry.ownedCardId === ownedCardId) ?? null
    return {
      ownedCard,
      card: ownedCard ? getOwnedCardDefinition(ownedCard) : null,
    }
  }

  function formatPlayerLoadoutSummary(player: PlayerStateDto) {
    const passiveSummary = player.passiveIds.length > 0 ? `${player.passiveIds.length} passives` : 'No passives'
    const exSummary = player.exCard ? `EX ${player.exCard}` : 'No EX card'
    return `Deck ${player.deckOwnedCardIds.length} cards | ${passiveSummary} | ${exSummary}`
  }

  function buildParticipantStateLabel(player: PlayerStateDto, playerId: string) {
    if (playerId === currentPlayerId) {
      return player.ready ? 'You · Ready' : 'You · Joined'
    }
    return player.ready ? 'Ready' : 'Joined'
  }

  function buildParticipantTone(player: PlayerStateDto, playerId: string) {
    if (playerId === currentPlayerId) {
      return player.ready ? 'success' : 'accent'
    }
    return player.ready ? 'success' : 'muted'
  }

  function buildParticipantItems(nextSession: SessionStateDto | null) {
    if (!nextSession) return [] as LobbyParticipantItem[]

    return Object.values(nextSession.players)
      .sort((left, right) => {
        if (left.playerId === currentPlayerId) return -1
        if (right.playerId === currentPlayerId) return 1
        return left.playerId.localeCompare(right.playerId)
      })
      .map((player, index) => ({
        id: player.playerId,
        slot: `P${index + 1}`,
        name: player.playerId === currentPlayerId ? `${player.playerId} (You)` : player.playerId,
        state: buildParticipantStateLabel(player, player.playerId),
        tone: buildParticipantTone(player, player.playerId),
        note: formatPlayerLoadoutSummary(player),
      }))
  }

  function syncLoadoutStateFromPlayer(
    player: PlayerStateDto | null,
    options: { preferredCharacterId?: number | null; force?: boolean } = {},
  ) {
    const nextBase = createSessionLoadoutDraft(player, options.preferredCharacterId ?? loadoutDraft.characterId)

    if (options.force || !isSessionLoadoutDraftDirty(savedLoadoutDraft, loadoutDraft)) {
      loadoutDraft = cloneSessionLoadoutDraft(nextBase)
    }

    savedLoadoutDraft = nextBase
    ownedCardCandidate = ''
    passiveCandidate = ''
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

    if (characterResult.status === 'fulfilled') characters = characterResult.value
    else {
      characters = []
      errors.push('character roster')
    }

    if (cardResult.status === 'fulfilled') cards = cardResult.value
    else {
      cards = []
      errors.push('card archive')
    }

    if (passiveResult.status === 'fulfilled') passives = passiveResult.value
    else {
      passives = []
      errors.push('passive archive')
    }

    referenceErrorMessage =
      errors.length > 0
        ? `Some loadout reference data could not be restored: ${errors.join(', ')}. Manual ids still work.`
        : null
    referenceLoading = false
  }

  async function loadAvailablePresets() {
    presetsLoading = true
    presetsErrorMessage = null

    try {
      const response = await listPresets()
      presets = response
      selectedPresetId = response[0] ? String(response[0].id) : ''
    } catch (error) {
      presets = []
      selectedPresetId = ''
      presetsErrorMessage = getApiErrorMessage(error, 'Unable to load the current preset archive.')
    } finally {
      presetsLoading = false
    }
  }

  async function loadPlayerLobbyState() {
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

      if (requestId !== requestSequence) return

      session = response
      setSelectionHandoff(selectionHandoffKeys.sessionCode, response.sessionCode)
      removeSelectionHandoff(selectionHandoffKeys.sessionId)
      syncLoadoutStateFromPlayer(currentPlayerId ? response.players[currentPlayerId] ?? null : null, { force: true })
    } catch (error) {
      if (requestId !== requestSequence) return

      if (typeof error === 'object' && error && 'status' in error && error.status === 404) {
        notFound = true
      } else {
        errorMessage = getApiErrorMessage(error, 'Unable to restore the current player lobby.')
      }
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function syncLobbyState(nextSession: SessionStateDto) {
    session = nextSession
    setSelectionHandoff(selectionHandoffKeys.sessionCode, nextSession.sessionCode)
    removeSelectionHandoff(selectionHandoffKeys.sessionId)
  }

  async function handleReadyToggle() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    actionPending = 'ready'
    actionErrorTitle = 'Player action failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await updatePlayerReady(
        routeSessionCode,
        currentPlayerId,
        { ready: !currentPlayer.ready },
        runtimeAccess.playerToken,
      )
      syncLobbyState(response)
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to update the current ready state.')
    } finally {
      actionPending = null
    }
  }

  async function handleLeave() {
    if (loading || actionPending || !routeSessionCode || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    actionPending = 'leave'
    actionErrorTitle = 'Player action failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      await leaveSession(routeSessionCode, runtimeAccess.playerToken)
      clearStoredSessionAccess()
      removeSelectionHandoff(selectionHandoffKeys.sessionId)
      removeSelectionHandoff(selectionHandoffKeys.sessionCode)
      setSessionPageFeedback(sessionEntryStateCopy.leftFeedback)
      navigateTo(pathBuilders.sessionEntry(), true)
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to leave the current session.')
      actionPending = null
    }
  }

  async function handleSaveLoadout() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    const normalizedDraft = normalizeSessionLoadoutDraft(loadoutDraft)
    loadoutDraft = normalizedDraft

    if (normalizedDraft.characterId === null) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = 'Character selection is required before saving the current loadout.'
      actionSuccessMessage = null
      return
    }

    if (!normalizedDraft.exCardId) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = 'EX card selection is required before saving the current loadout.'
      actionSuccessMessage = null
      return
    }

    actionPending = 'save-loadout'
    actionErrorTitle = 'Loadout save failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await updateSessionLoadout(
        routeSessionCode,
        currentPlayerId,
        {
          characterId: normalizedDraft.characterId,
          passiveIds: normalizedDraft.passiveIds,
          deckOwnedCardIds: normalizedDraft.deckOwnedCardIds,
          exCardId: normalizedDraft.exCardId,
        },
        runtimeAccess.playerToken,
      )

      syncLobbyState(response)
      syncLoadoutStateFromPlayer(response.players[currentPlayerId] ?? null, {
        preferredCharacterId: normalizedDraft.characterId,
        force: true,
      })
      actionSuccessMessage = playerLobbyStateCopy.loadoutSavedFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to save the current loadout.')
    } finally {
      actionPending = null
    }
  }

  async function handleApplyPreset() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    const presetId = Number(selectedPresetId)
    const preset = presets.find((entry) => String(entry.id) === selectedPresetId) ?? null

    if (!Number.isInteger(presetId) || presetId <= 0 || !preset) {
      actionErrorTitle = 'Preset apply failed'
      actionErrorMessage = 'Choose a saved preset before applying it to the current session.'
      actionSuccessMessage = null
      return
    }

    actionPending = 'apply-preset'
    actionErrorTitle = 'Preset apply failed'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const response = await applyPresetToSession(
        routeSessionCode,
        currentPlayerId,
        { presetId },
        runtimeAccess.playerToken,
      )

      syncLobbyState(response)
      syncLoadoutStateFromPlayer(response.players[currentPlayerId] ?? null, {
        preferredCharacterId: preset.characterId,
        force: true,
      })
      actionSuccessMessage = `${playerLobbyStateCopy.presetAppliedFeedback.message} (${preset.name})`
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to apply the selected preset.')
    } finally {
      actionPending = null
    }
  }

  function updateCharacterId(value: string) {
    const normalized = value.trim()
    const nextValue = normalized ? Number(normalized) : null
    loadoutDraft = {
      ...loadoutDraft,
      characterId: nextValue !== null && Number.isFinite(nextValue) ? nextValue : null,
    }
  }

  function updateDeckOwnedCardIds(value: string) {
    loadoutDraft = {
      ...loadoutDraft,
      deckOwnedCardIds: parseIdentifierText(value),
    }
  }

  function updatePassiveIds(value: string) {
    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: parseIdentifierText(value),
    }
  }

  function addOwnedCardCandidate() {
    const nextValue = normalizePresetIdentifier(ownedCardCandidate)
    if (!nextValue) return
    loadoutDraft = {
      ...loadoutDraft,
      deckOwnedCardIds: addPresetIdentifier(loadoutDraft.deckOwnedCardIds, nextValue),
    }
    ownedCardCandidate = ''
  }

  function addPassiveCandidate() {
    const nextValue = normalizePresetIdentifier(passiveCandidate)
    if (!nextValue) return
    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: addPresetIdentifier(loadoutDraft.passiveIds, nextValue),
    }
    passiveCandidate = ''
  }

  function handleWindowStateChange() {
    void loadPlayerLobbyState()
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
    void loadPlayerLobbyState()
    void loadReferenceCatalogs()
    void loadAvailablePresets()
    window.addEventListener('popstate', handleWindowStateChange)
    return () => window.removeEventListener('popstate', handleWindowStateChange)
  })

  const routeSessionCode = $derived.by(() => getSessionCodeFromRoute())
  const currentPlayerId = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess) ? runtimeAccess.playerId : null,
  )
  const currentPlayer = $derived.by(() =>
    currentPlayerId && session ? session.players[currentPlayerId] ?? null : null,
  )
  const currentPlayerSummary = $derived.by(() =>
    currentPlayer ? formatPlayerLoadoutSummary(currentPlayer) : 'Current player information is not available yet.',
  )
  const participantItems = $derived.by(() => buildParticipantItems(session))
  const participantCount = $derived.by(() => (session ? Object.keys(session.players).length : 0))
  const readyCount = $derived.by(() =>
    session ? Object.values(session.players).filter((player) => player.ready).length : 0,
  )
  const readyActionLabel = $derived.by(() =>
    actionPending === 'ready'
      ? currentPlayer?.ready ? 'Clearing ready...' : 'Setting ready...'
      : currentPlayer?.ready ? 'Clear ready' : 'Ready up',
  )
  const leaveActionLabel = $derived.by(() =>
    actionPending === 'leave' ? 'Leaving session...' : 'Leave session',
  )
  const saveLoadoutLabel = $derived.by(() =>
    actionPending === 'save-loadout' ? 'Saving loadout...' : 'Save loadout',
  )
  const applyPresetLabel = $derived.by(() =>
    actionPending === 'apply-preset' ? 'Applying preset...' : 'Apply preset',
  )
  const loadoutDirty = $derived.by(() =>
    isSessionLoadoutDraftDirty(savedLoadoutDraft, normalizeSessionLoadoutDraft(loadoutDraft)),
  )
  const resolvedDraftCharacter = $derived.by(() => getResolvedCharacter(loadoutDraft.characterId))
  const resolvedDraftExCard = $derived.by(() =>
    loadoutDraft.exCardId ? getResolvedCard(loadoutDraft.exCardId) : null,
  )
  const selectedPreset = $derived.by(
    () => presets.find((preset) => String(preset.id) === selectedPresetId) ?? null,
  )
  const selectedPresetPreviewState = $derived.by(() =>
    selectedPreset ? normalizePresetEditorState({
      name: selectedPreset.name,
      characterId: selectedPreset.characterId,
      deckCardIds: selectedPreset.deckCardIds,
      exCardId: selectedPreset.exCardId,
      passiveIds: selectedPreset.passiveIds,
    }) : null,
  )
  const resolvedPresetCharacter = $derived.by(() =>
    selectedPresetPreviewState ? getResolvedCharacter(selectedPresetPreviewState.characterId) : null,
  )
  const resolvedPresetExCard = $derived.by(() =>
    selectedPresetPreviewState?.exCardId ? getResolvedCard(selectedPresetPreviewState.exCardId) : null,
  )
  const ownedCardOptions = $derived.by(() =>
    (currentPlayer?.ownedCards ?? []).map((ownedCard) => {
      const card = getOwnedCardDefinition(ownedCard)
      return {
        id: ownedCard.ownedCardId,
        label: card ? `${card.name} (${ownedCard.ownedCardId})` : `${ownedCard.cardId} (${ownedCard.ownedCardId})`,
      }
    }),
  )
  const exCardOptions = $derived.by(() => cards.filter((card) => card.type === 'EX'))
  const deckOwnedCardItems = $derived.by(() =>
    loadoutDraft.deckOwnedCardIds.map((ownedCardId, index) => {
      const resolved = getResolvedOwnedCard(ownedCardId)
      if (!resolved.ownedCard) {
        return {
          id: `owned-card-${index + 1}`,
          title: ownedCardId,
          subtitle: 'Owned card id',
          meta: `Entry ${index + 1} | Unresolved`,
          note: 'This owned card id is not present in the current player inventory.',
          tags: [{ label: 'Unresolved', tone: 'warning' as const }],
        }
      }

      return {
        id: `owned-card-${index + 1}`,
        title: resolved.card ? `${resolved.card.name} (${resolved.ownedCard.ownedCardId})` : `${resolved.ownedCard.cardId} (${resolved.ownedCard.ownedCardId})`,
        subtitle: resolved.card ? getCardTypeLabel(resolved.card.type) : 'Owned card',
        meta: `Entry ${index + 1}${resolved.card ? ` | ${buildCardArchiveMeta(resolved.card)}` : ''}`,
        note: resolved.card?.description ?? 'Current player inventory card.',
        tags: resolved.card ? buildCardDisplayTags(resolved.card) : [{ label: 'Owned', tone: 'muted' as const }],
      }
    }),
  )
  const passivePreviewItems = $derived.by(() =>
    loadoutDraft.passiveIds.map((passiveId, index) => {
      const passive = getResolvedPassive(passiveId)
      return passive
        ? {
            id: `loadout-passive-${index + 1}`,
            title: `${passive.name} (${passive.id})`,
            subtitle: 'Passive definition',
            meta: `Entry ${index + 1} | Priority ${passive.priority ?? 'N/A'}`,
            note: passive.description,
            tags: [{ label: 'Passive', tone: 'success' as const }],
          }
        : {
            id: `loadout-passive-${index + 1}`,
            title: passiveId,
            subtitle: 'Passive id',
            meta: `Entry ${index + 1} | Unresolved`,
            note: 'This passive id was not found in the current passive archive.',
            tags: [{ label: 'Unresolved', tone: 'warning' as const }],
          }
    }),
  )
  const presetDeckPreviewItems = $derived.by(() =>
    (selectedPresetPreviewState?.deckCardIds ?? []).map((cardId, index) => {
      const card = getResolvedCard(cardId)
      return card
        ? {
            id: `preset-card-${index + 1}`,
            title: `${card.name} (${card.id})`,
            subtitle: getCardTypeLabel(card.type),
            meta: `Entry ${index + 1} | ${buildCardArchiveMeta(card)}`,
            note: card.description,
            tags: buildCardDisplayTags(card),
          }
        : {
            id: `preset-card-${index + 1}`,
            title: cardId,
            subtitle: 'Preset card id',
            meta: `Entry ${index + 1} | Unresolved`,
            note: 'This preset card id was not found in the current card archive.',
            tags: [{ label: 'Unresolved', tone: 'warning' as const }],
          }
    }),
  )
  const presetPassivePreviewItems = $derived.by(() =>
    (selectedPresetPreviewState?.passiveIds ?? []).map((passiveId, index) => {
      const passive = getResolvedPassive(passiveId)
      return passive
        ? {
            id: `preset-passive-${index + 1}`,
            title: `${passive.name} (${passive.id})`,
            subtitle: 'Passive definition',
            meta: `Entry ${index + 1} | Priority ${passive.priority ?? 'N/A'}`,
            note: passive.description,
            tags: [{ label: 'Passive', tone: 'success' as const }],
          }
        : {
            id: `preset-passive-${index + 1}`,
            title: passiveId,
            subtitle: 'Preset passive id',
            meta: `Entry ${index + 1} | Unresolved`,
            note: 'This preset passive id was not found in the current passive archive.',
            tags: [{ label: 'Unresolved', tone: 'warning' as const }],
          }
    }),
  )
</script>

<div class="player-lobby-page">
  {#if loading}
    <SectionFrame eyebrow="Session Summary" title="Loading player lobby" description="Restoring the current session state from the live session API.">
      <ContentStatePanel title={sessionPageStateCopy.loading.title} message="Fetching the current player lobby by session code." />
    </SectionFrame>
  {:else if invalidAccessMessage}
    <SectionFrame eyebrow="Player Access" title="Player lobby access is unavailable" description="This page now expects player runtime access that matches the current session code.">
      <ContentStatePanel title={sessionPageStateCopy.invalidPlayerAccess.title} message={invalidAccessMessage} tone="error">
        <p>Requested code: {routeSessionCode ?? 'Unavailable'}</p>
        <p>Open the session entry screen and join the session again to restore player access.</p>
      </ContentStatePanel>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if notFound}
    <SectionFrame eyebrow="Session Missing" title="Session not found" description="The requested player lobby code did not resolve to a live session.">
      <ContentStatePanel title={sessionPageStateCopy.notFound.title} message={sessionPageStateCopy.notFound.message} tone="error">
        <p>Requested code: {routeSessionCode ?? 'Unavailable'}</p>
        <p>Check the code from the session entry page and try again.</p>
      </ContentStatePanel>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame eyebrow="Session Summary" title="Player lobby could not be loaded" description="The session code was valid, but the current lobby state could not be restored.">
      <ContentStatePanel title="Unable to load player lobby" message={errorMessage} tone="error" actionLabel="Retry load" onAction={() => void loadPlayerLobbyState()} />
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
      </div>
    </SectionFrame>
  {:else if session}
    <SectionFrame eyebrow="Session Summary" title={`Session ${session.sessionCode}`} description="Player lobby restores the current session from the URL code and now supports direct loadout save or preset apply.">
      <div class="player-lobby-page__summary">
        <div class="player-lobby-page__summary-copy">
          <p>Player lobby</p>
          <h3>Code: {session.sessionCode}</h3>
        </div>
        <div class="player-lobby-page__summary-tags">
          <TagChip label="Player View" tone="accent" />
          <TagChip label={`Me: ${currentPlayerId ?? 'Unknown'}`} tone="success" />
        </div>
      </div>
      <div class="player-lobby-page__stats">
        <StatBlock value={participantCount} label="Joined" note="Current live participants" />
        <StatBlock value={readyCount} label="Ready" note="Players marked ready in the current state" />
        <StatBlock value={currentPlayer?.ready ? 'Ready' : 'Joined'} label="My state" note={currentPlayerId ?? 'Current player id'} />
      </div>
      {#if feedback}
        <ContentStatePanel title={feedback.title} message={feedback.message} />
      {/if}
      {#if referenceErrorMessage}
        <ContentStatePanel title="Loadout reference data unavailable" message={referenceErrorMessage} />
      {/if}
      {#if actionErrorMessage}
        <ContentStatePanel title={actionErrorTitle} message={actionErrorMessage} tone="error" />
      {:else if actionSuccessMessage}
        <ContentStatePanel title="Player action completed" message={actionSuccessMessage} />
      {/if}
    </SectionFrame>

    <div class="player-lobby-page__main">
      <SectionFrame title="Participant slots" description="The participant grid reflects the live session state while keeping the existing lobby shell.">
        {#if participantItems.length > 0}
          <div class="player-lobby-page__slots">
            {#each participantItems as participant}
              <ParticipantSlot slot={participant.slot} name={participant.name} state={participant.state} tone={participant.tone} note={participant.note} />
            {/each}
          </div>
        {:else}
          <ContentStatePanel title="No participants yet" message="The current session does not have any joined player slots to show yet." />
        {/if}
      </SectionFrame>

      <SectionFrame title="Current loadout" description="Current player draft and current session summary stay separate from preset apply.">
        <div class="player-lobby-page__guide">
          <p>Current player id: {currentPlayerId ?? 'Unavailable'}</p>
          <p>Ready state: {currentPlayer?.ready ? 'Ready' : 'Not ready yet'}</p>
          <p>{currentPlayerSummary}</p>
          <p>Character: {resolvedDraftCharacter ? `${resolvedDraftCharacter.name} #${resolvedDraftCharacter.id}` : loadoutDraft.characterId !== null ? `Character #${loadoutDraft.characterId} (unresolved)` : 'Not selected yet'}</p>
          <p>EX: {resolvedDraftExCard ? `${resolvedDraftExCard.name} (${resolvedDraftExCard.id})` : loadoutDraft.exCardId || 'Not selected yet'}</p>
        </div>
        <div class="player-lobby-page__todo">
          <p>{loadoutDirty ? 'Current draft has unsaved loadout changes.' : 'Current draft matches the last synced loadout state.'}</p>
          <p>Preset apply keeps a separate preview so the current draft and the preset source do not get mixed.</p>
        </div>
      </SectionFrame>
    </div>

    <div class="player-lobby-page__main">
      <SectionFrame title="Direct loadout save" description="Edit the current player loadout directly and save it with the player session token.">
        <div class="player-lobby-page__form-grid">
          <label class="player-lobby-page__field">
            <span>Character</span>
            <select value={loadoutDraft.characterId === null ? '' : String(loadoutDraft.characterId)} disabled={loading || actionPending !== null || referenceLoading} onchange={(event) => updateCharacterId((event.currentTarget as HTMLSelectElement).value)}>
              <option value="">Select character</option>
              {#if loadoutDraft.characterId !== null && !resolvedDraftCharacter}
                <option value={String(loadoutDraft.characterId)}>Character #{loadoutDraft.characterId} (unresolved)</option>
              {/if}
              {#each characters as character}
                <option value={String(character.id)}>{character.name} #{character.id}</option>
              {/each}
            </select>
          </label>

          <label class="player-lobby-page__field">
            <span>EX card</span>
            <select value={loadoutDraft.exCardId} disabled={loading || actionPending !== null || referenceLoading} onchange={(event) => (loadoutDraft = { ...loadoutDraft, exCardId: normalizePresetIdentifier((event.currentTarget as HTMLSelectElement).value) })}>
              <option value="">Select EX card</option>
              {#if loadoutDraft.exCardId && !resolvedDraftExCard}
                <option value={loadoutDraft.exCardId}>{loadoutDraft.exCardId} (unresolved)</option>
              {/if}
              {#each exCardOptions as card}
                <option value={card.id}>{card.name} ({card.id})</option>
              {/each}
            </select>
          </label>

          <label class="player-lobby-page__field player-lobby-page__field--span-2">
            <span>Deck owned card ids</span>
            <div class="player-lobby-page__picker-row">
              <select bind:value={ownedCardCandidate} disabled={loading || actionPending !== null}>
                <option value="">Quick add owned card</option>
                {#each ownedCardOptions as option}
                  <option value={option.id}>{option.label}</option>
                {/each}
              </select>
              <button type="button" disabled={loading || actionPending !== null || !ownedCardCandidate} onclick={addOwnedCardCandidate}>Add card</button>
            </div>
            <textarea rows="6" value={formatIdentifierText(loadoutDraft.deckOwnedCardIds)} placeholder="One owned card id per line" disabled={loading || actionPending !== null} oninput={(event) => updateDeckOwnedCardIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
          </label>

          <label class="player-lobby-page__field player-lobby-page__field--span-2">
            <span>Passive ids</span>
            <div class="player-lobby-page__picker-row">
              <select bind:value={passiveCandidate} disabled={loading || actionPending !== null || referenceLoading}>
                <option value="">Quick add passive</option>
                {#each passives as passive}
                  <option value={passive.id}>{passive.name} ({passive.id})</option>
                {/each}
              </select>
              <button type="button" disabled={loading || actionPending !== null || !passiveCandidate} onclick={addPassiveCandidate}>Add passive</button>
            </div>
            <textarea rows="4" value={formatIdentifierText(loadoutDraft.passiveIds)} placeholder="One passive id per line" disabled={loading || actionPending !== null} oninput={(event) => updatePassiveIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
          </label>
        </div>

        <div class="player-lobby-page__actions">
          <button type="button" onclick={() => void handleSaveLoadout()} disabled={loading || actionPending !== null || !isStoredPlayerSessionAccess(runtimeAccess) || !loadoutDirty}>{saveLoadoutLabel}</button>
        </div>

        <div class="player-lobby-page__grid">
          <EntityListPane items={deckOwnedCardItems} emptyMessage="No deck owned card ids are currently assigned to the draft." />
          <EntityListPane items={passivePreviewItems} emptyMessage="No passive ids are currently assigned to the draft." />
        </div>
      </SectionFrame>

      <SectionFrame title="Apply saved preset" description="Choose a saved preset, review its resolved references, and apply it to the live session loadout.">
        {#if presetsErrorMessage}
          <ContentStatePanel title="Preset archive unavailable" message={presetsErrorMessage} tone="error" actionLabel="Retry presets" onAction={() => void loadAvailablePresets()} />
        {:else}
          <label class="player-lobby-page__field">
            <span>Preset</span>
            <select bind:value={selectedPresetId} disabled={loading || actionPending !== null || presetsLoading}>
              <option value="">Select preset</option>
              {#each presets as preset}
                <option value={String(preset.id)}>{preset.name} #{preset.id}</option>
              {/each}
            </select>
          </label>

          {#if selectedPreset}
            <div class="player-lobby-page__guide">
              <p>Preset owner: {selectedPreset.owner}</p>
              <p>Character: {resolvedPresetCharacter ? `${resolvedPresetCharacter.name} #${resolvedPresetCharacter.id}` : `#${selectedPreset.characterId}`}</p>
              <p>EX: {resolvedPresetExCard ? `${resolvedPresetExCard.name} (${resolvedPresetExCard.id})` : selectedPreset.exCardId}</p>
            </div>
            <div class="player-lobby-page__actions">
              <button type="button" onclick={() => void handleApplyPreset()} disabled={loading || actionPending !== null || !isStoredPlayerSessionAccess(runtimeAccess)}>{applyPresetLabel}</button>
            </div>
            <div class="player-lobby-page__grid">
              <EntityListPane items={presetDeckPreviewItems} emptyMessage="This preset has no saved deck card entries." />
              <EntityListPane items={presetPassivePreviewItems} emptyMessage="This preset has no saved passive entries." />
            </div>
          {:else if presetsLoading}
            <ContentStatePanel title="Loading preset archive" message="Restoring the saved preset list for the current player." />
          {:else if presets.length === 0}
            <ContentStatePanel title="No saved presets yet" message="Create a preset from the preset editor first, then apply it here." />
          {:else}
            <ContentStatePanel message="No saved preset is currently selected." />
          {/if}
        {/if}
      </SectionFrame>
    </div>

    <SectionFrame title="Action zone" description="Bottom action strip keeps the ready and leave lifecycle separate from loadout editing.">
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
        <button type="button" onclick={() => void handleReadyToggle()} disabled={loading || actionPending !== null || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)}>{readyActionLabel}</button>
        <button type="button" onclick={() => void handleLeave()} disabled={loading || actionPending !== null || !isStoredPlayerSessionAccess(runtimeAccess)}>{leaveActionLabel}</button>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .player-lobby-page,
  .player-lobby-page__main,
  .player-lobby-page__guide,
  .player-lobby-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .player-lobby-page__summary {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .player-lobby-page__summary-copy {
    display: grid;
    gap: 0.5rem;
  }

  .player-lobby-page__summary-copy p,
  .player-lobby-page__summary-copy h3,
  .player-lobby-page__guide p,
  .player-lobby-page__todo p {
    margin: 0;
  }

  .player-lobby-page__summary-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .player-lobby-page__summary-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .player-lobby-page__summary-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .player-lobby-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .player-lobby-page__main {
    grid-template-columns: minmax(0, 1.35fr) minmax(18rem, 0.65fr);
    align-items: start;
  }

  .player-lobby-page__slots,
  .player-lobby-page__grid,
  .player-lobby-page__form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .player-lobby-page__field {
    display: grid;
    gap: 0.5rem;
  }

  .player-lobby-page__field--span-2 {
    grid-column: span 2;
  }

  .player-lobby-page__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .player-lobby-page__field select,
  .player-lobby-page__field textarea,
  .player-lobby-page__picker-row button {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .player-lobby-page__field textarea {
    min-height: 7rem;
    resize: vertical;
  }

  .player-lobby-page__guide p,
  .player-lobby-page__todo p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .player-lobby-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .player-lobby-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .player-lobby-page__picker-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.75rem;
  }

  .player-lobby-page__picker-row button {
    width: fit-content;
    min-width: 8rem;
  }

  .player-lobby-page__link-action,
  .player-lobby-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .player-lobby-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  @media (max-width: 960px) {
    .player-lobby-page__stats,
    .player-lobby-page__main,
    .player-lobby-page__slots,
    .player-lobby-page__grid,
    .player-lobby-page__form-grid {
      grid-template-columns: 1fr;
    }

    .player-lobby-page__field--span-2 {
      grid-column: span 1;
    }

    .player-lobby-page__picker-row {
      grid-template-columns: 1fr;
    }

    .player-lobby-page__picker-row button {
      width: 100%;
    }
  }
</style>
