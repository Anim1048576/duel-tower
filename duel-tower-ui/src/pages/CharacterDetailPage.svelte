<script lang="ts">
  import { onMount } from 'svelte'
  import { createCharacter, deleteCharacter, getCharacter, updateCharacter } from '../lib/api/characters'
  import type {
    CharacterGender,
    CharacterProfileRequest,
    CharacterProfileResponse,
  } from '../lib/api/characterTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    resolveRouteFirstSelection,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type DetailTag = {
    label: string
    tone?: 'accent' | 'muted' | 'success' | 'warning'
  }

  type CharacterDetailMode = 'create' | 'edit'

  type CharacterFormState = {
    name: string
    gender: CharacterGender | ''
    age: string
    wish: string
    disposition: string
    oneLiner: string
    story: string
    physical: string
    technique: string
    sense: string
    willpower: string
    trait1: string
    trait2: string
    ownedCards: string
    exCard: string
  }

  type CharacterNavigationState = {
    characterFeedback?: string
  }

  function createEmptyFormState(): CharacterFormState {
    return {
      name: '',
      gender: '',
      age: '',
      wish: '',
      disposition: '',
      oneLiner: '',
      story: '',
      physical: '',
      technique: '',
      sense: '',
      willpower: '',
      trait1: '',
      trait2: '',
      ownedCards: '',
      exCard: '',
    }
  }

  function createFormStateFromResponse(character: CharacterProfileResponse): CharacterFormState {
    return {
      name: character.name,
      gender: character.gender,
      age: character.age === null ? '' : String(character.age),
      wish: character.wish,
      disposition: character.disposition,
      oneLiner: character.oneLiner,
      story: character.story,
      physical: String(character.physical),
      technique: String(character.technique),
      sense: String(character.sense),
      willpower: String(character.willpower),
      trait1: character.trait1 ?? '',
      trait2: character.trait2 ?? '',
      ownedCards: character.ownedCards,
      exCard: character.exCard,
    }
  }

  function getCharacterRouteState() {
    if (typeof window === 'undefined') {
      return {
        mode: 'edit' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key === 'character-create') {
      return {
        mode: 'create' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    if (match?.page.key !== 'character-detail') {
      return {
        mode: 'edit' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    return {
      mode: 'edit' as CharacterDetailMode,
      routeId: match.params.id ?? null,
    }
  }

  function isCharacterApiId(value: string | null | undefined): value is string {
    return typeof value === 'string' && /^\d+$/.test(value.trim())
  }

  function getGenderLabel(gender: CharacterGender | '') {
    switch (gender) {
      case 'MALE':
        return 'Male'
      case 'FEMALE':
        return 'Female'
      case 'OTHER':
        return 'Other'
      default:
        return 'Unspecified'
    }
  }

  function formatNullableNumber(value: number | string | null | undefined) {
    if (value === null || value === undefined) return '-'
    const normalized = String(value).trim()
    return normalized || '-'
  }

  function parseNullableNumber(value: string) {
    const normalized = value.trim()

    if (!normalized) {
      return null
    }

    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
  }

  function normalizeOptionalText(value: string) {
    const normalized = value.trim()
    return normalized ? normalized : null
  }

  function buildCharacterTags(form: CharacterFormState): DetailTag[] {
    const tags: DetailTag[] = [{ label: getGenderLabel(form.gender), tone: 'muted' }]
    const currentDeckCount = character?.currentSkillDeck?.length ?? 0

    tags.push(
      currentDeckCount ? { label: 'Deck Linked', tone: 'success' } : { label: 'No Deck', tone: 'muted' },
    )

    if (form.trait1.trim() || form.trait2.trim()) {
      tags.push({ label: 'Traits', tone: 'accent' })
    }

    return tags
  }

  function buildCharacterPayload(form: CharacterFormState): CharacterProfileRequest {
    return {
      name: form.name.trim(),
      gender: form.gender || null,
      age: parseNullableNumber(form.age),
      wish: form.wish.trim(),
      disposition: form.disposition.trim(),
      oneLiner: form.oneLiner.trim(),
      story: form.story.trim(),
      physical: parseNullableNumber(form.physical),
      technique: parseNullableNumber(form.technique),
      sense: parseNullableNumber(form.sense),
      willpower: parseNullableNumber(form.willpower),
      trait1: normalizeOptionalText(form.trait1),
      trait2: normalizeOptionalText(form.trait2),
      ownedCards: form.ownedCards.trim(),
      currentSkillDeck: isCreateMode ? null : (character?.currentSkillDeck ?? null),
      exCard: form.exCard.trim(),
    }
  }

  function getNavigationFeedback() {
    if (typeof window === 'undefined') return null
    const state = history.state as CharacterNavigationState | null
    const feedback = state?.characterFeedback ?? null

    if (feedback) {
      history.replaceState({}, '', window.location.pathname)
    }

    return feedback
  }

  function navigateTo(path: string, mode: 'push' | 'replace' = 'push', state: CharacterNavigationState = {}) {
    if (typeof window === 'undefined') return

    history[mode === 'replace' ? 'replaceState' : 'pushState'](state, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function handleApplySavedDeck() {
    if (isCreateMode || !requestedCharacterId || saving || deleting || loading) {
      return
    }

    setSelectionHandoff(selectionHandoffKeys.deckApplyCharacterId, requestedCharacterId)
    setSelectionHandoff(selectionHandoffKeys.characterId, requestedCharacterId)
    navigateTo(pathBuilders.deckList())
  }

  const routeState = getCharacterRouteState()
  const isCreateMode = routeState.mode === 'create'
  const characterSelection = !isCreateMode
    ? resolveRouteFirstSelection({
        routeValue: routeState.routeId,
        handoffValue: readSelectionHandoff(selectionHandoffKeys.characterId),
        isValid: isCharacterApiId,
      })
    : {
        value: null,
        source: 'none' as const,
        missingRouteValue: null,
      }

  const requestedCharacterId = isCreateMode ? null : characterSelection.value
  const missingCharacterId = isCreateMode ? null : characterSelection.missingRouteValue

  let loading = $state(!isCreateMode && requestedCharacterId !== null)
  let saving = $state(false)
  let deleting = $state(false)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let saveErrorMessage = $state<string | null>(null)
  let deleteErrorMessage = $state<string | null>(null)
  let saveMessage = $state<string | null>(null)
  let character = $state<CharacterProfileResponse | null>(null)
  let form = $state<CharacterFormState>(createEmptyFormState())

  const characterTags = $derived.by(() => buildCharacterTags(form))
  const characterTraits = $derived.by(() =>
    [form.trait1.trim(), form.trait2.trim()].filter((trait): trait is string => Boolean(trait)),
  )
  const currentDeck = $derived.by(() => character?.currentSkillDeck ?? [])
  const selectionUnavailable = $derived.by(
    () => !isCreateMode && requestedCharacterId === null && missingCharacterId === null && !loading && !errorMessage,
  )
  const displayedTitle = $derived.by(() =>
    isCreateMode
      ? form.name.trim() || 'New Character Record'
      : character?.name || form.name.trim() || 'Character Detail',
  )
  const displayedEyebrow = $derived.by(() =>
    form.disposition.trim() || (isCreateMode ? 'New archive record' : 'Character archive record'),
  )
  const displayedSummary = $derived.by(() =>
    form.oneLiner.trim() ||
    (isCreateMode
      ? 'Prepare a new archive entry before saving the record.'
      : 'No one-line summary has been recorded yet.'),
  )

  function syncCharacterState(response: CharacterProfileResponse) {
    character = response
    form = createFormStateFromResponse(response)
    setSelectionHandoff(selectionHandoffKeys.characterId, String(response.id))
  }

  async function loadCharacterDetail(id: string) {
    loading = true
    notFound = false
    errorMessage = null
    saveErrorMessage = null
    deleteErrorMessage = null

    try {
      const response = await getCharacter(id)
      syncCharacterState(response)
    } catch (error) {
      character = null

      if (error instanceof ApiError && error.code === 'not_found') {
        notFound = true
      } else {
        errorMessage = getApiErrorMessage(error, 'Unable to load the character record.')
      }
    } finally {
      loading = false
    }
  }

  async function handleSave(event?: SubmitEvent) {
    event?.preventDefault()

    if (saving || loading) {
      return
    }

    if (!form.name.trim()) {
      saveErrorMessage = 'Character name is required before saving.'
      saveMessage = null
      return
    }

    saveErrorMessage = null
    deleteErrorMessage = null
    saveMessage = null
    saving = true

    try {
      const payload = buildCharacterPayload(form)
      const response = isCreateMode
        ? await createCharacter(payload)
        : await updateCharacter(requestedCharacterId ?? '', payload)

      syncCharacterState(response)
      notFound = false
      errorMessage = null
      saveMessage = isCreateMode ? 'Character record created.' : 'Character record saved.'

      if (isCreateMode) {
        navigateTo(pathBuilders.characterDetail(String(response.id)), 'replace')
      }
    } catch (error) {
      saveErrorMessage = getApiErrorMessage(
        error,
        isCreateMode ? 'Unable to create the character record.' : 'Unable to save the character record.',
      )
    } finally {
      saving = false
    }
  }

  async function handleDelete() {
    if (isCreateMode || !requestedCharacterId || saving || deleting || loading) {
      return
    }

    const confirmed = window.confirm(
      `Delete ${character?.name || 'this character record'}? This action cannot be undone.`,
    )

    if (!confirmed) {
      return
    }

    deleteErrorMessage = null
    saveErrorMessage = null
    saveMessage = null
    deleting = true

    try {
      await deleteCharacter(requestedCharacterId)
      removeSelectionHandoff(selectionHandoffKeys.characterId)
      navigateTo(pathBuilders.characterList(), 'replace', {
        characterFeedback: 'Character record deleted.',
      })
    } catch (error) {
      deleteErrorMessage = getApiErrorMessage(error, 'Unable to delete the character record.')
    } finally {
      deleting = false
    }
  }

  onMount(() => {
    saveMessage = getNavigationFeedback()

    if (isCreateMode) {
      loading = false
      return
    }

    if (!requestedCharacterId) {
      loading = false
      return
    }

    void loadCharacterDetail(requestedCharacterId)
  })
</script>

{#if loading}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Loading"
      title="Loading character detail"
      description="The selected record is being restored from the character API."
    >
      <div class="detail-page__todo">
        <p>Preparing the selected character record...</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
      </div>
    </SectionFrame>
  </div>
{:else if isCreateMode || character}
  <form class="detail-page" onsubmit={handleSave}>
    <SectionFrame
      eyebrow={isCreateMode ? 'Record Draft' : 'Selected Record'}
      title={displayedTitle}
      description={
        isCreateMode
          ? 'Fill the draft fields below to create a new character record.'
          : 'Character detail is loaded from the URL id first, then saved back through the update API.'
      }
    >
      <div class="detail-page__hero">
        <div class="detail-page__hero-copy">
          <p>{displayedEyebrow}</p>
          <h3>{displayedSummary}</h3>
        </div>

        <div class="detail-page__hero-tags">
          {#each characterTags as tag}
            <TagChip label={tag.label} tone={tag.tone} />
          {/each}
        </div>
      </div>

      <div class="detail-page__stats">
        <StatBlock value={formatNullableNumber(form.age)} label="Age" note="Recorded profile age" />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.maxHp)}
          label="Max HP"
          note={character ? 'Combat profile' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.maxAp)}
          label="Max AP"
          note={character ? 'Action capacity' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.attackPower)}
          label="Attack"
          note={character ? 'Combat power' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.healPower)}
          label="Heal"
          note={character ? 'Recovery power' : 'Calculated after the first save'}
        />
      </div>
    </SectionFrame>

    <div class="detail-page__grid">
      <SectionFrame
        title="Combat profile"
        description="Core identity and stat fields stay in the same area while the form is now wired to the create and update APIs."
      >
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field detail-page__field--span-2">
              <span>Name</span>
              <input bind:value={form.name} name="name" type="text" required />
            </label>

            <label class="detail-page__field">
              <span>Gender</span>
              <select bind:value={form.gender} name="gender">
                <option value="">Unspecified</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </label>

            <label class="detail-page__field">
              <span>Age</span>
              <input bind:value={form.age} name="age" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Disposition</span>
              <input bind:value={form.disposition} name="disposition" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>One-line summary</span>
              <input bind:value={form.oneLiner} name="oneLiner" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Story</span>
              <textarea bind:value={form.story} name="story" rows="6"></textarea>
            </label>

            <label class="detail-page__field">
              <span>Physical</span>
              <input bind:value={form.physical} name="physical" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Technique</span>
              <input bind:value={form.technique} name="technique" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Sense</span>
              <input bind:value={form.sense} name="sense" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Willpower</span>
              <input bind:value={form.willpower} name="willpower" type="number" min="0" step="1" />
            </label>
          </div>
        </fieldset>
      </SectionFrame>

      <SectionFrame
        title="Loadout and notes"
        description="Profile notes remain editable here. The current skill deck is displayed from the latest character API response."
      >
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field">
              <span>Trait 1</span>
              <input bind:value={form.trait1} name="trait1" type="text" />
            </label>

            <label class="detail-page__field">
              <span>Trait 2</span>
              <input bind:value={form.trait2} name="trait2" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Wish</span>
              <textarea bind:value={form.wish} name="wish" rows="4"></textarea>
            </label>

            <div class="detail-page__field detail-page__field--span-2">
              <span>Current skill deck</span>
              <div class="detail-page__readonly-list" aria-live="polite">
                {#if currentDeck.length}
                  {#each currentDeck as card, index}
                    <span>{index + 1}. {card}</span>
                  {/each}
                {:else}
                  <span>No saved deck has been applied.</span>
                {/if}
              </div>
            </div>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Owned cards</span>
              <textarea bind:value={form.ownedCards} name="ownedCards" rows="5"></textarea>
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>EX card</span>
              <textarea bind:value={form.exCard} name="exCard" rows="4"></textarea>
            </label>
          </div>
        </fieldset>

        <div class="detail-page__list-block">
          <div>
            <strong>Trait preview</strong>
            {#if characterTraits.length}
              <ul>
                {#each characterTraits as trait}
                  <li>{trait}</li>
                {/each}
              </ul>
            {:else}
              <p>No traits have been recorded yet.</p>
            {/if}
          </div>
          <div>
            <strong>Current skill deck preview</strong>
            {#if currentDeck.length}
              <ul>
                {#each currentDeck as card}
                  <li>{card}</li>
                {/each}
              </ul>
            {:else}
              <p>No deck has been assigned yet.</p>
            {/if}
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title={isCreateMode ? 'Create queue' : 'Edit queue'}
      description="Save profile fields here, or send this saved character to the deck archive to apply a server-validated saved deck."
    >
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <button type="submit" disabled={saving || deleting}>
          {saving ? 'Saving...' : isCreateMode ? 'Create character' : 'Save changes'}
        </button>
        {#if !isCreateMode}
          <button
            type="button"
            class="detail-page__danger-action"
            disabled={saving || deleting}
            onclick={() => void handleDelete()}
          >
            {deleting ? 'Deleting...' : 'Delete record'}
          </button>
        {/if}
        <button
          type="button"
          disabled={isCreateMode || saving || deleting || loading || !requestedCharacterId}
          onclick={handleApplySavedDeck}
        >
          Apply saved deck
        </button>
      </div>

      {#if saveMessage || saveErrorMessage || deleteErrorMessage}
        <div
          class="detail-page__status"
          class:detail-page__status--error={Boolean(saveErrorMessage || deleteErrorMessage)}
        >
          {#if saveMessage}
            <p>{saveMessage}</p>
          {/if}
          {#if saveErrorMessage}
            <p>{saveErrorMessage}</p>
          {/if}
          {#if deleteErrorMessage}
            <p>{deleteErrorMessage}</p>
          {/if}
        </div>
      {/if}

      <div class="detail-page__todo">
        {#if isCreateMode}
          <p>Save this character before applying a saved deck.</p>
        {:else}
          <p>Saved deck application is persisted by the server. This screen reloads the character detail before showing the result.</p>
        {/if}
      </div>
    </SectionFrame>
  </form>
{:else if notFound || missingCharacterId}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Missing"
      title="Character not found"
      description="The requested character id could not be restored from the current route."
    >
      <div class="detail-page__todo">
        <p>Requested id: {missingCharacterId ?? requestedCharacterId ?? 'Unknown'}</p>
        <p>Check the roster list and open a valid character record.</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterCreate()}>
          Create new character
        </a>
      </div>
    </SectionFrame>
  </div>
{:else if errorMessage}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Error"
      title="Unable to load character detail"
      description="The character record could not be restored from the API."
    >
      <div class="detail-page__todo">
        <p>{errorMessage}</p>
        {#if requestedCharacterId}
          <p>Requested id: {requestedCharacterId}</p>
        {/if}
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        {#if requestedCharacterId}
          <button type="button" onclick={() => void loadCharacterDetail(requestedCharacterId)}>
            Retry load
          </button>
        {/if}
      </div>
    </SectionFrame>
  </div>
{:else if selectionUnavailable}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Missing"
      title="Character selection unavailable"
      description="Open a character from the roster list or use a valid detail URL to restore this page."
    >
      <div class="detail-page__todo">
        <p>No character id was found in the URL, and no handoff selection is available.</p>
        <p>Open a character from the roster list to restore the expected detail context.</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterCreate()}>
          Create new character
        </a>
      </div>
    </SectionFrame>
  </div>
{/if}

<style>
  .detail-page,
  .detail-page__grid,
  .detail-page__list-block,
  .detail-page__todo,
  .detail-page__form-grid {
    display: grid;
    gap: 1.5rem;
  }

  .detail-page__fieldset {
    border: 0;
    margin: 0;
    padding: 0;
    min-width: 0;
  }

  .detail-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .detail-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .detail-page__hero-copy p,
  .detail-page__hero-copy h3,
  .detail-page__todo p {
    margin: 0;
  }

  .detail-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .detail-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .detail-page__hero-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .detail-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .detail-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__field {
    display: grid;
    gap: 0.55rem;
  }

  .detail-page__field--span-2 {
    grid-column: span 2;
  }

  .detail-page__field span {
    font-size: 0.8rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .detail-page__field input,
  .detail-page__field select,
  .detail-page__field textarea {
    min-height: 3rem;
    width: 100%;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    color: var(--color-text);
    font: inherit;
  }

  .detail-page__field textarea {
    min-height: 7rem;
    resize: vertical;
  }

  .detail-page__readonly-list {
    min-height: 7rem;
    width: 100%;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.16);
    color: var(--color-text-soft);
    display: grid;
    align-content: start;
    gap: 0.35rem;
    line-height: 1.5;
  }

  .detail-page__readonly-list span {
    color: inherit;
    font-size: 0.92rem;
    letter-spacing: 0;
    text-transform: none;
  }

  .detail-page__list-block > div {
    display: grid;
    gap: 0.65rem;
  }

  .detail-page__list-block strong {
    font-size: 0.92rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .detail-page__list-block p,
  .detail-page__list-block ul {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .detail-page__list-block ul {
    padding-left: 1.1rem;
  }

  .detail-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .detail-page__status {
    display: grid;
    gap: 0.6rem;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .detail-page__status--error {
    border-color: rgba(199, 129, 121, 0.38);
  }

  .detail-page__status p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .detail-page__link-action,
  .detail-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .detail-page__danger-action {
    border-color: rgba(199, 129, 121, 0.42);
    background: linear-gradient(180deg, rgba(199, 129, 121, 0.18), rgba(199, 129, 121, 0.08));
  }

  .detail-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .detail-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .detail-page__todo p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .detail-page__stats,
    .detail-page__grid,
    .detail-page__form-grid {
      grid-template-columns: 1fr;
    }

    .detail-page__field--span-2 {
      grid-column: auto;
    }
  }
</style>
