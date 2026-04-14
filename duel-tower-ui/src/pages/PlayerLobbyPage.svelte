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
  import type {
    OwnedCardDto,
    PlayerStateDto,
    SessionRequestAccess,
    SessionStateDto,
  } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders } from '../lib/navigation'
  import {
    addPresetIdentifier,
    normalizePresetEditorState,
    normalizePresetIdentifier,
  } from '../lib/presets/editorModel'
  import {
    clearStoredSessionAccess,
    hasStoredSessionCode,
    isStoredPlayerSessionAccess,
    readStoredSessionAccess,
    toPlayerReadAccess,
    updateStoredSessionAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    cloneSessionLoadoutDraft,
    createEmptySessionLoadoutDraft,
    createSessionLoadoutDraft,
    isSessionLoadoutDraftDirty,
    normalizeSessionLoadoutDraft,
    resolveSessionLoadoutExCardId,
    type SessionLoadoutDraft,
  } from '../lib/session/loadoutEditor'
  import {
    createLiveSessionPage,
  } from '../lib/session/liveSessionPage'
  import {
    playerLobbyStateCopy,
    readSessionPageFeedback,
    sessionEntryStateCopy,
    sessionPageStateCopy,
    setSessionPageFeedback,
    type SessionPageFeedback,
  } from '../lib/session/pageState'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'
  import { readSessionCodeFromRoute } from '../lib/session/sessionRoute'
  import { removeSelectionHandoff, selectionHandoffKeys } from '../lib/selectionHandoff'

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
  let actionSuccessTitle = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)
  let session = $state<SessionStateDto | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
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
  let lastAppliedPresetName = $state<string | null>(null)
  let loadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let savedLoadoutDraft = $state<SessionLoadoutDraft>(createEmptySessionLoadoutDraft())
  let ownedCardCandidate = $state('')
  let passiveCandidate = $state('')
  let exCardEdited = $state(false)
  let deckOwnedCardIdsEdited = $state(false)
  let passiveIdsEdited = $state(false)

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

  function formatCharacterLabel(characterId: number | null) {
    const resolvedCharacter = getResolvedCharacter(characterId)

    if (resolvedCharacter) {
      return `${resolvedCharacter.name} #${resolvedCharacter.id}`
    }

    if (characterId !== null) {
      return `Character #${characterId} (unresolved)`
    }

    return 'Not selected yet'
  }

  function formatExCardLabel(exCardId: string) {
    const resolvedCard = exCardId ? getResolvedCard(exCardId) : null

    if (resolvedCard) {
      return `${resolvedCard.name} (${resolvedCard.id})`
    }

    return exCardId || 'Not selected yet'
  }

  function formatPresetOptionLabel(preset: PresetResponse) {
    return `${preset.name} | Character #${preset.characterId} | ${preset.deckCardIds.length} cards | ${preset.passiveIds.length} passives`
  }

  function resetLoadoutEditState() {
    exCardEdited = false
    deckOwnedCardIdsEdited = false
    passiveIdsEdited = false
  }

  function persistSyncedCharacterId(characterId: number | null) {
    const nextAccess = updateStoredSessionAccess({
      characterId: characterId ?? undefined,
    })

    if (nextAccess) {
      runtimeAccess = nextAccess
    }
  }

  function formatPlayerLoadoutSummary(player: PlayerStateDto, nextSession: SessionStateDto | null) {
    const passiveSummary = player.passiveIds.length > 0 ? `${player.passiveIds.length} passives` : 'No passives'
    const resolvedExCardId = resolveSessionLoadoutExCardId(player, nextSession)
    const exSummary = resolvedExCardId ? `EX ${resolvedExCardId}` : 'No EX card'
    return `Deck ${player.deckOwnedCardIds.length} cards | ${passiveSummary} | ${exSummary}`
  }

  function buildParticipantStateLabel(player: PlayerStateDto, playerId: string) {
    if (playerId === currentPlayerId) {
      return player.ready ? 'You · Ready' : 'You · Joined'
    }
    return player.ready ? 'Ready' : 'Joined'
  }

  function buildParticipantTone(player: PlayerStateDto, playerId: string): LobbyParticipantItem['tone'] {
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
        note: formatPlayerLoadoutSummary(player, nextSession),
      }))
  }

  function syncLoadoutStateFromPlayer(
    player: PlayerStateDto | null,
    nextSession: SessionStateDto | null,
    options: { preferredCharacterId?: number | null; force?: boolean } = {},
  ) {
    const shouldReplaceDraft = options.force || !isSessionLoadoutDraftDirty(savedLoadoutDraft, loadoutDraft)
    const nextBase = createSessionLoadoutDraft(
      player,
      nextSession,
      options.preferredCharacterId ?? runtimeAccess?.characterId ?? loadoutDraft.characterId,
    )

    if (shouldReplaceDraft) {
      loadoutDraft = cloneSessionLoadoutDraft(nextBase)
      ownedCardCandidate = ''
      passiveCandidate = ''
      resetLoadoutEditState()
    }

    savedLoadoutDraft = nextBase
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
    const currentSelectedPresetId = selectedPresetId
    presetsLoading = true
    presetsErrorMessage = null

    try {
      const response = await listPresets()
      presets = response
      selectedPresetId =
        response.find((entry) => String(entry.id) === currentSelectedPresetId)
          ? currentSelectedPresetId
          : response[0]
            ? String(response[0].id)
            : ''
    } catch (error) {
      presets = []
      selectedPresetId = ''
      presetsErrorMessage = getApiErrorMessage(error, 'Unable to load the current preset archive.')
    } finally {
      presetsLoading = false
    }
  }

  function syncLobbyState(nextSession: SessionStateDto) {
    session = nextSession
    syncSessionSelectionHandoff(nextSession.sessionCode)
  }

  const playerLobbyPage = createLiveSessionPage<StoredSessionAccess | null>({
    readCode: () => routeSessionCode,
    readAccess: () => readStoredSessionAccess(),
    getInvalidMessage: getInvalidAccessMessage,
    loadState: getSessionState,
    getPollingAccess: toPlayerReadAccess,
    canPoll: ({ code, access, state }) =>
      state.sessionCode === code &&
      isStoredPlayerSessionAccess(access) &&
      hasStoredSessionCode(access, code),
    onBeforeLoad: ({ access, invalidMessage }) => {
      runtimeAccess = access
      invalidAccessMessage = invalidMessage
      loading = true
      notFound = false
      errorMessage = null
      actionErrorMessage = null
      actionSuccessTitle = null
      actionSuccessMessage = null
      session = null
    },
    onLoaded: (response, { access }) => {
      syncLobbyState(response)
      const nextPlayerId =
        access?.role === 'player' && typeof access.playerId === 'string' ? access.playerId : null
      syncLoadoutStateFromPlayer(nextPlayerId ? response.players[nextPlayerId] ?? null : null, response, {
        preferredCharacterId: access?.characterId ?? null,
        force: true,
      })
    },
    onPolled: (nextSession, { access }) => {
      runtimeAccess = access

      if (!isStoredPlayerSessionAccess(access)) {
        return
      }

      syncLobbyState(nextSession)
      syncLoadoutStateFromPlayer(nextSession.players[access.playerId] ?? null, nextSession, {
        preferredCharacterId: access.characterId ?? null,
      })
    },
    onNotFound: () => {
      notFound = true
    },
    onError: (error) => {
      errorMessage = getApiErrorMessage(error, 'Unable to restore the current player lobby.')
    },
    onLoadSettled: () => {
      loading = false
    },
  })

  async function loadPlayerLobbyState() {
    await playerLobbyPage.load()
  }

  function stopPlayerLobbyPolling() {
    playerLobbyPage.stopPolling()
  }

  function updatePlayerLobbyPollingVersion(nextSession: SessionStateDto) {
    playerLobbyPage.updatePollingVersion(nextSession.version)
  }

  function clearPlayerLobbyRuntimeState() {
    playerLobbyPage.dispose()
    session = null
    runtimeAccess = null
    invalidAccessMessage = null
    loadoutDraft = createEmptySessionLoadoutDraft()
    savedLoadoutDraft = createEmptySessionLoadoutDraft()
    ownedCardCandidate = ''
    passiveCandidate = ''
    selectedPresetId = ''
    lastAppliedPresetName = null
    resetLoadoutEditState()
  }

  async function handleReadyToggle() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    const requestedReady = !currentPlayer.ready
    actionPending = 'ready'
    actionErrorTitle = 'Ready update failed'
    actionErrorMessage = null
    actionSuccessTitle = null
    actionSuccessMessage = null

    try {
      const response = await updatePlayerReady(
        routeSessionCode,
        currentPlayerId,
        { ready: requestedReady },
        runtimeAccess.playerToken,
      )
      syncLobbyState(response)
      updatePlayerLobbyPollingVersion(response)
      const updatedReady = response.players[currentPlayerId]?.ready ?? requestedReady
      actionSuccessTitle = 'Ready updated'
      actionSuccessMessage = updatedReady
        ? 'You are marked ready in the current session.'
        : 'You are no longer marked ready in the current session.'
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to update the current ready state.')
    } finally {
      actionPending = null
    }
  }

  async function handleLeave() {
    if (loading || actionPending || !routeSessionCode || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    actionPending = 'leave'
    actionErrorTitle = 'Leave failed'
    actionErrorMessage = null
    actionSuccessTitle = null
    actionSuccessMessage = null

    try {
      const response = await leaveSession(routeSessionCode, runtimeAccess.playerToken)
      updatePlayerLobbyPollingVersion(response)
      clearStoredSessionAccess()
      removeSelectionHandoff(selectionHandoffKeys.sessionId)
      removeSelectionHandoff(selectionHandoffKeys.sessionCode)
      clearPlayerLobbyRuntimeState()
      actionPending = null
      setSessionPageFeedback(sessionEntryStateCopy.leftFeedback)
      navigateTo(pathBuilders.sessionEntry(), true)
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to leave the current session.')
      actionPending = null
    }
  }

  async function handleSaveLoadout() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    const normalizedDraft = normalizeSessionLoadoutDraft(loadoutDraft)
    const characterChanged = normalizedDraft.characterId !== savedLoadoutDraft.characterId
    const shouldSendExCard = !characterChanged || exCardEdited
    const shouldSendPassiveIds = !characterChanged || passiveIdsEdited
    const shouldSendDeckOwnedCardIds = !characterChanged || deckOwnedCardIdsEdited
    loadoutDraft = normalizedDraft

    if (normalizedDraft.characterId === null) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = 'Character selection is required before saving the current loadout.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    if (shouldSendExCard && !normalizedDraft.exCardId) {
      actionErrorTitle = 'Loadout save failed'
      actionErrorMessage = 'EX card selection is required before saving the current loadout.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    actionPending = 'save-loadout'
    actionErrorTitle = 'Loadout save failed'
    actionErrorMessage = null
    actionSuccessTitle = null
    actionSuccessMessage = null

    try {
      const payload = {
        characterId: normalizedDraft.characterId,
        ...(shouldSendPassiveIds ? { passiveIds: normalizedDraft.passiveIds } : {}),
        ...(shouldSendDeckOwnedCardIds ? { deckOwnedCardIds: normalizedDraft.deckOwnedCardIds } : {}),
        ...(shouldSendExCard ? { exCardId: normalizedDraft.exCardId } : {}),
      }
      const response = await updateSessionLoadout(
        routeSessionCode,
        currentPlayerId,
        payload,
        runtimeAccess.playerToken,
      )

      syncLobbyState(response)
      updatePlayerLobbyPollingVersion(response)
      persistSyncedCharacterId(normalizedDraft.characterId)
      syncLoadoutStateFromPlayer(response.players[currentPlayerId] ?? null, response, {
        preferredCharacterId: normalizedDraft.characterId,
        force: true,
      })
      actionSuccessTitle = playerLobbyStateCopy.loadoutSavedFeedback.title
      actionSuccessMessage = playerLobbyStateCopy.loadoutSavedFeedback.message
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to save the current loadout.')
    } finally {
      actionPending = null
    }
  }

  async function handleApplyPreset() {
    if (loading || actionPending || !routeSessionCode || !currentPlayerId || !currentPlayer || !isStoredPlayerSessionAccess(runtimeAccess)) {
      return
    }

    const presetId = Number(selectedPresetId)
    const preset = presets.find((entry) => String(entry.id) === selectedPresetId) ?? null

    if (!Number.isInteger(presetId) || presetId <= 0 || !preset) {
      actionErrorTitle = 'Preset apply failed'
      actionErrorMessage = 'Choose a saved preset before applying it to the current session.'
      actionSuccessTitle = null
      actionSuccessMessage = null
      return
    }

    actionPending = 'apply-preset'
    actionErrorTitle = 'Preset apply failed'
    actionErrorMessage = null
    actionSuccessTitle = null
    actionSuccessMessage = null

    try {
      const response = await applyPresetToSession(
        routeSessionCode,
        currentPlayerId,
        { presetId },
        runtimeAccess.playerToken,
      )

      syncLobbyState(response)
      updatePlayerLobbyPollingVersion(response)
      persistSyncedCharacterId(preset.characterId)
      syncLoadoutStateFromPlayer(response.players[currentPlayerId] ?? null, response, {
        preferredCharacterId: preset.characterId,
        force: true,
      })
      lastAppliedPresetName = preset.name
      actionSuccessTitle = playerLobbyStateCopy.presetAppliedFeedback.title
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
    const nextCharacterId = nextValue !== null && Number.isFinite(nextValue) ? nextValue : null

    if (nextCharacterId === loadoutDraft.characterId) {
      return
    }

    if (nextCharacterId === savedLoadoutDraft.characterId) {
      loadoutDraft = cloneSessionLoadoutDraft(savedLoadoutDraft)
      ownedCardCandidate = ''
      passiveCandidate = ''
      resetLoadoutEditState()
      return
    }

    loadoutDraft = {
      characterId: nextCharacterId,
      deckOwnedCardIds: [],
      exCardId: '',
      passiveIds: [],
    }
    ownedCardCandidate = ''
    passiveCandidate = ''
    resetLoadoutEditState()
  }

  function updateDeckOwnedCardIds(value: string) {
    loadoutDraft = {
      ...loadoutDraft,
      deckOwnedCardIds: parseIdentifierText(value),
    }
    deckOwnedCardIdsEdited = true
  }

  function updatePassiveIds(value: string) {
    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: parseIdentifierText(value),
    }
    passiveIdsEdited = true
  }

  function updateExCardId(value: string) {
    loadoutDraft = {
      ...loadoutDraft,
      exCardId: normalizePresetIdentifier(value),
    }
    exCardEdited = true
  }

  function addOwnedCardCandidate() {
    const nextValue = normalizePresetIdentifier(ownedCardCandidate)
    if (!nextValue) return
    loadoutDraft = {
      ...loadoutDraft,
      deckOwnedCardIds: addPresetIdentifier(loadoutDraft.deckOwnedCardIds, nextValue),
    }
    ownedCardCandidate = ''
    deckOwnedCardIdsEdited = true
  }

  function addPassiveCandidate() {
    const nextValue = normalizePresetIdentifier(passiveCandidate)
    if (!nextValue) return
    loadoutDraft = {
      ...loadoutDraft,
      passiveIds: addPresetIdentifier(loadoutDraft.passiveIds, nextValue),
    }
    passiveCandidate = ''
    passiveIdsEdited = true
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
    return () => {
      playerLobbyPage.dispose()
      window.removeEventListener('popstate', handleWindowStateChange)
    }
  })

  const routeSessionCode = $derived.by(() => readSessionCodeFromRoute('player-lobby'))
  const currentPlayerId = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess) ? runtimeAccess.playerId : null,
  )
  const currentPlayer = $derived.by(() =>
    currentPlayerId && session ? session.players[currentPlayerId] ?? null : null,
  )
  const currentPlayerSummary = $derived.by(() =>
    currentPlayer ? formatPlayerLoadoutSummary(currentPlayer, session) : 'Current player information is not available yet.',
  )
  const canEditOwnLoadout = $derived.by(() =>
    Boolean(
      session &&
        currentPlayer &&
        currentPlayerId &&
        isStoredPlayerSessionAccess(runtimeAccess) &&
        currentPlayer.playerId === currentPlayerId,
    ),
  )
  const loadoutEditGuardMessage = $derived.by(() => {
    if (!session || loading) return null
    if (!isStoredPlayerSessionAccess(runtimeAccess)) {
      return 'Player runtime access is required before editing the current loadout.'
    }
    if (!currentPlayerId) {
      return 'Current player information is unavailable in the saved player access.'
    }
    if (!currentPlayer) {
      return 'The current player is not present in the latest session state, so this loadout cannot be edited.'
    }
    return null
  })
  const participantItems = $derived.by(() => buildParticipantItems(session))
  const participantCount = $derived.by(() => (session ? Object.keys(session.players).length : 0))
  const readyCount = $derived.by(() =>
    session ? Object.values(session.players).filter((player) => player.ready).length : 0,
  )
  const readyStatusLabel = $derived.by(() =>
    currentPlayer?.ready ? 'Ready' : currentPlayer ? 'Not ready' : 'Unavailable',
  )
  const readyStatusTone = $derived.by(() =>
    currentPlayer?.ready ? 'success' : currentPlayer ? 'muted' : 'warning',
  )
  const canToggleReady = $derived.by(() =>
    Boolean(
      currentPlayer &&
        currentPlayerId &&
        isStoredPlayerSessionAccess(runtimeAccess) &&
        currentPlayer.playerId === currentPlayerId,
    ),
  )
  const canLeaveSession = $derived.by(() =>
    Boolean(
      currentPlayer &&
        currentPlayerId &&
        isStoredPlayerSessionAccess(runtimeAccess) &&
        runtimeAccess.playerId === currentPlayerId,
    ),
  )
  const membershipActionSummary = $derived.by(() => {
    if (!currentPlayer) {
      return 'Current player actions are unavailable until the session state is restored.'
    }

    return currentPlayer.ready
      ? 'You are marked ready. Clear ready if you need to adjust before the session proceeds.'
      : 'You are not ready yet. Toggle ready when you are prepared to stay in the session.'
  })
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
  const characterChangePending = $derived.by(() =>
    loadoutDraft.characterId !== null && loadoutDraft.characterId !== savedLoadoutDraft.characterId,
  )
  const deckEditingLocked = $derived.by(() => characterChangePending)
  const resolvedDraftCharacter = $derived.by(() => getResolvedCharacter(loadoutDraft.characterId))
  const resolvedDraftExCard = $derived.by(() =>
    loadoutDraft.exCardId ? getResolvedCard(loadoutDraft.exCardId) : null,
  )
  const syncedResolvedExCard = $derived.by(() =>
    savedLoadoutDraft.exCardId ? getResolvedCard(savedLoadoutDraft.exCardId) : null,
  )
  const selectedPreset = $derived.by(
    () => presets.find((preset) => String(preset.id) === selectedPresetId) ?? null,
  )
  const presetSummary = $derived.by(() =>
    presetsLoading
      ? 'Loading preset archive...'
      : presets.length > 0
        ? `${presets.length} saved presets are available for the current player.`
        : 'No saved presets are available for this account yet.',
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
          <TagChip label={`Ready: ${readyStatusLabel}`} tone={readyStatusTone} />
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
        <ContentStatePanel title={actionSuccessTitle ?? 'Player action completed'} message={actionSuccessMessage} />
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
        </div>
        <div class="player-lobby-page__reference-summary">
          <div>
            <strong>Last synced</strong>
            <p>Character: {formatCharacterLabel(savedLoadoutDraft.characterId)}</p>
            <p>EX: {syncedResolvedExCard ? `${syncedResolvedExCard.name} (${syncedResolvedExCard.id})` : formatExCardLabel(savedLoadoutDraft.exCardId)}</p>
            <p>Deck: {savedLoadoutDraft.deckOwnedCardIds.length} owned cards</p>
            <p>Passives: {savedLoadoutDraft.passiveIds.length}</p>
          </div>
          <div>
            <strong>Draft to save</strong>
            <p>Character: {resolvedDraftCharacter ? `${resolvedDraftCharacter.name} #${resolvedDraftCharacter.id}` : formatCharacterLabel(loadoutDraft.characterId)}</p>
            <p>EX: {resolvedDraftExCard ? `${resolvedDraftExCard.name} (${resolvedDraftExCard.id})` : formatExCardLabel(loadoutDraft.exCardId)}</p>
            <p>Deck: {loadoutDraft.deckOwnedCardIds.length} owned cards</p>
            <p>Passives: {loadoutDraft.passiveIds.length}</p>
          </div>
        </div>
        <div class="player-lobby-page__todo">
          <p>{loadoutDirty ? 'Current draft has unsaved loadout changes.' : 'Current draft matches the last synced loadout state.'}</p>
          <p>
            {#if characterChangePending}
              Save the new character first to refresh server-owned cards and character defaults before editing the deck again.
            {:else}
              Preset apply keeps a separate preview so the current draft and the preset source do not get mixed.
            {/if}
          </p>
          {#if lastAppliedPresetName}
            <p>Last preset applied to this live session: {lastAppliedPresetName}</p>
          {/if}
        </div>
      </SectionFrame>
    </div>

    <div class="player-lobby-page__main">
      <SectionFrame title="Direct loadout save" description="Edit the current player loadout directly and save it with the player session token.">
        {#if loadoutEditGuardMessage}
          <ContentStatePanel title="Loadout editing unavailable" message={loadoutEditGuardMessage} tone="error" />
        {/if}
        {#if characterChangePending}
          <ContentStatePanel
            title="Character change pending"
            message="Save this character selection first. The session response will refresh owned cards, deck defaults, and the synced loadout summary."
          />
        {/if}
        <div class="player-lobby-page__form-grid">
          <label class="player-lobby-page__field">
            <span>Character</span>
            <select value={loadoutDraft.characterId === null ? '' : String(loadoutDraft.characterId)} disabled={loading || actionPending !== null || referenceLoading || !canEditOwnLoadout} onchange={(event) => updateCharacterId((event.currentTarget as HTMLSelectElement).value)}>
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
            <select value={loadoutDraft.exCardId} disabled={loading || actionPending !== null || referenceLoading || !canEditOwnLoadout} onchange={(event) => updateExCardId((event.currentTarget as HTMLSelectElement).value)}>
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
              <select bind:value={ownedCardCandidate} disabled={loading || actionPending !== null || !canEditOwnLoadout || deckEditingLocked}>
                <option value="">Quick add owned card</option>
                {#each ownedCardOptions as option}
                  <option value={option.id}>{option.label}</option>
                {/each}
              </select>
              <button type="button" disabled={loading || actionPending !== null || !canEditOwnLoadout || deckEditingLocked || !ownedCardCandidate} onclick={addOwnedCardCandidate}>Add card</button>
            </div>
            <textarea rows="6" value={formatIdentifierText(loadoutDraft.deckOwnedCardIds)} placeholder={deckEditingLocked ? 'Save the selected character first to refresh owned card ids.' : 'One owned card id per line'} disabled={loading || actionPending !== null || !canEditOwnLoadout || deckEditingLocked} oninput={(event) => updateDeckOwnedCardIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
          </label>

          <label class="player-lobby-page__field player-lobby-page__field--span-2">
            <span>Passive ids</span>
            <div class="player-lobby-page__picker-row">
              <select bind:value={passiveCandidate} disabled={loading || actionPending !== null || referenceLoading || !canEditOwnLoadout}>
                <option value="">Quick add passive</option>
                {#each passives as passive}
                  <option value={passive.id}>{passive.name} ({passive.id})</option>
                {/each}
              </select>
              <button type="button" disabled={loading || actionPending !== null || !canEditOwnLoadout || !passiveCandidate} onclick={addPassiveCandidate}>Add passive</button>
            </div>
            <textarea rows="4" value={formatIdentifierText(loadoutDraft.passiveIds)} placeholder="One passive id per line" disabled={loading || actionPending !== null || !canEditOwnLoadout} oninput={(event) => updatePassiveIds((event.currentTarget as HTMLTextAreaElement).value)}></textarea>
          </label>
        </div>

        <div class="player-lobby-page__actions">
          <button type="button" onclick={() => void handleSaveLoadout()} disabled={loading || actionPending !== null || !canEditOwnLoadout || !loadoutDirty}>{saveLoadoutLabel}</button>
        </div>

        <div class="player-lobby-page__grid">
          <EntityListPane items={deckOwnedCardItems} emptyMessage="No deck owned card ids are currently assigned to the draft." />
          <EntityListPane items={passivePreviewItems} emptyMessage="No passive ids are currently assigned to the draft." />
        </div>
      </SectionFrame>

      <SectionFrame title="Apply saved preset" description="Choose a saved preset, review its resolved references, and apply it to the live session loadout.">
        <div class="player-lobby-page__guide">
          <p>{presetSummary}</p>
          {#if lastAppliedPresetName}
            <p>Current live session was last updated from preset: {lastAppliedPresetName}</p>
          {/if}
        </div>
        <div class="player-lobby-page__actions">
          <button type="button" onclick={() => void loadAvailablePresets()} disabled={loading || actionPending !== null || presetsLoading}>
            {presetsLoading ? 'Refreshing presets...' : 'Reload presets'}
          </button>
          <a class="player-lobby-page__link-action" data-nav href={pathBuilders.presetList()}>Open preset archive</a>
        </div>
        {#if presetsErrorMessage}
          <ContentStatePanel title="Preset archive unavailable" message={presetsErrorMessage} tone="error" actionLabel="Retry presets" onAction={() => void loadAvailablePresets()} />
        {:else}
          {#if loadoutDirty}
            <ContentStatePanel
              title="Preset apply replaces the current draft"
              message="Applying a preset updates the live session loadout and replaces the current unsaved draft with the server response."
            />
          {/if}
          <label class="player-lobby-page__field">
            <span>Preset</span>
            <select bind:value={selectedPresetId} disabled={loading || actionPending !== null || presetsLoading}>
              <option value="">Select preset</option>
              {#each presets as preset}
                <option value={String(preset.id)}>{formatPresetOptionLabel(preset)}</option>
              {/each}
            </select>
          </label>

          {#if selectedPreset}
            <div class="player-lobby-page__guide">
              <p>Preset id: {selectedPreset.id}</p>
              <p>Preset owner: {selectedPreset.owner}</p>
              <p>Character: {resolvedPresetCharacter ? `${resolvedPresetCharacter.name} #${resolvedPresetCharacter.id}` : `#${selectedPreset.characterId}`}</p>
              <p>EX: {resolvedPresetExCard ? `${resolvedPresetExCard.name} (${resolvedPresetExCard.id})` : selectedPreset.exCardId}</p>
              <p>Deck: {selectedPreset.deckCardIds.length} cards | Passives: {selectedPreset.passiveIds.length}</p>
            </div>
            <div class="player-lobby-page__actions">
              <button type="button" onclick={() => void handleApplyPreset()} disabled={loading || actionPending !== null || !canEditOwnLoadout}>{applyPresetLabel}</button>
            </div>
            <div class="player-lobby-page__grid">
              <EntityListPane items={presetDeckPreviewItems} emptyMessage="This preset has no saved deck card entries." />
              <EntityListPane items={presetPassivePreviewItems} emptyMessage="This preset has no saved passive entries." />
            </div>
          {:else if presetsLoading}
            <ContentStatePanel title="Loading preset archive" message="Restoring the saved preset list for the current player." />
          {:else if presets.length === 0}
            <ContentStatePanel title="No saved presets yet" message="Create a preset from the preset archive first, then return here to apply it to the current session." />
          {:else}
            <ContentStatePanel message="No saved preset is currently selected." />
          {/if}
        {/if}
      </SectionFrame>
    </div>

    <SectionFrame title="Action zone" description="Bottom action strip keeps the ready and leave lifecycle separate from loadout editing.">
      <div class="player-lobby-page__guide">
        <p>Current ready state: {readyStatusLabel}</p>
        <p>{membershipActionSummary}</p>
      </div>
      <div class="player-lobby-page__actions">
        <a class="player-lobby-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Back to session entry</a>
        <button type="button" onclick={() => void handleReadyToggle()} disabled={loading || actionPending !== null || !canToggleReady}>{readyActionLabel}</button>
        <button type="button" onclick={() => void handleLeave()} disabled={loading || actionPending !== null || !canLeaveSession}>{leaveActionLabel}</button>
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
  .player-lobby-page__todo p,
  .player-lobby-page__reference-summary p {
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
  .player-lobby-page__todo p,
  .player-lobby-page__reference-summary p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .player-lobby-page__reference-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
    padding: 0.9rem 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
  }

  .player-lobby-page__reference-summary strong {
    display: block;
    margin-bottom: 0.35rem;
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
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
    .player-lobby-page__form-grid,
    .player-lobby-page__reference-summary {
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
