<script lang="ts">
  import type { Snippet } from 'svelte'

  type Props = {
    eyebrow?: string
    title?: string
    description?: string
    children?: Snippet
  }

  let { eyebrow, title, description, children }: Props = $props()
</script>

<section class="section-frame">
  {#if eyebrow || title || description}
    <header class="section-frame__header">
      {#if eyebrow}
        <p class="section-frame__eyebrow">{eyebrow}</p>
      {/if}

      {#if title}
        <h3>{title}</h3>
      {/if}

      {#if description}
        <p class="section-frame__description">{description}</p>
      {/if}
    </header>
  {/if}

  <div class="section-frame__body">
    {@render children?.()}
  </div>
</section>

<style>
  .section-frame {
    border: 1px solid var(--color-border);
    background: var(--color-bg-panel-soft);
    padding: 1.25rem;
    display: grid;
    gap: 1rem;
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.12);
  }

  .section-frame__header {
    display: grid;
    gap: 0.5rem;
  }

  .section-frame__eyebrow {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  .section-frame__header h3 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 1.45rem;
  }

  .section-frame__description {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .section-frame__body {
    min-width: 0;
  }
</style>
