<script lang="ts">
  import TagChip from '../TagChip.svelte'
  import type { ResolvedCombatCardViewModel } from './types'

  type Props = {
    card: ResolvedCombatCardViewModel
    selected: boolean
    discardSelected: boolean
    onSelect: (instanceId: string) => void
    onToggleDiscard: (instanceId: string) => void
    onInspectHoverStart?: () => void
    onInspectHoverEnd?: () => void
    onInspectPin?: () => void
  }

  let { card, selected, discardSelected, onSelect, onToggleDiscard, onInspectHoverStart, onInspectHoverEnd, onInspectPin }: Props = $props()
</script>

<div
  class="combat-hand-card"
  class:selected={selected || discardSelected}
  class:combat-hand-card--unresolved={card.unresolved}
  role="button"
  tabindex="0"
  onclick={() => onInspectPin?.()}
  onkeydown={(event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onInspectPin?.()
    }
  }}
  onmouseenter={() => onInspectHoverStart?.()}
  onmouseleave={() => onInspectHoverEnd?.()}
>
  <div class="combat-hand-card__art" aria-hidden="true">
    <span>{card.unresolved ? '?' : card.title.slice(0, 2).toUpperCase()}</span>
  </div>

  <div class="combat-hand-card__copy">
    <p>{card.subtitle}</p>
    <h4>{card.title}</h4>
    <span>{card.meta || 'Inspect for details'}</span>
  </div>

  <div class="combat-hand-card__tag-row">
    {#each card.tags as tag}
      <TagChip label={tag.label} tone={tag.tone} />
    {/each}
    {#if discardSelected}
      <TagChip label="Discard selected" tone="warning" />
    {/if}
  </div>

  <div class="combat-hand-card__actions">
    <button
      type="button"
      onclick={(event) => {
        event.stopPropagation()
        onSelect(card.instanceId)
      }}
    >
      {selected ? 'Selected card' : 'Select card'}
    </button>
    <button
      type="button"
      class:selected={discardSelected}
      onclick={(event) => {
        event.stopPropagation()
        onToggleDiscard(card.instanceId)
      }}
    >
      {discardSelected ? 'Marked discard' : 'Mark discard'}
    </button>
  </div>
</div>

<style>
  .combat-hand-card,
  .combat-hand-card__copy,
  .combat-hand-card__tag-row,
  .combat-hand-card__actions {
    display: grid;
    gap: 0.75rem;
  }

  .combat-hand-card {
    position: relative;
    min-height: 13.25rem;
    align-content: start;
    padding: 0.8rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(180deg, rgba(55, 52, 50, 0.9), rgba(21, 19, 17, 0.94)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 16px 38px rgba(0, 0, 0, 0.22);
    transition:
      transform 150ms ease,
      border-color 150ms ease,
      box-shadow 150ms ease,
      opacity 150ms ease;
  }

  .combat-hand-card:hover {
    transform: translateY(-0.45rem);
    border-color: rgba(226, 193, 155, 0.46);
  }

  .combat-hand-card.selected {
    transform: translateY(-0.8rem);
    border-color: rgba(255, 179, 175, 0.72);
    box-shadow: var(--combat-focus, 0 0 0 1px rgba(255, 179, 175, 0.42)), 0 22px 60px rgba(0, 0, 0, 0.34);
  }

  .combat-hand-card--unresolved {
    filter: saturate(0.72);
    border-style: dashed;
  }

  .combat-hand-card__art {
    position: relative;
    min-height: 5.4rem;
    display: grid;
    place-items: center;
    overflow: hidden;
    border: 1px solid rgba(226, 193, 155, 0.18);
    background:
      radial-gradient(circle at 50% 24%, rgba(255, 179, 175, 0.18), transparent 40%),
      linear-gradient(155deg, rgba(16, 14, 12, 0.5), rgba(33, 31, 29, 0.94)),
      repeating-linear-gradient(135deg, rgba(226, 193, 155, 0.08) 0 1px, transparent 1px 12px);
  }

  .combat-hand-card__art::after {
    content: '';
    position: absolute;
    inset: 0.45rem;
    border: 1px solid rgba(231, 225, 222, 0.08);
  }

  .combat-hand-card__art span {
    font-family: var(--font-display);
    font-size: 1.25rem;
    color: var(--combat-secondary, var(--color-accent));
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .combat-hand-card__tag-row,
  .combat-hand-card__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .combat-hand-card p,
  .combat-hand-card h4,
  .combat-hand-card span {
    margin: 0;
  }

  .combat-hand-card h4 {
    font-family: var(--font-display);
    font-size: 1rem;
  }

  .combat-hand-card p,
  .combat-hand-card span {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.45;
    font-size: 0.86rem;
  }

  .combat-hand-card__actions button {
    min-height: 2rem;
    padding: 0.45rem 0.65rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
    color: var(--combat-text, var(--color-text));
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .combat-hand-card__actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .combat-hand-card__actions button:not(:disabled):active {
    transform: scale(0.98);
  }

  .combat-hand-card :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.38);
  }

  @media (max-width: 720px) {
    .combat-hand-card {
      min-height: auto;
    }
  }
</style>
