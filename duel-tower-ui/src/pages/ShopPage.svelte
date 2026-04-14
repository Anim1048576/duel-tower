<script lang="ts">
  import { onMount } from 'svelte'
  import { listItems } from '../lib/api/content'
  import { executeSessionCommand, getSessionStateAlias } from '../lib/api/sessions'
  import type { ItemDefinition } from '../lib/api/contentTypes'
  import type { SessionStateDto, RunInventoryItemDto } from '../lib/api/sessionTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    defaultShopCatalog,
    getShopOfferFallback,
    isEquipmentOfferRef,
    type ShopCatalogOffer,
  } from '../lib/session/shopCatalog'
  import {
    hasStoredSessionCode,
    isStoredGmSessionAccess,
    isStoredPlayerSessionAccess,
    readStoredSessionAccess,
    toSessionReadAccess,
    type StoredSessionAccess,
  } from '../lib/session/access'
  import {
    createLiveSessionPage,
  } from '../lib/session/liveSessionPage'
  import { syncSessionSelectionHandoff } from '../lib/session/sessionRuntime'
  import { readRequestedSessionCodeFromAccessOrHandoff } from '../lib/session/sessionRoute'

  type ShopFilterKey = 'all' | 'consumable' | 'equipment' | 'special'
  type ShopTone = 'accent' | 'muted' | 'success' | 'warning'
  type OfferCategory = Exclude<ShopFilterKey, 'all'>
  type ResolvedShopOffer = {
    offerId: string
    refId: string
    price: number
    stock: number
    bound: boolean
    name: string
    summary: string
    description: string
    tags: string[]
    category: OfferCategory
    source: 'item-api' | 'fallback'
  }

  const shopFilters = [
    { key: 'all', label: 'All', note: 'Entire expedition merchant catalog.', tone: 'accent' },
    { key: 'consumable', label: 'Consumables', note: 'Battle-use or repeatedly spent stock.', tone: 'success' },
    { key: 'equipment', label: 'Equipment', note: 'Weapons and persistent gear offers.', tone: 'warning' },
    { key: 'special', label: 'Special', note: 'Utility, ammo, or other reserve offers.', tone: 'muted' },
  ] satisfies Array<{ key: ShopFilterKey; label: string; note: string; tone: ShopTone }>

  let loading = $state(true)
  let errorMessage = $state<string | null>(null)
  let contextMessage = $state<string | null>(null)
  let accessNoticeMessage = $state<string | null>(null)
  let itemCatalogNoticeMessage = $state<string | null>(null)
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let runtimeAccess = $state<StoredSessionAccess | null>(null)
  let requestedSessionCode = $state<string | null>(null)
  let sessionState = $state<SessionStateDto | null>(null)
  let itemCatalog = $state<ItemDefinition[]>([])
  let selectedFilter = $state<ShopFilterKey>('all')
  let selectedOfferId = $state('')
  let purchasePendingOfferId = $state<string | null>(null)

  const goldFormatter = new Intl.NumberFormat('en-US')

  function formatGold(value: number | null | undefined) {
    return `${goldFormatter.format(typeof value === 'number' ? value : 0)} G`
  }

  function formatCount(value: number | null | undefined) {
    return goldFormatter.format(typeof value === 'number' ? value : 0)
  }

  function formatLabel(value: string | null | undefined, fallback = 'Unavailable') {
    const normalized = value?.trim()
    if (!normalized) return fallback
    return normalized
      .toLowerCase()
      .split(/[_-\s]+/)
      .filter(Boolean)
      .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
      .join(' ')
  }

  function getOfferCategory(offer: ShopCatalogOffer, itemDef: ItemDefinition | null): OfferCategory {
    if (isEquipmentOfferRef(offer.refId)) return 'equipment'
    const classifier = [itemDef?.id, ...(itemDef?.tags ?? [])].filter(Boolean).join(' ').toLowerCase()
    if (itemDef?.battleUsable || classifier.includes('consum')) return 'consumable'
    return classifier.includes('ammo') || classifier.includes('bundle') ? 'special' : 'special'
  }

  function getOfferTone(category: OfferCategory): ShopTone {
    return category === 'consumable' ? 'success' : category === 'equipment' ? 'warning' : 'accent'
  }

  function getOfferGlyph(refId: string, tags: readonly string[]) {
    const classifier = [refId, ...tags].join(' ').toLowerCase()
    if (classifier.includes('heal') || classifier.includes('potion')) return 'HP'
    if (classifier.includes('barrier') || classifier.includes('shield')) return 'BD'
    if (classifier.includes('ammo')) return 'AM'
    if (classifier.includes('weapon') || classifier.includes('equip')) return 'EQ'
    return 'SP'
  }

  function getInventoryGlyph(item: RunInventoryItemDto) {
    return getOfferGlyph(item.id, [item.entryType, ...item.tags])
  }

  function resolveOffer(offer: ShopCatalogOffer): ResolvedShopOffer {
    const itemDef = itemCatalog.find((item) => item.id === offer.refId) ?? null
    const fallback = getShopOfferFallback(offer.refId)
    const category = getOfferCategory(offer, itemDef)
    return {
      offerId: offer.offerId,
      refId: offer.refId,
      price: offer.price,
      stock: offer.stock,
      bound: offer.bound,
      name: itemDef?.name ?? fallback.name,
      summary: itemDef?.summary ?? fallback.summary,
      description: itemDef?.description ?? fallback.description,
      tags: Array.from(
        new Set([
          formatLabel(category),
          ...(itemDef?.tags ?? fallback.tags).slice(0, 3).map((tag) => formatLabel(tag)),
          ...(offer.bound ? ['Bound'] : []),
        ]),
      ),
      category,
      source: itemDef ? 'item-api' : 'fallback',
    }
  }

  function syncShopState(nextState: SessionStateDto | null) {
    sessionState = nextState
    if (requestedSessionCode) {
      syncSessionSelectionHandoff(requestedSessionCode)
    }
  }

  function isShopOpenState(nextState: SessionStateDto | null) {
    const nextRun = nextState?.run ?? null
    const nextNode = nextRun?.currentNode ?? null
    return Boolean(nextNode && nextNode.phase === 'EVENT' && !nextRun?.resultPending)
  }

  const shopPage = createLiveSessionPage<StoredSessionAccess | null>({
    readCode: () => readRequestedSessionCodeFromAccessOrHandoff({ storedAccess: readStoredSessionAccess() }).code,
    readAccess: () => readStoredSessionAccess(),
    canLoad: ({ code, access }) => Boolean(code && access),
    loadState: async (code) => {
      const [nextState, nextItems] = await Promise.allSettled([getSessionStateAlias(code), listItems()])

      if (nextState.status === 'rejected') {
        throw nextState.reason
      }

      if (nextItems.status === 'fulfilled') {
        itemCatalog = nextItems.value
        itemCatalogNoticeMessage = null
      } else {
        itemCatalog = []
        itemCatalogNoticeMessage = getApiErrorMessage(
          nextItems.reason,
          'Some item notes could not be loaded, so a few offers are shown with shorter fallback labels.',
        )
      }

      return nextState.value
    },
    getPollingAccess: toSessionReadAccess,
    canPoll: ({ code, access, state }) =>
      state.sessionCode === code &&
      access !== null &&
      hasStoredSessionCode(access, code) &&
      Boolean(toSessionReadAccess(access)) &&
      isShopOpenState(state),
    onBeforeLoad: ({ code, access }) => {
      runtimeAccess = access
      requestedSessionCode = code
      contextMessage = !code
        ? 'Open or rejoin a session first. The expedition shop follows the run you entered most recently.'
        : !access
          ? `Session ${code} is known, but this browser no longer has the access needed to reopen its merchant stop.`
          : null
      accessNoticeMessage =
        code && access
          ? isStoredPlayerSessionAccess(access)
            ? `Shopping as ${access.playerId} in session ${code}.`
            : isStoredGmSessionAccess(access)
              ? `Viewing the merchant in GM mode for session ${code}. Purchases stay disabled here.`
              : null
          : null
      loading = true
      errorMessage = null
      itemCatalogNoticeMessage = null
      actionErrorMessage = null
      actionSuccessMessage = null
      sessionState = null
      itemCatalog = []
    },
    onLoaded: (nextState) => {
      syncShopState(nextState)
    },
    onPolled: (nextState, { access }) => {
      runtimeAccess = access
      syncShopState(nextState)
    },
    onError: (error) => {
      errorMessage = getApiErrorMessage(error, 'The merchant catalog could not be loaded right now.')
    },
    onLoadSettled: () => {
      loading = false
    },
  })

  function stopShopPolling() {
    shopPage.stopPolling()
  }

  function updateShopPollingVersion(nextState: SessionStateDto) {
    shopPage.updatePollingVersion(nextState.version)
  }

  function startShopPolling(
    nextCode: string | null,
    nextAccess: StoredSessionAccess | null,
    nextState: SessionStateDto | null,
  ) {
    if (!nextState) {
      stopShopPolling()
      return
    }

    shopPage.startPolling(nextState, {
      code: nextCode,
      access: nextAccess,
    })
  }

  async function loadShop() {
    await shopPage.load()
  }

  async function handleBuySelectedOffer() {
    const selected = selectedOffer
    const access = toSessionReadAccess(runtimeAccess)
    const playerAccess = isStoredPlayerSessionAccess(runtimeAccess) ? runtimeAccess : null

    actionErrorMessage = null
    actionSuccessMessage = null

    if (!selected || !requestedSessionCode || !access || access.role !== 'player' || !playerAccess) {
      actionErrorMessage = 'Rejoin this session as a player before trying to buy from the merchant.'
      return
    }
    if (!shopOpen) {
      actionErrorMessage = 'The merchant only takes orders while the expedition is stopped on an event node.'
      return
    }
    if (typeof sessionState?.version !== 'number') {
      actionErrorMessage = 'The page is out of date. Refresh the shop and try again.'
      return
    }
    if (currentGold < selected.price) {
      actionErrorMessage = `Not enough gold for ${selected.name}.`
      return
    }

    purchasePendingOfferId = selected.offerId

    try {
      const response = await executeSessionCommand(
        requestedSessionCode,
        {
          type: 'BUY_SHOP_ITEM',
          expectedVersion: sessionState.version,
          playerId: playerAccess.playerId,
          offerId: selected.offerId,
          count: 1,
        },
        access,
      )

      if (response.state) {
        syncShopState(response.state)
        updateShopPollingVersion(response.state)

        if (!isShopOpenState(response.state)) {
          stopShopPolling()
        }
      }

      if (!response.accepted) {
        const message = response.errors.filter(Boolean).join(' ') || 'The expedition shop rejected the purchase command.'
        actionErrorMessage =
          response.errors.some((error) => error.includes('version mismatch')) && response.state
            ? `${message} The page synced to the latest shop state.`
            : message
        return
      }

      actionSuccessMessage = `${selected.name} was added to the expedition supplies for ${formatGold(selected.price)}.`
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, 'The purchase could not be completed right now.')
    } finally {
      purchasePendingOfferId = null
    }
  }

  function handleWindowStateChange() {
    void loadShop()
  }

  onMount(() => {
    void loadShop()
    window.addEventListener('popstate', handleWindowStateChange)

    return () => {
      shopPage.dispose()
      window.removeEventListener('popstate', handleWindowStateChange)
    }
  })

  const runState = $derived(sessionState?.run ?? null)
  const inventory = $derived(runState?.inventory ?? null)
  const currentNode = $derived(runState?.currentNode ?? null)
  const currentGold = $derived(inventory?.gold ?? 0)
  const shopOpen = $derived(Boolean(currentNode && currentNode.phase === 'EVENT' && !runState?.resultPending))
  const inventoryItems = $derived.by(() => [...(inventory?.items ?? [])].sort((left, right) => right.count - left.count || left.name.localeCompare(right.name)))
  const featuredItems = $derived.by(() => inventoryItems.slice(0, 5))
  const emptyFeaturedSlotCount = $derived.by(() => Math.max(0, 5 - featuredItems.length))
  const offerCatalog = $derived.by(() => defaultShopCatalog.map(resolveOffer))
  const filteredOffers = $derived.by(() => selectedFilter === 'all' ? offerCatalog : offerCatalog.filter((offer) => offer.category === selectedFilter))
  const selectedOffer = $derived.by(() => filteredOffers.find((offer) => offer.offerId === selectedOfferId) ?? filteredOffers[0] ?? null)
  const recentResults = $derived.by(() => runState?.recentResults.slice(0, 4) ?? [])
  const accessRoleLabel = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess) ? 'Current player' : isStoredGmSessionAccess(runtimeAccess) ? 'GM view' : 'No session access',
  )
  const viewerLabel = $derived.by(() =>
    isStoredPlayerSessionAccess(runtimeAccess) ? runtimeAccess.playerId : isStoredGmSessionAccess(runtimeAccess) ? 'GM view' : 'Unavailable',
  )
  const purchaseBlockedMessage = $derived.by(() => {
    if (!selectedOffer) return 'Select an offer to inspect its details.'
    if (!isStoredPlayerSessionAccess(runtimeAccess)) return 'Rejoin this session as a player to make purchases.'
    if (!shopOpen) return 'Buying opens only while the expedition is stopped on an event node.'
    if (currentGold < selectedOffer.price) return `This offer needs ${formatGold(selectedOffer.price)}, but the run currently holds ${formatGold(currentGold)}.`
    if (purchasePendingOfferId) return 'Sending the purchase request now.'
    return null
  })

  $effect(() => {
    const nextIds = filteredOffers.map((offer) => offer.offerId)
    if (selectedOfferId && nextIds.includes(selectedOfferId)) return
    selectedOfferId = nextIds[0] ?? ''
  })
