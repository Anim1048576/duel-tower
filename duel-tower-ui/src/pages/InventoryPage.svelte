<script lang="ts">
  import { onMount } from 'svelte'
  import { getSessionInventory } from '../lib/api/sessions'
  import type { RunInventoryDto, RunInventoryItemDto } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    isStoredGmSessionAccess,
    isStoredPlayerSessionAccess,
    normalizeSessionCode,
    readStoredSessionAccess,
    toSessionReadAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import { readSelectionHandoff, selectionHandoffKeys } from '../lib/selectionHandoff'
  import { readRequestedSessionCodeFromAccessOrHandoff } from '../lib/session/sessionRoute'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'

  type SupplyCategoryKey = 'equipment' | 'consumable' | 'special'
  type SupplyTone = 'accent' | 'muted' | 'success' | 'warning'

  type SupplyCategoryDefinition = {
    key: SupplyCategoryKey
    title: string
    note: string
    tone: SupplyTone
    emptyMessage: string
  }

  const supplyCategories: SupplyCategoryDefinition[] = [
    {
      key: 'equipment',
      title: 'Equipment',
      note: '장비와 탄약입니다.',
      tone: 'warning',
      emptyMessage: '표시할 장비가 없습니다.',
    },
    {
      key: 'consumable',
      title: 'Consumables',
      note: '사용 가능한 소모품입니다.',
      tone: 'success',
      emptyMessage: '표시할 소모품이 없습니다.',
    },
    {
      key: 'special',
      title: 'Special stock',
      note: '기타 보급품입니다.',
      tone: 'accent',
      emptyMessage: '표시할 보급품이 없습니다.',
    },
  ]

  let loading = $state(true)
  let errorMessage = $state<string | null>(null)
  let inventoryVersion = $state<number | null>(null)
  let inventory = $state<RunInventoryDto | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let requestedSessionCode = $state<string | null>(null)
  let contextMessage = $state<string | null>(null)
  let accessNoticeMessage = $state<string | null>(null)
  let selectedItemId = $state('')
  let requestSequence = 0

  function normalizeClassifierValue(value: string | null | undefined) {
    return value?.trim().toLowerCase().replace(/[_-]+/g, ' ') ?? ''
  }

  function includesClassifier(haystack: string, tokens: readonly string[]) {
    return tokens.some((token) => haystack.includes(token))
  }

  function formatInventoryLabel(value: string | null | undefined, fallback = 'Unspecified') {
    const normalized = value?.trim()

    if (!normalized) {
      return fallback
    }

    return normalized
      .toLowerCase()
      .split(/[_-\s]+/)
      .filter(Boolean)
      .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
      .join(' ')
  }

  function getContextMessage(nextCode: string | null, nextAccess: StoredSessionAccess | null) {
    if (!nextCode) {
      return '먼저 세션에 참가해 주세요.'
    }

    if (!nextAccess) {
      return `Session ${nextCode} 접근 권한이 없습니다.`
    }

    if (!toSessionReadAccess(nextAccess)) {
      return `Session ${nextCode} 접근 정보가 부족합니다.`
    }

    return null
  }

  function getAccessNotice(nextCode: string | null, nextAccess: StoredSessionAccess | null) {
    const handoffCode = readSelectionHandoff(selectionHandoffKeys.sessionCode)
    const normalizedHandoff = handoffCode ? normalizeSessionCode(handoffCode) : null

    if (!nextCode || !nextAccess) {
      return null
    }

    if (normalizedHandoff && normalizedHandoff !== nextCode) {
      return `다른 세션 코드(${normalizedHandoff})가 대기 중입니다.`
    }

    if (isStoredPlayerSessionAccess(nextAccess)) {
      return `Viewing expedition supplies as ${nextAccess.playerId} in session ${nextCode}.`
    }

    if (isStoredGmSessionAccess(nextAccess)) {
      return `GM 모드로 Session ${nextCode} 보급품을 확인 중입니다.`
    }

    return null
  }

  function getSupplyCategory(item: RunInventoryItemDto): SupplyCategoryKey {
    const classifier = [item.entryType, ...item.tags]
      .map(normalizeClassifierValue)
      .filter(Boolean)
      .join(' ')

    if (
      item.inventoryEquipId ||
      item.loadedAmmo !== null ||
      item.maxLoadedAmmo !== null ||
      includesClassifier(classifier, [
        'equip',
        'equipment',
        'weapon',
        'armor',
        'armour',
        'shield',
        'gear',
        'relic',
        'ammo',
        'tool',
      ])
    ) {
      return 'equipment'
    }

    if (
      item.battleUsable ||
      includesClassifier(classifier, [
        'consum',
        'potion',
        'elixir',
        'scroll',
        'ration',
        'food',
        'bomb',
        'medicine',
        'usable',
      ])
    ) {
      return 'consumable'
    }

    return 'special'
  }

  function getSupplyCategoryDefinition(key: SupplyCategoryKey) {
    return supplyCategories.find((category) => category.key === key) ?? supplyCategories[0]
  }

  function getSupplyGlyph(item: RunInventoryItemDto) {
    const classifier = [item.entryType, ...item.tags]
      .map(normalizeClassifierValue)
      .filter(Boolean)
      .join(' ')

    if (includesClassifier(classifier, ['sword', 'weapon', 'blade'])) return 'WG'
    if (includesClassifier(classifier, ['shield', 'armor', 'armour'])) return 'AR'
    if (includesClassifier(classifier, ['ammo', 'bolt', 'arrow'])) return 'AM'
    if (includesClassifier(classifier, ['potion', 'elixir', 'medicine', 'heal'])) return 'HP'
    if (includesClassifier(classifier, ['food', 'ration', 'bread'])) return 'RT'
    if (item.battleUsable) return 'US'

    return getSupplyCategory(item) === 'equipment'
      ? 'EQ'
      : getSupplyCategory(item) === 'consumable'
        ? 'CN'
        : 'SP'
  }

  function compareSupplyItems(left: RunInventoryItemDto, right: RunInventoryItemDto) {
    const categoryOrder: Record<SupplyCategoryKey, number> = {
      equipment: 0,
      consumable: 1,
      special: 2,
    }

    const leftCategory = getSupplyCategory(left)
    const rightCategory = getSupplyCategory(right)

    return (
      categoryOrder[leftCategory] - categoryOrder[rightCategory] ||
      right.count - left.count ||
      left.name.localeCompare(right.name)
    )
  }

  function buildSupplyMeta(item: RunInventoryItemDto) {
    const parts = [formatInventoryLabel(item.entryType)]

    if (item.count > 1) {
      parts.push(`x${item.count}`)
    }

    if (item.inventoryEquipId) {
      parts.push('Gear-linked')
    }

    if (item.loadedAmmo !== null) {
      parts.push(
        item.maxLoadedAmmo !== null
          ? `Ammo ${item.loadedAmmo}/${item.maxLoadedAmmo}`
          : `Ammo ${item.loadedAmmo}`,
      )
    }

    return parts.join(' | ')
  }

  function buildSupplyNote(item: RunInventoryItemDto) {
    return item.summary?.trim() || item.description?.trim() || 'No supply note is available for this entry.'
  }

  function buildSupplyTags(item: RunInventoryItemDto) {
    const category = getSupplyCategory(item)
    const categoryDefinition = getSupplyCategoryDefinition(category)
    const tags: Array<{ label: string; tone?: SupplyTone }> = [
      { label: categoryDefinition.title, tone: categoryDefinition.tone },
    ]

    if (item.bound) {
      tags.push({ label: 'Bound', tone: 'warning' })
    }

    if (item.battleUsable) {
      tags.push({ label: 'Battle Use', tone: 'success' })
    }

    for (const tag of item.tags.slice(0, 2)) {
      tags.push({ label: formatInventoryLabel(tag), tone: 'muted' })
    }

    return tags
  }

  async function loadInventory() {
    const nextAccess = readStoredSessionAccess()
    const nextCode = readRequestedSessionCodeFromAccessOrHandoff({
      storedAccess: nextAccess,
    }).code
    const nextSessionAccess = toSessionReadAccess(nextAccess)
    const requestId = ++requestSequence

    runtimeAccess = nextAccess
    requestedSessionCode = nextCode
    contextMessage = getContextMessage(nextCode, nextAccess)
    accessNoticeMessage = getAccessNotice(nextCode, nextAccess)
    loading = true
    errorMessage = null
    inventory = null
    inventoryVersion = null

    if (!nextCode || !nextSessionAccess) {
      loading = false
      return
    }

    try {
      const response = await getSessionInventory(nextCode, nextSessionAccess)

      if (requestId !== requestSequence) {
        return
      }

      inventoryVersion = response.version
      inventory = response.inventory
      syncSessionSelectionHandoff(nextCode)
    } catch (error) {
      if (requestId !== requestSequence) {
        return
      }

      errorMessage = getApiErrorMessage(error, '보급품을 불러오지 못했습니다.')
    } finally {
      if (requestId === requestSequence) {
        loading = false
      }
    }
  }

  function handleSelectItem(id: string) {
    selectedItemId = id
  }

  onMount(() => {
    void loadInventory()
  })

  const inventoryItems = $derived.by(() => [...(inventory?.items ?? [])].sort(compareSupplyItems))
  const selectedItem = $derived.by(
    () => inventoryItems.find((item) => item.id === selectedItemId) ?? inventoryItems[0] ?? null,
  )
  const featuredItems = $derived.by(() => inventoryItems.slice(0, 5))
  const emptyFeaturedSlotCount = $derived.by(() => Math.max(0, 5 - featuredItems.length))
  const totalEntryCount = $derived.by(() => inventoryItems.length)
  const totalUnitCount = $derived.by(() =>
    inventoryItems.reduce((total, item) => total + Math.max(item.count, 0), 0),
  )
  const battleUseCount = $derived.by(() => inventoryItems.filter((item) => item.battleUsable).length)
  const boundCount = $derived.by(() => inventoryItems.filter((item) => item.bound).length)
  const viewerLabel = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess)
      ? runtimeAccess.playerId
      : isStoredGmSessionAccess(runtimeAccess)
        ? 'GM view'
        : 'Unavailable',
  )
  const accessRoleLabel = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess)
      ? 'Current player'
      : isStoredGmSessionAccess(runtimeAccess)
        ? 'GM view'
        : 'No session access',
  )
  const inventorySections = $derived.by(() =>
    supplyCategories.map((category) => ({
      ...category,
      items: inventoryItems.filter((item) => getSupplyCategory(item) === category.key),
    })),
  )

  $effect(() => {
    const nextIds = inventoryItems.map((item) => item.id)

    if (selectedItemId && nextIds.includes(selectedItemId)) {
      return
    }

    selectedItemId = nextIds[0] ?? ''
  })
