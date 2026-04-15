<script lang="ts">
  import { onMount } from 'svelte'
  import type { DeckType, DeckValidationResponse } from '../lib/api/deckTypes'
  import { getScreen, invokeScreenAction } from '../lib/api/screens'
  import {
    buildScreenActionPayload,
    findScreenAction,
    type DeckEditorScreenResponse,
    type DeckEditorValidationDto,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import {
    buildDeckEditorActionPatch,
    createDeckEditorState,
    createEmptyDeckEditorState,
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

  type DeckCardTagItem = { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }

  const deckTypeOptions: DeckType[] = ['PLAYER', 'ENEMY']

  let loading = $state(true)
  let screen = $state<DeckEditorScreenResponse | null>(null)
  let editorState = $state<DeckEditorState>(createEmptyDeckEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedDeckId = $state<string | null>(null)
  let selectedCardKey = $state('')
  let pendingActionId = $state<string | null>(null)
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

  function getDeckTypeTone(type: DeckType | '' | null | undefined): 'muted' | 'success' | 'warning' {
    if (type === 'PLAYER') {
      return 'success'
    }

    if (type === 'ENEMY') {
      return 'warning'
    }

    return 'muted'
  }

  function buildDeckSummary(currentScreen: DeckEditorScreenResponse) {
    if (!currentScreen.draft.cards.length) {
      return 'This screen currently reports no saved card entries.'
    }

    const entryLabel = currentScreen.draft.cards.length === 1 ? 'entry' : 'entries'
    return `${currentScreen.derived.totalCards} total cards are currently reported across ${currentScreen.draft.cards.length} ${entryLabel}.`
  }

  function getDeckCardMetaLabel(entry: DeckEditorCardState, position: number) {
    return `Count ${entry.count} | Entry ${position}`
  }

  function buildDeckCardNote(entry: DeckEditorCardState, position: number, totalEntries: number) {
    const totalLabel = totalEntries === 1 ? 'draft entry' : 'draft entries'
    return `Card reference ${entry.cardId || 'N/A'} is currently tracked as item ${position} of ${totalEntries} ${totalLabel}.`
  }

  function getDeckCardTagItems(entry: DeckEditorCardState, position: number): DeckCardTagItem[] {
    return [
      { label: `x${entry.count}`, tone: entry.count > 1 ? 'success' : 'muted' as const },
      { label: `Entry ${position}`, tone: 'accent' as const },
    ]
  }

  function toDeckCardItem(entry: DeckEditorCardState, index: number, totalEntries: number): DeckCardItem {
    const position = index + 1

    return {
      id: entry.key,
      title: entry.cardId.trim() || 'Unnamed card reference',
      subtitle: 'Draft deck card',
      meta: getDeckCardMetaLabel(entry, position),
      note: buildDeckCardNote(entry, position, totalEntries),
      tags: getDeckCardTagItems(entry, position),
    }
  }

  function syncSelectedCard(nextCards: readonly DeckEditorCardState[]) {
    const nextKeys = nextCards.map((entry) => entry.key)
    selectedCardKey = nextKeys.includes(selectedCardKey) ? selectedCardKey : nextKeys[0] ?? ''
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function applyScreen(nextScreen: DeckEditorScreenResponse) {
    screen = nextScreen
    editorState = createDeckEditorState(nextScreen.draft)
    syncSelectedCard(editorState.cards)
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

  function getAction(actionId: string) {
    return screen ? findScreenAction(screen, actionId) : null
  }

  function getActionDisabledReason(actionId: string) {
    return getAction(actionId)?.disabledReason?.userMessage ?? null
  }

  function getPendingActionLabel(actionId: string) {
    switch (actionId) {
      case 'deckEditor.validate':
        return 'Validating deck...'
      case 'deckEditor.save':
        return 'Saving deck...'
      case 'deckEditor.create':
        return 'Creating deck...'
      case 'deckEditor.delete':
        return 'Deleting deck...'
      default:
        return 'Processing...'
    }
  }

  function getActionLabel(actionId: string) {
    const action = getAction(actionId)

    if (!action) {
      return ''
    }

    return pendingActionId === actionId ? getPendingActionLabel(actionId) : action.label
  }

  function toDeckEditorValidation(response: DeckValidationResponse): DeckEditorValidationDto {
    return {
      valid: response.valid,
      normalizedTotalCards: response.normalizedTotalCards,
      issues: response.issues,
      isStale: false,
    }
  }

  function getActionSuccessMessage(actionId: string) {
    switch (actionId) {
      case 'deckEditor.validate':
        return 'Deck validation refreshed.'
      case 'deckEditor.save':
        return 'Deck changes saved.'
      case 'deckEditor.create':
        return 'Deck created.'
      default:
        return null
    }
  }

  async function runAction(actionId: string) {
    if (!screen || pendingActionId) {
      return
    }

    const action = getAction(actionId)

    if (!action || !action.enabled) {
      return
    }

    pendingActionId = actionId
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const patch = buildDeckEditorActionPatch(action.id, editorState)
      const body = action.payloadTemplate ? buildScreenActionPayload(action, patch) : undefined
      const response = await invokeScreenAction(action, body === undefined ? undefined : { body })

      if (actionId === 'deckEditor.validate') {
        screen = {
          ...screen,
          validation: toDeckEditorValidation(response as DeckValidationResponse),
        }
        actionSuccessMessage = getActionSuccessMessage(actionId)
        return
      }

      if (actionId === 'deckEditor.delete') {
        removeSelectionHandoff(selectionHandoffKeys.deckId)
        setDeckPageFeedback(deckListStateCopy.deletedFeedback)
        navigateTo(pathBuilders.deckList(), true)
        return
      }

      const responseDeckId =
        response && typeof response === 'object' && 'id' in response ? String(response.id) : null
      const nextDeckId = responseDeckId ?? (screen.deckId == null ? null : String(screen.deckId))

      if (!nextDeckId) {
        throw new Error('Deck action completed without a follow-up deck id.')
      }

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
    if (!getAction('deckEditor.delete') || pendingActionId) {
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
  {:else if screen}
    <SectionFrame
      eyebrow={screen.mode === 'create' ? 'New Deck' : 'Selected Deck'}
      title={screen.derived.title}
      description={screen.mode === 'create'
        ? deckEditorStateCopy.createDescription
        : deckEditorStateCopy.editDescription}
    >
      <div class="editor-page__hero">
        <div class="editor-page__hero-copy">
          <p>{screen.derived.deckTypeLabel}</p>
          <h3>{buildDeckSummary(screen)}</h3>
        </div>

        <div class="editor-page__hero-tags">
          <TagChip label={screen.derived.deckTypeLabel} tone={getDeckTypeTone(screen.draft.type)} />
          <TagChip label={`${screen.draft.cards.length} Entries`} tone="accent" />
          <TagChip
            label={screen.derived.dirty ? 'Draft Changed' : 'Draft Synced'}
            tone={screen.derived.dirty ? 'warning' : 'success'}
          />
        </div>
      </div>

      <div class="editor-page__stats">
        <StatBlock
          value={screen.derived.totalCards}
          label="Cards"
          note="Total cards reported by the current screen response"
        />
        <StatBlock
          value={screen.draft.cards.length}
          label="Screen entries"
          note="Card entries contained in the latest server draft"
        />
        <StatBlock
          value={screen.derived.deckTypeLabel}
          label="Deck type"
          note="Deck classification reported by the current screen"
        />
      </div>

      <fieldset class="editor-page__fieldset">
        <legend>Deck metadata</legend>

        <div class="editor-page__form-grid">
          <label class="editor-page__field editor-page__field--span-2">
            <span>Deck name</span>
            <input
              type="text"
              bind:value={editorState.name}
              placeholder="Enter deck name"
              disabled={Boolean(pendingActionId)}
            />
          </label>

          <label class="editor-page__field">
            <span>Deck type</span>
            <select bind:value={editorState.type} disabled={Boolean(pendingActionId)}>
              {#each deckTypeOptions as option}
                <option value={option}>{option}</option>
              {/each}
            </select>
          </label>

          <div class="editor-page__field">
            <span>{screen.mode === 'create' ? 'Deck id' : 'Source deck id'}</span>
            <p class="editor-page__readonly">{screen.deckId == null ? 'Assigned after create' : screen.deckId}</p>
          </div>
        </div>
      </fieldset>

      {#if screen.uiNotices.length}
        <div class="editor-page__note">
          {#each screen.uiNotices as notice}
            <p>{notice}</p>
          {/each}
        </div>
      {/if}
    </SectionFrame>

    <div class="editor-page__grid">
      <SectionFrame
        title="Deck cards"
        description="The list reflects the current local input state while the summary above continues to reflect the latest screen response."
      >
        <EntityListPane
          items={editorState.cards.map((entry, index) => toDeckCardItem(entry, index, editorState.cards.length))}
          selectedId={selectedCardKey}
          onSelect={(id) => {
            if (!pendingActionId) {
              selectedCardKey = id
            }
          }}
          emptyMessage="No cards are assigned to this deck yet."
        />
      </SectionFrame>

      <SectionFrame
        title="Selected card"
        description="This panel only manages local input state for the currently selected entry."
      >
        {#if selectedCardEntry}
          <div class="editor-page__card-detail">
            <div>
              <h3>{selectedCardEntry.cardId || 'Unnamed card reference'}</h3>
              <p>{getDeckCardMetaLabel(selectedCardEntry, selectedCardPosition)}</p>
            </div>

            <div class="editor-page__card-tags">
              {#each getDeckCardTagItems(selectedCardEntry, selectedCardPosition) as tag}
                <TagChip label={tag.label} tone={tag.tone} />
              {/each}
            </div>

            <p>{buildDeckCardNote(selectedCardEntry, selectedCardPosition, editorState.cards.length)}</p>

            <fieldset class="editor-page__fieldset">
              <legend>Selected card draft</legend>

              <div class="editor-page__form-grid">
                <label class="editor-page__field editor-page__field--span-2">
                  <span>Card id</span>
                  <input
                    type="text"
                    value={selectedCardEntry.cardId}
                    disabled={Boolean(pendingActionId)}
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
                    disabled={Boolean(pendingActionId)}
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
              <p>Edits stay local until an action is invoked.</p>
              <p>Validation, dirty state, total cards, and button availability render from the latest screen model.</p>
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
      description={screen.mode === 'create'
        ? 'Create a new deck record from the current local input state.'
        : 'Validate, save, or delete by invoking the screen-declared actions.'}
    >
      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to deck list
        </a>

        {#if getAction('deckEditor.validate')}
          <button
            type="button"
            disabled={Boolean(pendingActionId) || deleteConfirmOpen || !getAction('deckEditor.validate')?.enabled}
            onclick={() => void runAction('deckEditor.validate')}
          >
            {getActionLabel('deckEditor.validate')}
          </button>
        {/if}

        {#if screen.mode === 'create'}
          <button
            type="button"
            disabled={Boolean(pendingActionId) || !getAction('deckEditor.create')?.enabled}
            onclick={() => void runAction('deckEditor.create')}
          >
            {getActionLabel('deckEditor.create')}
          </button>
        {:else}
          <button
            type="button"
            disabled={Boolean(pendingActionId) || deleteConfirmOpen || !getAction('deckEditor.save')?.enabled}
            onclick={() => void runAction('deckEditor.save')}
          >
            {getActionLabel('deckEditor.save')}
          </button>
          <button
            type="button"
            disabled={Boolean(pendingActionId) || !getAction('deckEditor.delete')?.enabled}
            onclick={openDeleteConfirmation}
          >
            {pendingActionId === 'deckEditor.delete'
              ? getPendingActionLabel('deckEditor.delete')
              : deleteConfirmOpen
                ? 'Delete pending'
                : getAction('deckEditor.delete')?.label}
          </button>
        {/if}
      </div>

      <div class="editor-page__status">
        <p>Current server draft title: {screen.derived.title}</p>
        <p>Current server dirty flag: {screen.derived.dirty ? 'Changed' : 'Synced'}</p>
        <p>Current server validation state: {screen.validation.valid ? 'Valid' : 'Invalid'}</p>
        {#if getActionDisabledReason('deckEditor.validate')}
          <p>{getActionDisabledReason('deckEditor.validate')}</p>
        {/if}
        {#if getActionDisabledReason('deckEditor.save')}
          <p>{getActionDisabledReason('deckEditor.save')}</p>
        {/if}
        {#if getActionDisabledReason('deckEditor.create')}
          <p>{getActionDisabledReason('deckEditor.create')}</p>
        {/if}
        {#if getActionDisabledReason('deckEditor.delete')}
          <p>{getActionDisabledReason('deckEditor.delete')}</p>
        {/if}
      </div>

      {#if actionErrorMessage}
        <ContentStatePanel
          title="Deck action failed"
          message={actionErrorMessage}
          tone="error"
        />
      {:else if actionSuccessMessage}
        <ContentStatePanel
          title="Deck action complete"
          message={actionSuccessMessage}
        />
      {/if}

      {#if deleteConfirmOpen}
        <ContentStatePanel
          title={deckEditorStateCopy.deleteConfirmTitle}
          message={deckEditorStateCopy.deleteConfirmMessage}
          tone="error"
        >
          <div class="editor-page__confirm-actions">
            <button type="button" onclick={() => void runAction('deckEditor.delete')}>
              Confirm delete
            </button>
            <button type="button" onclick={cancelDeleteConfirmation}>Cancel</button>
          </div>
        </ContentStatePanel>
      {/if}
    </SectionFrame>

    <SectionFrame
      title="Validation"
      description="The panel renders the validation block carried by the latest screen model."
    >
      <div class="editor-page__validation">
        <div class="editor-page__validation-header">
          <div class="editor-page__validation-copy">
            <p>Validation result</p>
            <h3>{screen.validation.valid ? 'Deck draft is valid' : 'Deck draft has validation issues'}</h3>
          </div>

          <div class="editor-page__hero-tags">
            <TagChip
              label={screen.validation.valid ? 'Valid' : 'Invalid'}
              tone={screen.validation.valid ? 'success' : 'warning'}
            />
            <TagChip label={`Normalized ${screen.validation.normalizedTotalCards}`} tone="accent" />
            {#if screen.validation.isStale}
              <TagChip label="Stale" tone="muted" />
            {/if}
          </div>
        </div>

        <div class="editor-page__stats editor-page__stats--compact">
          <StatBlock
            value={screen.validation.valid ? 'Valid' : 'Invalid'}
            label="Draft state"
            note="Validation state reported by the latest server response"
          />
          <StatBlock
            value={screen.validation.normalizedTotalCards}
            label="Normalized cards"
            note="Total cards reported by server-side validation"
          />
          <StatBlock
            value={screen.validation.issues.length}
            label="Issues"
            note={screen.validation.issues.length
              ? 'Server-reported validation issues'
              : 'No validation issues were returned'}
          />
        </div>

        {#if screen.validation.issues.length}
          <ul class="editor-page__validation-list">
            {#each screen.validation.issues as issue}
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

  .editor-page__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
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

  .editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .editor-page__link-action,
  .editor-page__actions button,
  .editor-page__confirm-actions button {
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

  .editor-page__validation,
  .editor-page__validation-copy {
    display: grid;
    gap: 1rem;
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