</script>

<div class="shop-page">
  {#if loading}
    <SectionFrame eyebrow="Field Merchant" title="Expedition Shop" description="Opening the current merchant stop for the expedition you entered most recently.">
      <ContentStatePanel title="Loading merchant stop" message="Gathering expedition funds, carried stock, and the current offer list." />
    </SectionFrame>
  {:else if contextMessage}
    <SectionFrame eyebrow="Field Merchant" title="Shop context is unavailable" description="The merchant view follows a live session, so it needs a recent session entry before it can open.">
      <ContentStatePanel title="Session context required" message={contextMessage} />
      <div class="shop-page__actions">
        <a class="shop-page__link-action" data-nav href={pathBuilders.sessionEntry()}>Go to Session Entry</a>
        <a class="shop-page__link-action shop-page__link-action--muted" data-nav href={pathBuilders.inventory()}>Open inventory</a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame eyebrow="Field Merchant" title="Merchant stop could not be restored" description="The session is known, but the merchant catalog could not be refreshed.">
      <ContentStatePanel title="Could not load shop" message={errorMessage} tone="error" actionLabel="Try again" onAction={() => void loadShop()} />
    </SectionFrame>
  {:else if !runState}
    <SectionFrame eyebrow="Field Merchant" title="This run is not ready for the shop yet" description="A session exists, but the expedition route has not exposed a merchant-ready run state yet.">
      <ContentStatePanel title="Shop data unavailable" message="Return to Session Entry or refresh after the expedition route is fully restored." actionLabel="Refresh shop" onAction={() => void loadShop()} />
    </SectionFrame>
  {:else}
    <SectionFrame eyebrow="Field Merchant" title="Expedition Shop" description="Browse what the current merchant stop can offer and buy when the expedition is paused on an event node.">
      <div class="shop-page__hero">
        <div class="shop-page__hero-copy">
          <p class="shop-page__eyebrow">Merchant Console</p>
          <h3>Browse merchant offers without losing track of the current expedition</h3>
          <p>Use this screen to compare the current offer list with the supplies the party already carries. Buying opens only when the route is waiting at an event stop.</p>
        </div>
        <div class="shop-page__tag-row">
          <TagChip label={shopOpen ? 'Shop Open' : 'View Only'} tone={shopOpen ? 'success' : 'warning'} />
          <TagChip label={accessRoleLabel} tone={isStoredPlayerSessionAccess(runtimeAccess) ? 'accent' : 'muted'} />
        </div>
      </div>
      <div class="shop-page__stats">
        <StatBlock value={formatGold(currentGold)} label="Gold" note="Current expedition funds" />
        <StatBlock value={formatCount(inventory?.keys)} label="Keys" note="Keys still carried" />
        <StatBlock value={formatCount(inventory?.chests)} label="Chests" note="Chests still carried" />
        <StatBlock value={currentNode ? `F${currentNode.floor}` : '-'} label="Node" note={currentNode ? `${currentNode.name} | ${formatLabel(currentNode.phase)}` : 'No active route node'} />
      </div>
    </SectionFrame>
    <div class="shop-page__notice-row">
      {#if accessNoticeMessage}<ContentStatePanel message={accessNoticeMessage} />{/if}
      {#if itemCatalogNoticeMessage}<ContentStatePanel title="Some item notes are simplified" message={itemCatalogNoticeMessage} />{/if}
      {#if !shopOpen}<ContentStatePanel title="Merchant is between stops" message={`The route is currently at ${currentNode ? `${currentNode.name} (${formatLabel(currentNode.phase)})` : 'an unavailable node'}, so the catalog is visible but purchases stay closed until the next event stop.`} />{/if}
    </div>
    <div class="shop-page__layout">
      <section class="shop-page__panel">
        <div class="shop-page__panel-head">
          <div>
            <p class="shop-page__eyebrow">Current Supplies</p>
            <h4>Carried stock beside the merchant table</h4>
          </div>
          <span>{featuredItems.length} / 5 slots highlighted</span>
        </div>
        <div class="shop-page__supply-strip">
          {#each featuredItems as item}
            <div class="shop-page__supply-tile">
              <span>{getInventoryGlyph(item)}</span>
              <strong>{item.name}</strong>
              <small>x{formatCount(item.count)}</small>
            </div>
          {/each}
          {#each Array(emptyFeaturedSlotCount) as _, index (index)}
            <div class="shop-page__supply-tile shop-page__supply-tile--empty">
              <span>--</span>
              <strong>Empty</strong>
              <small>Reserve slot</small>
            </div>
          {/each}
        </div>
        <div class="shop-page__filters">
          {#each shopFilters as filter}
            <button type="button" class:selected={selectedFilter === filter.key} onclick={() => (selectedFilter = filter.key)}>
              <span>{filter.label}</span>
              <small>{formatCount(filter.key === 'all' ? offerCatalog.length : offerCatalog.filter((offer) => offer.category === filter.key).length)}</small>
            </button>
          {/each}
        </div>
        <div class="shop-page__panel-head">
          <div>
            <p class="shop-page__eyebrow">Offer Catalog</p>
            <h4>Available merchant offers</h4>
          </div>
          <span>{shopFilters.find((filter) => filter.key === selectedFilter)?.note}</span>
        </div>
        {#if filteredOffers.length === 0}
          <ContentStatePanel title="No offers in this category" message="Try another filter to browse the rest of the merchant table." />
        {:else}
          <div class="shop-page__offer-grid">
            {#each filteredOffers as offer}
              <button type="button" class="shop-page__offer-card" class:selected={selectedOffer?.offerId === offer.offerId} class:shop-page__offer-card--blocked={currentGold < offer.price} onclick={() => { selectedOfferId = offer.offerId; actionErrorMessage = null; actionSuccessMessage = null }}>
                <div class="shop-page__offer-top">
                  <span>{getOfferGlyph(offer.refId, offer.tags)}</span>
                  <TagChip label={formatLabel(offer.category)} tone={getOfferTone(offer.category)} />
                </div>
                <div class="shop-page__offer-copy">
                  <strong>{offer.name}</strong>
                  <p>{offer.summary}</p>
                </div>
                <div class="shop-page__offer-foot">
                  <span>{formatGold(offer.price)}</span>
                  <small>{offer.refId} | stock {formatCount(offer.stock)}</small>
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </section>
      <aside class="shop-page__panel">
        {#if selectedOffer}
          <div class="shop-page__detail-head">
            <span class="shop-page__detail-glyph">{getOfferGlyph(selectedOffer.refId, selectedOffer.tags)}</span>
            <div>
              <p class="shop-page__eyebrow">Offer Selected</p>
              <h4>{selectedOffer.name}</h4>
              <p>{selectedOffer.source === 'item-api' ? 'Expanded item notes available' : 'Compact fallback notes'}</p>
            </div>
          </div>
          <div class="shop-page__tag-row">
            <TagChip label={selectedOffer.offerId} tone="accent" />
            <TagChip label={selectedOffer.refId} tone="muted" />
            <TagChip label={formatGold(selectedOffer.price)} tone={currentGold >= selectedOffer.price ? 'success' : 'warning'} />
            {#if selectedOffer.bound}<TagChip label="Bound" tone="warning" />{/if}
          </div>
          <p class="shop-page__detail-copy">{selectedOffer.summary}</p>
          <p class="shop-page__detail-copy">{selectedOffer.description}</p>
          <div class="shop-page__detail-meta">
            <div><span>Viewer</span><strong>{viewerLabel}</strong></div>
            <div><span>Current node</span><strong>{currentNode ? `${currentNode.name} | ${formatLabel(currentNode.phase)}` : 'Unavailable'}</strong></div>
            <div><span>Stock</span><strong>{formatCount(selectedOffer.stock)}</strong></div>
            <div><span>Detail level</span><strong>{selectedOffer.source === 'item-api' ? 'Expanded entry' : 'Compact entry'}</strong></div>
          </div>
          <div class="shop-page__tag-row">
            {#each selectedOffer.tags as tag}<TagChip label={tag} tone="muted" />{/each}
          </div>
          {#if actionSuccessMessage}
            <div class="shop-page__feedback shop-page__feedback--success"><strong>Purchase complete</strong><p>{actionSuccessMessage}</p></div>
          {/if}
          {#if actionErrorMessage}
            <ContentStatePanel title="Could not complete purchase" message={actionErrorMessage} tone="error" />
          {/if}
          <div class="shop-page__purchase">
            <div>
              <p class="shop-page__eyebrow">Required Gold</p>
              <strong>{formatGold(selectedOffer.price)}</strong>
              <span>Current balance: {formatGold(currentGold)}</span>
            </div>
            <button type="button" disabled={Boolean(purchaseBlockedMessage)} onclick={() => void handleBuySelectedOffer()}>
              {purchasePendingOfferId === selectedOffer.offerId ? 'Purchasing...' : 'Buy now'}
            </button>
          </div>
          {#if purchaseBlockedMessage}<p class="shop-page__detail-copy">{purchaseBlockedMessage}</p>{/if}
          <div class="shop-page__recent-results">
            <div class="shop-page__panel-head">
              <div>
                <p class="shop-page__eyebrow">Recent Results</p>
                <h4>Recent expedition updates</h4>
              </div>
            </div>
            {#if recentResults.length === 0}
              <ContentStatePanel message="No recent expedition updates are recorded yet." />
            {:else}
              <ul>
                {#each recentResults as result}
                  <li><strong>{result.title}</strong><p>{result.summary}</p></li>
                {/each}
              </ul>
            {/if}
          </div>
        {/if}
      </aside>
    </div>
  {/if}
</div>

<style>
  .shop-page,
  .shop-page__notice-row,
  .shop-page__panel,
  .shop-page__hero-copy,
  .shop-page__offer-copy,
  .shop-page__detail-meta div,
  .shop-page__purchase > div,
  .shop-page__recent-results,
  .shop-page__recent-results ul,
  .shop-page__recent-results li {
    display: grid;
    gap: 0.85rem;
  }

  .shop-page { gap: 1.5rem; }
  .shop-page__hero,
  .shop-page__panel-head,
  .shop-page__offer-top,
  .shop-page__offer-foot,
  .shop-page__detail-head,
  .shop-page__purchase {
    display: flex;
    gap: 1rem;
    justify-content: space-between;
    align-items: flex-start;
  }

  .shop-page__eyebrow,
  .shop-page__panel span,
  .shop-page__purchase span,
  .shop-page__recent-results p,
  .shop-page__offer-copy p,
  .shop-page__detail-copy {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .shop-page__eyebrow {
    font-size: 0.72rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  .shop-page__hero-copy h3,
  .shop-page__panel h4,
  .shop-page__panel strong {
    margin: 0;
    font-family: var(--font-display);
  }

  .shop-page__tag-row,
  .shop-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .shop-page__stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0.9rem;
    margin-top: 1rem;
  }

  .shop-page__layout {
    display: grid;
    grid-template-columns: minmax(0, 1.6fr) minmax(19rem, 0.95fr);
    gap: 1.5rem;
  }

  .shop-page__panel {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.32);
    padding: 1.25rem;
  }

  .shop-page__supply-strip {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 0.75rem;
  }

  .shop-page__supply-tile,
  .shop-page__offer-card,
  .shop-page__detail-meta div,
  .shop-page__purchase > div,
  .shop-page__recent-results li {
    border: 1px solid var(--color-border);
    background: rgba(9, 8, 7, 0.4);
    padding: 0.85rem;
  }

  .shop-page__supply-tile {
    display: grid;
    gap: 0.45rem;
    min-height: 6.8rem;
  }

  .shop-page__supply-tile--empty { opacity: 0.55; }

  .shop-page__supply-tile span,
  .shop-page__offer-top > span,
  .shop-page__detail-glyph {
    width: 2.7rem;
    height: 2.7rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 1px solid rgba(226, 193, 155, 0.22);
    background: rgba(9, 8, 7, 0.52);
    color: var(--color-accent);
    font-size: 0.84rem;
    font-weight: 700;
    letter-spacing: 0.12em;
  }

  .shop-page__detail-glyph {
    width: 4rem;
    height: 4rem;
    flex-shrink: 0;
  }

  .shop-page__filters,
  .shop-page__offer-grid,
  .shop-page__detail-meta {
    display: grid;
    gap: 0.75rem;
  }

  .shop-page__filters {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .shop-page__filters button,
  .shop-page__offer-card,
  .shop-page__purchase button,
  .shop-page__link-action {
    text-align: left;
  }

  .shop-page__filters button,
  .shop-page__purchase button,
  .shop-page__link-action {
    border: 1px solid var(--color-border);
    background: transparent;
    padding: 0.75rem 0.9rem;
  }

  .shop-page__filters button.selected,
  .shop-page__offer-card.selected {
    border-color: rgba(226, 193, 155, 0.4);
    background: rgba(226, 193, 155, 0.08);
  }

  .shop-page__offer-card {
    display: grid;
    gap: 0.75rem;
    border-left: 3px solid rgba(226, 193, 155, 0.24);
  }

  .shop-page__offer-card--blocked { opacity: 0.78; }
  .shop-page__offer-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .shop-page__detail-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }

  .shop-page__feedback {
    border: 1px solid rgba(188, 204, 173, 0.3);
    background: rgba(188, 204, 173, 0.08);
    padding: 0.95rem;
  }

  .shop-page__feedback p { margin: 0; color: var(--color-text-soft); }

  .shop-page__purchase button,
  .shop-page__link-action {
    min-height: 3rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.22), rgba(199, 129, 121, 0.18));
    color: var(--color-text);
  }

  .shop-page__purchase button:disabled { opacity: 0.56; cursor: not-allowed; }
  .shop-page__link-action--muted { background: rgba(12, 11, 10, 0.22); border-color: var(--color-border); }

  .shop-page__recent-results ul { list-style: none; padding: 0; margin: 0; }

  @media (max-width: 1100px) {
    .shop-page__layout { grid-template-columns: 1fr; }
    .shop-page__offer-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  }

  @media (max-width: 760px) {
    .shop-page__stats,
    .shop-page__filters,
    .shop-page__offer-grid,
    .shop-page__detail-meta,
    .shop-page__supply-strip { grid-template-columns: 1fr; }
    .shop-page__purchase { flex-direction: column; }
  }
</style>
