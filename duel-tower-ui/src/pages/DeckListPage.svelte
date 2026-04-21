<script lang="ts">
  import { onMount } from 'svelte'
  import { applySavedDeckToCharacter } from '../lib/api/characters'
  import { listDecks } from '../lib/api/decks'
  import type { DeckResponse, DeckType } from '../lib/api/deckTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { formatContentEnumLabel } from '../lib/content/display'
  import {
    deckListStateCopy,
    readDeckPageFeedback,
    type DeckPageFeedback,
  } from '../lib/decks/pageState'
  import { pathBuilders } from '../lib/navigation'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type DeckListItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  let selectedId = $state('')
  let loading = $state(true)
  let errorMessage = $state<string | null>(null)
  let applyErrorMessage = $state<string | null>(null)
  let applying = $state(false)
  let feedback = $state<DeckPageFeedback | null>(null)
  let decks = $state<DeckResponse[]>([])
  let requestSequence = 0
  let deckApplyCharacterId = $state<string | null>(null)

  function getDeckId(deck: Pick<DeckResponse, 'id'>) {
    return String(deck.id)
  }

  function getDeckTypeTone(type: DeckType) {
    return type === 'PLAYER' ? 'success' : 'warning'
  }

  function getDeckSubtitle(deck: DeckResponse) {
    return `${formatContentEnumLabel(deck.type)} deck`
  }

  function buildDeckMeta(deck: DeckResponse) {
    const typeLabel = formatContentEnumLabel(deck.type)
    const entryLabel = deck.cards.length === 1 ? 'entry' : 'entries'
    return `${typeLabel} deck · ${deck.totalCards} total cards · ${deck.cards.length} ${entryLabel}`
  }

  function buildDeckNote(deck: DeckResponse) {
    if (!deck.cards.length) {
      return 'No cards are assigned to this deck yet.'
    }

    const previewCards = deck.cards
      .slice(0, 3)
      .map((card) => `${card.cardId} x${card.count}`)
      .join(', ')
    const remainingCards = deck.cards.length - 3

    return remainingCards > 0
      ? `${previewCards} and ${remainingCards} more card entries.`
      : previewCards
  }

  function buildDeckTags(deck: DeckResponse) {
    const tags: DeckListItem['tags'] = [
      { label: formatContentEnumLabel(deck.type), tone: getDeckTypeTone(deck.type) },
      { label: `${deck.totalCards} Cards`, tone: 'muted' },
    ]

    if (!deck.cards.length) {
      tags.push({ label: 'Empty', tone: 'accent' })
    }

    return tags
  }

  function toDeckListItem(deck: DeckResponse): DeckListItem {
    return {
      id: getDeckId(deck),
      title: deck.name,
      subtitle: getDeckSubtitle(deck),
      meta: buildDeckMeta(deck),
      note: buildDeckNote(deck),
      tags: buildDeckTags(deck),
    }
  }

  function syncSelectedDeck(nextDecks: DeckResponse[]) {
    const nextIds = nextDecks.map(getDeckId)

    if (selectedId && nextIds.includes(selectedId)) {
      return
    }

    const handoffId = readSelectionHandoff(selectionHandoffKeys.deckId)

    if (handoffId && nextIds.includes(handoffId)) {
      selectedId = handoffId
      return
    }

    selectedId = nextIds[0] ?? ''
  }

  async function loadDeckArchive() {
    const requestId = ++requestSequence
    loading = true
    errorMessage = null

    try {
      const response = await listDecks()

      if (requestId !== requestSequence) {
        return
      }

      decks = response
      syncSelectedDeck(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      decks = []
      selectedId = ''
      errorMessage = getApiErrorMessage(error, 'Unable to load the deck archive.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function persistSelectedDeck(id: string) {
    if (!id) return
    setSelectionHandoff(selectionHandoffKeys.deckId, id)
  }

  function navigateTo(path: string, state: Record<string, unknown> = {}) {
    if (typeof window === 'undefined') return

    window.history.pushState(state, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function openDeckEditor(id: string) {
    if (!id) return

    selectedId = id
    persistSelectedDeck(id)
    navigateTo(pathBuilders.deckEditor(id))
  }

  function selectDeck(id: string) {
    if (!id) return
    selectedId = id
    persistSelectedDeck(id)
    applyErrorMessage = null
  }

  async function applySelectedDeckToCharacter() {
    if (!deckApplyCharacterId || !selectedDeck || applying) {
      return
    }

    applying = true
    applyErrorMessage = null

    try {
      const response = await applySavedDeckToCharacter(deckApplyCharacterId, getDeckId(selectedDeck))
      setSelectionHandoff(selectionHandoffKeys.characterId, String(response.id))
      removeSelectionHandoff(selectionHandoffKeys.deckApplyCharacterId)
      navigateTo(pathBuilders.characterDetail(String(response.id)), {
        characterFeedback: 'Saved deck applied to this character.',
      })
    } catch (error) {
      applyErrorMessage = getApiErrorMessage(error, 'Unable to apply the selected deck to this character.')
    } finally {
      applying = false
    }
  }

  onMount(() => {
    feedback = readDeckPageFeedback()
    deckApplyCharacterId = readSelectionHandoff(selectionHandoffKeys.deckApplyCharacterId)
    void loadDeckArchive()
  })

  const deckItems = $derived.by(() => decks.map(toDeckListItem))
  const selectedDeck = $derived.by(() => decks.find((deck) => getDeckId(deck) === selectedId) ?? decks[0] ?? null)
  const playerDeckCount = $derived.by(() => decks.filter((deck) => deck.type === 'PLAYER').length)
  const enemyDeckCount = $derived.by(() => decks.filter((deck) => deck.type === 'ENEMY').length)
  const emptyListMessage = $derived.by(() => deckListStateCopy.emptyMessage)
  const listSummary = $derived.by(() => {
    if (loading) return deckListStateCopy.loadingTitle
    if (errorMessage) return deckListStateCopy.loadErrorTitle
    return `${decks.length} live deck records are available from the API.`
  })
  const selectedDeckEditorPath = $derived.by(() =>
    selectedDeck ? pathBuilders.deckEditor(getDeckId(selectedDeck)) : pathBuilders.deckEditor(),
  )
  const inCharacterApplyFlow = $derived.by(() => Boolean(deckApplyCharacterId))
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Deck Overview"
    title="덱 보관소"
    description={deckListStateCopy.overviewDescription}
  >
    <div class="list-page__stats">
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : decks.length}
        label="Visible decks"
        note="Current deck API result"
      />
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : playerDeckCount}
        label="Player decks"
        note="Decks marked as PLAYER"
      />
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : enemyDeckCount}
        label="Enemy decks"
        note="Decks marked as ENEMY"
      />
    </div>

    <div class="list-page__actions">
      <a class="list-page__link-action" data-nav href={pathBuilders.deckEditor()}>
        {deckListStateCopy.createActionLabel}
      </a>
    </div>

    {#if feedback}
      <ContentStatePanel
        title={feedback.title}
        message={feedback.message}
      />
    {/if}
  </SectionFrame>

  <div class="list-page__content">
    <SectionFrame
      title="덱 목록"
      description={inCharacterApplyFlow
        ? 'Select a saved deck, then apply it through the character deck API.'
        : 'Each row comes from GET /api/content/decks and keeps the existing open-editor flow on click.'}
    >
      <p class="list-page__summary">{listSummary}</p>

      {#if loading}
        <ContentStatePanel
          title={deckListStateCopy.loadingTitle}
          message={deckListStateCopy.loadingMessage}
        />
      {:else if errorMessage}
        <ContentStatePanel
          title={deckListStateCopy.loadErrorTitle}
          message={errorMessage}
          tone="error"
          actionLabel="Retry load"
          onAction={() => void loadDeckArchive()}
        />
      {:else}
        <EntityListPane
          items={deckItems}
          selectedId={selectedId}
          onSelect={inCharacterApplyFlow ? selectDeck : openDeckEditor}
          emptyMessage={emptyListMessage}
        />
      {/if}
    </SectionFrame>

    <SectionFrame
      title="선택된 덱"
      description={deckListStateCopy.detailDescription}
    >
      {#if loading}
        <ContentStatePanel
          title={deckListStateCopy.detailLoadingTitle}
          message={deckListStateCopy.detailLoadingMessage}
        />
      {:else if errorMessage}
        <ContentStatePanel
          title={deckListStateCopy.detailErrorTitle}
          message={errorMessage}
          tone="error"
        />
      {:else if selectedDeck}
        <div class="list-page__detail">
          <div>
            <h3>{selectedDeck.name}</h3>
            <p>{buildDeckMeta(selectedDeck)}</p>
          </div>

          <div class="list-page__detail-tags">
            {#each buildDeckTags(selectedDeck) as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{buildDeckNote(selectedDeck)}</p>

          {#if inCharacterApplyFlow}
            <button
              type="button"
              class="list-page__link-action"
              disabled={applying}
              onclick={() => void applySelectedDeckToCharacter()}
            >
              {applying ? 'Applying deck...' : 'Apply to character'}
            </button>
          {/if}

          {#if applyErrorMessage}
            <ContentStatePanel
              title="Unable to apply deck"
              message={applyErrorMessage}
              tone="error"
            />
          {/if}

          <a
            class="list-page__link-action"
            data-nav
            href={selectedDeckEditorPath}
            onclick={() => persistSelectedDeck(getDeckId(selectedDeck))}
          >
            Open editor for {selectedDeck.name}
          </a>
        </div>
      {:else}
        <ContentStatePanel
          title={deckListStateCopy.detailEmptyTitle}
          message={emptyListMessage}
        />
      {/if}
    </SectionFrame>
  </div>
</div>

<style>
  .list-page,
  .list-page__content,
  .list-page__detail {
    display: grid;
    gap: 1.5rem;
  }

  .list-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .list-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .list-page__content {
    grid-template-columns: minmax(0, 1.25fr) minmax(19rem, 0.75fr);
    align-items: start;
  }

  .list-page__summary,
  .list-page__detail h3,
  .list-page__detail p {
    margin: 0;
  }

  .list-page__detail h3 {
    font-family: var(--font-display);
    font-size: 1.5rem;
  }

  .list-page__summary,
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

  @media (max-width: 960px) {
    .list-page__stats,
    .list-page__content {
      grid-template-columns: 1fr;
    }
  }
</style>

