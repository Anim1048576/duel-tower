<script lang="ts">
  import { onMount } from 'svelte'
  import { listAttachedKeywords, listKeywords, listPassives, listStatuses } from '../lib/api/content'
  import type {
    KeywordDefinition,
    PassiveDefinition,
    StatusDefinition,
  } from '../lib/api/contentTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { formatContentEnumLabel } from '../lib/content/display'

  type ReferenceSectionKey = 'keywords' | 'statuses' | 'passives'

  type ReferenceListItem =
    | {
        section: 'keywords'
        data: KeywordDefinition
      }
    | {
        section: 'statuses'
        data: StatusDefinition
      }
    | {
        section: 'passives'
        data: PassiveDefinition
      }

  type ReferenceEntityItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  const sectionOptions: Array<{
    key: ReferenceSectionKey
    label: string
    tone: 'accent' | 'warning' | 'success'
    emptyMessage: string
  }> = [
    {
      key: 'keywords',
      label: 'Keywords',
      tone: 'accent',
      emptyMessage: '표시할 키워드가 없습니다.',
    },
    {
      key: 'statuses',
      label: 'Statuses',
      tone: 'warning',
      emptyMessage: '표시할 상태가 없습니다.',
    },
    {
      key: 'passives',
      label: 'Passives',
      tone: 'success',
      emptyMessage: '표시할 패시브가 없습니다.',
    },
  ]

  let query = $state('')
  let activeSection = $state<ReferenceSectionKey>('keywords')

  let keywordLoading = $state(true)
  let statusLoading = $state(true)
  let passiveLoading = $state(true)

  let keywordErrorMessage = $state<string | null>(null)
  let statusErrorMessage = $state<string | null>(null)
  let passiveErrorMessage = $state<string | null>(null)
  let attachedKeywordErrorMessage = $state<string | null>(null)

  let keywords = $state<KeywordDefinition[]>([])
  let statuses = $state<StatusDefinition[]>([])
  let passives = $state<PassiveDefinition[]>([])
  let attachedKeywords = $state<KeywordDefinition[]>([])

  let selectedKeywordId = $state('')
  let selectedStatusId = $state('')
  let selectedPassiveId = $state('')
  let attachedKeywordLoading = $state(false)
  let attachedKeywordParentId = $state('')

  function normalizeText(value: string | null | undefined) {
    return value?.trim().toLowerCase() ?? ''
  }

  function matchesQuery(...values: Array<string | null | undefined>) {
    const normalizedQuery = normalizeText(query)

    if (!normalizedQuery) {
      return true
    }

    return values.some((value) => normalizeText(value).includes(normalizedQuery))
  }

  function syncSelection<T extends { id: string }>(
    items: T[],
    selectedId: string,
    onChange: (nextId: string) => void,
  ) {
    const nextIds = items.map((item) => item.id)
    onChange(nextIds.includes(selectedId) ? selectedId : nextIds[0] ?? '')
  }

  function getSectionMeta(section: ReferenceSectionKey) {
    return sectionOptions.find((option) => option.key === section) ?? sectionOptions[0]
  }

  function getKeywordStateLabel(parameterized: boolean) {
    return parameterized ? 'Parameterized' : 'Static'
  }

  function getPassivePriorityLabel(priority: number | null, fallback = 'No Priority') {
    return priority !== null ? `Priority ${priority}` : fallback
  }

  async function loadKeywords() {
    keywordLoading = true
    keywordErrorMessage = null

    try {
      keywords = await listKeywords()
      syncSelection(keywords, selectedKeywordId, (nextId) => (selectedKeywordId = nextId))
    } catch (error) {
      keywords = []
      selectedKeywordId = ''
      keywordErrorMessage = getApiErrorMessage(error, 'Unable to load keyword definitions.')
    } finally {
      keywordLoading = false
    }
  }

  async function loadAttachedKeywords(parentKeywordId: string | null | undefined) {
    const normalizedId = parentKeywordId?.trim() ?? ''
    attachedKeywordErrorMessage = null

    if (!normalizedId) {
      attachedKeywordParentId = ''
      attachedKeywords = []
      attachedKeywordLoading = false
      return
    }

    attachedKeywordLoading = true
    attachedKeywordParentId = normalizedId

    try {
      const nextAttachedKeywords = await listAttachedKeywords(normalizedId)

      if (attachedKeywordParentId === normalizedId) {
        attachedKeywords = nextAttachedKeywords
      }
    } catch (error) {
      if (attachedKeywordParentId === normalizedId) {
        attachedKeywords = []
        attachedKeywordErrorMessage = getApiErrorMessage(error, '부속 키워드를 불러오지 못했습니다.')
      }
    } finally {
      if (attachedKeywordParentId === normalizedId) {
        attachedKeywordLoading = false
      }
    }
  }

  async function loadStatuses() {
    statusLoading = true
    statusErrorMessage = null

    try {
      statuses = await listStatuses()
      syncSelection(statuses, selectedStatusId, (nextId) => (selectedStatusId = nextId))
    } catch (error) {
      statuses = []
      selectedStatusId = ''
      statusErrorMessage = getApiErrorMessage(error, 'Unable to load status definitions.')
    } finally {
      statusLoading = false
    }
  }

  async function loadPassives() {
    passiveLoading = true
    passiveErrorMessage = null

    try {
      passives = await listPassives()
      syncSelection(passives, selectedPassiveId, (nextId) => (selectedPassiveId = nextId))
    } catch (error) {
      passives = []
      selectedPassiveId = ''
      passiveErrorMessage = getApiErrorMessage(error, 'Unable to load passive definitions.')
    } finally {
      passiveLoading = false
    }
  }

  async function reloadSection(section: ReferenceSectionKey) {
    switch (section) {
      case 'keywords':
        await loadKeywords()
        return
      case 'statuses':
        await loadStatuses()
        return
      case 'passives':
        await loadPassives()
        return
    }
  }

  async function reloadAllSections() {
    await Promise.all([loadKeywords(), loadStatuses(), loadPassives()])
  }

  onMount(() => {
    void reloadAllSections()
  })

  $effect(() => {
    syncSelection(filteredKeywords, selectedKeywordId, (nextId) => (selectedKeywordId = nextId))
  })

  $effect(() => {
    syncSelection(filteredStatuses, selectedStatusId, (nextId) => (selectedStatusId = nextId))
  })

  $effect(() => {
    syncSelection(filteredPassives, selectedPassiveId, (nextId) => (selectedPassiveId = nextId))
  })

  const filteredKeywords = $derived.by(() =>
    keywords.filter((keyword) => matchesQuery(keyword.name, keyword.description, keyword.id)),
  )
  const filteredStatuses = $derived.by(() =>
    statuses.filter((status) =>
      matchesQuery(
        status.name,
        status.description,
        status.kind,
        status.scope,
        status.tags.join(' '),
        status.id,
      ),
    ),
  )
  const filteredPassives = $derived.by(() =>
    passives.filter((passive) =>
      matchesQuery(passive.name, passive.description, passive.priority?.toString() ?? '', passive.id),
    ),
  )

  const keywordItems = $derived.by<ReferenceEntityItem[]>(() =>
    filteredKeywords.map((keyword) => ({
      id: keyword.id,
      title: keyword.name,
      subtitle: keyword.description,
      meta: `ID ${keyword.id}`,
      note: keyword.parameterized ? '파라미터가 필요합니다.' : '고정 키워드입니다.',
      tags: [
        {
          label: getKeywordStateLabel(keyword.parameterized),
          tone: keyword.parameterized ? 'warning' : 'success',
        },
      ],
    })),
  )

  const statusItems = $derived.by<ReferenceEntityItem[]>(() =>
    filteredStatuses.map((status) => ({
      id: status.id,
      title: status.name,
      subtitle: status.description,
      meta: `${formatContentEnumLabel(status.kind)} · ${formatContentEnumLabel(status.scope)}`,
      note: status.persistsAfterCombat
        ? '전투 후에도 유지됩니다.'
        : '전투 종료 시 제거됩니다.',
      tags: [
        { label: formatContentEnumLabel(status.kind), tone: 'warning' },
        { label: formatContentEnumLabel(status.scope), tone: 'muted' },
        ...(status.tags.slice(0, 2).map((tag) => ({ label: tag, tone: 'accent' as const }))),
      ],
    })),
  )

  const passiveItems = $derived.by<ReferenceEntityItem[]>(() =>
    filteredPassives.map((passive) => ({
      id: passive.id,
      title: passive.name,
      subtitle: passive.description,
      meta: getPassivePriorityLabel(passive.priority, '우선순위 없음'),
      note: '패시브 효과입니다.',
      tags: [{ label: 'Passive', tone: 'success' }],
    })),
  )

  const currentLoading = $derived.by(() => {
    switch (activeSection) {
      case 'keywords':
        return keywordLoading
      case 'statuses':
        return statusLoading
      case 'passives':
        return passiveLoading
    }
  })

  const currentErrorMessage = $derived.by(() => {
    switch (activeSection) {
      case 'keywords':
        return keywordErrorMessage
      case 'statuses':
        return statusErrorMessage
      case 'passives':
        return passiveErrorMessage
    }
  })

  const currentItems = $derived.by(() => {
    switch (activeSection) {
      case 'keywords':
        return keywordItems
      case 'statuses':
        return statusItems
      case 'passives':
        return passiveItems
    }
  })

  const currentSelectedId = $derived.by(() => {
    switch (activeSection) {
      case 'keywords':
        return selectedKeywordId
      case 'statuses':
        return selectedStatusId
      case 'passives':
        return selectedPassiveId
    }
  })
  const sectionErrors = $derived.by(() =>
    sectionOptions.flatMap((section) => {
      const message =
        section.key === 'keywords'
          ? keywordErrorMessage
          : section.key === 'statuses'
            ? statusErrorMessage
            : passiveErrorMessage

      return message ? [{ section, message }] : []
    }),
  )

  const selectedReference = $derived.by<ReferenceListItem | null>(() => {
    switch (activeSection) {
      case 'keywords': {
        const selected =
          filteredKeywords.find((keyword) => keyword.id === selectedKeywordId) ?? filteredKeywords[0] ?? null
        return selected ? { section: 'keywords', data: selected } : null
      }
      case 'statuses': {
        const selected =
          filteredStatuses.find((status) => status.id === selectedStatusId) ?? filteredStatuses[0] ?? null
        return selected ? { section: 'statuses', data: selected } : null
      }
      case 'passives': {
        const selected =
          filteredPassives.find((passive) => passive.id === selectedPassiveId) ?? filteredPassives[0] ?? null
        return selected ? { section: 'passives', data: selected } : null
      }
    }
  })

  const selectedKeyword = $derived.by(() =>
    selectedReference?.section === 'keywords' ? selectedReference.data : null,
  )

  $effect(() => {
    const parentId = selectedKeyword?.id ?? ''

    if (activeSection !== 'keywords' || !parentId) {
      attachedKeywordParentId = ''
      attachedKeywords = []
      attachedKeywordErrorMessage = null
      attachedKeywordLoading = false
      return
    }

    if (attachedKeywordParentId === parentId) {
      return
    }

    void loadAttachedKeywords(parentId)
  })

  const selectedStatus = $derived.by(() =>
    selectedReference?.section === 'statuses' ? selectedReference.data : null,
  )

  const selectedPassive = $derived.by(() =>
    selectedReference?.section === 'passives' ? selectedReference.data : null,
  )
  const activeSectionMeta = $derived.by(() => getSectionMeta(activeSection))

  const currentSummary = $derived.by(() => {
    if (currentLoading) {
      return '규칙 정보를 불러오는 중입니다.'
    }

    if (currentErrorMessage) {
      return '규칙 정보를 불러오지 못했습니다.'
    }

    return `${currentItems.length}개 표시 중`
  })

  const emptyMessage = $derived.by(() => {
    if (query.trim()) {
      return '검색 결과가 없습니다.'
    }

    return getSectionMeta(activeSection).emptyMessage
  })

  function handleSelect(id: string) {
    switch (activeSection) {
      case 'keywords':
        selectedKeywordId = id
        return
      case 'statuses':
        selectedStatusId = id
        return
      case 'passives':
        selectedPassiveId = id
        return
    }
  }

  function getSectionTone(section: ReferenceSectionKey) {
    return getSectionMeta(section).tone
  }
