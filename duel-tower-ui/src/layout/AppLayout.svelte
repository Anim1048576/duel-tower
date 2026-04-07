<script lang="ts">
  import type { PageDefinition } from '../lib/navigation'
  import type { Snippet } from 'svelte'

  type Props = {
    pages: PageDefinition[]
    current: PageDefinition
    onNavigate: (path: string) => void
    children?: Snippet
  }

  let { pages, current, onNavigate, children }: Props = $props()
</script>

<div class="app-shell">
  <header class="shell-header">
    <h1>Duel Tower UI</h1>
    <p>Frontend Reset Shell · Svelte 5 + Vite</p>
  </header>

  <nav class="shell-nav" aria-label="Primary">
    {#each pages as page}
      <button
        type="button"
        class:active={page.path === current.path}
        onclick={() => onNavigate(page.path)}
      >
        {page.label}
      </button>
    {/each}
  </nav>

  <main class="shell-content">
    {@render children?.()}
  </main>
</div>
