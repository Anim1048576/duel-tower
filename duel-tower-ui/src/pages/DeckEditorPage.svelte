<script lang="ts">
  import { onMount } from 'svelte'
  import type { DeckType, DeckValidationResponse } from '../lib/api/deckTypes'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findDeckEditorAction,
    type DeckEditorActionId,
    type DeckEditorActionResponseById,
    type DeckEditorScreenResponse,
    type DeckEditorValidationDto,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import {
    buildDeckEditorActionPatch,
    createDeckEditorState,
    createEmptyDeckEditorState,
    type DeckEditorState,
  } from '../lib/decks/editorModel'
  import DeckEditorCardListPanel from '../lib/decks/components/DeckEditorCardListPanel.svelte'
  import DeckEditorControlsPanel from '../lib/decks/components/DeckEditorControlsPanel.svelte'
  import DeckEditorHeaderPanel from '../lib/decks/components/DeckEditorHeaderPanel.svelte'
  import DeckEditorSelectedCardPanel from '../lib/decks/components/DeckEditorSelectedCardPanel.svelte'
  import { toDeckCardItem } from '../lib/decks/deckEditorView'
  import {
    buildDeckEditorLocalSummary,
    getDeckEditorDeckTypeLabel,
    getDeckEditorLocalTitle,
    getDeckEditorLocalTotalCards,
    isDeckEditorLocalDirty,
  } from '../lib/decks/presentationState.js'
  import {
    createDeckEditorDraftSignature,
    isDeckEditorValidationStale,
  } from '../lib/decks/validationFreshness.js'
  import {
    deckEditorStateCopy,
    deckListStateCopy,
    setDeckPageFeedback,
  } from '../lib/decks/pageState'
  import { pathBuilders, resolveRouteMatch, routePaths } from '../lib/navigation'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  const deckTypeOptions: DeckType[] = ['PLAYER', 'ENEMY']

  let loading = $state(true)
  let screen = $state<DeckEditorScreenResponse | null>(null)
  let editorState = $state<DeckEditorState>(createEmptyDeckEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedDeckId = $state<string | null>(null)
  let selectedCardKey = $state('')
  let pendingActionId = $state<DeckEditorActionId | null>(null)
  let deleteConfirmOpen = $state(false)
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let requestSequence = 0

  function getDeckIdFromRoute() {
    if (typeof window === 'undefined') return null

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key !== 'deck-editor') {
      return null
    }

    const deckId = match.params.id?.trim()
    return deckId ? deckId : null
  }

  function isCreateDeckRoute() {
    if (typeof window === 'undefined') return false

    const match = resolveRouteMatch(window.location.pathname)
    return match?.page.key === 'deck-editor' && !match.params.id && match.page.path === routePaths.deckEditor
  }

  function syncSelectedCard(nextKeys: readonly string[]) {
    selectedCardKey = nextKeys.includes(selectedCardKey) ? selectedCardKey : nextKeys[0] ?? ''
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function applyScreen(nextScreen: DeckEditorScreenResponse) {
    screen = nextScreen
    editorState = createDeckEditorState(nextScreen.draft)
    syncSelectedCard(editorState.cards.map((card) => card.key))
    deleteConfirmOpen = false

    if (nextScreen.deckId != null) {
      setSelectionHandoff(selectionHandoffKeys.deckId, String(nextScreen.deckId))
    }
  }

  function resetTransientState() {
    pendingActionId = null
    deleteConfirmOpen = false
    clearActionFeedback()
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') return

    if (replace) {
      window.history.replaceState({}, '', path)
    } else {
      window.history.pushState({}, '', path)
    }

    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function replaceWithDeckEditor(id: string) {
    navigateTo(pathBuilders.deckEditor(id), true)
  }

  async function loadDeckEditorScreen(deckId?: string | null) {
    const requestId = ++requestSequence

    loading = true
    screen = null
    editorState = createEmptyDeckEditorState()
    requestedDeckId = deckId ?? null
    errorMessage = null
    notFoundId = null
    selectedCardKey = ''
    resetTransientState()

    try {
      const response = deckId
        ? await getScreen<DeckEditorScreenResponse>('DeckEditor', { deckId })
        : await getScreen<DeckEditorScreenResponse>('DeckEditor')

      if (requestId !== requestSequence) {
        return
      }

      applyScreen(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFoundId = deckId ?? null
        return
      }

      errorMessage = getApiErrorMessage(error, 'Unable to load the selected deck editor screen.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function retryLoad() {
    const routeDeckId = getDeckIdFromRoute()

    if (routeDeckId) {
      void loadDeckEditorScreen(routeDeckId)
      return
    }

    if (isCreateDeckRoute()) {
      void loadDeckEditorScreen(null)
      return
    }

    if (requestedDeckId) {
      void loadDeckEditorScreen(requestedDeckId)
    }
  }

  function sanitizeDeckCardCount(value: number) {
    if (!Number.isFinite(value)) {
      return 1
    }

    return Math.max(1, Math.floor(value))
  }

  function updateName(value: string) {
    editorState = {
      ...editorState,
      name: value,
    }
  }

  function updateType(value: DeckType) {
    editorState = {
      ...editorState,
      type: value,
    }
  }

  function updateSelectedCardId(value: string) {
    editorState = {
      ...editorState,
      cards: editorState.cards.map((card) =>
        card.key === selectedCardKey
          ? {
              ...card,
              cardId: value,
            }
          : card,
      ),
    }
  }

  function updateSelectedCardCount(value: number) {
    const nextCount = sanitizeDeckCardCount(value)

    editorState = {
      ...editorState,
      cards: editorState.cards.map((card) =>
        card.key === selectedCardKey
          ? {
              ...card,
              count: nextCount,
            }
          : card,
      ),
    }
  }

  function toDeckEditorValidation(response: DeckValidationResponse): DeckEditorValidationDto {
    return {
      valid: response.valid,
      normalizedTotalCards: response.normalizedTotalCards,
      issues: response.issues,
      isStale: false,
      validatedDraftSignature: createDeckEditorDraftSignature(editorState),
    }
  }

  function getActionSuccessMessage(actionId: DeckEditorActionId) {
    switch (actionId) {
      case 'deckEditor.validate':
        return 'Deck validation refreshed.'
      case 'deckEditor.save':
        return 'Deck changes saved.'
      case 'deckEditor.create':
        return 'Deck created.'
      case 'deckEditor.delete':
        return null
    }
  }

  async function runAction(actionId: DeckEditorActionId) {
    if (!screen || pendingActionId) {
      return
    }

    const action = findDeckEditorAction(screen, actionId)

    if (!action || !action.enabled) {
      return
    }

    pendingActionId = actionId
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const patch = buildDeckEditorActionPatch(action.id, editorState)
      const body = action.payloadTemplate ? buildScreenActionPayload(action, patch) : undefined

      if (actionId === 'deckEditor.validate') {
        const response = await invokeScreenAction<
          DeckEditorScreenResponse,
          DeckEditorActionResponseById['deckEditor.validate']
        >(action, body === undefined ? undefined : { body })

        screen = {
          ...screen,
          validation: toDeckEditorValidation(response as DeckValidationResponse),
        }
        actionSuccessMessage = getActionSuccessMessage(actionId)
        return
      }

      if (actionId === 'deckEditor.delete') {
        await invokeScreenAction<DeckEditorScreenResponse, DeckEditorActionResponseById['deckEditor.delete']>(
          action,
          body === undefined ? undefined : { body },
        )

        removeSelectionHandoff(selectionHandoffKeys.deckId)
        setDeckPageFeedback(deckListStateCopy.deletedFeedback)
        navigateTo(pathBuilders.deckList(), true)
        return
      }

      const response = await invokeScreenAction<
        DeckEditorScreenResponse,
        DeckEditorActionResponseById['deckEditor.save' | 'deckEditor.create']
      >(action, body === undefined ? undefined : { body })
      const nextDeckId = String(response.id)

      await loadDeckEditorScreen(nextDeckId)
      actionSuccessMessage = getActionSuccessMessage(actionId)

      if (actionId === 'deckEditor.create') {
        replaceWithDeckEditor(nextDeckId)
      }
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to complete the requested deck action.')
    } finally {
      pendingActionId = null
    }
  }

  function openDeleteConfirmation() {
    if (!screen || !findDeckEditorAction(screen, 'deckEditor.delete') || pendingActionId) {
      return
    }

    clearActionFeedback()
    deleteConfirmOpen = true
  }

  function cancelDeleteConfirmation() {
    if (pendingActionId === 'deckEditor.delete') {
      return
    }

    deleteConfirmOpen = false
  }

  onMount(() => {
    const routeDeckId = getDeckIdFromRoute()

    if (routeDeckId) {
      void loadDeckEditorScreen(routeDeckId)
      return
    }

    if (isCreateDeckRoute()) {
      void loadDeckEditorScreen(null)
      return
    }

    const handoffDeckId = readSelectionHandoff(selectionHandoffKeys.deckId)?.trim()

    if (handoffDeckId) {
      replaceWithDeckEditor(handoffDeckId)
      return
    }

    loading = false
  })

  const selectedCardEntry = $derived.by(
    () => editorState.cards.find((entry) => entry.key === selectedCardKey) ?? editorState.cards[0] ?? null,
  )

  const selectedCardPosition = $derived.by(() => {
    const selectedIndex = editorState.cards.findIndex((entry) => entry.key === selectedCardKey)
    return selectedIndex >= 0 ? selectedIndex + 1 : editorState.cards.length ? 1 : 0
  })

  const validationState = $derived.by(() => {
    if (!screen) {
      return null
    }

    return {
      ...screen.validation,
      isStale: isDeckEditorValidationStale(editorState, screen.validation),
    }
  })

  const localPresentation = $derived.by(() => {
    if (!screen) {
      return null
    }

    const localDeckType = editorState.type || screen.draft.type

    return {
      title: getDeckEditorLocalTitle(editorState, screen.draft, screen.mode),
      deckType: localDeckType,
      deckTypeLabel: getDeckEditorDeckTypeLabel(localDeckType),
      totalCards: getDeckEditorLocalTotalCards(editorState),
      dirty: isDeckEditorLocalDirty(editorState, screen.draft),
      summary: buildDeckEditorLocalSummary(editorState),
      draftEntries: editorState.cards.length,
    }
  })

  const cardItems = $derived.by(() =>
    editorState.cards.map((entry, index) => toDeckCardItem(entry, index, editorState.cards.length)),
  )

  const validateAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.validate') : null))
  const saveAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.save') : null))
  const createAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.create') : null))
  const deleteAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.delete') : null))
</script>

<div class="editor-page">
  {#if loading}
    <SectionFrame
      eyebrow="Deck Editor"
      title={deckEditorStateCopy.loadingTitle}
      description="Resolving the current deck editor screen from the URL."
    >
      <ContentStatePanel
        title={deckEditorStateCopy.loadingTitle}
        message={deckEditorStateCopy.loadingMessage}
      />
    </SectionFrame>
  {:else if notFoundId}
    <SectionFrame
      eyebrow="Deck Missing"
      title={deckEditorStateCopy.notFoundTitle}
      description={deckEditorStateCopy.notFoundDescription}
    >
      <div class="editor-page__note">
        <p>Requested id: {notFoundId}</p>
        <p>{deckEditorStateCopy.notFoundMessage}</p>
      </div>

      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to deck list
        </a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame
      eyebrow="Deck Error"
      title={deckEditorStateCopy.loadErrorTitle}
      description="The editor could not retrieve the current screen response."
    >
      <ContentStatePanel
        title={deckEditorStateCopy.loadErrorMessageTitle}
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={retryLoad}
      />

      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to deck list
        </a>
      </div>
    </SectionFrame>
  {:else if screen && localPresentation}
    <DeckEditorHeaderPanel
      {screen}
      title={localPresentation.title}
      deckType={localPresentation.deckType}
      deckTypeLabel={localPresentation.deckTypeLabel}
      summary={localPresentation.summary}
      totalCards={localPresentation.totalCards}
      draftEntries={localPresentation.draftEntries}
      editorName={editorState.name}
      editorType={editorState.type}
      {deckTypeOptions}
      controlsDisabled={Boolean(pendingActionId)}
      onNameInput={updateName}
      onTypeChange={updateType}
    />

    <div class="editor-page__grid">
      <DeckEditorCardListPanel
        items={cardItems}
        {selectedCardKey}
        controlsDisabled={Boolean(pendingActionId)}
        onSelect={(id) => {
          selectedCardKey = id
        }}
      />

      <DeckEditorSelectedCardPanel
        {selectedCardEntry}
        {selectedCardPosition}
        totalEntries={editorState.cards.length}
        controlsDisabled={Boolean(pendingActionId)}
        onCardIdInput={updateSelectedCardId}
        onCountInput={updateSelectedCardCount}
      />
    </div>

    <DeckEditorControlsPanel
      {screen}
      {validationState}
      {localPresentation}
      {pendingActionId}
      {deleteConfirmOpen}
      {actionErrorMessage}
      {actionSuccessMessage}
      {validateAction}
      {saveAction}
      {createAction}
      {deleteAction}
      onRunAction={(actionId) => void runAction(actionId)}
      onOpenDeleteConfirmation={openDeleteConfirmation}
      onCancelDeleteConfirmation={cancelDeleteConfirmation}
    />
  {:else}
    <SectionFrame
      eyebrow="Deck Selection"
      title={deckEditorStateCopy.selectionTitle}
      description={deckEditorStateCopy.selectionDescription}
    >
      <div class="editor-page__note">
        <p>{deckEditorStateCopy.selectionRouteMessage}</p>
        <p>{deckEditorStateCopy.selectionActionMessage}</p>
      </div>

      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to deck list
        </a>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .editor-page,
  .editor-page__note {
    display: grid;
    gap: 1.5rem;
  }

  .editor-page__grid {
    display: grid;
    gap: 1.5rem;
    grid-template-columns: minmax(0, 1.05fr) minmax(19rem, 0.95fr);
  }

  .editor-page__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .editor-page__note p {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .editor-page__link-action {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  @media (max-width: 960px) {
    .editor-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
