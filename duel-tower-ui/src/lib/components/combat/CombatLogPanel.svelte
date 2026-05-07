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

  let showDetails = $state(false)
  let showDebug = $state(false)

  function previewLines(lines: readonly string[]) {
    return lines.slice(0, 2)
  }

  function displayMessage(entry: CombatFeedEntry) {
    return entry.message ?? entry.lines[0] ?? ''
  }

  function detailLines(entry: CombatFeedEntry) {
    return entry.details?.length ? entry.details : entry.lines.slice(1)
  }

  function debugLines(entry: CombatFeedEntry) {
    return [
      `type: ${entry.type ?? entry.title}`,
      `version: ${entry.version ?? 'N/A'}`,
      `cursor: ${entry.cursor ?? 'N/A'}`,
      `createdAt: ${entry.createdAt ?? 'N/A'}`,
      `raw: ${JSON.stringify(entry.rawPayload ?? entry.data ?? {}, null, 2)}`,
    ]
  }
</script>

<div class="combat-log-panel">
  <div class="combat-log-panel__heading">
    <strong>{title}</strong>
    <div class="combat-log-panel__toggles">
      <button type="button" class:active={showDetails} onclick={() => (showDetails = !showDetails)}>
        Details
      </button>
      <button type="button" class:active={showDebug} onclick={() => (showDebug = !showDebug)}>
        Debug
      </button>
    </div>
  </div>

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
          <p>{displayMessage(entry)}</p>

          {#if showDetails && detailLines(entry).length > 0}
            <div class="combat-log-panel__detail-list">
              {#each detailLines(entry) as line}
                <p>{line}</p>
              {/each}
            </div>
          {:else if !showDetails && previewLines(detailLines(entry)).length > 0}
            {#each previewLines(detailLines(entry)) as line}
              <small>{line}</small>
            {/each}
            {#if detailLines(entry).length > 2}
              <small>+{detailLines(entry).length - 2} more details</small>
            {/if}
          {/if}

          {#if showDebug}
            <div class="combat-log-panel__debug-list">
              {#each debugLines(entry) as line}
                <pre>{line}</pre>
              {/each}
            </div>
          {/if}
        </article>
      {/each}
    </div>
  {:else}
    <ContentStatePanel title={emptyTitle} message={emptyMessage} />
  {/if}
</div>

<style>
  .combat-log-panel,
  .combat-log-panel__feed-list,
  .combat-log-panel__heading,
  .combat-log-panel__detail-list,
  .combat-log-panel__debug-list {
    display: grid;
    gap: 0.75rem;
  }

  .combat-log-panel__heading {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.5rem;
  }

  .combat-log-panel strong {
    color: var(--combat-tertiary, var(--color-success));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    border-bottom: 1px solid rgba(188, 204, 173, 0.18);
    padding-bottom: 0.35rem;
  }

  .combat-log-panel__toggles {
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
  }

  .combat-log-panel__toggles button {
    min-height: 1.65rem;
    padding: 0.25rem 0.5rem;
    border: 1px solid rgba(188, 204, 173, 0.24);
    background: rgba(16, 14, 12, 0.58);
    color: var(--combat-text-soft, var(--color-text-soft));
    font-size: 0.7rem;
  }

  .combat-log-panel__toggles button.active {
    border-color: rgba(188, 204, 173, 0.52);
    color: var(--combat-text, var(--color-text));
    background: rgba(188, 204, 173, 0.12);
  }

  .combat-log-panel__feed-card {
    padding: 0.7rem 0.8rem;
    border: 1px solid var(--combat-border, var(--color-border));
    border-left: 3px solid rgba(255, 179, 175, 0.54);
    background: rgba(16, 14, 12, 0.64);
    display: grid;
    gap: 0.22rem;
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
  .combat-log-panel__feed-card p,
  .combat-log-panel__feed-card small,
  .combat-log-panel__feed-card pre {
    margin: 0;
  }

  .combat-log-panel__feed-card p,
  .combat-log-panel__feed-card small {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.4;
    font-size: 0.84rem;
  }

  .combat-log-panel__detail-list {
    gap: 0.2rem;
    padding-top: 0.15rem;
  }

  .combat-log-panel__debug-list {
    gap: 0.25rem;
    padding: 0.45rem;
    border: 1px dashed rgba(152, 143, 135, 0.28);
    background: rgba(0, 0, 0, 0.18);
  }

  .combat-log-panel__feed-card pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    color: var(--combat-text-muted, var(--color-text-muted));
    font-size: 0.68rem;
    line-height: 1.35;
  }
</style>
