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
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import {
    formatContentEnumLabel,
    getCardTypeLabel,
    getCardTypeTone,
  } from '../lib/content/display'
  import { pathBuilders } from '../lib/navigation'

  const QUERY_DEBOUNCE_MS = 180
  const cardTypeOptions: Array<{ value: '' | CardType; label: string }> = [
    { value: '', label: 'All types' },
    { value: 'SKILL', label: getCardTypeLabel('SKILL') },
    { value: 'EX', label: getCardTypeLabel('EX') },
    { value: 'TOKEN', label: getCardTypeLabel('TOKEN') },
  ]

  let query = $state('')
  let selectedType = $state<'' | CardType>('')
  let selectedKeywordId = $state('')
  let selectedCardId = $state('')

  let cardsLoading = $state(true)
  let keywordsLoading = $state(true)
  let cardsErrorMessage = $state<string | null>(null)
  let keywordsErrorMessage = $state<string | null>(null)

  let cards = $state<CardDefinition[]>([])
  let keywords = $state<KeywordDefinition[]>([])

  let requestSequence = 0
  let mounted = false
  let cardLoadTimer = 0

  function summarizeDescription(description: string | null | undefined, maxLength = 132) {
    const normalized = description?.trim()

    if (!normalized) {
      return 'No card description is currently registered for this record.'
    }

    if (normalized.length <= maxLength) {
      return normalized
    }

    return `${normalized.slice(0, maxLength).trimEnd()}...`
  }

  function createCardQueryParams(
    nextQuery: string,
    nextType: '' | CardType,
    nextKeywordId: string,
  ): CardListQueryParams {
    return {
      q: nextQuery.trim() || null,
      type: nextType || null,
      keywordId: nextKeywordId.trim() || null,
    }
  }

  function buildCardMeta(card: Pick<CardDefinition, 'type' | 'cost' | 'resolveTo'>) {
    const parts = [getCardTypeLabel(card.type)]

    if (card.cost !== null) {
      parts.push(`Cost ${card.cost}`)
    }

    if (card.resolveTo) {
      parts.push(`Resolve ${formatContentEnumLabel(card.resolveTo)}`)
    }

    return parts.join(' | ')
  }

  function syncSelectedCard(nextCards: CardDefinition[]) {
    const nextIds = nextCards.map((card) => card.id)

    if (selectedCardId && nextIds.includes(selectedCardId)) {
      return
    }

    selectedCardId = nextIds[0] ?? ''
  }

  async function loadKeywordArchive() {
    keywordsLoading = true
    keywordsErrorMessage = null

    try {
      const response = await listKeywords()
      const nextKeywords = [...response].sort((left, right) => left.name.localeCompare(right.name))

      keywords = nextKeywords

      if (!nextKeywords.some((keyword) => keyword.id === selectedKeywordId)) {
        selectedKeywordId = ''
      }
    } catch (error) {
      keywords = []
      selectedKeywordId = ''
      keywordsErrorMessage = getApiErrorMessage(error, 'Unable to load keyword definitions.')
    } finally {
      keywordsLoading = false
    }
  }

  async function loadCardArchive(
    params: CardListQueryParams = createCardQueryParams(query, selectedType, selectedKeywordId),
  ) {
    const requestId = ++requestSequence
    cardsLoading = true
    cardsErrorMessage = null

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
      selectedCardId = ''
      cardsErrorMessage = getApiErrorMessage(error, 'Unable to load the card archive.')
    } finally {
      if (requestId === requestSequence) {
        cardsLoading = false
      }
    }
  }

  function resetFilters() {
    query = ''
    selectedType = ''
    selectedKeywordId = ''
  }

  function previewCard(id: string) {
    selectedCardId = id
  }

  onMount(() => {
    mounted = true
    void loadKeywordArchive()

    return () => {
      mounted = false

      if (cardLoadTimer) {
        window.clearTimeout(cardLoadTimer)
      }
    }
  })

  $effect(() => {
    if (!mounted || typeof window === 'undefined') {
      return
    }

    const nextQuery = query
    const nextType = selectedType
    const nextKeywordId = selectedKeywordId

    if (cardLoadTimer) {
      window.clearTimeout(cardLoadTimer)
      cardLoadTimer = 0
    }

    const delay = nextQuery.trim() ? QUERY_DEBOUNCE_MS : 0

    cardLoadTimer = window.setTimeout(() => {
      cardLoadTimer = 0
      void loadCardArchive(createCardQueryParams(nextQuery, nextType, nextKeywordId))
    }, delay)

    return () => {
      if (cardLoadTimer) {
        window.clearTimeout(cardLoadTimer)
        cardLoadTimer = 0
      }
    }
  })

  const keywordLabelMap = $derived.by(() => new Map(keywords.map((keyword) => [keyword.id, keyword.name])))

  function getKeywordLabel(keywordId: string) {
    return keywordLabelMap.get(keywordId) ?? keywordId
  }

  function getKeywordPreview(card: CardDefinition) {
    return card.keywords.slice(0, 3).map(getKeywordLabel)
  }

  const visibleCardCount = $derived.by(() => cards.length)
  const activeFilterCount = $derived.by(
    () => Number(Boolean(query.trim())) + Number(Boolean(selectedType)) + Number(Boolean(selectedKeywordId)),
  )
  const selectedKeywordName = $derived.by(
    () => keywords.find((keyword) => keyword.id === selectedKeywordId)?.name ?? null,
  )
  const selectedCard = $derived.by(() => cards.find((card) => card.id === selectedCardId) ?? cards[0] ?? null)
  const listSummary = $derived.by(() => {
    if (cardsLoading) {
      return 'Refreshing the current card archive.'
    }

    if (cardsErrorMessage) {
      return 'The card archive could not be restored with the current filters.'
    }

    const filterSummary = [
      query.trim() ? `query "${query.trim()}"` : null,
      selectedType ? getCardTypeLabel(selectedType) : null,
      selectedKeywordName ? `keyword ${selectedKeywordName}` : null,
    ]
      .filter(Boolean)
      .join(' | ')

    return filterSummary
      ? `${cards.length} cards matched ${filterSummary}.`
      : `${cards.length} cards are visible in the archive.`
  })
  const emptyStateTitle = $derived.by(() =>
    activeFilterCount > 0 ? 'No cards matched the current filter' : 'No cards are available',
  )
  const emptyStateMessage = $derived.by(() =>
    activeFilterCount > 0
      ? 'Try a broader query, clear the selected keyword, or switch back to all card types.'
      : 'No cards are currently registered in the archive.',
  )
