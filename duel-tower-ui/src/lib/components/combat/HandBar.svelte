<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import CombatHandCard from './CombatHandCard.svelte'
  import type { CommandOptionViewModel, ResolvedCombatCardViewModel } from './types'

  type Props = {
    handCards: readonly ResolvedCombatCardViewModel[]
    commandOptions: readonly CommandOptionViewModel[]
    commandPending: string | null
    selectedCardId: string | null
    selectedDiscardIds: readonly string[]
    selectedCommandType: string | null
    currentActorLabel: string
    visibleHandOwner: string | null
    selectedCardView: ResolvedCombatCardViewModel | null
    pendingDecisionType: string | null
    handExpanded: boolean
    catalogLoading: boolean
    emptyMessage: string
    onCommandButtonClick: (commandType: string) => void
    onToggleExpanded: () => void
    onSelectHandCard: (instanceId: string) => void
    onToggleDiscard: (instanceId: string) => void
    onHoverHandCard: (instanceId: string | null) => void
    onPinHandCard: (instanceId: string | null) => void
    resolveInspectState: (instanceId: string) => 'idle' | 'hovered' | 'pinned'
  }

  let {
    handCards,
    commandOptions,
    commandPending,
    selectedCardId,
    selectedDiscardIds,
    selectedCommandType,
    currentActorLabel,
    visibleHandOwner,
    selectedCardView,
    pendingDecisionType,
    handExpanded,
    catalogLoading,
    emptyMessage,
    onCommandButtonClick,
    onToggleExpanded,
    onSelectHandCard,
    onToggleDiscard,
    onHoverHandCard,
    onPinHandCard,
    resolveInspectState,
  }: Props = $props()

  function resolvePreviewCard() {
    return handCards.find((card) => resolveInspectState(card.instanceId) !== 'idle') ?? null
  }

  function resolveCardCostBadge(card: ResolvedCombatCardViewModel) {
    const tags = card.tags.map((tag) => tag.label)
    const costTag =
      tags.find((label) => /\b(cost|ap|mana|ex)\b/i.test(label)) ??
      tags.find((label) => /^\d+$/.test(label.trim())) ??
      tags.find((label) => /\d/.test(label))

    return costTag ?? null
  }

  const previewCard = $derived.by(() => resolvePreviewCard())
</script>

<SectionFrame
  title="Hand dock"
  description="Fast hand actions and selected-card context."
