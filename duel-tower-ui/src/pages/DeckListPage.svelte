<script lang="ts">
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const decks = [
    {
      id: 'deck-vanguard',
      title: 'Vanguard Ember',
      subtitle: 'Frontline pressure deck',
      meta: '12 cards · Aggro opener · Assigned to Ashen Knight',
      note: 'Strong early pace with low recovery margin.',
      tags: [
        { label: 'Active', tone: 'success' },
        { label: 'Aggro', tone: 'accent' },
      ],
    },
    {
      id: 'deck-siege',
      title: 'Siege Ledger',
      subtitle: 'Midrange control',
      meta: '12 cards · Stable curve · Shared utility core',
      note: 'Most balanced list for general expedition use.',
      tags: [
        { label: 'Ready', tone: 'success' },
        { label: 'Control', tone: 'muted' },
      ],
    },
    {
      id: 'deck-frost',
      title: 'Frost Seal Draft',
      subtitle: 'Experimental build',
      meta: '10 cards · Two slots open · Not assigned',
      note: 'Reserved for the next editor pass.',
      tags: [
        { label: 'Draft', tone: 'warning' },
        { label: 'Open', tone: 'accent' },
      ],
    },
  ] satisfies Array<{
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }>

  let query = $state('')
  let selectedId = $state(decks[0]?.id ?? '')
  const DECK_HANDOFF_KEY = 'duel-tower:selected-deck-id'

  const filteredDecks = $derived.by(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return decks

    return decks.filter((item) =>
      [item.title, item.subtitle, item.meta, item.note].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  })

  const selectedDeck = $derived.by(
    () => filteredDecks.find((item) => item.id === selectedId) ?? filteredDecks[0] ?? null,
  )

  function persistSelectedDeck(id: string) {
    if (typeof window === 'undefined' || !id) return
    window.sessionStorage.setItem(DECK_HANDOFF_KEY, id)
  }

  function handleSelectDeck(id: string) {
    selectedId = id
    persistSelectedDeck(id)
  }

  function handleOpenEditor() {
    persistSelectedDeck(selectedDeck?.id ?? decks[0]?.id ?? '')
  }
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Deck Overview"
    title="전술 보관소"
    description="Deck List Stitch 화면을 따라, 목록 탐색과 다음 편집 대상으로 이어지는 흐름을 먼저 고정합니다."
  >
    <div class="list-page__stats">
      <StatBlock value={decks.length} label="Tracked decks" note="Current mock deck count" />
      <StatBlock value="2" label="Ready decks" note="Available for assignment" />
      <StatBlock value="1" label="Draft decks" note="Needs editor follow-up" />
    </div>
  </SectionFrame>

  <div class="list-page__content">
    <SectionFrame
      title="전술 목록"
      description="다음 배치의 편집기 이전 단계로서, 전술을 빠르게 비교하고 선택하는 목록 패턴에 집중합니다."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search decks"
        summary={`${filteredDecks.length}개의 전술 구성이 현재 목록에 표시됩니다.`}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <TagChip label="All" tone="accent" />
          <TagChip label="Ready" tone="success" />
          <TagChip label="Draft" tone="warning" />
        {/snippet}

        {#snippet sort()}
          <TagChip label="Updated" tone="muted" />
          <TagChip label="Power" tone="muted" />
        {/snippet}
      </SearchFilterBar>

      <EntityListPane
        items={filteredDecks}
        selectedId={selectedId}
        onSelect={handleSelectDeck}
        emptyMessage="조건에 맞는 전술 구성이 없습니다."
      />
    </SectionFrame>

    <SectionFrame
      title="선택된 전술"
      description="현재 배치는 목록까지 구현하고, 실제 카드 편집기 연결은 다음 단계로 미룹니다."
    >
      {#if selectedDeck}
        <div class="list-page__detail">
          <div>
            <h3>{selectedDeck.title}</h3>
            <p>{selectedDeck.subtitle}</p>
          </div>

          <div class="list-page__detail-tags">
            {#each selectedDeck.tags ?? [] as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{selectedDeck.meta}</p>
          <p>{selectedDeck.note}</p>

          <a class="list-page__link-action" data-nav href="/decks/editor" onclick={handleOpenEditor}>
            Open editor for {selectedDeck.title}
          </a>

          <div class="list-page__todo">
            <p>TODO: Expand /decks/editor into an id-based route such as /decks/:id/editor.</p>
            <p>TODO: Connect deck selection and editor entry to the deck API contract.</p>
            <p>TODO: Remove mock deck summary data after live deck state is available.</p>
          </div>
        </div>
      {:else}
        <p class="list-page__empty">표시할 전술 구성이 없습니다.</p>
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
