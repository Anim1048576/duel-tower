<script lang="ts">
  import TagChip from '../TagChip.svelte'
  import type {
    CombatActionButtonViewModel,
    CombatMetric,
    CombatTag,
  } from './types'

  type Props = {
    title: string
    subtitle: string
    metrics: readonly CombatMetric[]
    summaryLines?: readonly string[]
    tagRows?: readonly (readonly CombatTag[])[]
    actionButtons?: readonly CombatActionButtonViewModel[]
    activeTurn?: boolean
    variant?: 'default' | 'enemy'
    compactMetrics?: boolean
  }

  let {
    title,
    subtitle,
    metrics,
    summaryLines = [],
    tagRows = [],
    actionButtons = [],
    activeTurn = false,
    variant = 'default',
    compactMetrics = false,
  }: Props = $props()

  function getMetricFill(metric: CombatMetric) {
    const valueText = String(metric.value)
    const ratioMatch = /^(\d+(?:\.\d+)?)\/(\d+(?:\.\d+)?)/.exec(valueText)

    if (ratioMatch) {
      const current = Number(ratioMatch[1])
      const max = Number(ratioMatch[2])
      return max > 0 ? Math.max(0, Math.min(100, Math.round((current / max) * 100))) : 0
    }

    const limitMatch = /Limit\s+(\d+)/i.exec(metric.note)

    if (typeof metric.value === 'number' && limitMatch) {
      const max = Number(limitMatch[1])
      return max > 0 ? Math.max(0, Math.min(100, Math.round((metric.value / max) * 100))) : 0
    }

    return null
  }
</script>

<article
  class={`combat-entity-card combat-entity-card--${variant}`}
  class:combat-entity-card--active-turn={activeTurn}
  class:combat-entity-card--selected={actionButtons.some((action) => action.selected)}