>
  <div class="hand-bar" class:hand-bar--collapsed={!handExpanded}>
    {#if previewCard && handExpanded}
      <section class="hand-bar__preview" aria-label="Card detail preview">
        <div class="hand-bar__preview-head">
          <strong>{previewCard.title}</strong>
          {#if resolveCardCostBadge(previewCard)}
            <span>{resolveCardCostBadge(previewCard)}</span>
          {/if}
        </div>

        <p>{previewCard.description || previewCard.subtitle}</p>

        {#if previewCard.meta}
          <small>{previewCard.meta}</small>
        {/if}

        {#if previewCard.tags.length > 0}
          <div class="hand-bar__preview-tags">
            {#each previewCard.tags.slice(0, 4) as tag}
              <span>{tag.label}</span>
            {/each}
          </div>
        {/if}
      </section>
    {/if}

    <div class="hand-bar__dock-meta">
      <div class="hand-bar__summary-strip">
        <button type="button" class="hand-bar__toggle" onclick={() => onToggleExpanded()}>
          {handExpanded ? 'Hide hand' : 'Show hand'}
        </button>

        <div class="hand-bar__selected-card">
          <strong>Selected</strong>
          <p>{selectedCardView?.title ?? 'No card'}</p>
          <span>{selectedCardView?.subtitle ?? visibleHandOwner ?? currentActorLabel}</span>
          {#if pendingDecisionType}
            <small>{pendingDecisionType}</small>
          {/if}
        </div>
      </div>

      <div class="hand-bar__quick-actions">
        {#if commandOptions.length > 0}
          {#each commandOptions as option}
            <button
              type="button"
              disabled={option.disabled || commandPending !== null}
              class:selected={selectedCommandType === option.id}
              onclick={() => onCommandButtonClick(option.id)}
            >
              {commandPending === option.id ? `${option.title}...` : option.title}
            </button>
          {/each}
        {:else}
          <span class="hand-bar__empty-chip">No quick actions available</span>
        {/if}
      </div>
    </div>

    {#if handExpanded}
      <div class="hand-bar__cards" role="list" aria-label="Visible hand cards">
        {#if handCards.length > 0}
          {#each handCards as card}
            <CombatHandCard
              {card}
              selected={selectedCardId === card.instanceId}
              discardSelected={selectedDiscardIds.includes(card.instanceId)}
              inspectState={resolveInspectState(card.instanceId)}
              onInspectHoverStart={() => onHoverHandCard(card.instanceId)}
              onInspectHoverEnd={() => onHoverHandCard(null)}
              onInspectPin={() => onPinHandCard(card.instanceId)}
              onSelect={onSelectHandCard}
              onToggleDiscard={onToggleDiscard}
            />
          {/each}
        {:else}
          <ContentStatePanel
            title="No visible hand yet"
            message={catalogLoading
              ? '손패를 불러오는 중입니다.'
              : emptyMessage}
          />
        {/if}
      </div>
    {/if}
  </div>
</SectionFrame>

<style>
  .hand-bar,
  .hand-bar__summary-strip,
  .hand-bar__preview,
  .hand-bar__preview-tags,
  .hand-bar__cards,
  .hand-bar__dock-meta,
  .hand-bar__selected-card {
    display: grid;
    gap: 0.5rem;
  }

  .hand-bar {
    position: relative;
    gap: 0.55rem;
    padding: 0.55rem 0.65rem;
    border: 1px solid rgba(226, 193, 155, 0.2);
    background:
      linear-gradient(180deg, rgba(21, 19, 17, 0.96), rgba(16, 14, 12, 0.95)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 -18px 60px rgba(0, 0, 0, 0.26);
  }

  .hand-bar__dock-meta {
    grid-template-columns: minmax(13rem, auto) minmax(0, 1fr);
    align-items: center;
    gap: 0.55rem;
  }

  .hand-bar__summary-strip {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 0.45rem;
    min-width: 0;
  }

  .hand-bar__preview {
    position: absolute;
    left: 0.7rem;
    bottom: calc(100% + 0.45rem);
    width: min(22rem, calc(100vw - 2rem));
    padding: 0.55rem 0.65rem;
    border: 1px solid rgba(226, 193, 155, 0.28);
    background: rgba(18, 16, 14, 0.96);
    box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.28);
    z-index: 4;
    pointer-events: none;
  }

  .hand-bar__preview-head {
    display: flex;
    justify-content: space-between;
    gap: 0.5rem;
    align-items: baseline;
  }

  .hand-bar__preview-head strong,
  .hand-bar__preview-head span,
  .hand-bar__preview p,
  .hand-bar__preview small {
    margin: 0;
  }

  .hand-bar__preview-head strong {
    font-family: var(--font-display);
    font-size: 0.95rem;
  }

  .hand-bar__preview-head span {
    padding: 0.12rem 0.35rem;
    border: 1px solid rgba(226, 193, 155, 0.24);
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.68rem;
    white-space: nowrap;
  }

  .hand-bar__preview p,
  .hand-bar__preview small {
    color: var(--combat-text-soft, var(--color-text-soft));
    font-size: 0.76rem;
    line-height: 1.3;
  }

  .hand-bar__preview-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }

  .hand-bar__preview-tags span {
    padding: 0.1rem 0.35rem;
    border: 1px solid rgba(152, 143, 135, 0.24);
    color: var(--combat-text-soft, var(--color-text-soft));
    font-size: 0.64rem;
    text-transform: uppercase;
  }

  .hand-bar__cards {
    grid-auto-flow: column;
    grid-auto-columns: minmax(5.6rem, 6.4rem);
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 0.1rem;
    align-items: stretch;
    scrollbar-width: thin;
  }

  .hand-bar__quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
    align-items: center;
  }

  .hand-bar__toggle,
  .hand-bar__selected-card,
  .hand-bar__quick-actions button {
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
  }

  .hand-bar__toggle {
    min-height: 1.85rem;
    padding: 0.35rem 0.55rem;
    color: var(--combat-text, var(--color-text));
    font-size: 0.74rem;
    white-space: nowrap;
  }

  .hand-bar__empty-chip {
    padding: 0.45rem 0.7rem;
    border: 1px dashed rgba(152, 143, 135, 0.32);
    color: var(--combat-text-soft, var(--color-text-soft));
    font-size: 0.82rem;
  }

  .hand-bar__selected-card {
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.45rem;
    padding: 0.45rem 0.6rem;
    min-width: 0;
  }

  .hand-bar__selected-card strong,
  .hand-bar__selected-card p,
  .hand-bar__selected-card span,
  .hand-bar__selected-card small {
    margin: 0;
  }

  .hand-bar__selected-card p,
  .hand-bar__selected-card span,
  .hand-bar__selected-card small {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.2;
  }

  .hand-bar__selected-card strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.68rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .hand-bar__selected-card p {
    font-family: var(--font-display);
    font-size: 0.92rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hand-bar__selected-card span,
  .hand-bar__selected-card small {
    font-size: 0.72rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hand-bar__selected-card small {
    justify-self: end;
  }

  .hand-bar--collapsed {
    gap: 0;
  }

  .hand-bar__quick-actions button {
    min-height: 1.85rem;
    padding: 0.35rem 0.55rem;
    color: var(--combat-text, var(--color-text));
    font-size: 0.78rem;
    white-space: nowrap;
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .hand-bar__quick-actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .hand-bar__quick-actions button:not(:disabled):active {
    transform: scale(0.98);
  }

  .hand-bar :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.38);
  }

  @media (max-width: 1080px) {
    .hand-bar__dock-meta {
      grid-template-columns: 1fr;
    }

    .hand-bar__summary-strip,
    .hand-bar__selected-card {
      grid-template-columns: 1fr;
    }

    .hand-bar__selected-card small {
      justify-self: start;
    }

    .hand-bar__preview {
      left: 0.55rem;
      right: 0.55rem;
      width: auto;
    }
  }

  @media (max-width: 720px) {
    .hand-bar {
      padding: 0.5rem 0.55rem;
    }

    .hand-bar__cards {
      grid-auto-columns: minmax(5.15rem, 5.8rem);
    }

    .hand-bar__quick-actions {
      overflow-x: auto;
      flex-wrap: nowrap;
      padding-bottom: 0.15rem;
    }
  }
</style>
