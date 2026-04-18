<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import type { CombatRecentResultEntry } from './types'

  type Props = {
    loading: boolean
    errorMessage: string | null
    entries: readonly CombatRecentResultEntry[]
    onRetry: () => void
  }

  let { loading, errorMessage, entries, onRetry }: Props = $props()
</script>

<div class="combat-results-panel">
  <strong>Recent results</strong>

  {#if loading}
    <ContentStatePanel
      title="Loading recent results"
      message="Restoring the latest recent-result summary for this session."
    />
  {:else if errorMessage}
    <ContentStatePanel
      title="Recent results unavailable"
      message={errorMessage}
      tone="error"
      actionLabel="Retry results"
      onAction={onRetry}
    />
  {:else if entries.length > 0}
    <div class="combat-results-panel__feed-list">
      {#each entries as entry}
        <article class="combat-results-panel__feed-card">
          <strong>{entry.title}</strong>
          <p>{entry.summary}</p>
          <small>{entry.meta}</small>
        </article>
      {/each}
    </div>
  {:else}
    <ContentStatePanel
      title="No recent results"
      message="No recent result summary is available for this session yet."
    />
  {/if}
</div>

<style>
  .combat-results-panel,
  .combat-results-panel__feed-list {
    display: grid;
    gap: 0.75rem;
  }

  .combat-results-panel strong {
    color: var(--combat-tertiary, var(--color-success));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    border-bottom: 1px solid rgba(188, 204, 173, 0.18);
    padding-bottom: 0.35rem;
  }

  .combat-results-panel__feed-card {
    padding: 0.7rem 0.8rem;
    border: 1px solid var(--combat-border, var(--color-border));
    border-left: 3px solid rgba(188, 204, 173, 0.58);
    background: rgba(16, 14, 12, 0.64);
    display: grid;
    gap: 0.22rem;
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .combat-results-panel__feed-card:hover {
    transform: translateX(-2px);
    border-color: rgba(188, 204, 173, 0.42);
    background: rgba(33, 31, 29, 0.78);
  }

  .combat-results-panel__feed-card strong,
  .combat-results-panel__feed-card p,
  .combat-results-panel__feed-card small {
    margin: 0;
  }

  .combat-results-panel__feed-card p,
  .combat-results-panel__feed-card small {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.4;
    font-size: 0.84rem;
  }
</style>
