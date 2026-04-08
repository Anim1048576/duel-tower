<script lang="ts">
  import TagChip from './TagChip.svelte'
  import type { PageTag } from '../navigation'

  type Props = {
    eyebrow?: string
    title: string
    description?: string
    tags?: PageTag[]
  }

  let { eyebrow, title, description, tags = [] }: Props = $props()
</script>

<header class="shell-header">
  <div class="shell-header__copy">
    {#if eyebrow}
      <p class="shell-header__eyebrow">{eyebrow}</p>
    {/if}

    <div class="shell-header__title-row">
      <h2>{title}</h2>

      {#if tags.length}
        <div class="shell-header__tags" aria-label="Page status">
          {#each tags as tag}
            <TagChip label={tag.label} tone={tag.tone} />
          {/each}
        </div>
      {/if}
    </div>

    {#if description}
      <p class="shell-header__description">{description}</p>
    {/if}
  </div>
</header>

<style>
  .shell-header {
    width: min(100%, var(--content-width));
    border-bottom: 1px solid var(--color-border);
    padding-bottom: 1.25rem;
  }

  .shell-header__copy {
    display: grid;
    gap: 0.7rem;
  }

  .shell-header__eyebrow {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  .shell-header__title-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.9rem;
  }

  .shell-header__title-row h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(2rem, 3vw, 2.8rem);
    line-height: 0.98;
  }

  .shell-header__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .shell-header__description {
    margin: 0;
    max-width: 48rem;
    color: var(--color-text-soft);
    line-height: 1.75;
  }
</style>
