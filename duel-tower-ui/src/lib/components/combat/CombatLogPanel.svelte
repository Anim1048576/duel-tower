<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import type { CombatFeedEntry } from './types'

  type Props = {
    title: string
    loading: boolean
    loadingTitle: string
    loadingMessage: string
    errorTitle: string
    errorMessage: string | null
    retryLabel: string
    emptyTitle: string
    emptyMessage: string
    entries: readonly CombatFeedEntry[]
    onRetry: () => void
  }

  let {
    title,
    loading,
    loadingTitle,
    loadingMessage,
    errorTitle,
    errorMessage,
    retryLabel,
    emptyTitle,
    emptyMessage,
    entries,
    onRetry,
  }: Props = $props()
</script>

<div class="combat-log-panel">
  <strong>{title}</strong>

  {#if loading}
    <ContentStatePanel title={loadingTitle} message={loadingMessage} />
  {:else if errorMessage}
    <ContentStatePanel
      title={errorTitle}
      message={errorMessage}
      tone="error"
      actionLabel={retryLabel}
      onAction={onRetry}
    />
  {:else if entries.length > 0}
    <div class="combat-log-panel__feed-list">
      {#each entries as entry}
        <article class="combat-log-panel__feed-card">
          <strong>{entry.title}</strong>
          {#each entry.lines as line}
            <p>{line}</p>
          {/each}
        </article>
      {/each}
    </div>
  {:else}
    <ContentStatePanel title={emptyTitle} message={emptyMessage} />
  {/if}
</div>

<style>
  .combat-log-panel,
  .combat-log-panel__feed-list {
    display: grid;
    gap: 1rem;
  }

  .combat-log-panel strong {
    color: var(--combat-tertiary, var(--color-success));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    border-bottom: 1px solid rgba(188, 204, 173, 0.18);
    padding-bottom: 0.35rem;
  }

  .combat-log-panel__feed-card {
    padding: 0.9rem;
    border: 1px solid var(--combat-border, var(--color-border));
    border-left: 3px solid rgba(255, 179, 175, 0.54);
    background: rgba(16, 14, 12, 0.64);
    display: grid;
    gap: 0.3rem;
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .combat-log-panel__feed-card:hover {
    transform: translateX(-2px);
    border-color: rgba(226, 193, 155, 0.4);
    background: rgba(33, 31, 29, 0.78);
  }

  .combat-log-panel__feed-card strong,
  .combat-log-panel__feed-card p {
    margin: 0;
  }

  .combat-log-panel__feed-card p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }
</style>
