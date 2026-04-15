<script lang="ts">
  import { onMount } from 'svelte'
  import { getScreen } from '../lib/api/screens'
  import {
    invokeEditorEntityActionAndRefresh,
    invokeEditorScreenAction,
  } from '../lib/api/editorScreenActions'
  import {
    findPresetEditorAction,
    type PresetEditorActionPayload,
    type PresetEditorActionId,
    type PresetEditorActionResponseById,
    type PresetEditorScreenAction,
    type PresetEditorScreenResponse,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders, resolveRouteMatch, routePaths } from '../lib/navigation'
  import {
    buildPresetEditorActionPatch,
    createEmptyPresetEditorState,
    createPresetEditorState,
    type PresetEditorState,
  } from '../lib/presets/editorModel'
  import {
    createPresetEditorLocalPresentation,
    isPresetEditorLocalDirty,
  } from '../lib/presets/presentationState.js'
  import {
    presetEditorStateCopy,
    presetListStateCopy,
    readPresetPageFeedback,
    setPresetPageFeedback,
    type PresetPageFeedback,
  } from '../lib/presets/pageState'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  let loading = $state(true)
  // Server snapshot: the latest preset editor screen returned by the Screen API.
  let screen = $state<PresetEditorScreenResponse | null>(null)
  let editorState = $state<PresetEditorState>(createEmptyPresetEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedPresetId = $state<string | null>(null)
  let pendingActionId = $state<PresetEditorActionId | null>(null)
  let deleteConfirmOpen = $state(false)
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let requestSequence = 0
  let feedback = $state<PresetPageFeedback | null>(null)

  function getPresetIdFromRoute() {
    if (typeof window === 'undefined') return null

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key !== 'preset-editor') {
      return null
    }

    const presetId = match.params.id?.trim()
    return presetId ? presetId : null
  }

  function isCreatePresetRoute() {
    if (typeof window === 'undefined') return false

    const match = resolveRouteMatch(window.location.pathname)
    return match?.page.key === 'preset-editor' && !match.params.id && match.page.path === routePaths.presetEditor
  }

  function isPresetApiId(value: string | null | undefined): value is string {
    return typeof value === 'string' && /^\d+$/.test(value.trim())
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function applyScreen(nextScreen: PresetEditorScreenResponse) {
    // Action success and initial load both resync the local draft from the latest screen snapshot.
    screen = nextScreen
    editorState = createPresetEditorState(nextScreen.draft)
    deleteConfirmOpen = false

    if (nextScreen.presetId != null) {
      setSelectionHandoff(selectionHandoffKeys.presetId, String(nextScreen.presetId))
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

  function replaceWithPresetEditor(id: string) {
    navigateTo(pathBuilders.presetEditor(id), true)
  }

  async function loadPresetEditorScreen(presetId?: string | null) {
    const requestId = ++requestSequence

    loading = true
    screen = null
    editorState = createEmptyPresetEditorState()
    requestedPresetId = presetId ?? null
    errorMessage = null
    notFoundId = null
    resetTransientState()

    if (presetId && !isPresetApiId(presetId)) {
      notFoundId = presetId
      loading = false
      return
    }

    try {
      const response = presetId
        ? await getScreen<PresetEditorScreenResponse>('PresetEditor', { presetId })
        : await getScreen<PresetEditorScreenResponse>('PresetEditor')

      if (requestId !== requestSequence) {
        return
      }

      applyScreen(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFoundId = presetId ?? null
        return
      }

      errorMessage = getApiErrorMessage(error, 'Unable to load the selected preset editor screen.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function retryLoad() {
    const routePresetId = getPresetIdFromRoute()

    if (routePresetId) {
      void loadPresetEditorScreen(routePresetId)
      return
    }

    if (isCreatePresetRoute()) {
      void loadPresetEditorScreen(null)
      return
    }

    if (requestedPresetId) {
      void loadPresetEditorScreen(requestedPresetId)
    }
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

  function toNullablePositiveNumber(value: string) {
    const normalized = value.trim()
    const parsed = normalized ? Number(normalized) : null

    if (parsed === null || !Number.isFinite(parsed) || parsed <= 0) {
      return null
    }

    return parsed
  }

  function updateName(value: string) {
    editorState = {
      ...editorState,
      name: value,
    }
  }

  function updateCharacterId(value: string) {
    editorState = {
      ...editorState,
      characterId: toNullablePositiveNumber(value),
    }
  }

  function updateExCardId(value: string) {
    editorState = {
      ...editorState,
      exCardId: value,
    }
  }

  function updateDeckCardIds(value: string) {
    editorState = {
      ...editorState,
      deckCardIds: parseIdentifierText(value),
    }
  }

  function updatePassiveIds(value: string) {
    editorState = {
      ...editorState,
      passiveIds: parseIdentifierText(value),
    }
  }

  function handlePrimarySubmit(event: SubmitEvent) {
    event.preventDefault()
    void runAction(isCreateMode ? 'presetEditor.create' : 'presetEditor.save')
  }

  function getPendingActionLabel(actionId: PresetEditorActionId) {
    switch (actionId) {
      case 'presetEditor.save':
        return 'Saving preset...'
      case 'presetEditor.create':
        return 'Creating preset...'
      case 'presetEditor.clone':
        return 'Cloning preset...'
      case 'presetEditor.delete':
        return 'Deleting preset...'
    }
  }

  function getActionSuccessMessage(actionId: PresetEditorActionId) {
    switch (actionId) {
      case 'presetEditor.save':
        return 'Preset saved.'
      case 'presetEditor.create':
        return 'Preset created.'
      case 'presetEditor.clone':
        return null
      case 'presetEditor.delete':
        return null
    }
  }

  async function runAction(actionId: PresetEditorActionId) {
    if (!screen || pendingActionId) {
      return
    }

    if (!findPresetEditorAction(screen, actionId)?.enabled) {
      return
    }

    pendingActionId = actionId
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      if (actionId === 'presetEditor.delete') {
        const result = await invokeEditorScreenAction<
          PresetEditorScreenResponse,
          PresetEditorScreenAction,
          PresetEditorActionId,
          PresetEditorState,
          PresetEditorActionPayload,
          PresetEditorActionResponseById['presetEditor.delete']
        >({
          screen,
          actionId,
          editorState,
          findAction: findPresetEditorAction,
          buildPatch: buildPresetEditorActionPatch,
        })

        if (!result) {
          return
        }

        removeSelectionHandoff(selectionHandoffKeys.presetId)
        setPresetPageFeedback(presetListStateCopy.deletedFeedback)
        navigateTo(pathBuilders.presetList(), true)
        return
      }

      const result = await invokeEditorEntityActionAndRefresh<
        PresetEditorScreenResponse,
        PresetEditorScreenAction,
        PresetEditorActionId,
        PresetEditorState,
        PresetEditorActionPayload,
        PresetEditorActionResponseById['presetEditor.save' | 'presetEditor.create' | 'presetEditor.clone'],
        string
      >({
        screen,
        actionId,
        editorState,
        findAction: findPresetEditorAction,
        buildPatch: buildPresetEditorActionPatch,
        getResourceId: (response) => String(response.id),
        refreshScreen: async (nextPresetId) => {
          await loadPresetEditorScreen(nextPresetId)
        },
      })

      if (!result) {
        return
      }

      const nextPresetId = result.resourceId

      if (actionId === 'presetEditor.clone') {
        setPresetPageFeedback(presetEditorStateCopy.cloneSuccessFeedback)
        navigateTo(pathBuilders.presetEditor(nextPresetId))
        return
      }

      actionSuccessMessage = getActionSuccessMessage(actionId)

      if (actionId === 'presetEditor.create') {
        replaceWithPresetEditor(nextPresetId)
      }
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'Unable to complete the requested preset action.')
    } finally {
      pendingActionId = null
    }
  }

  function openDeleteConfirmation() {
    if (!screen || !findPresetEditorAction(screen, 'presetEditor.delete') || pendingActionId) {
      return
    }

    clearActionFeedback()
    deleteConfirmOpen = true
  }

  function cancelDeleteConfirmation() {
    if (pendingActionId === 'presetEditor.delete') {
      return
    }

    deleteConfirmOpen = false
  }

  onMount(() => {
    feedback = readPresetPageFeedback()

    const routePresetId = getPresetIdFromRoute()

    if (routePresetId) {
      void loadPresetEditorScreen(routePresetId)
      return
    }

    if (isCreatePresetRoute()) {
      void loadPresetEditorScreen(null)
      return
    }

    const handoffPresetId = readSelectionHandoff(selectionHandoffKeys.presetId)?.trim()

    if (handoffPresetId) {
      replaceWithPresetEditor(handoffPresetId)
      return
    }

    loading = false
  })

  const isCreateMode = $derived.by(() => screen?.mode === 'create')

  const localPresentation = $derived.by(() => {
    if (!screen) {
      return null
    }

    const localDirty = isPresetEditorLocalDirty(editorState, screen.draft)

    // Local presentation mirrors current editor input for title/dirty/preview only.
    return createPresetEditorLocalPresentation(
      editorState,
      screen.draft,
      screen.resolved,
      screen.mode,
      localDirty,
    )
  })

  const editorControlsDisabled = $derived.by(() => Boolean(loading || pendingActionId))
  const saveAction = $derived.by(() => (screen ? findPresetEditorAction(screen, 'presetEditor.save') : null))
  const createAction = $derived.by(() => (screen ? findPresetEditorAction(screen, 'presetEditor.create') : null))
  const cloneAction = $derived.by(() => (screen ? findPresetEditorAction(screen, 'presetEditor.clone') : null))
  const deleteAction = $derived.by(() => (screen ? findPresetEditorAction(screen, 'presetEditor.delete') : null))
</script>

<div class="preset-editor-page-shell">
  {#if loading}
    <SectionFrame
      eyebrow="Preset Editor"
      title="Loading preset editor"
      description="Resolving the current preset editor screen from the URL."
    >
      <ContentStatePanel
        title="Loading preset"
        message="Fetching the selected preset editor screen."
      />
    </SectionFrame>
  {:else if notFoundId}
    <SectionFrame
      eyebrow="Preset Missing"
      title="Preset not found"
      description="The requested preset id could not be restored from the current route or the preset screen API."
    >
      <div class="preset-editor-page__copy">
        <p>Requested id: {notFoundId}</p>
        <p>Open a saved preset from the preset archive and try again.</p>
      </div>

      <div class="preset-editor-page__actions">
        <a class="preset-editor-page__link-action" data-nav href={pathBuilders.presetList()}>
          Back to preset archive
        </a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame
      eyebrow="Preset Error"
      title="Preset could not be loaded"
      description="The editor could not retrieve the current preset screen response."
    >
      <ContentStatePanel
        title="Unable to load preset editor"
        message={errorMessage}
        tone="error"
        actionLabel="Retry load"
        onAction={retryLoad}
      />

      <div class="preset-editor-page__actions">
        <a class="preset-editor-page__link-action" data-nav href={pathBuilders.presetList()}>
          Back to preset archive
        </a>
      </div>
    </SectionFrame>
  {:else if screen && localPresentation}
    <form class="preset-editor-page" onsubmit={handlePrimarySubmit}>
      <SectionFrame
        eyebrow={isCreateMode ? 'New Preset' : 'Selected Preset'}
        title={localPresentation.title}
        description={isCreateMode
          ? 'Create a new preset from the current local draft.'
          : 'The editor renders the current preset screen model and keeps only local input state in the browser.'}
      >
        {#if feedback}
          <ContentStatePanel title={feedback.title} message={feedback.message} />
        {/if}

        <div class="preset-editor-page__hero">
          <div class="preset-editor-page__hero-copy">
            <p>{localPresentation.character.label}</p>
            <h3>
              {localPresentation.deckCount} deck cards, {localPresentation.passiveCount} passives, EX {localPresentation.ex.label}
            </h3>
            <span>{localPresentation.summary}</span>
          </div>

          <div class="preset-editor-page__hero-tags">
            {#each localPresentation.character.tags as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
            {#each localPresentation.ex.tags as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
            <TagChip
              label={localPresentation.dirty ? 'Draft Changed' : 'Draft Synced'}
              tone={localPresentation.dirty ? 'warning' : 'success'}
            />
            <TagChip
              label={localPresentation.previewNeedsResolveRefresh ? 'Preview Pending Refresh' : 'Preview Synced'}
              tone={localPresentation.previewNeedsResolveRefresh ? 'warning' : 'success'}
            />
          </div>
        </div>

        <div class="preset-editor-page__stats">
          <StatBlock
            value={screen.presetId == null ? 'Assigned after create' : screen.presetId}
            label="Preset id"
            note="Current preset screen source"
          />
          <StatBlock
            value={screen.derived.updatedAtLabel}
            label="Updated"
            note="Last update label from the screen model"
          />
          <StatBlock
            value={screen.derived.createdAtLabel}
            label="Created"
            note="Original creation label from the screen model"
          />
        </div>

        <fieldset class="preset-editor-page__fieldset">
          <legend>Preset draft</legend>

          <div class="preset-editor-page__form-grid">
            <label class="preset-editor-page__field preset-editor-page__field--span-2">
              <span>Preset name</span>
              <input
                type="text"
                value={editorState.name}
                placeholder="Enter preset name"
                disabled={editorControlsDisabled}
                oninput={(event) => updateName((event.currentTarget as HTMLInputElement).value)}
              />
            </label>

            <label class="preset-editor-page__field">
              <span>Character id</span>
              <input
                type="number"
                min="1"
                step="1"
                value={editorState.characterId ?? ''}
                placeholder="Enter character id"
                disabled={editorControlsDisabled}
                oninput={(event) => updateCharacterId((event.currentTarget as HTMLInputElement).value)}
              />
            </label>

            <label class="preset-editor-page__field">
              <span>EX card id</span>
              <input
                type="text"
                value={editorState.exCardId}
                placeholder="Enter EX card id"
                disabled={editorControlsDisabled}
                oninput={(event) => updateExCardId((event.currentTarget as HTMLInputElement).value)}
              />
            </label>

            <label class="preset-editor-page__field preset-editor-page__field--span-2">
              <span>Deck card ids</span>
              <textarea
                rows="6"
                value={formatIdentifierText(editorState.deckCardIds)}
                placeholder="One card id per line"
                disabled={editorControlsDisabled}
                oninput={(event) => updateDeckCardIds((event.currentTarget as HTMLTextAreaElement).value)}
              ></textarea>
            </label>

            <label class="preset-editor-page__field preset-editor-page__field--span-2">
              <span>Passive ids</span>
              <textarea
                rows="4"
                value={formatIdentifierText(editorState.passiveIds)}
                placeholder="One passive id per line"
                disabled={editorControlsDisabled}
                oninput={(event) => updatePassiveIds((event.currentTarget as HTMLTextAreaElement).value)}
              ></textarea>
            </label>
          </div>
        </fieldset>
      </SectionFrame>

      <div class="preset-editor-page__grid">
        <SectionFrame
          title="Deck card preview"
          description="The preview reflects the current local draft and reuses the latest resolved screen metadata when ids still match."
        >
          <EntityListPane
            items={localPresentation.deckItems}
            emptyMessage="No deck card ids are currently present in the local draft."
          />
        </SectionFrame>

        <SectionFrame
          title="Passive preview"
          description="The preview reflects the current local draft and reuses the latest resolved screen metadata when ids still match."
        >
          <EntityListPane
            items={localPresentation.passiveItems}
            emptyMessage="No passive ids are currently present in the local draft."
          />
        </SectionFrame>
      </div>

      <div class="preset-editor-page__grid">
        <SectionFrame
          title="Character reference"
          description="The reference follows the current local character selection and falls back to the latest resolved server snapshot."
        >
          <div class="preset-editor-page__copy">
            <p><strong>{localPresentation.character.label}</strong></p>
            <p>{localPresentation.character.subtitle}</p>
          </div>
        </SectionFrame>

        <SectionFrame
          title="EX card reference"
          description="The reference follows the current local EX selection and falls back to the latest resolved server snapshot."
        >
          <div class="preset-editor-page__copy">
            <p><strong>{localPresentation.ex.label}</strong></p>
            <p>{localPresentation.ex.subtitle}</p>
          </div>
        </SectionFrame>
      </div>

      <SectionFrame
        title="Editor actions"
        description="Save, create, clone, and delete are invoked through screen-declared actions."
      >
        <div class="preset-editor-page__actions">
          <a class="preset-editor-page__link-action" data-nav href={pathBuilders.presetList()}>
            Back to preset archive
          </a>

          {#if isCreateMode}
            {#if createAction}
              <button
                type="submit"
                disabled={editorControlsDisabled || !createAction.enabled}
              >
                {pendingActionId === createAction.id ? getPendingActionLabel(createAction.id) : createAction.label}
              </button>
            {/if}
          {:else}
            {#if saveAction}
              <button
                type="submit"
                disabled={editorControlsDisabled || deleteConfirmOpen || !saveAction.enabled}
              >
                {pendingActionId === saveAction.id ? getPendingActionLabel(saveAction.id) : saveAction.label}
              </button>
            {/if}
            {#if cloneAction}
              <button
                type="button"
                disabled={editorControlsDisabled || deleteConfirmOpen || !cloneAction.enabled}
                onclick={() => void runAction(cloneAction.id)}
              >
                {pendingActionId === cloneAction.id ? getPendingActionLabel(cloneAction.id) : cloneAction.label}
              </button>
            {/if}
            {#if deleteAction}
              <button
                type="button"
                disabled={editorControlsDisabled || !deleteAction.enabled}
                onclick={openDeleteConfirmation}
              >
                {pendingActionId === 'presetEditor.delete'
                  ? getPendingActionLabel('presetEditor.delete')
                  : deleteConfirmOpen
                    ? 'Delete pending'
                    : deleteAction.label}
              </button>
            {/if}
          {/if}
        </div>

        <div class="preset-editor-page__status">
          <p>
            {localPresentation.dirty
              ? 'Local changes are pending on top of the last loaded preset screen.'
              : 'Local draft matches the last loaded preset screen.'}
          </p>
          <p>
            {localPresentation.previewNeedsResolveRefresh
              ? 'Changed ids are reflected immediately in the local preview. Save, create, or clone to refresh server-resolved metadata for those ids.'
              : 'Local preview is aligned with the latest resolved screen snapshot.'}
          </p>
          <p>Character, deck card ids, EX card id, and passive ids are normalized when an action is invoked.</p>
        </div>

        {#if actionErrorMessage}
          <ContentStatePanel title="Preset action failed" message={actionErrorMessage} tone="error" />
        {:else if actionSuccessMessage}
          <ContentStatePanel title="Preset action complete" message={actionSuccessMessage} />
        {/if}

        {#if deleteConfirmOpen}
          <ContentStatePanel
            title={presetEditorStateCopy.deleteConfirmTitle}
            message={presetEditorStateCopy.deleteConfirmMessage}
            tone="error"
          >
            <div class="preset-editor-page__confirm-actions">
              <button type="button" onclick={() => void runAction('presetEditor.delete')}>Confirm delete</button>
              <button type="button" onclick={cancelDeleteConfirmation}>Cancel</button>
            </div>
          </ContentStatePanel>
        {/if}
      </SectionFrame>
    </form>
  {:else}
    <SectionFrame
      eyebrow="Preset Selection"
      title="Preset selection unavailable"
      description="Open a preset from the preset archive to restore the expected editor context."
    >
      <div class="preset-editor-page__copy">
        <p>No preset id is present in the current URL.</p>
        <p>Use the preset archive to open an existing preset or start a new draft.</p>
      </div>

      <div class="preset-editor-page__actions">
        <a class="preset-editor-page__link-action" data-nav href={pathBuilders.presetList()}>
          Back to preset archive
        </a>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .preset-editor-page-shell,
  .preset-editor-page,
  .preset-editor-page__grid,
  .preset-editor-page__copy,
  .preset-editor-page__actions {
    display: grid;
    gap: 1.5rem;
  }

  .preset-editor-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .preset-editor-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .preset-editor-page__hero-copy p,
  .preset-editor-page__hero-copy h3,
  .preset-editor-page__hero-copy span,
  .preset-editor-page__copy p,
  .preset-editor-page__status p {
    margin: 0;
  }

  .preset-editor-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .preset-editor-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .preset-editor-page__hero-copy span {
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .preset-editor-page__hero-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .preset-editor-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .preset-editor-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .preset-editor-page__fieldset {
    display: grid;
    gap: 1rem;
    border: 1px solid var(--color-border);
    padding: 1rem;
    margin: 0;
  }

  .preset-editor-page__fieldset legend {
    padding: 0 0.5rem;
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .preset-editor-page__form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .preset-editor-page__field {
    display: grid;
    gap: 0.5rem;
  }

  .preset-editor-page__field--span-2 {
    grid-column: span 2;
  }

  .preset-editor-page__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .preset-editor-page__field input,
  .preset-editor-page__field textarea,
  .preset-editor-page__actions button,
  .preset-editor-page__confirm-actions button {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .preset-editor-page__field textarea {
    min-height: 7rem;
    resize: vertical;
  }

  .preset-editor-page__copy p,
  .preset-editor-page__status p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .preset-editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .preset-editor-page__link-action {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .preset-editor-page__status {
    display: grid;
    gap: 0.5rem;
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid var(--color-border);
  }

  .preset-editor-page__confirm-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .preset-editor-page__confirm-actions button {
    min-height: 2.75rem;
  }

  @media (max-width: 960px) {
    .preset-editor-page__stats,
    .preset-editor-page__grid,
    .preset-editor-page__form-grid {
      grid-template-columns: 1fr;
    }

    .preset-editor-page__field--span-2 {
      grid-column: span 1;
    }
  }
</style>
