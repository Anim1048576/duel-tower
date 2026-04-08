<script lang="ts">
  import PrimaryNavRail from '../lib/components/PrimaryNavRail.svelte'
  import ShellHeader from '../lib/components/ShellHeader.svelte'
  import type { AppNavItem, PageDefinition } from '../lib/navigation'
  import type { Snippet } from 'svelte'

  type Props = {
    pages: AppNavItem[]
    current: PageDefinition
    currentUsername?: string | null
    authMessage?: string | null
    logoutPending?: boolean
    onLogout: () => void | Promise<void>
    onNavigate: (path: string) => void
    children?: Snippet
  }

  let {
    pages,
    current,
    currentUsername = null,
    authMessage = null,
    logoutPending = false,
    onLogout,
    onNavigate,
    children,
  }: Props = $props()
</script>

<div class="app-shell">
  <aside class="app-sidebar">
    <div class="app-layout__brand">
      <p class="app-layout__eyebrow">Archive Node</p>
      <h1>Duel Tower</h1>
      <p class="app-layout__copy">
        Archive shell for roster, decks, sessions, and upcoming battle flows. Later batches can
        extend this structure without changing the routing model.
      </p>

      {#if currentUsername}
        <div class="app-layout__session-block">
          <p class="app-layout__session">Signed in as {currentUsername}</p>

          {#if authMessage}
            <p class="app-layout__auth-message">{authMessage}</p>
          {/if}

          <button type="button" class="app-layout__logout" onclick={() => onLogout()} disabled={logoutPending}>
            {#if logoutPending}
              Signing out...
            {:else}
              Sign out
            {/if}
          </button>
        </div>
      {/if}
    </div>

    <PrimaryNavRail items={pages} currentPath={current.path} onNavigate={onNavigate} />
  </aside>

  <div class="app-main">
    <ShellHeader
      eyebrow={current.eyebrow}
      title={current.title}
      description={current.description}
      tags={current.tags}
    />

    <main class="page-stack">
      {@render children?.()}
    </main>
  </div>
</div>

<style>
  .app-layout__brand {
    display: grid;
    gap: 0.75rem;
  }

  .app-layout__eyebrow {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.18em;
    text-transform: uppercase;
  }

  .app-layout__brand h1 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(1.9rem, 2vw, 2.4rem);
    line-height: 1;
  }

  .app-layout__copy {
    margin: 0;
    color: var(--color-text-soft);
    font-size: 0.95rem;
    line-height: 1.65;
  }

  .app-layout__session {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.78rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .app-layout__session-block {
    display: grid;
    gap: 0.65rem;
  }

  .app-layout__auth-message {
    margin: 0;
    color: var(--color-warning);
    font-size: 0.88rem;
    line-height: 1.5;
  }

  .app-layout__logout {
    min-height: 2.7rem;
    width: fit-content;
    padding: 0.65rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .app-layout__logout:disabled {
    opacity: 0.74;
  }
</style>
