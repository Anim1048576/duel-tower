<script lang="ts">
  import { onMount } from 'svelte'
  import { getCard } from '../lib/api/content'
  import type { CardDetailResponse } from '../lib/api/contentTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import PlaySpecSummary from '../lib/components/PlaySpecSummary.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import {
    buildCardDisplayTags,
    formatContentEnumLabel,
    getCardTypeLabel,
    getCardTypeTone,
  } from '../lib/content/display'
  import { normalizePath, pathBuilders, resolveRouteMatch } from '../lib/navigation'

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

  function getReturnToPath() {
    if (typeof window === 'undefined') {
      return pathBuilders.cardLibrary()
    }

    const returnTo = new URLSearchParams(window.location.search).get('returnTo')

    if (!returnTo) {
      return pathBuilders.cardLibrary()
    }

    try {
      const url = new URL(returnTo, window.location.origin)

      if (url.origin !== window.location.origin || normalizePath(url.pathname) !== pathBuilders.cardLibrary()) {
        return pathBuilders.cardLibrary()
      }

      return `${pathBuilders.cardLibrary()}${url.search}${url.hash}`
    } catch {
      return pathBuilders.cardLibrary()
    }
  }

  const requestedCardId = getCardIdFromRoute()
  const cardId = hasCardId(requestedCardId) ? requestedCardId.trim() : null
  const invalidRouteId = requestedCardId !== null && !hasCardId(requestedCardId) ? requestedCardId : null
  const returnToPath = getReturnToPath()

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
  const stateTitle = $derived.by(() => {
    if (notFound) {
      return 'Card not found'
    }

    if (errorMessage) {
      return 'Unable to load card detail'
    }

    return 'Card detail unavailable'
  })
  const stateMessage = $derived.by(() => {
    if (notFound) {
      return '요청한 카드를 찾을 수 없습니다.'
    }

    if (errorMessage) {
      return errorMessage
    }

    return '카드 목록에서 카드를 열어 주세요.'
  })
</script>

