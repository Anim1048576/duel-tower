<script lang="ts">
  import type { ResolvedCombatCardViewModel } from './types'

  type Props = {
    card: ResolvedCombatCardViewModel
    selected: boolean
    discardSelected: boolean
    inspectState?: 'idle' | 'hovered' | 'pinned'
    onSelect: (instanceId: string) => void
    onToggleDiscard: (instanceId: string) => void
    onInspectHoverStart?: () => void
    onInspectHoverEnd?: () => void
    onInspectPin?: () => void
  }

  let {
    card,
    selected,
    discardSelected,
    inspectState = 'idle',
    onSelect,
    onToggleDiscard,
    onInspectHoverStart,
    onInspectHoverEnd,
    onInspectPin,
  }: Props = $props()

  let longPressTimer: ReturnType<typeof setTimeout> | null = null
  let longPressTriggered = false

  function resolveCostBadge() {
    const labels = card.tags.map((tag) => tag.label)
    return (
      labels.find((label) => /\b(cost|ap|mana|ex)\b/i.test(label)) ??
      labels.find((label) => /^\d+$/.test(label.trim())) ??
      labels.find((label) => /\d/.test(label)) ??
      null
    )
  }

  const costBadge = $derived(resolveCostBadge())

  function clearLongPress() {
    if (longPressTimer) {
      clearTimeout(longPressTimer)
      longPressTimer = null
    }
  }

  function startLongPress() {
    clearLongPress()
    longPressTriggered = false
    longPressTimer = setTimeout(() => {
      longPressTriggered = true
      onInspectPin?.()
    }, 380)
  }

  function stopLongPress() {
    clearLongPress()
  }

  function handleCardClick() {
    if (longPressTriggered) {
      longPressTriggered = false
      return
    }

    onSelect(card.instanceId)
  }
</script>

<div
  class="combat-hand-card"
  class:combat-hand-card--selected={selected}
  class:combat-hand-card--discard-selected={discardSelected}
  class:combat-hand-card--unresolved={card.unresolved}
  class:combat-hand-card--hovered={inspectState === 'hovered'}
  class:combat-hand-card--pinned={inspectState === 'pinned'}
  role="button"
  tabindex="0"
  aria-pressed={selected}
  onclick={handleCardClick}
  onkeydown={(event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onSelect(card.instanceId)
    }
  }}
  onmouseenter={() => onInspectHoverStart?.()}
  onmouseleave={() => {
    stopLongPress()
    onInspectHoverEnd?.()
  }}
  onpointerdown={() => startLongPress()}
  onpointerup={() => stopLongPress()}
  onpointercancel={() => stopLongPress()}
>
  {#if costBadge}
    <span class="combat-hand-card__cost">{costBadge}</span>
  {/if}

  <div class="combat-hand-card__art" aria-hidden="true">
    <span>{card.unresolved ? '?' : card.title.slice(0, 2).toUpperCase()}</span>
  </div>

  <div class="combat-hand-card__copy">
    <h4>{card.title}</h4>
  </div>

  <div class="combat-hand-card__actions">
    <button
      type="button"
      class:selected={discardSelected}
      aria-label={discardSelected ? 'Unmark discard' : 'Mark discard'}
      onclick={(event) => {
        event.stopPropagation()
        onToggleDiscard(card.instanceId)
      }}
    >
      D
    </button>
    <button
      type="button"
      aria-label="Show card details"
      onclick={(event) => {
        event.stopPropagation()
        onInspectPin?.()
      }}
    >
      i
    </button>
  </div>
</div>

<style>
  .combat-hand-card,
  .combat-hand-card__copy {
    display: grid;
    gap: 0.32rem;
  }

  .combat-hand-card {
    position: relative;
    min-height: 7.25rem;
    align-content: start;
    padding: 0.38rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(180deg, rgba(55, 52, 50, 0.92), rgba(21, 19, 17, 0.96)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 12px 28px rgba(0, 0, 0, 0.2);
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      box-shadow 120ms ease;
  }

  .combat-hand-card:hover,
  .combat-hand-card--hovered {
    transform: translateY(-0.2rem);
    border-color: rgba(226, 193, 155, 0.42);
  }

  .combat-hand-card--selected {
    border-color: rgba(255, 179, 175, 0.72);
    box-shadow: var(--combat-focus, 0 0 0 1px rgba(255, 179, 175, 0.42)), 0 16px 34px rgba(0, 0, 0, 0.28);
  }

  .combat-hand-card--discard-selected {
    border-color: rgba(199, 167, 125, 0.5);
  }

  .combat-hand-card--pinned {
    border-color: rgba(226, 193, 155, 0.68);
  }

  .combat-hand-card--unresolved {
    filter: saturate(0.72);
    border-style: dashed;
  }

  .combat-hand-card__cost {
    position: absolute;
    top: 0.35rem;
    left: 0.35rem;
    z-index: 1;
    min-width: 1.6rem;
    padding: 0.08rem 0.28rem;
    border: 1px solid rgba(226, 193, 155, 0.28);
    background: rgba(16, 14, 12, 0.86);
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.62rem;
    font-weight: 700;
    text-align: center;
  }

  .combat-hand-card__art {
    position: relative;
    min-height: 4.05rem;
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
    inset: 0.3rem;
    border: 1px solid rgba(231, 225, 222, 0.08);
  }

  .combat-hand-card__art span {
    font-family: var(--font-display);
    font-size: 0.94rem;
    color: var(--combat-secondary, var(--color-accent));
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .combat-hand-card__copy h4 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 0.8rem;
    line-height: 1.1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .combat-hand-card__actions {
    display: flex;
    gap: 0.25rem;
    justify-content: flex-end;
  }

  .combat-hand-card__actions button {
    min-width: 1.55rem;
    min-height: 1.45rem;
    padding: 0.15rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.62);
    color: var(--combat-text, var(--color-text));
    font-size: 0.62rem;
    font-weight: 700;
    text-transform: uppercase;
  }

  .combat-hand-card__actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .combat-hand-card__actions :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.38);
  }
 </style>
