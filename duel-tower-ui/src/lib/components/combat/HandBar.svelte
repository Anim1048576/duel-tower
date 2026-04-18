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
    catalogLoading: boolean
    emptyMessage: string
    onCommandButtonClick: (commandType: string) => void
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
    catalogLoading,
    emptyMessage,
    onCommandButtonClick,
    onSelectHandCard,
    onToggleDiscard,
    onHoverHandCard,
    onPinHandCard,
    resolveInspectState,
  }: Props = $props()
</script>

<SectionFrame
  title="Hand dock"
  description="The bottom dock keeps hand actions fast and pushes detailed reading toward the inspector and command context."
>
  <div class="hand-bar">
    <div class="hand-bar__dock-meta">
      <div class="hand-bar__selected-card">
        <strong>Selected card</strong>
        <p>{selectedCardView?.title ?? 'No card selected'}</p>
        <span>{selectedCardView?.subtitle ?? `${visibleHandOwner ?? 'No visible owner'} | ${currentActorLabel}`}</span>
        {#if pendingDecisionType}
          <small>Pending: {pendingDecisionType}</small>
        {/if}
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
            ? 'Loading the card archive before resolving live hand cards.'
            : emptyMessage}
        />
      {/if}
    </div>
  </div>
</SectionFrame>

<style>
  .hand-bar,
  .hand-bar__cards,
  .hand-bar__dock-meta,
  .hand-bar__selected-card {
    display: grid;
    gap: 0.75rem;
  }

  .hand-bar {
    gap: 0.85rem;
    padding: 0.85rem;
    border: 1px solid rgba(226, 193, 155, 0.2);
    background:
      linear-gradient(180deg, rgba(21, 19, 17, 0.96), rgba(16, 14, 12, 0.95)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 -18px 60px rgba(0, 0, 0, 0.26);
  }

  .hand-bar__dock-meta {
    grid-template-columns: minmax(15rem, 0.9fr) minmax(0, 1.8fr);
    align-items: center;
  }

  .hand-bar__cards {
    grid-auto-flow: column;
    grid-auto-columns: minmax(8.75rem, 10rem);
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 0.2rem;
    align-items: stretch;
    scrollbar-width: thin;
  }

  .hand-bar__quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .hand-bar__selected-card,
  .hand-bar__quick-actions button {
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
  }

  .hand-bar__empty-chip {
    padding: 0.45rem 0.7rem;
    border: 1px dashed rgba(152, 143, 135, 0.32);
    color: var(--combat-text-soft, var(--color-text-soft));
    font-size: 0.82rem;
  }

  .hand-bar__selected-card {
    padding: 0.7rem 0.85rem;
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
    line-height: 1.4;
  }

  .hand-bar__selected-card strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .hand-bar__selected-card p {
    font-family: var(--font-display);
    font-size: 1.05rem;
  }

  .hand-bar__quick-actions button {
    min-height: 2.1rem;
    padding: 0.45rem 0.7rem;
    color: var(--combat-text, var(--color-text));
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
  }

  @media (max-width: 720px) {
    .hand-bar {
      padding: 0.7rem;
    }

    .hand-bar__cards {
      grid-auto-columns: minmax(7.6rem, 8.6rem);
    }

    .hand-bar__quick-actions {
      overflow-x: auto;
      flex-wrap: nowrap;
      padding-bottom: 0.15rem;
    }
  }
</style>