</script>

<div class="rules-page">
  <SectionFrame
    eyebrow="Rules Codex"
    title="Tactical Reference"
    description="키워드, 상태, 패시브를 확인합니다."
  >
    <div class="rules-page__stats">
      <StatBlock value={keywords.length} label="Keywords" note="Live keyword definitions" />
      <StatBlock value={statuses.length} label="Statuses" note="Status and condition records" />
      <StatBlock value={passives.length} label="Passives" note="Passive effect references" />
    </div>
    {#if sectionErrors.length > 0}
      <ContentStatePanel
        title="Some reference sections are unavailable"
        message="일부 규칙 정보를 불러오지 못했습니다."
        tone="error"
        actionLabel="Reload all"
        onAction={() => void reloadAllSections()}
      >
        {#each sectionErrors as sectionError}
          <p>{sectionError.section.label}: {sectionError.message}</p>
        {/each}
      </ContentStatePanel>
    {/if}
  </SectionFrame>

  <div class="rules-page__content">
    <SectionFrame
      title="Codex catalog"
      description="섹션을 선택하고 검색합니다."
    >
      <SearchFilterBar
        query={query}
        queryPlaceholder="Search names, descriptions, and tags"
        summary={currentSummary}
        onQueryChange={(value) => (query = value)}
      >
        {#snippet filters()}
          <div class="rules-page__section-tabs" role="tablist" aria-label="Reference sections">
            {#each sectionOptions as section}
              <button
                type="button"
                role="tab"
                class="rules-page__tab"
                class:rules-page__tab--active={activeSection === section.key}
                aria-selected={activeSection === section.key}
                onclick={() => (activeSection = section.key)}
              >
                {section.label}
              </button>
            {/each}
          </div>
        {/snippet}

        {#snippet sort()}
          <TagChip label={activeSectionMeta.label} tone={activeSectionMeta.tone} />
          <TagChip label={`${currentItems.length} Visible`} tone="muted" />
        {/snippet}

        {#snippet actions()}
          <button type="button" class="rules-page__reload" onclick={() => void reloadAllSections()}>
            Reload all
          </button>
        {/snippet}
      </SearchFilterBar>

      {#if currentLoading}
        <ContentStatePanel
          title="Loading reference data"
          message="규칙 정보를 불러오는 중입니다."
        />
      {:else if currentErrorMessage}
        <ContentStatePanel
          title={`${activeSectionMeta.label} are unavailable`}
          message={currentErrorMessage}
          tone="error"
          actionLabel="Retry load"
          onAction={() => void reloadSection(activeSection)}
        />
      {:else}
        <EntityListPane
          items={currentItems}
          selectedId={currentSelectedId}
          emptyMessage={emptyMessage}
          onSelect={handleSelect}
        />
      {/if}
    </SectionFrame>

    <SectionFrame
      title="Selected codex entry"
      description="선택한 항목의 상세 정보입니다."
    >
      {#if currentLoading}
        <ContentStatePanel
          title="Preparing selected reference"
          message="선택 항목을 불러오는 중입니다."
        />
      {:else if currentErrorMessage}
        <ContentStatePanel
          title="Selected reference summary is unavailable"
          message={currentErrorMessage}
          tone="error"
        />
      {:else if selectedReference}
        <div class="rules-page__detail">
          <div>
            <h3>{selectedReference.data.name}</h3>
            <p>{selectedReference.data.description}</p>
          </div>

          <div class="rules-page__detail-tags">
            <TagChip
              label={getSectionMeta(selectedReference.section).label}
              tone={getSectionTone(selectedReference.section)}
            />

            {#if selectedKeyword}
              <TagChip
                label={getKeywordStateLabel(selectedKeyword.parameterized)}
                tone={selectedKeyword.parameterized ? 'warning' : 'success'}
              />
            {:else if selectedStatus}
              <TagChip label={formatContentEnumLabel(selectedStatus.kind)} tone="warning" />
              <TagChip label={formatContentEnumLabel(selectedStatus.scope)} tone="muted" />
              {#if selectedStatus.persistsAfterCombat}
                <TagChip label="Persists" tone="accent" />
              {/if}
            {:else}
              <TagChip
                label={getPassivePriorityLabel(selectedPassive?.priority ?? null)}
                tone="success"
              />
            {/if}
          </div>

          {#if selectedKeyword}
            <div class="rules-page__note">
              <p>ID: {selectedKeyword.id}</p>
              <p>
                {selectedKeyword.parameterized
                  ? '파라미터가 필요한 키워드입니다.'
                  : '고정 키워드입니다.'}
              </p>
            </div>

            {#if attachedKeywordLoading}
              <div class="rules-page__attached">
                <h4>부속 키워드</h4>
                <p>부속 키워드를 불러오는 중입니다.</p>
              </div>
            {:else if attachedKeywordErrorMessage}
              <div class="rules-page__attached">
                <h4>부속 키워드</h4>
                <p>{attachedKeywordErrorMessage}</p>
              </div>
            {:else if attachedKeywords.length > 0}
              <div class="rules-page__attached">
                <div class="rules-page__attached-header">
                  <h4>부속 키워드</h4>
                  <TagChip label={`${attachedKeywords.length}개`} tone="muted" />
                </div>

                <div class="rules-page__attached-list">
                  {#each attachedKeywords as attachedKeyword}
                    <article class="rules-page__attached-card">
                      <div class="rules-page__attached-card-header">
                        <h5>{attachedKeyword.name}</h5>
                        <TagChip
                          label={getKeywordStateLabel(attachedKeyword.parameterized)}
                          tone={attachedKeyword.parameterized ? 'warning' : 'success'}
                        />
                      </div>
                      <p>{attachedKeyword.description}</p>
                      <p class="rules-page__attached-meta">ID: {attachedKeyword.id}</p>
                    </article>
                  {/each}
                </div>
              </div>
            {/if}
          {:else if selectedStatus}
            <div class="rules-page__note">
              <p>ID: {selectedStatus.id}</p>
              <p>
                Kind: {formatContentEnumLabel(selectedStatus.kind)} | Scope: {formatContentEnumLabel(selectedStatus.scope)}
              </p>
              <p>
                Priority:
                {selectedStatus.priority !== null
                  ? ` ${selectedStatus.priority}`
                  : ' not set'}
              </p>
              {#if selectedStatus.tags.length > 0}
                <div class="rules-page__detail-tags">
                  {#each selectedStatus.tags as tag}
                    <TagChip label={tag} tone="accent" />
                  {/each}
                </div>
              {:else}
                <p>상태 태그가 없습니다.</p>
              {/if}
            </div>
          {:else}
            <div class="rules-page__note">
              <p>ID: {selectedPassive?.id}</p>
              <p>
                {selectedPassive?.priority !== null
                  ? `우선순위 ${selectedPassive?.priority}`
                  : '우선순위가 없습니다.'}
              </p>
            </div>
          {/if}
        </div>
      {:else}
        <ContentStatePanel
          title={`No ${activeSectionMeta.label.toLowerCase()} to show`}
          message={emptyMessage}
        />
      {/if}
    </SectionFrame>
  </div>
</div>

<style>
  .rules-page,
  .rules-page__content,
  .rules-page__detail,
  .rules-page__note {
    display: grid;
    gap: 1.5rem;
  }

  .rules-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .rules-page__content {
    grid-template-columns: minmax(0, 1.15fr) minmax(20rem, 0.85fr);
    align-items: start;
  }

  .rules-page__section-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .rules-page__tab {
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

  .rules-page__tab--active {
    border-color: rgba(226, 193, 155, 0.38);
    background: rgba(226, 193, 155, 0.08);
    color: var(--color-accent);
  }

  .rules-page__reload {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .rules-page__detail h3,
  .rules-page__detail p,
  .rules-page__note p {
    margin: 0;
  }

  .rules-page__detail h3 {
    font-family: var(--font-display);
    font-size: 1.5rem;
  }

  .rules-page__detail > div:first-child p,
  .rules-page__note p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .rules-page__detail-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .rules-page__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .rules-page__attached {
    display: grid;
    gap: 0.85rem;
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .rules-page__attached-header,
  .rules-page__attached-card-header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
  }

  .rules-page__attached h4,
  .rules-page__attached h5,
  .rules-page__attached p {
    margin: 0;
  }

  .rules-page__attached h4 {
    font-family: var(--font-display);
    font-size: 1.05rem;
  }

  .rules-page__attached-list {
    display: grid;
    gap: 0.75rem;
  }

  .rules-page__attached-card {
    display: grid;
    gap: 0.55rem;
    padding: 0.85rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
  }

  .rules-page__attached-card p,
  .rules-page__attached > p {
    color: var(--color-text-soft);
    line-height: 1.55;
  }

  .rules-page__attached-meta {
    font-size: 0.78rem;
    letter-spacing: 0.04em;
  }

  @media (max-width: 960px) {
    .rules-page__stats,
    .rules-page__content {
      grid-template-columns: 1fr;
    }
  }
</style>
