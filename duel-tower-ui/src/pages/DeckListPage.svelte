<script lang="ts">
  import { onMount } from 'svelte'
  import { listCards, listKeywords } from '../lib/api/content'
  import type {
    CardDefinition,
    CardListQueryParams,
    CardType,
    KeywordDefinition,
  } from '../lib/api/contentTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { buildCardArchiveMeta, buildCardDisplayTags, getCardTypeLabel } from '../lib/content/display'
  import { pathBuilders } from '../lib/navigation'

  type CardArchiveItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  type CardTypeFilter = CardType | ''

  const typeOptions: { label: string; value: CardTypeFilter }[] = [
    { label: 'All', value: '' },
    { label: getCardTypeLabel('SKILL'), value: 'SKILL' },
    { label: getCardTypeLabel('EX'), value: 'EX' },
    { label: getCardTypeLabel('TOKEN'), value: 'TOKEN' },
  ]

  let query = $state('')
  let selectedType = $state<CardTypeFilter>('')
  let selectedKeywordId = $state('')
  let selectedId = $state('')
  let loading = $state(true)
  let keywordLoading = $state(true)
  let errorMessage = $state<string | null>(null)
  let keywordErrorMessage = $state<string | null>(null)
  let cards = $state<CardDefinition[]>([])
  let keywords = $state<KeywordDefinition[]>([])
  let requestSequence = 0

  function toCardArchiveItem(card: CardDefinition): CardArchiveItem {
    return {
      id: card.id,
      title: card.name,
      subtitle: card.description,
      meta: buildCardArchiveMeta(card),
      note: card.token ? `Token: ${card.token}` : undefined,
      tags: buildCardDisplayTags(card),
    }
  }

  function getCurrentArchiveParams(): CardListQueryParams {
    return {
      q: query.trim() || null,
      type: selectedType || null,
      keywordId: selectedKeywordId || null,
    }
  }

  function syncSelectedCard(nextCards: CardDefinition[]) {
    const nextIds = nextCards.map((card) => card.id)
    selectedId = nextIds.includes(selectedId) ? selectedId : nextIds[0] ?? ''
  }

  async function loadKeywordOptions() {
    keywordLoading = true
    keywordErrorMessage = null

    try {
      keywords = await listKeywords()
    } catch (error) {
      keywords = []
      selectedKeywordId = ''
      keywordErrorMessage = getApiErrorMessage(error, 'Keyword filters could not be loaded.')
    } finally {
      keywordLoading = false
    }
  }

  async function loadCardArchive(params: CardListQueryParams) {
    const requestId = ++requestSequence
    loading = true
    errorMessage = null

    try {
      const response = await listCards(params)

      if (requestId !== requestSequence) {
        return
      }

      cards = response
      syncSelectedCard(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      cards = []
      selectedId = ''
      errorMessage = getApiErrorMessage(error, 'Unable to load the card archive.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  onMount(() => {
    void loadKeywordOptions()
  })

  $effect(() => {
    if (typeof window === 'undefined') {
      return
    }

    const handle = window.setTimeout(() => {
      void loadCardArchive(getCurrentArchiveParams())
    }, 250)

    return () => window.clearTimeout(handle)
  })

  const archiveItems = $derived.by(() => cards.map(toCardArchiveItem))
  const selectedCard = $derived.by(() => cards.find((card) => card.id === selectedId) ?? cards[0] ?? null)
  const selectedKeyword = $derived.by(
    () => keywords.find((keyword) => keyword.id === selectedKeywordId) ?? null,
  )
  const skillCount = $derived.by(() => cards.filter((card) => card.type === 'SKILL').length)
  const exCount = $derived.by(() => cards.filter((card) => card.type === 'EX').length)
  const tokenCount = $derived.by(() => cards.filter((card) => card.type === 'TOKEN').length)
  const emptyListMessage = $derived.by(() =>
    query || selectedType || selectedKeywordId
      ? 'No cards matched the current archive query.'
      : 'No cards are currently available in the archive.',
  )
  const listSummary = $derived.by(() => {
    if (loading) return 'Loading card archive...'
    if (errorMessage) return 'Card archive could not be loaded.'
    return `${cards.length} cards visible from the current archive query.`
  })
  const detailPath = $derived.by(() =>
    selectedCard ? pathBuilders.cardDetail(selectedCard.id) : pathBuilders.cardDetail(),
  )
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Archive Overview"
    title="카드 보관소"
    description="The card archive now loads live content from the API while keeping the existing browse-and-select layout intact."
  >
    <div class="list-page__stats">
      <StatBlock value={cards.length} label="Visible cards" note="Current API query result" />
      <StatBlock value={skillCount} label="Skill cards" note="Cards tagged as SKILL" />
      <StatBlock value={exCount + tokenCount} label="Special cards" note="EX and TOKEN results combined" />
    </div>
    {#if keywordErrorMessage}
      <ContentStatePanel
        title="Keyword filters are unavailable."
        message={keywordErrorMessage}
        tone="error"
        actionLabel="Reload filters"
        onAction={() => void loadKeywordOptions()}
      />
    {/if}
  </SectionFrame>

  <div class="list-page__content">
    <SectionFrame
      title="카드 목록"
      description="Search, type, and keyword filters now query the live card archive."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search cards"
        summary={listSummary}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <div class="card-archive__filter-group" role="group" aria-label="Card type filter">
            {#each typeOptions as option}
              <button
                type="button"
                class="card-archive__filter-button"
                class:card-archive__filter-button--active={selectedType === option.value}
                onclick={() => (selectedType = option.value)}
              >
                {option.label}
              </button>
            {/each}
          </div>

          <label class="card-archive__keyword-filter">
            <span class="visually-hidden">Keyword filter</span>
            <select
              bind:value={selectedKeywordId}
              disabled={keywordLoading || Boolean(keywordErrorMessage)}
            >
              <option value="">All keywords</option>
              {#each keywords as keyword}
                <option value={keyword.id}>{keyword.name}</option>
              {/each}
            </select>
          </label>
        {/snippet}

        {#snippet sort()}
          <TagChip label={selectedType ? getCardTypeLabel(selectedType) : 'All types'} tone="muted" />
          <TagChip label={selectedKeyword?.name || 'All keywords'} tone="muted" />
        {/snippet}
      </SearchFilterBar>

      {#if loading}
        <ContentStatePanel
          title="Loading card archive"
          message="Refreshing the current card list from the content API."
        />
      {:else if errorMessage}
        <ContentStatePanel
          title="Unable to load the card archive"
          message={errorMessage}
          tone="error"
          actionLabel="Retry load"
          onAction={() => void loadCardArchive(getCurrentArchiveParams())}
        />
      {:else}
        <EntityListPane
          items={archiveItems}
          selectedId={selectedId}
          onSelect={(id) => (selectedId = id)}
          emptyMessage={emptyListMessage}
        />
      {/if}
    </SectionFrame>

    <SectionFrame
      title="선택된 카드"
      description="Selection stays in this page shell and feeds the live card-detail route used by the next deck flows."
    >
      {#if loading}
        <ContentStatePanel
          title="Preparing selected card"
          message="The current archive selection is being summarized."
        />
      {:else if errorMessage}
        <ContentStatePanel
          title="Selected card summary is unavailable"
          message={errorMessage}
          tone="error"
        />
      {:else if selectedCard}
        <div class="list-page__detail">
          <div>
            <h3>{selectedCard.name}</h3>
            <p>{selectedCard.description || 'No card description is available for this record.'}</p>
          </div>

          <div class="list-page__detail-tags">
            {#each buildCardDisplayTags(selectedCard) as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{buildCardArchiveMeta(selectedCard)}</p>
          {#if selectedCard.token}
            <p>Token: {selectedCard.token}</p>
          {/if}

          <a class="list-page__link-action" data-nav href={detailPath}>
            Open detail for {selectedCard.name}
          </a>
        </div>
      {:else}
        <ContentStatePanel
          title="No card is selected"
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

  .list-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .list-page__content {
    grid-template-columns: minmax(0, 1.25fr) minmax(19rem, 0.75fr);
    align-items: start;
  }

  .card-archive__filter-group {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .card-archive__filter-button {
    min-height: 2.2rem;
    padding: 0.45rem 0.7rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text-muted);
    font: inherit;
    font-size: 0.74rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .card-archive__filter-button--active {
    border-color: rgba(226, 193, 155, 0.38);
    background: rgba(226, 193, 155, 0.08);
    color: var(--color-accent);
  }

  .card-archive__keyword-filter select {
    min-height: 2.2rem;
    min-width: 11rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    font: inherit;
    padding: 0.45rem 0.7rem;
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