<div class="card-detail-page">
  {#if loading}
    <SectionFrame
      eyebrow="Card Record"
      title="Card detail"
      description="View the selected card record."
    >
      <ContentStatePanel
        title="Loading card detail"
        message="Restoring the requested card record."
      />
    </SectionFrame>
  {:else if card}
    <SectionFrame
      eyebrow="Selected Card"
      title={card.name}
      description="카드의 정체성과 플레이 조건을 읽습니다."
    >
      <div class="card-detail-page__reader">
        <article class="card-detail-page__preview">
          <div class="card-detail-page__preview-chrome">
            <strong>{card.cost ?? 'N/A'}</strong>
            <span>{getCardTypeLabel(card.type)}</span>
          </div>

          <div class="card-detail-page__preview-art">
            <p>{card.id}</p>
          </div>

          <div class="card-detail-page__preview-copy">
            <p>{getCardTypeLabel(card.type)}</p>
            <h3>{card.name}</h3>
            <p>{card.description || '카드 설명이 없습니다.'}</p>
          </div>

          <div class="card-detail-page__hero-tags">
            <TagChip label={getCardTypeLabel(card.type)} tone={getCardTypeTone(card.type)} />
            {#if card.token}
              <TagChip label="Token Link" tone="warning" />
            {/if}
          </div>
        </article>

        <aside class="card-detail-page__quick-facts" aria-label="Card quick facts">
          <StatBlock value={card.cost ?? 'N/A'} label="Cost" note="카드 비용" />
          <StatBlock value={keywordCount} label="Keywords" note="연결된 키워드" />
          <StatBlock
            value={formatContentEnumLabel(card.resolveTo)}
            label="Resolve To"
            note="카드 처리 대상"
          />

          <div class="card-detail-page__field">
            <h3>Identifier</h3>
            <p>{card.id}</p>
          </div>
        </aside>
      </div>
    </SectionFrame>

    <div class="card-detail-page__grid">
      <SectionFrame
        title="Effect text"
        description="카드 효과와 연결 키워드입니다."
      >
        <div class="card-detail-page__section">
          <div class="card-detail-page__field">
            <h3>Effect</h3>
            <p>{card.description || '카드 설명이 없습니다.'}</p>
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
              <p>연결된 키워드가 없습니다.</p>
            {/if}
          </div>
        </div>
      </SectionFrame>

      <SectionFrame
        title="Play rules"
        description="사용자가 읽을 수 있는 play spec 요약입니다."
      >
        <div class="card-detail-page__section">
          <div class="card-detail-page__field">
            <h3>Resolve target</h3>
            <p>{formatContentEnumLabel(card.resolveTo, '처리 대상이 없습니다.')}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Token link</h3>
            <p>{card.token ? 'Registered' : '연결된 토큰이 없습니다.'}</p>
          </div>

          <div class="card-detail-page__field">
            <h3>Play spec summary</h3>
            <PlaySpecSummary playSpec={card.playSpec} />
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Archive actions"
      description="카드 목록으로 돌아갈 수 있습니다."
    >
      <div class="card-detail-page__actions">
        <a class="card-detail-page__link-action" data-nav href={returnToPath}>
          Back to card library
        </a>
      </div>

      <div class="card-detail-page__note">
        <p>카드 식별자와 규칙 정보를 확인합니다.</p>
      </div>
    </SectionFrame>
  {:else}
    <SectionFrame
      eyebrow="Card Record"
      title="Card detail"
      description="View the selected card record."
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
          <p>카드 목록에서 다시 열어 주세요.</p>
        {:else if !errorMessage}
          <p>현재 경로에 카드 id가 없습니다.</p>
          <p>카드 목록에서 카드를 열어 주세요.</p>
        {/if}
      </ContentStatePanel>

      <div class="card-detail-page__actions">
        <a class="card-detail-page__link-action" data-nav href={returnToPath}>
          Back to card library
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

  .card-detail-page__reader {
    display: grid;
    grid-template-columns: minmax(17rem, 0.95fr) minmax(18rem, 1.05fr);
    gap: 1.25rem;
    align-items: stretch;
  }

  .card-detail-page__preview,
  .card-detail-page__quick-facts {
    border: 1px solid var(--color-border);
    background:
      linear-gradient(180deg, rgba(20, 17, 16, 0.9), rgba(14, 12, 11, 0.96)),
      radial-gradient(circle at top, rgba(226, 193, 155, 0.08), transparent 60%);
    padding: 1rem;
    display: grid;
    gap: 1rem;
  }

  .card-detail-page__preview {
    align-content: start;
  }

  .card-detail-page__quick-facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-content: start;
  }

  .card-detail-page__quick-facts .card-detail-page__field {
    grid-column: 1 / -1;
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .card-detail-page__preview-chrome {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: center;
  }

  .card-detail-page__preview-chrome strong,
  .card-detail-page__preview-chrome span {
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

  .card-detail-page__preview-chrome strong {
    font-family: var(--font-display);
    font-size: 1rem;
    letter-spacing: 0.04em;
  }

  .card-detail-page__preview-art {
    min-height: 13.5rem;
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

  .card-detail-page__preview-art p {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    word-break: break-all;
  }

  .card-detail-page__preview-copy {
    display: grid;
    gap: 0.55rem;
  }

  .card-detail-page__preview-copy p,
  .card-detail-page__preview-copy h3,
  .card-detail-page__field h3,
  .card-detail-page__field p,
  .card-detail-page__note p {
    margin: 0;
  }

  .card-detail-page__preview-copy > p:first-child,
  .card-detail-page__field h3 {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .card-detail-page__preview-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.9rem, 2.8vw, 2.6rem);
    line-height: 1.1;
  }

  .card-detail-page__preview-copy > p:last-child {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .card-detail-page__hero-tags,
  .card-detail-page__keyword-list,
  .card-detail-page__actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
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
    .card-detail-page__reader,
    .card-detail-page__grid {
      grid-template-columns: 1fr;
    }

    .card-detail-page__quick-facts {
      grid-template-columns: 1fr;
    }
  }
</style>

