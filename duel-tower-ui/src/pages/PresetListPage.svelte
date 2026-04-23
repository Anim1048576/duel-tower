<script lang="ts">
  import { onMount } from 'svelte'
  import { listPresets } from '../lib/api/presets'
  import type { PresetResponse, PresetTimestampValue } from '../lib/api/presetTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { authState } from '../lib/auth/authState.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    presetListStateCopy,
    readPresetPageFeedback,
    type PresetPageFeedback,
  } from '../lib/presets/pageState'
  import {
    readSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type PresetListItem = {
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

  let selectedId = $state('')
  let loading = $state(true)
  let errorMessage = $state<string | null>(null)
  let feedback = $state<PresetPageFeedback | null>(null)
  let presets = $state<PresetResponse[]>([])
  let requestSequence = 0

  function getPresetId(preset: Pick<PresetResponse, 'id'>) {
    return String(preset.id)
  }

  function formatPresetTimestamp(value: PresetTimestampValue) {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
      const fallback = String(value).trim()
      return fallback || 'Unknown time'
    }

    return timestampFormatter.format(date)
  }

  function getPresetOwnerLabel(preset: PresetResponse) {
    const currentUsername = authState.user?.username?.trim()
    return currentUsername && currentUsername === preset.owner ? 'You' : preset.owner
  }

  function buildPresetMeta(preset: PresetResponse) {
    return `${getPresetOwnerLabel(preset)} | Updated ${formatPresetTimestamp(preset.updatedAt)}`
  }

  function buildPresetNote(preset: PresetResponse) {
    return `Character #${preset.characterId} | ${preset.deckCardIds.length} deck cards | ${preset.passiveIds.length} passives | EX ${preset.exCardId}`
  }

  function buildPresetTags(preset: PresetResponse) {
    const tags: PresetListItem['tags'] = [
      { label: `${preset.deckCardIds.length} Cards`, tone: 'muted' },
      { label: `${preset.passiveIds.length} Passives`, tone: 'accent' },
    ]

    if (authState.user?.username?.trim() === preset.owner) {
      tags.push({ label: 'Mine', tone: 'success' })
    }

    return tags
  }

  function toPresetListItem(preset: PresetResponse): PresetListItem {
    return {
      id: getPresetId(preset),
      title: preset.name,
      subtitle: `Owner ${getPresetOwnerLabel(preset)}`,
      meta: buildPresetMeta(preset),
      note: buildPresetNote(preset),
      tags: buildPresetTags(preset),
    }
  }

  function syncSelectedPreset(nextPresets: PresetResponse[]) {
    const nextIds = nextPresets.map(getPresetId)

    if (selectedId && nextIds.includes(selectedId)) {
      return
    }

    const handoffId = readSelectionHandoff(selectionHandoffKeys.presetId)

    if (handoffId && nextIds.includes(handoffId)) {
      selectedId = handoffId
      return
    }

    selectedId = nextIds[0] ?? ''
  }

  async function loadPresetArchive() {
    const requestId = ++requestSequence
    loading = true
    errorMessage = null

    try {
      const response = await listPresets()

      if (requestId !== requestSequence) {
        return
      }

      presets = response
      syncSelectedPreset(response)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      presets = []
      selectedId = ''
      errorMessage = getApiErrorMessage(error, 'Unable to load the preset archive.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function persistSelectedPreset(id: string) {
    if (!id) return
    setSelectionHandoff(selectionHandoffKeys.presetId, id)
  }

  function navigateTo(path: string) {
    if (typeof window === 'undefined') return

    window.history.pushState({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function openPresetEditor(id: string) {
    if (!id) return

    selectedId = id
    persistSelectedPreset(id)
    navigateTo(pathBuilders.presetEditor(id))
  }

  onMount(() => {
    feedback = readPresetPageFeedback()
    void loadPresetArchive()
  })

  const presetItems = $derived.by(() => presets.map(toPresetListItem))
  const selectedPreset = $derived.by(
    () => presets.find((preset) => getPresetId(preset) === selectedId) ?? presets[0] ?? null,
  )
  const uniqueCharacterCount = $derived.by(() => new Set(presets.map((preset) => preset.characterId)).size)
  const presetsWithPassivesCount = $derived.by(
    () => presets.filter((preset) => preset.passiveIds.length > 0).length,
  )
  const listSummary = $derived.by(() => {
    if (loading) return 'Loading preset archive...'
    if (errorMessage) return 'Preset archive could not be loaded.'
    if (!presets.length) return 'No presets are currently available.'
    return `${presets.length}개 프리셋 표시 중`
  })
  const emptyListMessage = $derived.by(() => 'No preset records are available yet.')
  const selectedPresetEditorPath = $derived.by(() =>
    selectedPreset ? pathBuilders.presetEditor(getPresetId(selectedPreset)) : pathBuilders.presetEditor(),
  )
</script>

<div class="list-page">
  <SectionFrame
    eyebrow="Preset Overview"
    title="프리셋 보관소"
    description="프리셋을 조회하고 편집합니다."
  >
    <div class="list-page__stats">
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : presets.length}
        label="Visible presets"
        note="Current preset API result"
      />
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : uniqueCharacterCount}
        label="Characters used"
        note="Unique character ids across presets"
      />
      <StatBlock
        value={loading ? '...' : errorMessage ? '-' : presetsWithPassivesCount}
        label="With passives"
        note="Presets with at least one passive"
      />
    </div>

    <div class="list-page__actions">
      <a class="list-page__link-action" data-nav href={pathBuilders.presetEditor()}>
        {presetListStateCopy.createActionLabel}
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
      title="프리셋 목록"
      description="프리셋을 선택합니다."
    >
      <p class="list-page__summary">{listSummary}</p>

      {#if loading}
        <ContentStatePanel
          title={presetListStateCopy.loadingTitle}
          message={presetListStateCopy.loadingMessage}
        />
      {:else if errorMessage}
        <ContentStatePanel
          title={presetListStateCopy.loadErrorTitle}
          message={errorMessage}
          tone="error"
          actionLabel="Retry load"
          onAction={() => void loadPresetArchive()}
        />
      {:else}
        <EntityListPane
          items={presetItems}
          selectedId={selectedId}
          onSelect={openPresetEditor}
          emptyMessage={presetListStateCopy.emptyMessage}
        />
      {/if}
    </SectionFrame>

    <SectionFrame
      title="선택된 프리셋"
      description="선택한 프리셋 요약입니다."
    >
      {#if loading}
        <ContentStatePanel
          title={presetListStateCopy.detailLoadingTitle}
          message={presetListStateCopy.detailLoadingMessage}
        />
      {:else if errorMessage}
        <ContentStatePanel
          title={presetListStateCopy.detailErrorTitle}
          message={errorMessage}
          tone="error"
        />
      {:else if selectedPreset}
        <div class="list-page__detail">
          <div>
            <h3>{selectedPreset.name}</h3>
            <p>{buildPresetMeta(selectedPreset)}</p>
          </div>

          <div class="list-page__detail-tags">
            {#each buildPresetTags(selectedPreset) as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{buildPresetNote(selectedPreset)}</p>
          <p>Created {formatPresetTimestamp(selectedPreset.createdAt)}</p>

          <a
            class="list-page__link-action"
            data-nav
            href={selectedPresetEditorPath}
            onclick={() => persistSelectedPreset(getPresetId(selectedPreset))}
          >
            Open editor for {selectedPreset.name}
          </a>
        </div>
      {:else}
        <ContentStatePanel
          title={presetListStateCopy.detailEmptyTitle}
          message={presetListStateCopy.emptyMessage}
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
