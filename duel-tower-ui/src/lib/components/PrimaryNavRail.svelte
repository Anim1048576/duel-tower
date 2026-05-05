<script lang="ts">
  import TagChip from './TagChip.svelte'
  import type { AppNavItem, PageKey } from '../navigation'

  type Props = {
    items: AppNavItem[]
    currentPath: string
    currentKey: PageKey
    onNavigate: (path: string) => void
  }

  let { items, currentPath, currentKey, onNavigate }: Props = $props()

  const SESSION_NAV_KEYS = new Set<PageKey>(['lobby', 'player-lobby', 'gm-lobby'])
  const CARD_LIBRARY_NAV_KEYS = new Set<PageKey>(['cards', 'card-detail'])
  const DECK_NAV_KEYS = new Set<PageKey>(['decks', 'deck-editor'])

  function isSelected(item: AppNavItem) {
    if (item.key === 'lobby' && SESSION_NAV_KEYS.has(currentKey)) {
      return true
    }

    if (item.key === 'cards' && CARD_LIBRARY_NAV_KEYS.has(currentKey)) {
      return true
    }

    if (item.key === 'decks' && DECK_NAV_KEYS.has(currentKey)) {
      return true
    }

    if (item.key === currentKey) {
      return true
    }

    return currentPath === item.path || currentPath.startsWith(`${item.path}/`)
  }
</script>

<nav class="nav-rail" aria-label="Primary">
  {#each items as item}
    {#if item.enabled}
      <button
        type="button"
        class:selected={isSelected(item)}
        onclick={() => onNavigate(item.path)}
      >
        <strong>{item.label}</strong>
        <span>{item.description}</span>
      </button>
    {:else}
      <div class="nav-rail__pending" aria-disabled="true">
        <div class="nav-rail__pending-copy">
          <strong>{item.label}</strong>
          <span>{item.description}</span>
        </div>
        <TagChip label="Unavailable" tone="muted" />
      </div>
    {/if}
  {/each}
</nav>

<style>
  .nav-rail {
    display: grid;
    gap: 0.65rem;
  }

  .nav-rail button,
  .nav-rail__pending {
    width: 100%;
    border: 1px solid var(--color-border);
    background: var(--color-bg-elevated);
    padding: 0.9rem 1rem;
    text-align: left;
    display: grid;
    gap: 0.3rem;
  }

  .nav-rail button {
    transition:
      border-color 160ms ease,
      background-color 160ms ease,
      transform 160ms ease;
  }

  .nav-rail button:hover {
    border-color: var(--color-border-strong);
    background: var(--color-bg-panel);
    transform: translateX(2px);
  }

  .nav-rail button.selected {
    border-color: rgba(226, 193, 155, 0.4);
    background: rgba(226, 193, 155, 0.09);
  }

  .nav-rail strong,
  .nav-rail__pending-copy strong {
    font-size: 0.98rem;
    font-weight: 600;
  }

  .nav-rail span,
  .nav-rail__pending-copy span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    line-height: 1.5;
  }

  .nav-rail__pending {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.75rem;
    opacity: 0.82;
  }

  .nav-rail__pending-copy {
    display: grid;
    gap: 0.3rem;
  }
</style>
