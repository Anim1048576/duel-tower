<script lang="ts">
  import type { Snippet } from 'svelte'

  type Props = {
    query?: string
    queryPlaceholder?: string
    summary?: string
    onQueryChange?: (value: string) => void
    filters?: Snippet
    sort?: Snippet
    actions?: Snippet
  }

  let {
    query = '',
    queryPlaceholder = 'Search',
    summary,
    onQueryChange,
    filters,
    sort,
    actions,
  }: Props = $props()

  function handleInput(event: Event) {
    const target = event.currentTarget as HTMLInputElement
    onQueryChange?.(target.value)
  }
</script>

<section class="search-filter-bar" aria-label="Search and filters">
  <label class="search-filter-bar__search">
    <span class="visually-hidden">Search</span>
    <input
      type="search"
      value={query}
      placeholder={queryPlaceholder}
      oninput={handleInput}
    />
  </label>

  {#if filters}
    <div class="search-filter-bar__slot">
      <span>Filter</span>
      <div class="search-filter-bar__slot-body">
        {@render filters()}
      </div>
    </div>
  {/if}

  {#if sort}
    <div class="search-filter-bar__slot">
      <span>Sort</span>
      <div class="search-filter-bar__slot-body">
        {@render sort()}
      </div>
    </div>
  {/if}

  {#if actions}
    <div class="search-filter-bar__slot search-filter-bar__slot--actions">
      <span>Actions</span>
      <div class="search-filter-bar__slot-body">
        {@render actions()}
      </div>
    </div>
  {/if}

  {#if summary}
    <p class="search-filter-bar__summary">{summary}</p>
  {/if}
</section>

<style>
  .search-filter-bar {
    display: grid;
    grid-template-columns: minmax(14rem, 1.4fr) repeat(3, minmax(0, 1fr));
    gap: 0.85rem;
    align-items: end;
  }

  .search-filter-bar__search,
  .search-filter-bar__slot {
    display: grid;
    gap: 0.4rem;
  }

  .search-filter-bar__slot > span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .search-filter-bar__search input,
  .search-filter-bar__slot-body {
    min-height: 3rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.42);
  }

  .search-filter-bar__search input {
    width: 100%;
    padding: 0.85rem 0.95rem;
    outline: none;
  }

  .search-filter-bar__search input:focus {
    border-color: rgba(255, 179, 175, 0.4);
  }

  .search-filter-bar__slot-body {
    padding: 0.5rem 0.65rem;
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    align-items: center;
  }

  .search-filter-bar__slot--actions .search-filter-bar__slot-body {
    justify-content: flex-end;
  }

  .search-filter-bar__summary {
    grid-column: 1 / -1;
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.88rem;
    line-height: 1.5;
  }

  @media (max-width: 1080px) {
    .search-filter-bar {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 720px) {
    .search-filter-bar {
      grid-template-columns: 1fr;
    }
  }
</style>