</script>

<div class="inventory-page">
  {#if loading}
    <SectionFrame
      eyebrow="Supply Locker"
      title="Expedition Supply"
      description="보유 자원을 불러오는 중입니다."
    >
      <ContentStatePanel
        title="Loading supply locker"
        message="불러오는 중입니다."
      />
    </SectionFrame>
  {:else if contextMessage}
    <SectionFrame
      eyebrow="Supply Locker"
      title="Inventory context is unavailable"
      description="먼저 세션에 참가해 주세요."
    >
      <ContentStatePanel
        title="Session context required"
        message={contextMessage}
      />

      <div class="inventory-page__actions">
        <a class="inventory-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Go to Session Entry
        </a>
        <a class="inventory-page__link-action inventory-page__link-action--muted" data-nav href={pathBuilders.reference()}>
          Open Reference instead
        </a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame
      eyebrow="Supply Locker"
      title="Expedition supply could not be restored"
      description="보급품을 불러오지 못했습니다."
    >
      <ContentStatePanel
        title="Could not load supplies"
        message={errorMessage}
        tone="error"
        actionLabel="Try again"
        onAction={() => void loadInventory()}
      />

      <div class="inventory-page__actions">
        <a class="inventory-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
          Back to Session Entry
        </a>
      </div>
    </SectionFrame>
  {:else}
    <SectionFrame
      eyebrow="Supply Locker"
      title="Expedition Supply"
      description="현재 보유 자원을 확인합니다."
    >
      <div class="inventory-page__hero">
        <div class="inventory-page__hero-copy">
          <p>Expedition overview</p>
          <h3>현재 보유 자원을 확인합니다.</h3>
        </div>

        <div class="inventory-page__hero-tags">
          <TagChip label={accessRoleLabel} tone="accent" />
          <TagChip label={`Session ${requestedSessionCode ?? 'N/A'}`} tone="muted" />
          {#if inventoryVersion !== null}
            <TagChip label={`Sync ${inventoryVersion}`} tone="warning" />
          {/if}
        </div>
      </div>

      <div class="inventory-page__stats">
        <StatBlock value={inventory?.gold ?? 0} label="Gold" note="Current expedition currency" />
        <StatBlock value={inventory?.keys ?? 0} label="Keys" note="Keys carried into the run" />
        <StatBlock value={inventory?.chests ?? 0} label="Chests" note="Chest stock in the run state" />
        <StatBlock value={totalEntryCount} label="Entries" note={`${totalUnitCount} total stored units`} />
      </div>

      <div class="inventory-page__stats inventory-page__stats--secondary">
        <StatBlock value={battleUseCount} label="Battle-use" note="Entries flagged as battle usable" />
        <StatBlock value={boundCount} label="Bound stock" note="Entries locked to the current expedition" />
        <StatBlock value={viewerLabel} label="Viewer" note="Current access identity" />
      </div>

      {#if accessNoticeMessage}
        <ContentStatePanel title="Current viewing context" message={accessNoticeMessage} />
      {/if}
    </SectionFrame>

    <div class="inventory-page__main">
      <SectionFrame
        title="Supply tray"
        description="주요 보유 자원입니다."
      >
        {#if inventoryItems.length > 0}
          <div class="inventory-page__tray">
            {#each featuredItems as item}
              {@const category = getSupplyCategory(item)}
              {@const categoryDefinition = getSupplyCategoryDefinition(category)}
              <button
                type="button"
                class="inventory-page__tray-slot"
                class:inventory-page__tray-slot--selected={selectedItem?.id === item.id}
                onclick={() => handleSelectItem(item.id)}
              >
                <div class="inventory-page__tray-slot-head">
                  <TagChip label={categoryDefinition.title} tone={categoryDefinition.tone} />
                  <span>x{item.count}</span>
                </div>

                <div class="inventory-page__tray-glyph">{getSupplyGlyph(item)}</div>

                <div class="inventory-page__tray-copy">
                  <strong>{item.name}</strong>
                  <p>{buildSupplyMeta(item)}</p>
                </div>
              </button>
            {/each}

            {#each Array.from({ length: emptyFeaturedSlotCount }) as _, index}
              <div class="inventory-page__tray-slot inventory-page__tray-slot--empty">
                <div class="inventory-page__tray-glyph inventory-page__tray-glyph--empty">
                  0{index + 1}
                </div>
                <div class="inventory-page__tray-copy">
                  <strong>Empty slot</strong>
                  <p>Reserve compartment</p>
                </div>
              </div>
            {/each}
          </div>

          {#if selectedItem}
            {@const selectedCategory = getSupplyCategory(selectedItem)}
            {@const selectedCategoryDefinition = getSupplyCategoryDefinition(selectedCategory)}
            <div class="inventory-page__detail-ledger">
              <div class="inventory-page__detail-art">
                <span>{getSupplyGlyph(selectedItem)}</span>
              </div>

              <div class="inventory-page__detail-copy">
                <div class="inventory-page__detail-head">
                  <div>
                    <h3>{selectedItem.name}</h3>
                    <p>{buildSupplyMeta(selectedItem)}</p>
                  </div>

                  <div class="inventory-page__detail-value">
                    <span>Stored units</span>
                    <strong>x{selectedItem.count}</strong>
                  </div>
                </div>

                <div class="inventory-page__detail-tags">
                  <TagChip label={selectedCategoryDefinition.title} tone={selectedCategoryDefinition.tone} />
                  {#if selectedItem.bound}
                    <TagChip label="Bound to run" tone="warning" />
                  {/if}
                  {#if selectedItem.battleUsable}
                    <TagChip label="Battle usable" tone="success" />
                  {/if}
                  {#if selectedItem.loadedAmmo !== null}
                    <TagChip
                      label={selectedItem.maxLoadedAmmo !== null
                        ? `Ammo ${selectedItem.loadedAmmo}/${selectedItem.maxLoadedAmmo}`
                        : `Ammo ${selectedItem.loadedAmmo}`}
                      tone="muted"
                    />
                  {/if}
                </div>

                <div class="inventory-page__detail-grid">
                  <div class="inventory-page__detail-block">
                    <strong>Summary</strong>
                    <p>{selectedItem.summary ?? 'No short summary is stored for this entry.'}</p>
                  </div>

                  <div class="inventory-page__detail-block">
                    <strong>Description</strong>
                    <p>{selectedItem.description ?? '상세 설명이 없습니다.'}</p>
                  </div>

                  <div class="inventory-page__detail-block">
                    <strong>Tags</strong>
                    {#if selectedItem.tags.length > 0}
                      <div class="inventory-page__detail-tags">
                        {#each selectedItem.tags as tag}
                          <TagChip label={formatInventoryLabel(tag)} tone="muted" />
                        {/each}
                      </div>
                    {:else}
                      <p>No supply tags are stored for this entry.</p>
                    {/if}
                  </div>

                  <div class="inventory-page__detail-block">
                    <strong>Storage notes</strong>
                    <ul class="inventory-page__detail-list">
                      <li>Entry type: {formatInventoryLabel(selectedItem.entryType)}</li>
                      <li>
                        {selectedItem.inventoryEquipId
                          ? `Linked equipment id: ${selectedItem.inventoryEquipId}`
                          : 'Not linked to a stored equipment slot.'}
                      </li>
                      <li>
                        {selectedItem.bound
                          ? '현재 세션에 연결된 항목입니다.'
                          : 'This entry is not marked as bound.'}
                      </li>
                      <li>
                        {selectedItem.battleUsable
                          ? 'This entry is marked as usable during battle.'
                          : 'This entry is not marked as battle usable.'}
                      </li>
                    </ul>
                  </div>
                </div>

                <div class="inventory-page__actions">
                  <button type="button" disabled>Read-only view</button>
                  <button type="button" disabled>Actions unavailable</button>
                </div>
              </div>
            </div>
          {/if}
        {:else}
          <ContentStatePanel
            title="No stored supplies yet"
            message="표시할 항목이 없습니다."
          >
            <p>자원 수량은 위에서 확인할 수 있습니다.</p>
          </ContentStatePanel>
        {/if}
      </SectionFrame>

      <div class="inventory-page__sidebar">
        <SectionFrame
          title="Session context"
          description="최근 세션 기준으로 표시됩니다."
        >
          <div class="inventory-page__context-grid">
            <div class="inventory-page__context-row">
              <strong>Session</strong>
              <span>{requestedSessionCode ?? 'Unavailable'}</span>
            </div>
            <div class="inventory-page__context-row">
              <strong>Viewing mode</strong>
              <span>{accessRoleLabel}</span>
            </div>
            <div class="inventory-page__context-row">
              <strong>Viewer</strong>
              <span>{viewerLabel}</span>
            </div>
            <div class="inventory-page__context-row">
              <strong>Last sync</strong>
              <span>{inventoryVersion ?? 'Unavailable'}</span>
            </div>
          </div>

          <div class="inventory-page__actions">
            <a class="inventory-page__link-action" data-nav href={pathBuilders.sessionEntry()}>
              Switch session
            </a>
            <a class="inventory-page__link-action inventory-page__link-action--muted" data-nav href={pathBuilders.shop()}>
              Open shop
            </a>
          </div>
        </SectionFrame>

        {#each inventorySections as section}
          <SectionFrame title={section.title} description={section.note}>
            <EntityListPane
              items={section.items.map((item) => ({
                id: item.id,
                title: item.name,
                subtitle: buildSupplyNote(item),
                meta: buildSupplyMeta(item),
                note: item.description ?? '상세 설명이 없습니다.',
                tags: buildSupplyTags(item),
              }))}
              selectedId={selectedItemId}
              onSelect={handleSelectItem}
              emptyMessage={section.emptyMessage}
            />
          </SectionFrame>
        {/each}
      </div>
    </div>
  {/if}
</div>

<style>
  .inventory-page,
  .inventory-page__main,
  .inventory-page__sidebar,
  .inventory-page__detail-copy,
  .inventory-page__detail-grid,
  .inventory-page__detail-block,
  .inventory-page__context-grid {
    display: grid;
    gap: 1.5rem;
  }

  .inventory-page__hero {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .inventory-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 44rem;
  }

  .inventory-page__hero-copy p,
  .inventory-page__hero-copy h3,
  .inventory-page__detail-head h3,
  .inventory-page__detail-head p,
  .inventory-page__detail-value span,
  .inventory-page__detail-value strong,
  .inventory-page__detail-block strong,
  .inventory-page__detail-block p,
  .inventory-page__context-row strong,
  .inventory-page__context-row span {
    margin: 0;
  }

  .inventory-page__hero-copy p,
  .inventory-page__detail-value span,
  .inventory-page__detail-block strong,
  .inventory-page__context-row strong {
    color: var(--color-text-muted);
    font-size: 0.76rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .inventory-page__hero-copy h3,
  .inventory-page__detail-head h3 {
    font-family: var(--font-display);
    font-size: clamp(1.9rem, 2.4vw, 2.6rem);
    line-height: 1.12;
  }

  .inventory-page__hero-tags,
  .inventory-page__detail-tags,
  .inventory-page__actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
    align-items: center;
  }

  .inventory-page__stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1rem;
  }

  .inventory-page__stats--secondary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .inventory-page__main {
    grid-template-columns: minmax(0, 1.2fr) minmax(20rem, 0.8fr);
    align-items: start;
  }

  .inventory-page__tray {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 1rem;
  }

  .inventory-page__tray-slot {
    border: 1px solid var(--color-border);
    background:
      linear-gradient(180deg, rgba(20, 17, 16, 0.94), rgba(14, 12, 11, 0.98)),
      radial-gradient(circle at top, rgba(226, 193, 155, 0.08), transparent 62%);
    padding: 1rem;
    display: grid;
    gap: 0.9rem;
    color: var(--color-text);
    text-align: left;
    transition:
      border-color 160ms ease,
      transform 160ms ease;
  }

  .inventory-page__tray-slot:hover,
  .inventory-page__tray-slot--selected {
    border-color: rgba(255, 179, 175, 0.42);
    transform: translateY(-2px);
  }

  .inventory-page__tray-slot--empty {
    border-style: dashed;
    opacity: 0.72;
  }

  .inventory-page__tray-slot-head,
  .inventory-page__detail-head,
  .inventory-page__context-row {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: flex-start;
  }

  .inventory-page__tray-slot-head span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .inventory-page__tray-glyph {
    min-height: 9rem;
    border: 1px solid rgba(255, 255, 255, 0.06);
    background:
      linear-gradient(145deg, rgba(44, 38, 34, 0.36), rgba(10, 9, 8, 0.88)),
      repeating-linear-gradient(
        135deg,
        rgba(255, 255, 255, 0.025),
        rgba(255, 255, 255, 0.025) 6px,
        transparent 6px,
        transparent 14px
      );
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: var(--font-display);
    font-size: 2.4rem;
    letter-spacing: 0.08em;
    color: var(--color-secondary, var(--color-accent));
  }

  .inventory-page__tray-glyph--empty {
    font-size: 1.2rem;
    color: var(--color-text-muted);
  }

  .inventory-page__tray-copy {
    display: grid;
    gap: 0.35rem;
  }

  .inventory-page__tray-copy strong {
    font-family: var(--font-display);
    font-size: 1.15rem;
  }

  .inventory-page__tray-copy p,
  .inventory-page__detail-head p,
  .inventory-page__detail-block p,
  .inventory-page__context-row span,
  .inventory-page__detail-list li {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .inventory-page__detail-ledger {
    display: grid;
    grid-template-columns: minmax(13rem, 0.65fr) minmax(0, 1.35fr);
    margin-top: 1.5rem;
    border: 1px solid var(--color-border);
    background:
      linear-gradient(180deg, rgba(27, 24, 22, 0.98), rgba(18, 16, 15, 0.98)),
      repeating-linear-gradient(
        transparent,
        transparent 31px,
        rgba(152, 143, 135, 0.08) 31px,
        rgba(152, 143, 135, 0.08) 32px
      );
  }

  .inventory-page__detail-art {
    min-height: 20rem;
    background: rgba(55, 52, 50, 0.46);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 2rem;
    font-family: var(--font-display);
    font-size: clamp(3rem, 6vw, 5rem);
    color: rgba(226, 193, 155, 0.42);
  }

  .inventory-page__detail-copy {
    padding: 1.5rem;
  }

  .inventory-page__detail-value {
    display: grid;
    gap: 0.3rem;
    text-align: right;
  }

  .inventory-page__detail-value strong {
    font-family: var(--font-display);
    font-size: 2rem;
    line-height: 1;
  }

  .inventory-page__detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
    padding-block: 1rem;
    border-top: 1px solid rgba(152, 143, 135, 0.16);
    border-bottom: 1px solid rgba(152, 143, 135, 0.16);
  }

  .inventory-page__detail-list {
    margin: 0;
    padding-left: 1.15rem;
    display: grid;
    gap: 0.45rem;
  }

  .inventory-page__link-action,
  .inventory-page__actions button {
    min-height: 3rem;
    width: fit-content;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .inventory-page__link-action--muted,
  .inventory-page__actions button:disabled {
    border-color: var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text-muted);
  }

  @media (max-width: 1180px) {
    .inventory-page__main {
      grid-template-columns: 1fr;
    }

    .inventory-page__tray {
      grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
    }
  }

  @media (max-width: 960px) {
    .inventory-page__stats,
    .inventory-page__stats--secondary,
    .inventory-page__detail-grid,
    .inventory-page__detail-ledger {
      grid-template-columns: 1fr;
    }
  }
</style>
