<script lang="ts">
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import { selectionHandoffKeys, setSelectionHandoff } from '../lib/selectionHandoff'

  const characters = [
    {
      id: 'char-ash',
      title: 'Ashen Knight',
      subtitle: 'Frontline guardian',
      meta: 'Level 18 · Guard line · Expedition ready',
      note: 'Balanced defense and reliable taunt timing.',
      tags: [
        { label: 'Ready', tone: 'success' },
        { label: 'Front', tone: 'muted' },
      ],
    },
    {
      id: 'char-mira',
      title: 'Mira of Cinders',
      subtitle: 'Control caster',
      meta: 'Level 16 · Burn control · Recovery limited',
      note: 'Strong tempo swing but currently tied to a draft deck.',
      tags: [
        { label: 'Assigned', tone: 'warning' },
        { label: 'Mage', tone: 'muted' },
      ],
    },
    {
      id: 'char-rune',
      title: 'Rune Archivist',
      subtitle: 'Support analyst',
      meta: 'Level 14 · Utility support · Not in party',
      note: 'Best suited for scouting and status cleansing support.',
      tags: [
        { label: 'Idle', tone: 'accent' },
        { label: 'Support', tone: 'muted' },
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
  let selectedId = $state(characters[0]?.id ?? '')

  const filteredCharacters = $derived.by(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return characters

    return characters.filter((item) =>
      [item.title, item.subtitle, item.meta, item.note].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  })

  const selectedCharacter = $derived.by(
    () => filteredCharacters.find((item) => item.id === selectedId) ?? filteredCharacters[0] ?? null,
  )

  function persistSelectedCharacter(id: string) {
    setSelectionHandoff(selectionHandoffKeys.characterId, id)
  }

  function handleSelectCharacter(id: string) {
    selectedId = id
    persistSelectedCharacter(id)
  }

  function handleOpenDetail() {
    persistSelectedCharacter(selectedCharacter?.id ?? characters[0]?.id ?? '')
  }

  const selectedCharacterDetailPath = $derived.by(() =>
    pathBuilders.characterDetail(selectedCharacter?.id ?? characters[0]?.id),
  )
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Roster Summary"
    title="원정 가능한 모험가"
    description="Stitch의 명부 화면을 기준으로, 목록 탐색과 선택 흐름을 우선 구현하는 배치입니다."
  >
    <div class="list-page__stats">
      <StatBlock value={characters.length} label="Total roster" note="Current mock roster size" />
      <StatBlock value="2" label="Ready units" note="Can join the next expedition" />
      <StatBlock value="1" label="Unassigned" note="Available for a new deck slot" />
    </div>
  </SectionFrame>

  <div class="list-page__content">
    <SectionFrame
      title="모험가 목록"
      description="검색과 필터 영역은 UI만 우선 고정하고, 실제 데이터 연결 지점은 이후 API 계약에 맞춰 붙입니다."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search characters"
        summary={`${filteredCharacters.length}명의 모험가가 현재 필터에 표시됩니다.`}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <TagChip label="All" tone="accent" />
          <TagChip label="Ready" tone="success" />
          <TagChip label="Assigned" tone="warning" />
        {/snippet}

        {#snippet sort()}
          <TagChip label="Level" tone="muted" />
          <TagChip label="Role" tone="muted" />
        {/snippet}
      </SearchFilterBar>

      <EntityListPane
        items={filteredCharacters}
        selectedId={selectedId}
        onSelect={handleSelectCharacter}
        emptyMessage="조건에 맞는 모험가가 없습니다."
      />
    </SectionFrame>

    <SectionFrame
      title="선택 요약"
      description="상세 편집 화면은 다음 배치에서 연결하고, 현재는 선택 대상의 핵심 정보만 요약합니다."
    >
      {#if selectedCharacter}
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

          <div class="list-page__todo">
            <p>TODO: Remove the legacy fixed detail route fallback after URL-based entry is fully stable.</p>
            <p>TODO: Connect roster selection and detail entry to the character API contract.</p>
            <p>TODO: Remove mock roster summary data after live character state is available.</p>
          </div>
        </div>
      {:else}
        <p class="list-page__empty">표시할 모험가가 없습니다.</p>
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
