<script lang="ts">
  import ContentStatePanel from './ContentStatePanel.svelte'
  import TagChip from './TagChip.svelte'

  export type EntityListTag = {
    label: string
    tone?: 'accent' | 'muted' | 'success' | 'warning'
  }

  export type EntityListItem = {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: readonly EntityListTag[]
  }

  type Props = {
    items: readonly EntityListItem[]
    selectedId?: string
    emptyMessage?: string
    onSelect?: (id: string) => void
  }

  let {
    items,
    selectedId,
    emptyMessage = '표시할 목록이 없습니다.',
    onSelect,
  }: Props = $props()
</script>

<div class="entity-list-pane">
  {#if items.length}
    <ul>
      {#each items as item}
        <li>
          <button
            type="button"
            class:selected={item.id === selectedId}
            onclick={() => onSelect?.(item.id)}
          >
            <div class="entity-list-pane__row">
              <div class="entity-list-pane__copy">
                <strong>{item.title}</strong>

                {#if item.subtitle}
                  <span>{item.subtitle}</span>
                {/if}

                {#if item.meta}
                  <p>{item.meta}</p>
                {/if}
              </div>

              {#if item.tags?.length}
                <div class="entity-list-pane__tags">
                  {#each item.tags as tag}
                    <TagChip label={tag.label} tone={tag.tone} />
                  {/each}
                </div>
              {/if}
            </div>

            {#if item.note}
              <small>{item.note}</small>
            {/if}
          </button>
        </li>
      {/each}
    </ul>
  {:else}
    <ContentStatePanel message={emptyMessage} />
  {/if}
</div>

<style>
  .entity-list-pane ul {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 0.85rem;
  }

  .entity-list-pane button {
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 0.95rem 1rem;
    text-align: left;
    display: grid;
    gap: 0.75rem;
    transition:
      border-color 160ms ease,
      background-color 160ms ease,
      transform 160ms ease;
  }

  .entity-list-pane button:hover {
    border-color: var(--color-border-strong);
    background: rgba(26, 23, 21, 0.58);
    transform: translateY(-1px);
  }

  .entity-list-pane button.selected {
    border-color: rgba(226, 193, 155, 0.38);
    background: rgba(226, 193, 155, 0.08);
  }

  .entity-list-pane__row {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
  }

  .entity-list-pane__copy {
    display: grid;
    gap: 0.3rem;
  }

  .entity-list-pane__copy strong {
    font-size: 1rem;
  }

  .entity-list-pane__copy span {
    color: var(--color-text-soft);
    font-size: 0.92rem;
  }

  .entity-list-pane__copy p,
  .entity-list-pane button small {
    margin: 0;
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .entity-list-pane__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.45rem;
    justify-content: flex-end;
  }

  @media (max-width: 720px) {
    .entity-list-pane__row {
      flex-direction: column;
    }

    .entity-list-pane__tags {
      justify-content: flex-start;
    }
  }
</style>
