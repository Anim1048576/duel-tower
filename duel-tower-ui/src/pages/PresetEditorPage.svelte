<script lang="ts">
  import { onMount } from 'svelte'
  import { listCharacters } from '../lib/api/characters'
  import type { CharacterProfileResponse } from '../lib/api/characterTypes'
  import { clonePreset, createPreset, deletePreset, getPreset, updatePreset } from '../lib/api/presets'
  import type { PresetResponse, PresetTimestampValue } from '../lib/api/presetTypes'
  import { listCards, listPassives } from '../lib/api/content'
  import type { CardDefinition, PassiveDefinition } from '../lib/api/contentTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders, resolveRouteMatch, routePaths } from '../lib/navigation'
  import {
    addPresetIdentifier,
    clonePresetEditorState,
    createEmptyPresetEditorState,
    createPresetEditorState,
    isPresetEditorStateDirty,
    normalizePresetEditorState,
    normalizePresetIdentifier,
    toPresetEditorPayload,
    type PresetEditorState,
  } from '../lib/presets/editorModel'
  import {
    presetListStateCopy,
    presetEditorStateCopy,
    readPresetPageFeedback,
    setPresetPageFeedback,
    type PresetPageFeedback,
  } from '../lib/presets/pageState'
  import {
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type PresetEditorMode = 'create' | 'edit' | 'selection'

  type PresetEntryItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  const timestampFormatter = new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })

  let loading = $state(true)
  let editorMode = $state<PresetEditorMode>('selection')
  let preset = $state<PresetResponse | null>(null)
  let originalState = $state<PresetEditorState | null>(null)
  let editorState = $state<PresetEditorState>(createEmptyPresetEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedPresetId = $state<string | null>(null)
  let requestSequence = 0
  let saving = $state(false)
  let saveErrorMessage = $state<string | null>(null)
  let saveSuccessMessage = $state<string | null>(null)
  let saveRequestSequence = 0
  let lastSaveMode = $state<'create' | 'edit' | null>(null)
  let cloning = $state(false)
  let cloneErrorMessage = $state<string | null>(null)
  let cloneRequestSequence = 0
  let deleting = $state(false)
  let deleteConfirmOpen = $state(false)
  let deleteErrorMessage = $state<string | null>(null)
  let deleteRequestSequence = 0
  let feedback = $state<PresetPageFeedback | null>(null)
  let referenceLoading = $state(true)
  let referenceErrorMessage = $state<string | null>(null)
  let characters = $state<CharacterProfileResponse[]>([])
  let cards = $state<CardDefinition[]>([])
  let passives = $state<PassiveDefinition[]>([])
  let deckCardCandidate = $state('')
  let passiveCandidate = $state('')

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

  function formatPresetTimestamp(value: PresetTimestampValue) {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
      const fallback = String(value).trim()
      return fallback || 'Unknown time'
    }

    return timestampFormatter.format(date)
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

  async function loadReferenceCatalogs() {
    referenceLoading = true
    referenceErrorMessage = null

    const [characterResult, cardResult, passiveResult] = await Promise.allSettled([
      listCharacters(),
      listCards(),
      listPassives(),
    ])

    const errors: string[] = []

    if (characterResult.status === 'fulfilled') {
      characters = characterResult.value
    } else {
      characters = []
      errors.push('character roster')
    }

    if (cardResult.status === 'fulfilled') {
      cards = cardResult.value
    } else {
      cards = []
      errors.push('card archive')
    }

    if (passiveResult.status === 'fulfilled') {
      passives = passiveResult.value
    } else {
      passives = []
      errors.push('passive archive')
    }

    referenceErrorMessage =
      errors.length > 0
        ? `Some reference data could not be restored: ${errors.join(', ')}. Manual ids still work.`
        : null

    referenceLoading = false
  }

  function getResolvedCharacter(characterId: number | null) {
    if (characterId === null) {
      return null
    }

    return characters.find((character) => character.id === characterId) ?? null
  }

  function getResolvedCard(cardId: string) {
    const normalized = normalizePresetIdentifier(cardId)
    return cards.find((card) => card.id === normalized) ?? null
  }

  function getResolvedPassive(passiveId: string) {
    const normalized = normalizePresetIdentifier(passiveId)
    return passives.find((passive) => passive.id === normalized) ?? null
  }

  function buildCharacterMeta(character: CharacterProfileResponse) {
    const parts = [character.disposition || character.oneLiner || 'Character record']

    if (character.currentSkillDeck?.length) {
      parts.push(`${character.currentSkillDeck.length} linked deck cards`)
    }

    return parts.join(' | ')
  }

  function buildResolvedDeckCardItem(cardId: string, index: number): PresetEntryItem {
    const resolved = getResolvedCard(cardId)

    if (!resolved) {
      return {
        id: `deck-${index + 1}`,
        title: cardId,
        subtitle: 'Deck card id',
        meta: `Entry ${index + 1} | Unresolved`,
        note: 'This card id was not found in the current card archive.',
        tags: [{ label: 'Unresolved', tone: 'warning' }],
      }
    }

    return {
      id: `deck-${index + 1}`,
      title: `${resolved.name} (${resolved.id})`,
      subtitle: getCardTypeLabel(resolved.type),
      meta: `Entry ${index + 1} | ${buildCardArchiveMeta(resolved)}`,
      note: resolved.description,
      tags: buildCardDisplayTags(resolved),
    }
  }

  function buildResolvedPassiveItem(passiveId: string, index: number): PresetEntryItem {
    const resolved = getResolvedPassive(passiveId)

    if (!resolved) {
      return {
        id: `passive-${index + 1}`,
        title: passiveId,
        subtitle: 'Passive id',
        meta: `Entry ${index + 1} | Unresolved`,
        note: 'This passive id was not found in the current passive archive.',
        tags: [{ label: 'Unresolved', tone: 'warning' }],
      }
    }

    return {
      id: `passive-${index + 1}`,
      title: `${resolved.name} (${resolved.id})`,
      subtitle: 'Passive definition',
      meta: `Entry ${index + 1} | Priority ${resolved.priority ?? 'N/A'}`,
      note: resolved.description,
      tags: [{ label: 'Passive', tone: 'success' }],
    }
  }

  function addDeckCardCandidate() {
    const nextValue = normalizePresetIdentifier(deckCardCandidate)

    if (!nextValue) {
      return
    }

    editorState = {
      ...editorState,
      deckCardIds: addPresetIdentifier(editorState.deckCardIds, nextValue),
    }
    deckCardCandidate = ''
  }

  function addPassiveCandidate() {
    const nextValue = normalizePresetIdentifier(passiveCandidate)

    if (!nextValue) {
      return
    }

    editorState = {
      ...editorState,
      passiveIds: addPresetIdentifier(editorState.passiveIds, nextValue),
    }
    passiveCandidate = ''
  }

  function syncPresetState(response: PresetResponse) {
    const nextOriginal = createPresetEditorState(response)

    preset = response
    originalState = nextOriginal
    editorState = clonePresetEditorState(nextOriginal)
    editorMode = 'edit'
    setSelectionHandoff(selectionHandoffKeys.presetId, String(response.id))
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

  function enterCreateMode() {
    editorMode = 'create'
    loading = false
    preset = null
    originalState = null
    editorState = createEmptyPresetEditorState()
    errorMessage = null
    notFoundId = null
    requestedPresetId = null
    saveErrorMessage = null
    saveSuccessMessage = null
    lastSaveMode = null
    cloneErrorMessage = null
    deleteErrorMessage = null
    deleteConfirmOpen = false
  }

  async function loadPresetRecord(id: string) {
    const requestId = ++requestSequence

    editorMode = 'edit'
    loading = true
    preset = null
    originalState = null
    editorState = createEmptyPresetEditorState()
    errorMessage = null
    notFoundId = null
    requestedPresetId = id
    saveErrorMessage = null
    saveSuccessMessage = null
    lastSaveMode = null
    cloneErrorMessage = null
    deleteErrorMessage = null
    deleteConfirmOpen = false

    if (!isPresetApiId(id)) {
      notFoundId = id
      loading = false
      return
    }

    try {
      const response = await getPreset(id)

      if (requestId !== requestSequence) {
        return
      }

      syncPresetState(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      preset = null
      originalState = null
      editorState = createEmptyPresetEditorState()

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFoundId = id
        return
      }

      errorMessage = getApiErrorMessage(error, 'Unable to load the selected preset.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function retryLoad() {
    const routePresetId = getPresetIdFromRoute()

    if (routePresetId) {
      void loadPresetRecord(routePresetId)
      return
    }

    if (requestedPresetId) {
      void loadPresetRecord(requestedPresetId)
    }
  }

  async function handleSave(event?: SubmitEvent) {
    event?.preventDefault()

    if (loading || saving || cloning || deleting) {
      return
    }

    const normalizedState = normalizePresetEditorState(editorState)
    editorState = normalizedState

    if (!normalizedState.name) {
      saveErrorMessage = 'Preset name is required before saving.'
      saveSuccessMessage = null
      return
    }

    if (normalizedState.characterId === null || normalizedState.characterId <= 0) {
      saveErrorMessage = 'Character selection is required before saving.'
      saveSuccessMessage = null
      return
    }

    if (!normalizedState.exCardId) {
      saveErrorMessage = 'EX card selection is required before saving.'
      saveSuccessMessage = null
      return
    }

    const requestId = ++saveRequestSequence
    const payload = toPresetEditorPayload(normalizedState)
    const presetToUpdate = preset
    const nextSaveMode = isCreateMode || !presetToUpdate ? 'create' : 'edit'

    saving = true
    saveErrorMessage = null
    saveSuccessMessage = null
    lastSaveMode = null
    cloneErrorMessage = null
    deleteErrorMessage = null
    deleteConfirmOpen = false

    try {
      let response: PresetResponse

      if (nextSaveMode === 'create') {
        response = await createPreset(payload)
      } else {
        if (!presetToUpdate) {
          saveErrorMessage = 'The preset record is unavailable. Reload the preset and try again.'
          return
        }

        response = await updatePreset(presetToUpdate.id, payload)
      }

      if (requestId !== saveRequestSequence) {
        return
      }

      syncPresetState(response)
      saveSuccessMessage = nextSaveMode === 'create' ? 'Preset created.' : 'Preset saved.'
      lastSaveMode = nextSaveMode

      if (nextSaveMode === 'create') {
        navigateTo(pathBuilders.presetEditor(String(response.id)), true)
      }
    } catch (error) {
      if (requestId !== saveRequestSequence) {
        return
      }

      saveErrorMessage = getApiErrorMessage(
        error,
        nextSaveMode === 'create' ? 'Unable to create the preset.' : 'Unable to save the preset.',
      )
    } finally {
      if (requestId === saveRequestSequence) {
        saving = false
      }
    }
  }

  async function handleClone() {
    if (!preset || isCreateMode || loading || saving || cloning || deleting || deleteConfirmOpen) {
      return
    }

    const requestId = ++cloneRequestSequence

    cloning = true
    cloneErrorMessage = null
    saveErrorMessage = null
    saveSuccessMessage = null
    deleteErrorMessage = null

    try {
      const response = await clonePreset(preset.id)

      if (requestId !== cloneRequestSequence) {
        return
      }

      setPresetPageFeedback(presetEditorStateCopy.cloneSuccessFeedback)
      navigateTo(pathBuilders.presetEditor(String(response.id)))
    } catch (error) {
      if (requestId !== cloneRequestSequence) {
        return
      }

      cloneErrorMessage = getApiErrorMessage(error, 'Unable to clone the preset.')
    } finally {
      if (requestId === cloneRequestSequence) {
        cloning = false
      }
    }
  }

  function openDeleteConfirmation() {
    if (!preset || isCreateMode || loading || saving || cloning || deleting) {
      return
    }

    deleteConfirmOpen = true
    deleteErrorMessage = null
    cloneErrorMessage = null
    saveErrorMessage = null
    saveSuccessMessage = null
  }

  function cancelDeleteConfirmation() {
    if (deleting) {
      return
    }

    deleteConfirmOpen = false
    deleteErrorMessage = null
  }

  async function handleDelete() {
    if (!preset || isCreateMode || loading || saving || cloning || deleting) {
      return
    }

    const requestId = ++deleteRequestSequence

    deleting = true
    deleteErrorMessage = null
    cloneErrorMessage = null
    saveErrorMessage = null
    saveSuccessMessage = null

    try {
      await deletePreset(preset.id)

      if (requestId !== deleteRequestSequence) {
        return
      }

      removeSelectionHandoff(selectionHandoffKeys.presetId)
      setPresetPageFeedback(presetListStateCopy.deletedFeedback)
      navigateTo(pathBuilders.presetList(), true)
    } catch (error) {
      if (requestId !== deleteRequestSequence) {
        return
      }

      deleteErrorMessage = getApiErrorMessage(error, 'Unable to delete the preset.')
    } finally {
      if (requestId === deleteRequestSequence) {
        deleting = false
      }
    }
  }

  function updateCharacterId(value: string) {
    const normalized = value.trim()
    const nextValue = normalized ? Number(normalized) : null

    editorState = {
      ...editorState,
      characterId: nextValue !== null && Number.isFinite(nextValue) ? nextValue : null,
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

  function buildPresetEntryItems(values: readonly string[], kind: 'deck' | 'passive'): PresetEntryItem[] {
    return values.map((value, index) => ({
      id: `${kind}-${index + 1}`,
      title: value,
      subtitle: kind === 'deck' ? 'Deck card id' : 'Passive id',
      meta: `Entry ${index + 1}`,
      note: kind === 'deck' ? 'Included in the local deck card draft.' : 'Included in the local passive draft.',
      tags: [{ label: `#${index + 1}`, tone: 'accent' }],
    }))
  }

  onMount(() => {
    feedback = readPresetPageFeedback()
    void loadReferenceCatalogs()
    const routePresetId = getPresetIdFromRoute()

    if (routePresetId) {
      void loadPresetRecord(routePresetId)
      return
    }

    if (isCreatePresetRoute()) {
      enterCreateMode()
      return
    }

    editorMode = 'selection'
    loading = false
  })

  const isCreateMode = $derived.by(() => editorMode === 'create')
  const editorDirty = $derived.by(() =>
    originalState ? isPresetEditorStateDirty(originalState, editorState) : false,
  )
  const editorControlsDisabled = $derived.by(() => loading || saving || cloning || deleting)
  const resolvedCharacter = $derived.by(() => getResolvedCharacter(editorState.characterId))
  const exCardOptions = $derived.by(() => cards.filter((card) => card.type === 'EX'))
  const deckCardOptions = $derived.by(() => cards.filter((card) => card.type !== 'EX'))
  const resolvedExCard = $derived.by(() =>
    editorState.exCardId ? getResolvedCard(editorState.exCardId) : null,
  )
  const editorTitle = $derived.by(() =>
    isCreateMode ? editorState.name.trim() || 'New Preset' : editorState.name.trim() || 'Preset Detail',
  )
  const editorDescription = $derived.by(() =>
    isCreateMode
      ? 'Prepare a new preset draft before wiring the create flow.'
      : 'The selected preset is loaded from the URL id and converted into a local edit model.',
  )
  const draftStateLabel = $derived.by(() =>
    isCreateMode ? 'Create Mode' : editorDirty ? 'Draft Changed' : 'Draft Synced',
  )
  const draftStateTone = $derived.by(() => (isCreateMode ? 'accent' : editorDirty ? 'warning' : 'success'))
  const characterIdLabel = $derived.by(() =>
    editorState.characterId === null ? 'Not assigned' : String(editorState.characterId),
  )
  const sourcePresetIdLabel = $derived.by(() => (preset ? String(preset.id) : 'Assigned after create'))
  const deckCardItems = $derived.by(() =>
    editorState.deckCardIds.map((cardId, index) => buildResolvedDeckCardItem(cardId, index)),
  )
  const passiveItems = $derived.by(() =>
    editorState.passiveIds.map((passiveId, index) => buildResolvedPassiveItem(passiveId, index)),
  )
  const updatedAtLabel = $derived.by(() =>
    preset ? formatPresetTimestamp(preset.updatedAt) : 'Available after the first save',
  )
  const createdAtLabel = $derived.by(() =>
    preset ? formatPresetTimestamp(preset.createdAt) : 'Available after the first save',
  )
  const saveButtonLabel = $derived.by(() =>
    saving ? (isCreateMode ? 'Creating preset...' : 'Saving preset...') : isCreateMode ? 'Create preset' : 'Save preset',
  )
  const cloneButtonLabel = $derived.by(() => (cloning ? 'Cloning preset...' : 'Clone preset'))
</script>

<div class="preset-editor-page-shell">
  {#if loading}
    <SectionFrame
      eyebrow="Loadout Editor"
      title="Loading preset"
      description="Resolving the preset record from the URL before preparing the local edit model."
    >
      <ContentStatePanel
        title="Loading preset"
        message="Fetching the selected preset from the preset API."
      />
    </SectionFrame>
  {:else if notFoundId}
    <SectionFrame
      eyebrow="Preset Missing"
      title="Preset not found"
      description="The requested preset id could not be restored from the current route or the preset API."
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
      description="The editor could not retrieve the current preset record."
    >
      <ContentStatePanel
        title="Unable to load preset data"
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
  {:else if preset || isCreateMode}
    <form class="preset-editor-page" onsubmit={handleSave}>
    <SectionFrame
      eyebrow={isCreateMode ? 'New Preset' : 'Selected Preset'}
      title={editorTitle}
      description={editorDescription}
    >
      {#if feedback}
        <ContentStatePanel
          title={feedback.title}
          message={feedback.message}
        />
      {/if}

      {#if referenceErrorMessage}
        <ContentStatePanel
          title="Reference data unavailable"
          message={referenceErrorMessage}
        />
      {/if}

      <div class="preset-editor-page__hero">
        <div class="preset-editor-page__hero-copy">
          <p>{characterIdLabel}</p>
          <h3>
            {editorState.deckCardIds.length} deck cards, {editorState.passiveIds.length} passives, EX
            {resolvedExCard ? ` ${resolvedExCard.name}` : editorState.exCardId.trim() || ' not assigned'}
          </h3>
        </div>

        <div class="preset-editor-page__hero-tags">
          <TagChip label={`Character ${characterIdLabel}`} tone="muted" />
          <TagChip label={`${editorState.deckCardIds.length} Cards`} tone="accent" />
          <TagChip label={`${editorState.passiveIds.length} Passives`} tone="success" />
          <TagChip label={draftStateLabel} tone={draftStateTone} />
        </div>
      </div>

      <div class="preset-editor-page__stats">
        <StatBlock value={sourcePresetIdLabel} label="Preset id" note="Current URL-bound preset source" />
        <StatBlock value={updatedAtLabel} label="Updated" note="Last preset update from the API" />
        <StatBlock value={createdAtLabel} label="Created" note="Original preset creation time" />
      </div>

      <fieldset class="preset-editor-page__fieldset">
        <legend>Preset metadata</legend>

        <div class="preset-editor-page__form-grid">
          <label class="preset-editor-page__field preset-editor-page__field--span-2">
            <span>Preset name</span>
            <input bind:value={editorState.name} type="text" placeholder="Enter preset name" disabled={editorControlsDisabled} />
          </label>

          <label class="preset-editor-page__field">
            <span>Character</span>
            <select
              value={editorState.characterId === null ? '' : String(editorState.characterId)}
              disabled={editorControlsDisabled || referenceLoading}
              onchange={(event) => updateCharacterId((event.currentTarget as HTMLSelectElement).value)}
            >
              <option value="">Select character</option>
              {#if editorState.characterId !== null && !resolvedCharacter}
                <option value={String(editorState.characterId)}>
                  Character #{editorState.characterId} (unresolved)
                </option>
              {/if}
              {#each characters as character}
                <option value={String(character.id)}>
                  {character.name} #{character.id}
                </option>
              {/each}
            </select>
          </label>

          <div class="preset-editor-page__field">
            <span>EX card</span>
            <select
              value={editorState.exCardId}
              disabled={editorControlsDisabled || referenceLoading}
              onchange={(event) =>
                (editorState = {
                  ...editorState,
                  exCardId: normalizePresetIdentifier((event.currentTarget as HTMLSelectElement).value),
                })}
            >
              <option value="">Select EX card</option>
              {#if editorState.exCardId && !resolvedExCard}
                <option value={editorState.exCardId}>{editorState.exCardId} (unresolved)</option>
              {/if}
              {#each exCardOptions as card}
                <option value={card.id}>
                  {card.name} ({card.id})
                </option>
              {/each}
            </select>
          </div>

          <div class="preset-editor-page__field preset-editor-page__field--span-2">
            <span>Current references</span>
            <div class="preset-editor-page__reference-summary">
              <div>
                <strong>Character</strong>
                <p>
                  {#if resolvedCharacter}
                    {resolvedCharacter.name} #{resolvedCharacter.id}
                  {:else if editorState.characterId !== null}
                    Character #{editorState.characterId} (unresolved)
                  {:else}
                    No character selected.
                  {/if}
                </p>
              </div>
              <div>
                <strong>EX card</strong>
                <p>
                  {#if resolvedExCard}
                    {resolvedExCard.name} ({resolvedExCard.id})
                  {:else if editorState.exCardId}
                    {editorState.exCardId} (unresolved)
                  {:else}
                    No EX card selected.
                  {/if}
                </p>
              </div>
            </div>
          </div>

          <label class="preset-editor-page__field preset-editor-page__field--span-2">
            <span>Deck card ids</span>
            <div class="preset-editor-page__picker-row">
              <select bind:value={deckCardCandidate} disabled={editorControlsDisabled || referenceLoading}>
                <option value="">Quick add deck card</option>
                {#each deckCardOptions as card}
                  <option value={card.id}>
                    {card.name} ({card.id})
                  </option>
                {/each}
              </select>
              <button type="button" disabled={editorControlsDisabled || !deckCardCandidate} onclick={addDeckCardCandidate}>
                Add card
              </button>
            </div>
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
            <div class="preset-editor-page__picker-row">
              <select bind:value={passiveCandidate} disabled={editorControlsDisabled || referenceLoading}>
                <option value="">Quick add passive</option>
                {#each passives as passive}
                  <option value={passive.id}>
                    {passive.name} ({passive.id})
                  </option>
                {/each}
              </select>
              <button type="button" disabled={editorControlsDisabled || !passiveCandidate} onclick={addPassiveCandidate}>
                Add passive
              </button>
            </div>
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
        description="The preview list renders from the local editor state, not directly from the API response."
      >
        <EntityListPane
          items={deckCardItems}
          emptyMessage="No deck card ids are currently assigned to this preset."
        />
      </SectionFrame>

      <SectionFrame
        title="Passive preview"
        description="Passive ids are also rendered from the local editor state for the next save step."
      >
        <EntityListPane
          items={passiveItems}
          emptyMessage="No passive ids are currently assigned to this preset."
        />
      </SectionFrame>
    </div>

    {#if resolvedCharacter || resolvedExCard}
      <div class="preset-editor-page__grid">
        <SectionFrame
          title="Character reference"
          description="Current character selection is resolved from the existing character archive."
        >
          {#if resolvedCharacter}
            <div class="preset-editor-page__copy">
              <p><strong>{resolvedCharacter.name}</strong></p>
              <p>{buildCharacterMeta(resolvedCharacter)}</p>
              <p>{resolvedCharacter.oneLiner || resolvedCharacter.story}</p>
            </div>
          {:else}
            <ContentStatePanel message="No character record is currently resolved for this preset." />
          {/if}
        </SectionFrame>

        <SectionFrame
          title="EX card reference"
          description="Current EX card selection is resolved from the card archive."
        >
          {#if resolvedExCard}
            <div class="preset-editor-page__copy">
              <p><strong>{resolvedExCard.name}</strong></p>
              <p>{buildCardArchiveMeta(resolvedExCard)}</p>
              <p>{resolvedExCard.description}</p>
            </div>
          {:else}
            <ContentStatePanel message="No EX card is currently resolved for this preset." />
          {/if}
        </SectionFrame>
      </div>
    {/if}

    <SectionFrame
      title="Editor actions"
      description="Create, save, clone, and delete use the preset API while the loadout selections stay aligned with the existing character and content archives."
    >
      <div class="preset-editor-page__actions">
        <a class="preset-editor-page__link-action" data-nav href={pathBuilders.presetList()}>
          Back to preset archive
        </a>
        <button type="submit" disabled={editorControlsDisabled || (!isCreateMode && !editorDirty)}>
          {saveButtonLabel}
        </button>
        {#if !isCreateMode}
          <button type="button" disabled={editorControlsDisabled || deleteConfirmOpen} onclick={() => void handleClone()}>
            {cloneButtonLabel}
          </button>
          <button type="button" disabled={editorControlsDisabled} onclick={openDeleteConfirmation}>
            {deleting ? 'Deleting preset...' : deleteConfirmOpen ? 'Delete pending' : 'Delete preset'}
          </button>
        {/if}
      </div>

      <div class="preset-editor-page__status">
        <p>
          {isCreateMode
            ? 'The current preset draft is empty and ready for the create flow.'
            : editorDirty
              ? 'Local changes are pending on top of the last loaded preset response.'
              : 'Local draft matches the last loaded preset response.'}
        </p>
        <p>Character, deck card ids, EX card id, and passive ids are normalized before save.</p>
        {#if !isCreateMode && preset}
          <p>Owner: {preset.owner}</p>
        {/if}
      </div>

      {#if saveErrorMessage}
        <ContentStatePanel
          title={isCreateMode ? 'Create failed' : 'Save failed'}
          message={saveErrorMessage}
          tone="error"
        />
      {:else if cloneErrorMessage}
        <ContentStatePanel
          title={presetEditorStateCopy.cloneErrorTitle}
          message={cloneErrorMessage}
          tone="error"
        />
      {:else if deleteErrorMessage}
        <ContentStatePanel
          title={presetEditorStateCopy.deleteErrorTitle}
          message={deleteErrorMessage}
          tone="error"
        />
      {:else if saveSuccessMessage}
        <ContentStatePanel
          title={lastSaveMode === 'create' ? 'Preset created' : 'Preset saved'}
          message={saveSuccessMessage}
        />
      {/if}

      {#if !isCreateMode && deleting}
        <ContentStatePanel
          title={presetEditorStateCopy.deleteLoadingTitle}
          message={presetEditorStateCopy.deleteLoadingMessage}
        />
      {:else if !isCreateMode && deleteConfirmOpen}
        <ContentStatePanel
          title={presetEditorStateCopy.deleteConfirmTitle}
          message={presetEditorStateCopy.deleteConfirmMessage}
          tone="error"
        >
          <div class="preset-editor-page__confirm-actions">
            <button type="button" onclick={() => void handleDelete()}>Confirm delete</button>
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
  .preset-editor-page__field select,
  .preset-editor-page__picker-row button {
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

  .preset-editor-page__picker-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.75rem;
  }

  .preset-editor-page__picker-row button {
    width: fit-content;
    min-width: 8rem;
  }

  .preset-editor-page__reference-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
    padding: 0.9rem 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
  }

  .preset-editor-page__reference-summary strong {
    display: block;
    margin-bottom: 0.35rem;
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .preset-editor-page__copy p,
  .preset-editor-page__status p,
  .preset-editor-page__reference-summary p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .preset-editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .preset-editor-page__link-action,
  .preset-editor-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .preset-editor-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
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
    padding: 0.65rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  @media (max-width: 960px) {
    .preset-editor-page__stats,
    .preset-editor-page__grid,
    .preset-editor-page__form-grid,
    .preset-editor-page__reference-summary {
      grid-template-columns: 1fr;
    }

    .preset-editor-page__field--span-2 {
      grid-column: span 1;
    }

    .preset-editor-page__picker-row {
      grid-template-columns: 1fr;
    }

    .preset-editor-page__picker-row button {
      width: 100%;
    }
  }
</style>