>
  <div class="combat-entity-card__portrait" aria-hidden="true">
    <span>{variant === 'enemy' ? '!' : title.slice(0, 2).toUpperCase()}</span>
  </div>

  <div class="combat-entity-card__body">
    <div class="combat-entity-card__head">
      <div>
        <h3>{title}</h3>
        <p>{subtitle}</p>
      </div>

      {#if tagRows[0]?.length}
        <div class="combat-entity-card__tag-row">
          {#each tagRows[0] as tag}
            <TagChip label={tag.label} tone={tag.tone} />
          {/each}
        </div>
      {/if}
    </div>

    <div class:combat-entity-card__metric-grid--compact={compactMetrics} class="combat-entity-card__metric-grid">
      {#each metrics as metric}
        <div class="combat-entity-card__metric-card">
          <div class="combat-entity-card__metric-value">
            <strong>{metric.value}</strong>
            <span>{metric.label}</span>
          </div>
          <p>{metric.note}</p>
          <div class="combat-entity-card__meter" aria-hidden="true">
            <span style={`width: ${getMetricFill(metric) ?? 100}%`}></span>
          </div>
        </div>
      {/each}
    </div>

    {#each summaryLines as line}
      <p class="combat-entity-card__unit-note">{line}</p>
    {/each}

    {#each tagRows.slice(1) as tags}
      <div class="combat-entity-card__tag-row">
        {#each tags as tag}
          <TagChip label={tag.label} tone={tag.tone} />
        {/each}
      </div>
    {/each}

    {#if actionButtons.length > 0}
      <div class="combat-entity-card__action-buttons">
        {#each actionButtons as action}
          <button
            type="button"
            class:selected={action.selected}
            disabled={action.disabled}
            onclick={() => action.onClick()}
          >
            {action.label}
          </button>
        {/each}
      </div>
    {/if}
  </div>
</article>

<style>
  .combat-entity-card,
  .combat-entity-card__metric-card,
  .combat-entity-card__action-buttons button {
    border: 1px solid var(--combat-border, var(--color-border));
    background: var(--combat-surface-card, rgba(12, 11, 10, 0.28));
  }

  .combat-entity-card {
    position: relative;
    display: flex;
    gap: 1rem;
    padding: 1rem;
    overflow: hidden;
    transition:
      transform 150ms ease,
      border-color 150ms ease,
      box-shadow 150ms ease,
      background 150ms ease;
  }

  .combat-entity-card::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: linear-gradient(90deg, rgba(226, 193, 155, 0.08), transparent 38%);
    opacity: 0;
    transition: opacity 150ms ease;
  }

  .combat-entity-card--enemy {
    flex-direction: row-reverse;
    background: linear-gradient(120deg, rgba(55, 25, 24, 0.74), rgba(21, 19, 17, 0.9));
  }

  .combat-entity-card:hover {
    transform: translateY(-2px);
    border-color: rgba(226, 193, 155, 0.36);
    box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
  }

  .combat-entity-card:hover::before,
  .combat-entity-card--selected::before,
  .combat-entity-card--active-turn::before {
    opacity: 1;
  }

  .combat-entity-card--active-turn {
    border-color: rgba(188, 204, 173, 0.52);
    box-shadow: 0 0 0 1px rgba(188, 204, 173, 0.18), 0 20px 58px rgba(0, 0, 0, 0.34);
  }

  .combat-entity-card--selected {
    border-color: rgba(255, 179, 175, 0.62);
    box-shadow: var(--combat-focus, 0 0 0 1px rgba(255, 179, 175, 0.42));
  }

  .combat-entity-card__portrait {
    position: relative;
    flex: 0 0 5.25rem;
    min-height: 6.5rem;
    border: 2px solid rgba(226, 193, 155, 0.22);
    background:
      radial-gradient(circle at 50% 20%, rgba(226, 193, 155, 0.2), transparent 48%),
      linear-gradient(160deg, rgba(55, 52, 50, 0.9), rgba(16, 14, 12, 0.96));
    display: grid;
    place-items: center;
    color: var(--combat-secondary, var(--color-accent));
    font-family: var(--font-display);
    font-size: 1.35rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .combat-entity-card--enemy .combat-entity-card__portrait {
    border-color: rgba(255, 180, 171, 0.38);
    color: var(--combat-danger, var(--color-accent-strong));
    filter: saturate(0.9);
  }

  .combat-entity-card__portrait::after {
    content: '';
    position: absolute;
    inset: 0.45rem;
    border: 1px solid rgba(231, 225, 222, 0.08);
  }

  .combat-entity-card__body {
    min-width: 0;
    flex: 1 1 auto;
    display: grid;
    gap: 0.85rem;
  }

  .combat-entity-card__head {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
  }

  .combat-entity-card__head h3,
  .combat-entity-card__head p,
  .combat-entity-card__unit-note {
    margin: 0;
  }

  .combat-entity-card__head h3 {
    font-size: 1.16rem;
    font-family: var(--font-display);
    letter-spacing: 0.02em;
  }

  .combat-entity-card__head p,
  .combat-entity-card__unit-note,
  .combat-entity-card__metric-card p,
  .combat-entity-card__metric-card span {
    color: var(--combat-text-soft, var(--color-text-muted));
    line-height: 1.6;
  }

  .combat-entity-card__metric-grid,
  .combat-entity-card__tag-row,
  .combat-entity-card__action-buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .combat-entity-card__metric-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  }

  .combat-entity-card__metric-grid--compact {
    grid-template-columns: repeat(auto-fit, minmax(6rem, 1fr));
  }

  .combat-entity-card__metric-card {
    padding: 0.8rem;
    display: grid;
    gap: 0.35rem;
    background: rgba(16, 14, 12, 0.55);
  }

  .combat-entity-card__metric-value {
    display: flex;
    justify-content: space-between;
    gap: 0.6rem;
    align-items: baseline;
  }

  .combat-entity-card__metric-card strong {
    font-family: var(--font-display);
    font-size: 1.3rem;
    line-height: 1;
  }

  .combat-entity-card__metric-card span {
    font-size: 0.72rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .combat-entity-card__metric-card p {
    margin: 0;
    font-size: 0.8rem;
  }

  .combat-entity-card__meter {
    height: 0.25rem;
    background: rgba(16, 14, 12, 0.9);
    overflow: hidden;
  }

  .combat-entity-card__meter span {
    display: block;
    height: 100%;
    background: linear-gradient(90deg, var(--combat-primary, var(--color-accent-strong)), var(--combat-secondary, var(--color-accent)));
  }

  .combat-entity-card--enemy .combat-entity-card__meter span {
    background: linear-gradient(90deg, var(--combat-danger, var(--color-accent-strong)), rgba(255, 180, 171, 0.45));
  }

  .combat-entity-card__action-buttons button {
    min-height: 2.5rem;
    padding: 0.6rem 0.85rem;
    color: var(--combat-text, var(--color-text));
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .combat-entity-card__action-buttons button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .combat-entity-card__action-buttons button:not(:disabled):active {
    transform: scale(0.98);
  }

  .combat-entity-card :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.42);
  }

  @media (max-width: 620px) {
    .combat-entity-card,
    .combat-entity-card--enemy {
      flex-direction: column;
    }

    .combat-entity-card__portrait {
      min-height: 4.5rem;
      flex-basis: auto;
    }
  }
</style>
