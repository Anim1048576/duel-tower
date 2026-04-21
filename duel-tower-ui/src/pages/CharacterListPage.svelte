<script lang="ts">
  import { onMount } from 'svelte'
  import { listCharacters } from '../lib/api/characters'
  import type { CharacterGender, CharacterProfileResponse } from '../lib/api/characterTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import { readSelectionHandoff, selectionHandoffKeys, setSelectionHandoff } from '../lib/selectionHandoff'

  type CharacterListItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  let query = $state('')
  let selectedId = $state('')
  let loading = $state(true)
  let errorMessage = $state<string | null>(null)
  let feedbackMessage = $state<string | null>(null)
  let characters = $state<CharacterProfileResponse[]>([])

  function consumeCharacterFeedback() {
    if (typeof window === 'undefined') return null

    const state = window.history.state as { characterFeedback?: string } | null
    const message = typeof state?.characterFeedback === 'string' ? state.characterFeedback : null

    if (!message) {
      return null
    }

    const nextState =
      state && typeof state === 'object'
        ? Object.fromEntries(
            Object.entries(state).filter(([key]) => key !== 'characterFeedback'),
          )
        : {}

    window.history.replaceState(
      nextState,
      '',
      `${window.location.pathname}${window.location.search}${window.location.hash}`,
    )

    return message
  }

  function getGenderLabel(gender: CharacterGender) {
    switch (gender) {
      case 'MALE':
        return 'Male'
      case 'FEMALE':
        return 'Female'
      default:
        return 'Other'
    }
  }

  function buildCharacterMeta(character: CharacterProfileResponse) {
    const parts = [getGenderLabel(character.gender)]

    if (character.age !== null) {
      parts.push(`Age ${character.age}`)
    }

    if (character.disposition) {
      parts.push(character.disposition)
    }

    return parts.join(' · ')
  }

  function buildCharacterTags(character: CharacterProfileResponse): CharacterListItem['tags'] {
    const tags: NonNullable<CharacterListItem['tags']> = []

    tags.push(
      character.currentSkillDeck?.length
        ? { label: 'Deck Applied', tone: 'success' }
        : { label: 'No Applied Deck', tone: 'muted' },
    )

    if (character.trait1 || character.trait2) {
      tags.push({ label: 'Traits', tone: 'accent' })
    }

    return tags
  }

  function toCharacterListItem(character: CharacterProfileResponse): CharacterListItem {
    return {
      id: String(character.id),
      title: character.name,
      subtitle: character.oneLiner || character.disposition,
      meta: buildCharacterMeta(character),
      note: character.wish ? `Wish: ${character.wish}` : undefined,
      tags: buildCharacterTags(character),
    }
  }

  function persistSelectedCharacter(id: string) {
    setSelectionHandoff(selectionHandoffKeys.characterId, id)
  }

  function syncSelectedCharacter(nextCharacters: CharacterProfileResponse[]) {
    const nextIds = nextCharacters.map((character) => String(character.id))
    const handoffId = readSelectionHandoff(selectionHandoffKeys.characterId)

    const nextSelectedId = nextIds.includes(selectedId)
      ? selectedId
      : handoffId && nextIds.includes(handoffId)
        ? handoffId
        : nextIds[0] ?? ''

    selectedId = nextSelectedId

    if (nextSelectedId) {
      persistSelectedCharacter(nextSelectedId)
    }
  }

  async function loadCharacterRoster() {
    loading = true
    errorMessage = null

    try {
      const response = await listCharacters()
      characters = response
      syncSelectedCharacter(response)
    } catch (error) {
      characters = []
      selectedId = ''
      errorMessage = getApiErrorMessage(error, 'Unable to load the character roster.')
    } finally {
      loading = false
    }
  }

  onMount(() => {
    feedbackMessage = consumeCharacterFeedback()
    void loadCharacterRoster()
  })

  const filteredCharacters = $derived.by(() => {
    const items = characters.map(toCharacterListItem)
    const normalized = query.trim().toLowerCase()

    if (!normalized) return items

    return items.filter((item) =>
      [item.title, item.subtitle, item.meta, item.note].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  })

  const selectedCharacter = $derived.by(
    () => filteredCharacters.find((item) => item.id === selectedId) ?? filteredCharacters[0] ?? null,
  )

  const deckAppliedCount = $derived.by(() =>
    characters.filter((character) => (character.currentSkillDeck?.length ?? 0) > 0).length,
  )

  const traitTaggedCount = $derived.by(() =>
    characters.filter((character) => Boolean(character.trait1 || character.trait2)).length,
  )

  const listSummary = $derived.by(() => {
    if (loading) return 'Loading character roster...'
    if (errorMessage) return 'Character roster could not be loaded.'
    if (!characters.length) return 'No character records are currently available.'
    return `${filteredCharacters.length} character records visible from ${characters.length} total.`
  })
  const rosterEmpty = $derived.by(() => !loading && !errorMessage && characters.length === 0)
  const emptyListMessage = $derived.by(() =>
    characters.length === 0
      ? 'No character records are available yet.'
      : 'No character records matched the current search.',
  )

  function handleSelectCharacter(id: string) {
    selectedId = id
    persistSelectedCharacter(id)
  }

  function handleOpenDetail() {
    persistSelectedCharacter(selectedCharacter?.id ?? '')
  }

  const selectedCharacterDetailPath = $derived.by(() =>
    pathBuilders.characterDetail(selectedCharacter?.id),
  )
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Roster Summary"
    title="Available Characters"
    description="The roster loads from the character API and keeps the existing URL-based detail flow intact."
  >
    <div class="list-page__stats">
      <StatBlock value={characters.length} label="Total roster" note="Loaded from the character API" />
      <StatBlock value={deckAppliedCount} label="Deck applied" note="Characters with an applied current skill deck" />
      <StatBlock value={traitTaggedCount} label="Trait tagged" note="Characters with at least one saved trait" />
    </div>
    <div class="list-page__actions">
      <a class="list-page__link-action" data-nav href={pathBuilders.characterCreate()}>
        Create new character
      </a>
    </div>
    {#if feedbackMessage}
      <div class="list-page__status list-page__status--success">
        <p>{feedbackMessage}</p>
      </div>
    {/if}
  </SectionFrame>

  <div class="list-page__content">
    <SectionFrame
      title="Character roster"
      description="Search stays local for now, while list data is restored from the API each time the page is opened."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search characters"
        summary={listSummary}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <TagChip label="All" tone="accent" />
          <TagChip label="Deck Applied" tone="success" />
          <TagChip label="Traits" tone="warning" />
        {/snippet}

        {#snippet sort()}
          <TagChip label="Name" tone="muted" />
          <TagChip label="Disposition" tone="muted" />
        {/snippet}
      </SearchFilterBar>

      {#if loading}
        <p class="list-page__status">Loading character roster...</p>
      {:else if errorMessage}
        <div class="list-page__status list-page__status--error">
          <p>{errorMessage}</p>
          <button type="button" class="list-page__status-action" onclick={() => void loadCharacterRoster()}>
            Retry load
          </button>
        </div>
      {:else}
        <EntityListPane
          items={filteredCharacters}
          selectedId={selectedId}
          onSelect={handleSelectCharacter}
          emptyMessage={emptyListMessage}
        />
      {/if}
    </SectionFrame>

    <SectionFrame
      title="Selected summary"
      description="Selection stays local to this page, while detail navigation keeps the existing URL-based character route."
    >
      {#if loading}
        <p class="list-page__empty">Preparing the selected character summary...</p>
      {:else if errorMessage}
        <div class="list-page__todo">
          <p>The selected summary is unavailable until the roster is restored.</p>
          <p>{errorMessage}</p>
        </div>
      {:else if rosterEmpty}
        <div class="list-page__detail">
          <p class="list-page__empty">No character records are available yet.</p>
          <a class="list-page__link-action" data-nav href={pathBuilders.characterCreate()}>
            Create the first character
          </a>
        </div>
      {:else if selectedCharacter}
        <div class="list-page__detail">
          <div>
            <h3>{selectedCharacter.title}</h3>
            <p>{selectedCharacter.subtitle}</p>
          </div>

          <div class="list-page__detail-tags">
            {#each selectedCharacter.tags ?? [] as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{selectedCharacter.meta}</p>
          <p>{selectedCharacter.note}</p>

          <a
            class="list-page__link-action"
            data-nav
            href={selectedCharacterDetailPath}
            onclick={handleOpenDetail}
          >
            Open detail for {selectedCharacter.title}
          </a>
        </div>
      {:else}
        <p class="list-page__empty">{emptyListMessage}</p>
      {/if}
    </SectionFrame>
  </div>
</div>

<style>
  .list-page,
  .list-page__content,
  .list-page__detail,
  .list-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .list-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .list-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .list-page__content {
    grid-template-columns: minmax(0, 1.25fr) minmax(19rem, 0.75fr);
    align-items: start;
  }

  .list-page__detail h3,
  .list-page__detail p {
    margin: 0;
  }

  .list-page__detail h3 {
    font-family: var(--font-display);
    font-size: 1.5rem;
  }

  .list-page__detail > div:first-child p,
  .list-page__detail > p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .list-page__detail-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .list-page__status,
  .list-page__status p {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .list-page__status {
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    display: grid;
    gap: 0.85rem;
  }

  .list-page__status--error {
    border-color: rgba(199, 129, 121, 0.38);
  }

  .list-page__status--success {
    border-color: rgba(134, 171, 126, 0.42);
  }

  .list-page__status-action {
    min-height: 2.75rem;
    width: fit-content;
    padding: 0.65rem 0.95rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .list-page__link-action {
    min-height: 3rem;
    width: fit-content;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    display: inline-flex;
    align-items: center;
    color: var(--color-text);
  }

  .list-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .list-page__todo p,
  .list-page__empty {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .list-page__stats,
    .list-page__content {
      grid-template-columns: 1fr;
    }
  }
</style>
