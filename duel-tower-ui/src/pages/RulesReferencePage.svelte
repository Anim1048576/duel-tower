<script lang="ts">
  import { onMount } from 'svelte'
  import { listKeywords, listPassives, listStatuses } from '../lib/api/content'
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
      emptyMessage: 'No keyword definitions are currently available.',
    },
    {
      key: 'statuses',
      label: 'Statuses',
      tone: 'warning',
      emptyMessage: 'No status definitions are currently available.',
    },
    {
      key: 'passives',
      label: 'Passives',
      tone: 'success',
      emptyMessage: 'No passive definitions are currently available.',
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

  let keywords = $state<KeywordDefinition[]>([])
  let statuses = $state<StatusDefinition[]>([])
  let passives = $state<PassiveDefinition[]>([])

  let selectedKeywordId = $state('')
  let selectedStatusId = $state('')
  let selectedPassiveId = $state('')

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
      note: keyword.parameterized ? 'Requires parameter input when referenced.' : 'Static keyword definition.',
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
        ? 'Persists after combat.'
        : 'Removed when combat resolution ends.',
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
      meta: getPassivePriorityLabel(passive.priority, 'No explicit priority registered'),
      note: 'Passive effect reference from the live content archive.',
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

  const selectedStatus = $derived.by(() =>
    selectedReference?.section === 'statuses' ? selectedReference.data : null,
  )

  const selectedPassive = $derived.by(() =>
    selectedReference?.section === 'passives' ? selectedReference.data : null,
  )
  const activeSectionMeta = $derived.by(() => getSectionMeta(activeSection))

  const currentSummary = $derived.by(() => {
    if (currentLoading) {
      return 'Loading live reference data...'
    }

    if (currentErrorMessage) {
      return 'The current reference section could not be restored.'
    }

    return `${currentItems.length} records visible in the current reference section.`
  })

  const emptyMessage = $derived.by(() => {
    if (query.trim()) {
      return 'No reference records matched the current filter.'
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
    description="Keywords, statuses, and passive definitions load from the live content API in a dedicated reference route, separate from the future inventory front."
  >
    <div class="rules-page__stats">
      <StatBlock value={keywords.length} label="Keywords" note="Live keyword definitions" />
      <StatBlock value={statuses.length} label="Statuses" note="Status and condition records" />
      <StatBlock value={passives.length} label="Passives" note="Passive effect references" />
    </div>
    {#if sectionErrors.length > 0}
      <ContentStatePanel
        title="Some reference sections are unavailable"
        message="One or more encyclopedia sections could not be restored from the content API."
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
      description="Use the section switcher and local filter to inspect the live tactical reference without mixing it with inventory terminology."
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
          message="Refreshing the current encyclopedia section from the content API."
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
      description="The same list-detail shell can absorb more encyclopedia sections later while keeping the reference route focused on rules data."
    >
      {#if currentLoading}
        <ContentStatePanel
          title="Preparing selected reference"
          message="The current encyclopedia selection is being summarized."
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
                  ? 'This keyword expects parameter input when used by cards or rules.'
                  : 'This keyword is defined as a static rule reference.'}
              </p>
            </div>
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
                <p>No status tags are registered for this definition.</p>
              {/if}
            </div>
          {:else}
            <div class="rules-page__note">
              <p>ID: {selectedPassive?.id}</p>
              <p>
                {selectedPassive?.priority !== null
                  ? `Priority ${selectedPassive?.priority} is registered for this passive.`
                  : 'No explicit priority is registered for this passive.'}
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

  @media (max-width: 960px) {
    .rules-page__stats,
    .rules-page__content {
      grid-template-columns: 1fr;
    }
  }
</style>
