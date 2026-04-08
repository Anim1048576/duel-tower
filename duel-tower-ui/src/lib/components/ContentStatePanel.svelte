<script lang="ts">
  import type { Snippet } from 'svelte'

  type StateTone = 'default' | 'error'

  type Props = {
    title?: string
    message: string
    tone?: StateTone
    actionLabel?: string
    onAction?: (() => void) | undefined
    children?: Snippet
  }

  let {
    title,
    message,
    tone = 'default',
    actionLabel,
    onAction,
    children,
  }: Props = $props()
</script>

<div class={`content-state-panel content-state-panel--${tone}`}>
  <div class="content-state-panel__copy">
    {#if title}
      <h4>{title}</h4>
    {/if}

    <p>{message}</p>
  </div>

  {#if children}
    <div class="content-state-panel__detail">
      {@render children()}
    </div>
  {/if}

  {#if actionLabel && onAction}
    <button type="button" class="content-state-panel__action" onclick={() => onAction()}>
      {actionLabel}
    </button>
  {/if}
</div>

<style>
  .content-state-panel {
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    display: grid;
    gap: 0.85rem;
  }

  .content-state-panel--error {
    border-color: rgba(199, 129, 121, 0.38);
  }

  .content-state-panel__copy,
  .content-state-panel__detail {
    display: grid;
    gap: 0.45rem;
  }

  .content-state-panel__copy h4,
  .content-state-panel__copy p,
  .content-state-panel__detail :global(p) {
    margin: 0;
  }

  .content-state-panel__copy h4 {
    font-size: 0.95rem;
    font-weight: 600;
  }

  .content-state-panel__copy p,
  .content-state-panel__detail :global(p) {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .content-state-panel__action {
    min-height: 2.75rem;
    width: fit-content;
    padding: 0.65rem 0.95rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }
</style>
