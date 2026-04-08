<script lang="ts">
  import { onMount } from 'svelte'
  import { getCard } from '../lib/api/content'
  import type { CardDetailResponse } from '../lib/api/contentTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import {
    buildCardDisplayTags,
    formatContentEnumLabel,
    getCardTypeLabel,
    getCardTypeTone,
  } from '../lib/content/display'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'

  function hasCardId(value: string | null | undefined): value is string {
    return typeof value === 'string' && value.trim().length > 0
  }

  function getCardIdFromRoute() {
    if (typeof window === 'undefined') return null

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key !== 'card-detail') {
      return null
    }

    return match.params.id ?? null
  }

  function formatPlaySpec(playSpec: CardDetailResponse['playSpec']) {
    if (playSpec === null) {
      return null
    }

    if (typeof playSpec === 'string') {
      const normalized = playSpec.trim()
      return normalized.length > 0 ? normalized : null
    }

    try {
      return JSON.stringify(playSpec, null, 2)
    } catch {
      return 'Play spec is present but could not be rendered.'
    }
  }

  const requestedCardId = getCardIdFromRoute()
  const cardId = hasCardId(requestedCardId) ? requestedCardId.trim() : null
  const invalidRouteId = requestedCardId !== null && !hasCardId(requestedCardId) ? requestedCardId : null

  let loading = $state(cardId !== null)
  let notFound = $state(invalidRouteId !== null)
  let errorMessage = $state<string | null>(null)
  let card = $state<CardDetailResponse | null>(null)

  async function loadCardDetail(id: string) {
    loading = true
    notFound = false
    errorMessage = null

    try {
      card = await getCard(id)
    } catch (error) {
      card = null

      if (error instanceof ApiError && error.code === 'not_found') {
        notFound = true
        return
      }

      errorMessage = getApiErrorMessage(error, 'Unable to load the card record.')
    } finally {
      loading = false
    }
  }

  onMount(() => {
    if (!cardId) {
      loading = false
      return
    }

    void loadCardDetail(cardId)
  })

  const keywordCount = $derived.by(() => card?.keywords.length ?? 0)
  const formattedPlaySpec = $derived.by(() => (card ? formatPlaySpec(card.playSpec) : null))
  const hasStructuredPlaySpec = $derived.by(
    () => card?.playSpec !== null && typeof card?.playSpec === 'object',
  )
  const stateTitle = $derived.by(() => {
    if (notFound) {
      return 'Card not found'
    }

    if (errorMessage) {
      return 'Unable to load card detail'
    }

    return 'Card selection unavailable'
  })
  const stateMessage = $derived.by(() => {
    if (notFound) {
      return 'The requested card could not be found in the current archive.'
    }

    if (errorMessage) {
      return errorMessage
    }

    return 'Open a card from the archive or use a valid card detail URL.'
  })
</script>