</script>

<div class="card-library-page">
  <SectionFrame
    eyebrow="Content Library"
    title="카드 보관소"
    description="등록된 카드를 탐색하고, 검색과 필터를 유지한 채 상세 기록으로 이동할 수 있습니다."
  >
    <div class="card-library-page__hero">
      <div class="card-library-page__hero-copy">
        <p>Card archive</p>
        <h3>실시간 카드 레지스트리를 탐색하고, 현재 필터 흐름에서 바로 상세 기록으로 이동합니다.</h3>
      </div>

      <div class="card-library-page__hero-tags">
        <TagChip label="Live Archive" tone="accent" />
        {#if selectedType}
          <TagChip label={getCardTypeLabel(selectedType)} tone={getCardTypeTone(selectedType)} />
        {/if}
        {#if selectedKeywordName}
          <TagChip label={selectedKeywordName} tone="accent" />
        {/if}
        {#if activeFilterCount > 0}
          <TagChip label={`${activeFilterCount} Filters`} tone="warning" />
        {/if}
      </div>
    </div>

    <div class="card-library-page__stats">
      <StatBlock
        value={cardsLoading ? '...' : cardsErrorMessage ? '-' : visibleCardCount}
        label="Visible cards"
        note="Current archive result count"
      />
      <StatBlock
        value={keywordsLoading ? '...' : keywordsErrorMessage ? '-' : keywords.length}
        label="Keyword glossary"
        note="Available keyword filter entries"
      />
      <StatBlock
        value={activeFilterCount}
        label="Active filters"
        note="Search, type, and keyword filters now applied"
      />
    </div>

    {#if keywordsErrorMessage}
      <ContentStatePanel
        title="Keyword filter unavailable"
        message={keywordsErrorMessage}
        tone="error"
        actionLabel="Retry keywords"
        onAction={() => void loadKeywordArchive()}
      >
        <p>Card browsing remains available while the keyword glossary is unavailable.</p>
      </ContentStatePanel>
    {/if}
  </SectionFrame>

  <div class="card-library-page__content">
    <SectionFrame
      title="Archive catalog"
      description="Search by name, id, or description, then narrow the archive by card type and linked keyword."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search card names, ids, and descriptions"
        summary={listSummary}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <label class="card-library-page__control">
            <span>Type</span>
            <select bind:value={selectedType}>
              {#each cardTypeOptions as option}
                <option value={option.value}>{option.label}</option>
              {/each}
            </select>
          </label>

          <label class="card-library-page__control">
            <span>Keyword</span>
            <select bind:value={selectedKeywordId} disabled={keywordsLoading || Boolean(keywordsErrorMessage)}>
              <option value="">All keywords</option>

              {#if keywordsLoading}
                <option value="" disabled>Loading keywords...</option>
              {:else}
                {#each keywords as keyword}
                  <option value={keyword.id}>{keyword.name}</option>
                {/each}
              {/if}
            </select>
          </label>
        {/snippet}

        {#snippet actions()}
          <button
            type="button"
            class="card-library-page__toolbar-action"
            onclick={resetFilters}
            disabled={activeFilterCount === 0}
          >
            Clear filters
          </button>

          <button
            type="button"
            class="card-library-page__toolbar-action card-library-page__toolbar-action--accent"
            onclick={() => void loadCardArchive()}
            disabled={cardsLoading}
          >
            Reload cards
          </button>
        {/snippet}
      </SearchFilterBar>

      {#if cardsLoading}
        <ContentStatePanel
          title="Loading card archive"
          message="Pulling the latest card collection into the archive."
        />
      {:else if cardsErrorMessage}
        <ContentStatePanel
          title="Unable to load the card archive"
          message={cardsErrorMessage}
          tone="error"
          actionLabel="Retry load"
          onAction={() => void loadCardArchive()}
        />
      {:else if cards.length === 0}
        <ContentStatePanel
          title={emptyStateTitle}
          message={emptyStateMessage}
        >
          {#if activeFilterCount > 0}
            <p>Clear one or more filters to broaden the archive view.</p>
          {:else}
            <p>Populate the card registry to make library entries visible here.</p>
          {/if}
        </ContentStatePanel>
      {:else}
        <div class="card-library-page__grid">
          {#each cards as card (card.id)}
            <a
              class="card-library-page__card"
              class:card-library-page__card--selected={selectedCard?.id === card.id}
              data-nav
              href={pathBuilders.cardDetail(card.id)}
              onmouseenter={() => previewCard(card.id)}
              onfocus={() => previewCard(card.id)}
            >
              <div class="card-library-page__card-chrome">
                <strong>{card.cost ?? 'N/A'}</strong>
                <span>{getCardTypeLabel(card.type)}</span>
              </div>

              <div class="card-library-page__card-art">
                <p>{card.id}</p>
              </div>

              <div class="card-library-page__card-copy">
                <h3>{card.name}</h3>
                <p class="card-library-page__card-meta">{buildCardMeta(card)}</p>
                <p class="card-library-page__card-description">{summarizeDescription(card.description)}</p>
              </div>

              <div class="card-library-page__card-tags">
                <TagChip label={getCardTypeLabel(card.type)} tone={getCardTypeTone(card.type)} />
                {#each getKeywordPreview(card) as keywordLabel}
                  <TagChip label={keywordLabel} tone="accent" />
                {/each}
                {#if card.keywords.length > 3}
                  <TagChip label={`+${card.keywords.length - 3} more`} tone="muted" />
                {/if}
              </div>
            </a>
          {/each}
        </div>
      {/if}
    </SectionFrame>

    <SectionFrame
      title="Archive focus"
      description="Keep one card in view while filtering so the detail transition remains readable."
    >
      {#if cardsLoading}
        <ContentStatePanel
          title="Preparing archive focus"
          message="The spotlight card will appear once the current result set is loaded."
        />
      {:else if cardsErrorMessage}
        <ContentStatePanel
          title="Archive focus unavailable"
          message={cardsErrorMessage}
          tone="error"
        />
      {:else if selectedCard}
        <div class="card-library-page__focus">
          <div class="card-library-page__focus-copy">
            <p>Spotlight card</p>
            <h3>{selectedCard.name}</h3>
            <p>{selectedCard.description || 'No description is currently stored for this card.'}</p>
          </div>

          <div class="card-library-page__focus-tags">
            <TagChip label={getCardTypeLabel(selectedCard.type)} tone={getCardTypeTone(selectedCard.type)} />
            <TagChip label={`Cost ${selectedCard.cost ?? 'N/A'}`} tone="muted" />
            {#if selectedCard.resolveTo}
              <TagChip label={`Resolve ${formatContentEnumLabel(selectedCard.resolveTo)}`} tone="warning" />
            {/if}
          </div>

          <div class="card-library-page__focus-note">
            <p>ID {selectedCard.id}</p>
            <p>{buildCardMeta(selectedCard)}</p>

            {#if selectedCard.keywords.length > 0}
              <div class="card-library-page__focus-tags">
                {#each selectedCard.keywords as keywordId}
                  <TagChip label={getKeywordLabel(keywordId)} tone="accent" />
                {/each}
              </div>
            {:else}
              <p>No keyword references are linked to this card.</p>
            {/if}
          </div>

          <a
            class="card-library-page__link-action"
            data-nav
            href={pathBuilders.cardDetail(selectedCard.id)}
          >
            Open detail for {selectedCard.name}
          </a>
        </div>
      {:else}
        <ContentStatePanel
          title="No spotlight card"
          message="Once the archive returns cards, the current focus card will appear here."
        />
      {/if}
    </SectionFrame>
  </div>
</div>

<style>
  .card-library-page,
  .card-library-page__content,
  .card-library-page__focus,
  .card-library-page__focus-note {
    display: grid;
    gap: 1.5rem;
  }

  .card-library-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .card-library-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 44rem;
  }

  .card-library-page__hero-copy p,
  .card-library-page__hero-copy h3,
  .card-library-page__focus-copy p,
  .card-library-page__focus-copy h3,
  .card-library-page__focus-note p,
  .card-library-page__card-copy h3,
  .card-library-page__card-copy p {
    margin: 0;
  }

  .card-library-page__hero-copy p,
  .card-library-page__focus-copy > p {
    color: var(--color-text-muted);
    font-size: 0.76rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .card-library-page__hero-copy h3,
  .card-library-page__focus-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.7rem, 2.4vw, 2.3rem);
    line-height: 1.15;
  }

  .card-library-page__hero-tags,
  .card-library-page__focus-tags,
  .card-library-page__card-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .card-library-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .card-library-page__content {
    grid-template-columns: minmax(0, 1.2fr) minmax(19rem, 0.8fr);
    align-items: start;
  }

  .card-library-page__control {
    min-width: min(100%, 11rem);
    display: grid;
    gap: 0.35rem;
  }

  .card-library-page__control span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .card-library-page__control select {
    min-height: 2.4rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    padding: 0.5rem 0.65rem;
    font: inherit;
  }

  .card-library-page__toolbar-action,
  .card-library-page__link-action {
    min-height: 2.8rem;
    width: fit-content;
    padding: 0.65rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .card-library-page__toolbar-action:disabled {
    opacity: 0.68;
  }

  .card-library-page__toolbar-action--accent,
  .card-library-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .card-library-page__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(15.5rem, 1fr));
    gap: 1rem;
  }

  .card-library-page__card {
    border: 1px solid var(--color-border);
    background:
      linear-gradient(180deg, rgba(20, 17, 16, 0.92), rgba(14, 12, 11, 0.96)),
      radial-gradient(circle at top, rgba(226, 193, 155, 0.08), transparent 60%);
    padding: 0.9rem;
    display: grid;
    gap: 0.9rem;
    color: var(--color-text);
    transition:
      transform 160ms ease,
      border-color 160ms ease,
      background-color 160ms ease;
  }

  .card-library-page__card:hover,
  .card-library-page__card:focus-visible,
  .card-library-page__card--selected {
    border-color: rgba(226, 193, 155, 0.42);
    transform: translateY(-2px);
  }

  .card-library-page__card-chrome {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: center;
  }

  .card-library-page__card-chrome strong,
  .card-library-page__card-chrome span {
    display: inline-flex;
    align-items: center;
    min-height: 2rem;
    padding: 0.2rem 0.55rem;
    border: 1px solid var(--color-border);
    background: rgba(8, 7, 6, 0.42);
    font-size: 0.72rem;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .card-library-page__card-chrome strong {
    font-family: var(--font-display);
    font-size: 1rem;
    letter-spacing: 0.04em;
  }

  .card-library-page__card-art {
    min-height: 10.5rem;
    border: 1px solid rgba(255, 255, 255, 0.06);
    background:
      linear-gradient(145deg, rgba(44, 38, 34, 0.36), rgba(10, 9, 8, 0.88)),
      radial-gradient(circle at 30% 20%, rgba(226, 193, 155, 0.12), transparent 42%),
      repeating-linear-gradient(
        135deg,
        rgba(255, 255, 255, 0.025),
        rgba(255, 255, 255, 0.025) 6px,
        transparent 6px,
        transparent 14px
      );
    display: flex;
    align-items: flex-end;
    padding: 0.85rem;
  }

  .card-library-page__card-art p {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    word-break: break-all;
  }

  .card-library-page__card-copy {
    display: grid;
    gap: 0.4rem;
  }

  .card-library-page__card-copy h3 {
    font-family: var(--font-display);
    font-size: 1.35rem;
    line-height: 1.2;
  }

  .card-library-page__card-meta,
  .card-library-page__focus-note p,
  .card-library-page__focus-copy > p:last-child {
    color: var(--color-text-muted);
  }

  .card-library-page__card-description,
  .card-library-page__focus-copy > p:last-child,
  .card-library-page__focus-note p {
    line-height: 1.65;
  }

  .card-library-page__focus {
    height: 100%;
    align-content: start;
  }

  .card-library-page__focus-copy {
    display: grid;
    gap: 0.6rem;
  }

  .card-library-page__focus-copy > p:last-child {
    color: var(--color-text-soft);
  }

  .card-library-page__focus-note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  @media (max-width: 1080px) {
    .card-library-page__content {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 720px) {
    .card-library-page__stats {
      grid-template-columns: 1fr;
    }

    .card-library-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
