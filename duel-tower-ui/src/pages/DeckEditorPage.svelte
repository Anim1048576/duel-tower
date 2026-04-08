<script lang="ts">
  import { onMount } from 'svelte'
  import { createDeck, deleteDeck, getDeck, replaceDeckCards, updateDeck, validateDeck } from '../lib/api/decks'
  import type {
    DeckCardDto,
    DeckResponse,
    DeckType,
    DeckValidationResponse,
  } from '../lib/api/deckTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { formatContentEnumLabel } from '../lib/content/display'
  import {
    createDeckEditorState,
    createEmptyDeckEditorState,
    getDeckEditorTotalCards,
    isDeckEditorStateDirty,
    toDeckEditorReplaceCardsRequest,
    toDeckEditorUpdateRequest,
    type DeckEditorCardState,
    type DeckEditorState,
  } from '../lib/decks/editorModel'
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

  type DeckCardItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  type DeckCardEntry = DeckCardDto & {
    entryId: string
    position: number
  }

  type DeckEditorMode = 'selection' | 'create' | 'edit'

  const deckTypeOptions: DeckType[] = ['PLAYER', 'ENEMY']

  let loading = $state(true)
  let editorMode = $state<DeckEditorMode>('selection')
  let deck = $state<DeckResponse | null>(null)
  let editorState = $state<DeckEditorState>(createEmptyDeckEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedDeckId = $state<string | null>(null)
  let selectedCardKey = $state('')
  let requestSequence = 0
  let creating = $state(false)
  let createErrorMessage = $state<string | null>(null)
  let createRequestSequence = 0
  let deleting = $state(false)
  let deleteConfirmOpen = $state(false)
  let deleteErrorMessage = $state<string | null>(null)
  let deleteRequestSequence = 0
  let saving = $state(false)
  let saveErrorMessage = $state<string | null>(null)
  let saveSuccessMessage = $state<string | null>(null)
  let saveRequestSequence = 0
  let validating = $state(false)
  let validationResult = $state<DeckValidationResponse | null>(null)
  let validationErrorMessage = $state<string | null>(null)
  let lastValidatedCardsSignature = $state<string | null>(null)
  let validationRequestSequence = 0

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

  function createNewDeckEditorState(): DeckEditorState {
    return {
      ...createEmptyDeckEditorState(),
      type: 'PLAYER',
    }
  }

  function getDeckTypeTone(type: DeckType | '' | null | undefined): 'muted' | 'success' | 'warning' {
    if (type === 'PLAYER') {
      return 'success'
    }

    if (type === 'ENEMY') {
      return 'warning'
    }

    return 'muted'
  }

  function buildDeckSummary(state: Pick<DeckEditorState, 'cards'>) {
    if (!state.cards.length) {
      return 'This deck draft currently has no card entries.'
    }

    const totalCards = getDeckEditorTotalCards(state)
    const entryLabel = state.cards.length === 1 ? 'entry' : 'entries'
    return `${totalCards} total cards are currently distributed across ${state.cards.length} draft ${entryLabel}.`
  }

  function buildDeckCardMeta(entry: DeckCardEntry) {
    return `Count ${entry.count} · Entry ${entry.position}`
  }

  function buildDeckCardNote(entry: DeckCardEntry, totalEntries: number) {
    const totalLabel = totalEntries === 1 ? 'saved entry' : 'saved entries'
    return `Card reference ${entry.cardId} is stored as item ${entry.position} of ${totalEntries} ${totalLabel}.`
  }

  function getDeckCardMetaLabel(entry: DeckCardEntry) {
    return `Count ${entry.count} | Entry ${entry.position}`
  }

  function getDeckCardTagItems(entry: DeckCardEntry) {
    const countTone: 'muted' | 'success' = entry.count > 1 ? 'success' : 'muted'

    return [
      { label: `x${entry.count}`, tone: countTone },
      { label: `Entry ${entry.position}`, tone: 'accent' as const },
    ]
  }

  function buildDeckCardTags(entry: DeckCardEntry) {
    return [
      { label: `x${entry.count}`, tone: entry.count > 1 ? 'success' : 'muted' as const },
      { label: `Entry ${entry.position}`, tone: 'accent' as const },
    ]
  }

  function getDeckCardEntryId(card: DeckCardDto, index: number) {
    return `${String(card.cardId)}:${index}`
  }

  function toDeckCardEntry(card: DeckCardDto, index: number): DeckCardEntry {
    return {
      ...card,
      entryId: getDeckCardEntryId(card, index),
      position: index + 1,
    }
  }

  function toDeckCardItem(entry: DeckCardEntry, totalEntries: number): DeckCardItem {
    return {
      id: entry.entryId,
      title: entry.cardId,
      subtitle: 'Saved deck card',
      meta: getDeckCardMetaLabel(entry),
      note: buildDeckCardNote(entry, totalEntries),
      tags: getDeckCardTagItems(entry),
    }
  }

  function syncSelectedCardEntry(nextEntries: readonly DeckCardEntry[]) {
    const nextIds = nextEntries.map((entry) => entry.entryId)
    selectedCardKey = nextIds.includes(selectedCardKey) ? selectedCardKey : nextIds[0] ?? ''
  }

  function getDraftDeckCardMetaLabel(entry: DeckEditorCardState, position: number) {
    return `Count ${entry.count} | Entry ${position}`
  }

  function buildDraftDeckCardNote(entry: DeckEditorCardState, position: number, totalEntries: number) {
    const totalLabel = totalEntries === 1 ? 'draft entry' : 'draft entries'
    return `Card reference ${entry.cardId || 'N/A'} is currently tracked as item ${position} of ${totalEntries} ${totalLabel}.`
  }

  function getDraftDeckCardTagItems(entry: DeckEditorCardState, position: number) {
    const countTone: 'muted' | 'success' = entry.count > 1 ? 'success' : 'muted'

    return [
      { label: `x${entry.count}`, tone: countTone },
      { label: `Entry ${position}`, tone: 'accent' as const },
    ]
  }

  function toDraftDeckCardItem(
    entry: DeckEditorCardState,
    index: number,
    totalEntries: number,
  ): DeckCardItem {
    const position = index + 1

    return {
      id: entry.key,
      title: entry.cardId.trim() || 'Unnamed card reference',
      subtitle: 'Draft deck card',
      meta: getDraftDeckCardMetaLabel(entry, position),
      note: buildDraftDeckCardNote(entry, position, totalEntries),
      tags: getDraftDeckCardTagItems(entry, position),
    }
  }

  function syncDraftSelectedCard(nextCards: readonly DeckEditorCardState[]) {
    const nextKeys = nextCards.map((entry) => entry.key)
    selectedCardKey = nextKeys.includes(selectedCardKey) ? selectedCardKey : nextKeys[0] ?? ''
  }

  function syncEditorState(response: DeckResponse) {
    const nextState = createDeckEditorState(response)

    editorMode = 'edit'
    deck = response
    editorState = nextState
    syncDraftSelectedCard(nextState.cards)
    setSelectionHandoff(selectionHandoffKeys.deckId, String(response.id))
  }

  function sanitizeDeckCardCount(value: number) {
    if (!Number.isFinite(value)) {
      return 1
    }

    return Math.max(1, Math.floor(value))
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

  function getDeckValidationSignature(cards: readonly DeckEditorCardState[]) {
    return cards.map((card) => `${card.cardId.trim()}:${card.count}`).join('|')
  }

  function resetValidationState() {
    validationRequestSequence += 1
    validating = false
    validationResult = null
    validationErrorMessage = null
    lastValidatedCardsSignature = null
  }

  function resetCreateState() {
    createRequestSequence += 1
    creating = false
    createErrorMessage = null
  }

  function resetDeleteState() {
    deleteRequestSequence += 1
    deleting = false
    deleteConfirmOpen = false
    deleteErrorMessage = null
  }

  function resetSaveState() {
    saveRequestSequence += 1
    saving = false
    saveErrorMessage = null
    saveSuccessMessage = null
  }

  function enterCreateMode() {
    editorMode = 'create'
    loading = false
    deck = null
    editorState = createNewDeckEditorState()
    errorMessage = null
    notFoundId = null
    requestedDeckId = null
    selectedCardKey = ''
    resetCreateState()
    resetDeleteState()
    resetSaveState()
    resetValidationState()
  }

  async function runValidation() {
    if (!deck || editorMode !== 'edit' || validating || saving || creating || deleting || deleteConfirmOpen) {
      return
    }

    const requestId = ++validationRequestSequence
    const payload = replaceCardsPayload
    const payloadSignature = validationCardsSignature

    validating = true
    validationErrorMessage = null

    try {
      const response = await validateDeck(deck.id, payload)

      if (requestId !== validationRequestSequence) {
        return
      }

      validationResult = response
      lastValidatedCardsSignature = payloadSignature
    } catch (error) {
      if (requestId !== validationRequestSequence) {
        return
      }

      validationResult = null
      validationErrorMessage = getApiErrorMessage(error, 'Unable to validate the current deck draft.')
      lastValidatedCardsSignature = null
    } finally {
      if (requestId === validationRequestSequence) {
        validating = false
      }
    }
  }

  async function createDeckRecord() {
    if (editorMode !== 'create' || creating || saving || validating || deleting) {
      return
    }

    const requestId = ++createRequestSequence
    const payload = updatePayload

    creating = true
    createErrorMessage = null
    deleteErrorMessage = null
    saveErrorMessage = null
    saveSuccessMessage = null

    try {
      const response = await createDeck(payload)

      if (requestId !== createRequestSequence) {
        return
      }

      syncEditorState(response)
      resetValidationState()
      replaceWithDeckEditor(String(response.id))
    } catch (error) {
      if (requestId !== createRequestSequence) {
        return
      }

      createErrorMessage = getApiErrorMessage(error, 'Unable to create a new deck.')
    } finally {
      if (requestId === createRequestSequence) {
        creating = false
      }
    }
  }

  async function saveDeck() {
    if (!deck || editorMode !== 'edit' || saving || validating || creating || deleting || deleteConfirmOpen) {
      return
    }

    const requestId = ++saveRequestSequence
    const deckId = deck.id
    const nextUpdatePayload = updatePayload
    const nextReplacePayload = replaceCardsPayload

    saving = true
    saveErrorMessage = null
    deleteErrorMessage = null
    saveSuccessMessage = null

    try {
      await updateDeck(deckId, nextUpdatePayload)

      if (requestId !== saveRequestSequence) {
        return
      }

      const response = await replaceDeckCards(deckId, nextReplacePayload)

      if (requestId !== saveRequestSequence) {
        return
      }

      syncEditorState(response)
      resetValidationState()
      saveSuccessMessage = 'Deck changes saved.'
    } catch (error) {
      if (requestId !== saveRequestSequence) {
        return
      }

      saveErrorMessage = getApiErrorMessage(error, 'Unable to save deck changes.')
    } finally {
      if (requestId === saveRequestSequence) {
        saving = false
      }
    }
  }

  function openDeleteConfirmation() {
    if (!deck || editorMode !== 'edit' || creating || saving || validating || deleting) {
      return
    }

    deleteConfirmOpen = true
    deleteErrorMessage = null
    saveSuccessMessage = null
  }

  function cancelDeleteConfirmation() {
    if (deleting) {
      return
    }

    deleteConfirmOpen = false
    deleteErrorMessage = null
  }

  async function deleteDeckRecord() {
    if (!deck || editorMode !== 'edit' || deleting || saving || validating || creating) {
      return
    }

    const requestId = ++deleteRequestSequence

    deleting = true
    deleteErrorMessage = null
    saveErrorMessage = null
    saveSuccessMessage = null

    try {
      await deleteDeck(deck.id)

      if (requestId !== deleteRequestSequence) {
        return
      }

      removeSelectionHandoff(selectionHandoffKeys.deckId)
      setDeckPageFeedback(deckListStateCopy.deletedFeedback)
      navigateTo(pathBuilders.deckList(), true)
    } catch (error) {
      if (requestId !== deleteRequestSequence) {
        return
      }

      deleteErrorMessage = getApiErrorMessage(error, 'Unable to delete the current deck.')
    } finally {
      if (requestId === deleteRequestSequence) {
        deleting = false
      }
    }
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

  async function loadDeckRecord(id: string) {
    const requestId = ++requestSequence
    editorMode = 'edit'
    requestedDeckId = id
    loading = true
    deck = null
    editorState = createEmptyDeckEditorState()
    resetCreateState()
    resetDeleteState()
    resetSaveState()
    resetValidationState()
    errorMessage = null
    notFoundId = null
    selectedCardKey = ''

    try {
      const response = await getDeck(id)

      if (requestId !== requestSequence) {
        return
      }

      syncEditorState(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      deck = null
      editorState = createEmptyDeckEditorState()
      selectedCardKey = ''

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFoundId = id
        return
      }

      errorMessage = getApiErrorMessage(error, 'Unable to load the selected deck.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function retryLoad() {
    const routeDeckId = getDeckIdFromRoute()

    if (routeDeckId) {
      void loadDeckRecord(routeDeckId)
      return
    }

    if (requestedDeckId) {
      void loadDeckRecord(requestedDeckId)
    }
  }

  onMount(() => {
    const routeDeckId = getDeckIdFromRoute()

    if (routeDeckId) {
      void loadDeckRecord(routeDeckId)
      return
    }

    if (isCreateDeckRoute()) {
      enterCreateMode()
      return
    }

    const handoffDeckId = readSelectionHandoff(selectionHandoffKeys.deckId)?.trim()

    if (handoffDeckId) {
      replaceWithDeckEditor(handoffDeckId)
      return
    }

    editorMode = 'selection'
    loading = false
  })

  const deckNameLabel = $derived.by(() => editorState.name.trim() || deck?.name || 'Untitled deck')
  const deckTypeLabel = $derived.by(() => formatContentEnumLabel(editorState.type || deck?.type, 'N/A'))
  const totalCards = $derived.by(() => getDeckEditorTotalCards(editorState))
  const editorDirty = $derived.by(() => (deck ? isDeckEditorStateDirty(deck, editorState) : false))
  const updatePayload = $derived.by(() => toDeckEditorUpdateRequest(editorState))
  const replaceCardsPayload = $derived.by(() => toDeckEditorReplaceCardsRequest(editorState))
  const updatePayloadCardCount = $derived.by(() => updatePayload.cards?.length ?? 0)
  const replacePayloadCardCount = $derived.by(() => replaceCardsPayload.cards?.length ?? 0)
  const isCreateMode = $derived.by(() => editorMode === 'create')
  const editorControlsDisabled = $derived.by(() => saving || creating || deleting)
  const editorTitle = $derived.by(() => (isCreateMode ? editorState.name.trim() || 'New deck' : deckNameLabel))
  const editorEyebrow = $derived.by(() => (isCreateMode ? 'New Deck' : 'Selected Deck'))
  const editorDescription = $derived.by(() =>
    isCreateMode
      ? deckEditorStateCopy.createDescription
      : deckEditorStateCopy.editDescription,
  )
  const draftStatusLabel = $derived.by(() => (isCreateMode ? 'Create Mode' : editorDirty ? 'Draft Changed' : 'Draft Synced'))
  const draftStatusTone = $derived.by(() => (isCreateMode ? 'accent' : editorDirty ? 'warning' : 'success'))
  const sourceDeckIdLabel = $derived.by(() => (deck ? String(deck.id) : 'Assigned after create'))
  const saveSuccessVisible = $derived.by(() => Boolean(saveSuccessMessage) && !editorDirty && !isCreateMode)
  const actionButtonsDisabled = $derived.by(() => creating || saving || deleting || validating)
  const validationCardsSignature = $derived.by(() => getDeckValidationSignature(editorState.cards))
  const validationIssueCount = $derived.by(() => validationResult?.issues.length ?? 0)
  const validationIsStale = $derived.by(
    () => Boolean(validationResult) && lastValidatedCardsSignature !== validationCardsSignature,
  )
  const deckCardItems = $derived.by(() => {
    const entries = editorState.cards
    return entries.map((entry, index) => toDraftDeckCardItem(entry, index, entries.length))
  })
  const selectedCardEntry = $derived.by(
    () => editorState.cards.find((entry) => entry.key === selectedCardKey) ?? editorState.cards[0] ?? null,
  )
  const selectedCardPosition = $derived.by(() => {
    const selectedIndex = editorState.cards.findIndex((entry) => entry.key === selectedCardKey)

    if (selectedIndex >= 0) {
      return selectedIndex + 1
    }

    return editorState.cards.length ? 1 : 0
  })
</script>

<div class="editor-page">
  {#if loading}
    <SectionFrame
      eyebrow="Deck Editor"
      title={deckEditorStateCopy.loadingTitle}
      description="Resolving the current deck record from the URL before showing the editor shell."
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
      description="The editor could not retrieve the current deck record."
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
  {:else if deck || isCreateMode}
    <SectionFrame
      eyebrow={editorEyebrow}
      title={editorTitle}
      description={editorDescription}
    >
      <div class="editor-page__hero">
        <div class="editor-page__hero-copy">
          <p>{deckTypeLabel}</p>
          <h3>{buildDeckSummary(editorState)}</h3>
        </div>

        <div class="editor-page__hero-tags">
          <TagChip label={deckTypeLabel} tone={getDeckTypeTone(editorState.type || deck?.type)} />
          <TagChip label={`${editorState.cards.length} Entries`} tone="accent" />
          <TagChip label={draftStatusLabel} tone={draftStatusTone} />
        </div>
      </div>

      <div class="editor-page__stats">
        <StatBlock value={totalCards} label="Cards" note="Total cards computed from the local deck draft" />
        <StatBlock value={editorState.cards.length} label="Draft entries" note="Card entries currently tracked in the local editor state" />
        <StatBlock value={deckTypeLabel} label="Deck type" note="Deck classification in the current local draft" />
      </div>

      <fieldset class="editor-page__fieldset">
        <legend>Deck metadata</legend>

        <div class="editor-page__form-grid">
          <label class="editor-page__field editor-page__field--span-2">
            <span>Deck name</span>
            <input type="text" bind:value={editorState.name} placeholder="Enter deck name" disabled={editorControlsDisabled} />
          </label>

          <label class="editor-page__field">
            <span>Deck type</span>
            <select bind:value={editorState.type} disabled={editorControlsDisabled}>
              {#each deckTypeOptions as option}
                <option value={option}>{formatContentEnumLabel(option, option)}</option>
              {/each}
            </select>
          </label>

          <div class="editor-page__field">
            <span>{isCreateMode ? 'Deck id' : 'Source deck id'}</span>
            <p class="editor-page__readonly">{sourceDeckIdLabel}</p>
          </div>
        </div>
      </fieldset>
    </SectionFrame>

    <div class="editor-page__grid">
      <SectionFrame
        title="Deck cards"
        description="EntityListPane stays in use here so the draft card list matches the existing list-page browsing pattern."
      >
        <EntityListPane
          items={deckCardItems}
          selectedId={selectedCardKey}
          onSelect={(id) => {
            if (!editorControlsDisabled) {
              selectedCardKey = id
            }
          }}
          emptyMessage="No cards are assigned to this deck yet."
        />
      </SectionFrame>

      <SectionFrame
        title="Selected card"
        description="This panel now edits the selected card entry from the local draft model."
      >
        {#if selectedCardEntry}
          <div class="editor-page__card-detail">
            <div>
              <h3>{selectedCardEntry.cardId || 'Unnamed card reference'}</h3>
              <p>{getDraftDeckCardMetaLabel(selectedCardEntry, selectedCardPosition)}</p>
            </div>

            <div class="editor-page__card-tags">
              {#each getDraftDeckCardTagItems(selectedCardEntry, selectedCardPosition) as tag}
                <TagChip label={tag.label} tone={tag.tone} />
              {/each}
            </div>

            <p>{buildDraftDeckCardNote(selectedCardEntry, selectedCardPosition, editorState.cards.length)}</p>

            <fieldset class="editor-page__fieldset">
              <legend>Selected card draft</legend>

              <div class="editor-page__form-grid">
                <label class="editor-page__field editor-page__field--span-2">
                  <span>Card id</span>
                  <input
                    type="text"
                    value={selectedCardEntry.cardId}
                    disabled={editorControlsDisabled}
                    oninput={(event) => updateSelectedCardId((event.currentTarget as HTMLInputElement).value)}
                  />
                </label>

                <label class="editor-page__field">
                  <span>Count</span>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={selectedCardEntry.count}
                    disabled={editorControlsDisabled}
                    oninput={(event) =>
                      updateSelectedCardCount((event.currentTarget as HTMLInputElement).valueAsNumber)}
                  />
                </label>

                <div class="editor-page__field">
                  <span>Entry key</span>
                  <p class="editor-page__readonly">{selectedCardEntry.key}</p>
                </div>
              </div>
            </fieldset>

            <div class="editor-page__note">
              <p>This card entry now renders from the local draft state instead of the live response object.</p>
              <p>Save, validation, and delete actions now operate on the same local draft model.</p>
            </div>
          </div>
        {:else}
          <ContentStatePanel
            title={deckEditorStateCopy.noCardsTitle}
            message={deckEditorStateCopy.noCardsMessage}
          />
        {/if}
      </SectionFrame>
    </div>

    <SectionFrame
      title="Editor actions"
      description={isCreateMode
        ? 'Create a new deck record from the current draft.'
        : 'Run validation, save deck changes, or delete the current deck from here.'}
    >
      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to deck list
        </a>
        <button type="button" disabled={isCreateMode || actionButtonsDisabled || deleteConfirmOpen} onclick={runValidation}>
          {isCreateMode ? 'Validate after create' : validating ? 'Validating deck...' : 'Validate deck'}
        </button>
        {#if isCreateMode}
          <button type="button" disabled={actionButtonsDisabled} onclick={createDeckRecord}>
            {creating ? 'Creating deck...' : 'Create deck'}
          </button>
        {:else}
          <button type="button" disabled={actionButtonsDisabled || deleteConfirmOpen} onclick={saveDeck}>
            {saving ? 'Saving deck...' : 'Save deck'}
          </button>
          <button type="button" disabled={actionButtonsDisabled} onclick={openDeleteConfirmation}>
            {deleting ? 'Deleting deck...' : deleteConfirmOpen ? 'Delete pending' : 'Delete deck'}
          </button>
        {/if}
      </div>

      <div class="editor-page__status">
        <p>{isCreateMode ? 'The current local draft is ready to be submitted as a new deck record.' : editorDirty ? 'Local changes are pending on top of the last loaded deck response.' : 'Local draft matches the last loaded deck response.'}</p>
        <p>Update payload is ready with {updatePayloadCardCount} card entries plus deck metadata.</p>
        <p>Replace payload is ready with {replacePayloadCardCount} card entries.</p>
      </div>

      {#if createErrorMessage}
        <ContentStatePanel
          title={deckEditorStateCopy.createErrorTitle}
          message={createErrorMessage}
          tone="error"
        />
      {:else if saveErrorMessage}
        <ContentStatePanel
          title={deckEditorStateCopy.saveErrorTitle}
          message={saveErrorMessage}
          tone="error"
        />
      {:else if deleteErrorMessage}
        <ContentStatePanel
          title={deckEditorStateCopy.deleteErrorTitle}
          message={deleteErrorMessage}
          tone="error"
        />
      {:else if saveSuccessVisible}
        <ContentStatePanel
          title={deckEditorStateCopy.saveSuccessTitle}
          message={saveSuccessMessage ?? 'Deck changes were saved.'}
        />
      {/if}

      {#if !isCreateMode && deleting}
        <ContentStatePanel
          title={deckEditorStateCopy.deleteLoadingTitle}
          message={deckEditorStateCopy.deleteLoadingMessage}
        />
      {:else if !isCreateMode && deleteConfirmOpen}
        <ContentStatePanel
          title={deckEditorStateCopy.deleteConfirmTitle}
          message={deckEditorStateCopy.deleteConfirmMessage}
          tone="error"
        >
          <div class="editor-page__confirm-actions">
            <button type="button" onclick={deleteDeckRecord}>Confirm delete</button>
            <button type="button" onclick={cancelDeleteConfirmation}>Cancel</button>
          </div>
        </ContentStatePanel>
      {:else if !isCreateMode && validating}
        <ContentStatePanel
          title={deckEditorStateCopy.validationLoadingTitle}
          message={deckEditorStateCopy.validationLoadingMessage}
        />
      {:else if !isCreateMode && validationErrorMessage}
        <ContentStatePanel
          title={deckEditorStateCopy.validationErrorTitle}
          message={validationErrorMessage}
          tone="error"
          actionLabel="Retry validation"
          onAction={runValidation}
        />
      {:else if !isCreateMode && validationResult}
        <div class="editor-page__validation">
          <div class="editor-page__validation-header">
            <div class="editor-page__validation-copy">
              <p>Validation result</p>
              <h3>{validationResult.valid ? 'Deck draft is valid' : 'Deck draft has validation issues'}</h3>
            </div>

            <div class="editor-page__hero-tags">
              <TagChip label={validationResult.valid ? 'Valid' : 'Invalid'} tone={validationResult.valid ? 'success' : 'warning'} />
              <TagChip label={`Normalized ${validationResult.normalizedTotalCards}`} tone="accent" />
              {#if validationIsStale}
                <TagChip label="Draft changed" tone="muted" />
              {/if}
            </div>
          </div>

          <div class="editor-page__stats editor-page__stats--compact">
            <StatBlock
              value={validationResult.valid ? 'Valid' : 'Invalid'}
              label="Draft state"
              note={validationIsStale ? 'Last validation result applies to an older draft state' : 'Validation matches the current draft cards'}
            />
            <StatBlock
              value={validationResult.normalizedTotalCards}
              label="Normalized cards"
              note="Total cards reported by the validation response"
            />
            <StatBlock
              value={validationIssueCount}
              label="Issues"
              note={validationIssueCount ? 'Server-reported validation issues' : 'No validation issues were returned'}
            />
          </div>

          {#if validationIssueCount}
            <ul class="editor-page__validation-list">
              {#each validationResult.issues as issue}
                <li>
                  <p>{issue.message}</p>
                  <span>{issue.field ? `${issue.field} | ${issue.code}` : issue.code}</span>
                </li>
              {/each}
            </ul>
          {:else}
            <div class="editor-page__note">
              <p>{deckEditorStateCopy.validationNoIssuesMessage}</p>
            </div>
          {/if}
        </div>
      {:else if isCreateMode}
        <div class="editor-page__note">
          <p>{deckEditorStateCopy.createModeValidationMessage}</p>
        </div>
      {/if}
    </SectionFrame>
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
  .editor-page__grid,
  .editor-page__card-detail,
  .editor-page__note {
    display: grid;
    gap: 1.5rem;
  }

  .editor-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .editor-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .editor-page__hero-copy p,
  .editor-page__hero-copy h3,
  .editor-page__card-detail p,
  .editor-page__note p {
    margin: 0;
  }

  .editor-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .editor-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .editor-page__hero-tags,
  .editor-page__card-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .editor-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .editor-page__grid {
    grid-template-columns: minmax(0, 1.05fr) minmax(19rem, 0.95fr);
  }

  .editor-page__card-detail {
    align-content: start;
  }

  .editor-page__card-detail h3 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 1.45rem;
  }

  .editor-page__card-detail > div:first-child p,
  .editor-page__card-detail > p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .editor-page__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .editor-page__fieldset {
    display: grid;
    gap: 1rem;
    border: 1px solid var(--color-border);
    padding: 1rem;
    margin: 0;
  }

  .editor-page__fieldset legend {
    padding: 0 0.5rem;
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .editor-page__form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .editor-page__field {
    display: grid;
    gap: 0.5rem;
  }

  .editor-page__field--span-2 {
    grid-column: span 2;
  }

  .editor-page__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .editor-page__field input,
  .editor-page__field select {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .editor-page__readonly {
    min-height: 3rem;
    display: flex;
    align-items: center;
    padding: 0.75rem 0.9rem;
    margin: 0;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    color: var(--color-text-soft);
  }

  .editor-page__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .editor-page__link-action,
  .editor-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .editor-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .editor-page__status {
    display: grid;
    gap: 0.5rem;
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid var(--color-border);
  }

  .editor-page__status p {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .editor-page__confirm-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .editor-page__confirm-actions button {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .editor-page__validation,
  .editor-page__validation-copy {
    display: grid;
    gap: 1rem;
  }

  .editor-page__validation {
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid var(--color-border);
  }

  .editor-page__validation-header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .editor-page__validation-copy p,
  .editor-page__validation-copy h3 {
    margin: 0;
  }

  .editor-page__validation-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .editor-page__validation-copy h3 {
    font-family: var(--font-display);
    font-size: 1.4rem;
    line-height: 1.15;
  }

  .editor-page__stats--compact {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .editor-page__validation-list {
    margin: 0;
    padding-left: 1.25rem;
    display: grid;
    gap: 0.85rem;
  }

  .editor-page__validation-list li {
    display: grid;
    gap: 0.3rem;
  }

  .editor-page__validation-list p,
  .editor-page__validation-list span {
    margin: 0;
  }

  .editor-page__validation-list p {
    color: var(--color-text);
    line-height: 1.55;
  }

  .editor-page__validation-list span {
    color: var(--color-text-muted);
    font-size: 0.88rem;
  }

  @media (max-width: 960px) {
    .editor-page__stats,
    .editor-page__grid,
    .editor-page__form-grid {
      grid-template-columns: 1fr;
    }

    .editor-page__field--span-2 {
      grid-column: span 1;
    }
  }
</style>