<div class="card-detail-page">
  {#if loading}
    <SectionFrame
      eyebrow="Card Record"
      title="Card detail"
      description="View the selected live card record."
    >
      <ContentStatePanel
        title="Loading card detail"
        message="Restoring the requested card record from the content API."
      />
    </SectionFrame>
  {:else if card}
    <SectionFrame
      eyebrow="Selected Card"
      title={card.name}
      description="Card detail is resolved from the URL id and loaded from the live content API."
    >
      <div class="card-detail-page__hero">
        <div class="card-detail-page__hero-copy">
          <p>{getCardTypeLabel(card.type)}</p>
          <h3>{card.description || 'No card description is available for this record.'}</h3>
        </div>

        <div class="card-detail-page__hero-tags">
          <TagChip label={getCardTypeLabel(card.type)} tone={getCardTypeTone(card.type)} />
          {#if card.keywords.length > 0}
            <TagChip label={`${card.keywords.length} Keywords`} tone="accent" />
          {/if}
          {#if card.token}
            <TagChip label="Token Link" tone="warning" />
          {/if}
        </div>
      </div>

      <div class="card-detail-page__stats">
        <StatBlock value={card.cost ?? 'N/A'} label="Cost" note="Card cost from the content registry" />
        <StatBlock value={keywordCount} label="Keywords" note="Keyword references linked to this card" />
        <StatBlock
          value={formatContentEnumLabel(card.resolveTo)}
          label="Resolve To"
          note="Zone or destination resolved by the card"
        />
      </div>
    </SectionFrame>

    <div class="card-detail-page__grid">
      <SectionFrame
        title="Card profile"
        description="Core fields from the selected CardDetailResponse record."
      >
        <div class="card-detail-page__section">
          <div class="card-detail-page__field">
            <h3>Identifier</h3>
            <p>{card.id}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Description</h3>
            <p>{card.description || 'No description is currently stored for this card.'}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Keywords</h3>
            {#if card.keywords.length > 0}
              <div class="card-detail-page__keyword-list">
                {#each buildCardDisplayTags(card).filter((tag) => tag.tone === 'accent') as keywordTag}
                  <TagChip label={keywordTag.label} tone={keywordTag.tone} />
                {/each}
                {#each card.keywords.slice(2) as keyword}
                  <TagChip label={keyword} tone="accent" />
                {/each}
              </div>
            {:else}
              <p>No keyword references are linked to this card.</p>
            {/if}
          </div>
        </div>
      </SectionFrame>

      <SectionFrame
        title="Resolution and play spec"
        description="This section shows resolve targets and play-spec payload without extra rule interpretation."
      >
        <div class="card-detail-page__section">
          <div class="card-detail-page__field">
            <h3>Resolve target</h3>
            <p>{formatContentEnumLabel(card.resolveTo, 'No resolve target is registered for this card.')}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Token link</h3>
            <p>{card.token ?? 'No linked token is registered for this card.'}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Play spec</h3>
            {#if formattedPlaySpec}
              {#if hasStructuredPlaySpec}
                <pre class="card-detail-page__play-spec">{formattedPlaySpec}</pre>
              {:else}
                <p>{formattedPlaySpec}</p>
              {/if}
            {:else}
              <p>No play-spec payload is currently stored for this card.</p>
            {/if}
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Archive actions"
      description="This read-only record keeps the same fields the next deck picker and card reference flows can reuse."
    >
      <div class="card-detail-page__actions">
        <a class="card-detail-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to card archive
        </a>
      </div>

      <div class="card-detail-page__note">
        <p>Identifier, keywords, and play-spec stay visible here so the next deck step can reuse the same content contract.</p>
      </div>
    </SectionFrame>
  {:else}
    <SectionFrame
      eyebrow="Card Record"
      title="Card detail"
      description="View the selected live card record."
    >
      <ContentStatePanel
        title={stateTitle}
        message={stateMessage}
        tone={errorMessage ? 'error' : 'default'}
        actionLabel={errorMessage && cardId ? 'Retry load' : undefined}
        onAction={errorMessage && cardId ? () => void loadCardDetail(cardId) : undefined}
      >
        {#if notFound}
          <p>Requested id: {invalidRouteId ?? cardId}</p>
          <p>Check the card archive and open a valid card record.</p>
        {:else if !errorMessage}
          <p>No card id was found in the current route.</p>
          <p>Open a card from the archive to restore the expected detail context.</p>
        {/if}
      </ContentStatePanel>

      <div class="card-detail-page__actions">
        <a class="card-detail-page__link-action" data-nav href={pathBuilders.deckList()}>
          Back to card archive
        </a>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .card-detail-page,
  .card-detail-page__grid,
  .card-detail-page__section,
  .card-detail-page__note {
    display: grid;
    gap: 1.5rem;
  }

  .card-detail-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .card-detail-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .card-detail-page__hero-copy p,
  .card-detail-page__hero-copy h3,
  .card-detail-page__field h3,
  .card-detail-page__field p,
  .card-detail-page__note p {
    margin: 0;
  }

  .card-detail-page__hero-copy p,
  .card-detail-page__field h3 {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .card-detail-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .card-detail-page__hero-tags,
  .card-detail-page__keyword-list,
  .card-detail-page__actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .card-detail-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .card-detail-page__grid {
    grid-template-columns: minmax(0, 1.05fr) minmax(19rem, 0.95fr);
  }

  .card-detail-page__field {
    display: grid;
    gap: 0.5rem;
  }

  .card-detail-page__field p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .card-detail-page__play-spec {
    margin: 0;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    color: var(--color-text-soft);
    font: 0.88rem/1.6 'Fira Code', 'Consolas', monospace;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .card-detail-page__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .card-detail-page__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .card-detail-page__link-action {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .card-detail-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  @media (max-width: 960px) {
    .card-detail-page__stats,
    .card-detail-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>

