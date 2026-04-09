<script lang="ts">
  import AppLayout from './layout/AppLayout.svelte'
  import CardLibraryPage from './pages/CardLibraryPage.svelte'
  import CardDetailPage from './pages/CardDetailPage.svelte'
  import CharacterDetailPage from './pages/CharacterDetailPage.svelte'
  import PublicEntryLayout from './layout/PublicEntryLayout.svelte'
  import CharacterListPage from './pages/CharacterListPage.svelte'
  import CombatCommandPage from './pages/CombatCommandPage.svelte'
  import DeckEditorPage from './pages/DeckEditorPage.svelte'
  import DeckListPage from './pages/DeckListPage.svelte'
  import GmLobbyPage from './pages/GmLobbyPage.svelte'
  import HubPage from './pages/HubPage.svelte'
  import LoginPage from './pages/LoginPage.svelte'
  import PresetEditorPage from './pages/PresetEditorPage.svelte'
  import PresetListPage from './pages/PresetListPage.svelte'
  import PlayerLobbyPage from './pages/PlayerLobbyPage.svelte'
  import RulesReferencePage from './pages/RulesReferencePage.svelte'
  import SessionEntryPage from './pages/SessionEntryPage.svelte'
  import {
    APP_NAV_ITEMS,
    normalizePath,
    pathBuilders,
    resolvePage,
    type PageKey,
  } from './lib/navigation'
  import { authState } from './lib/auth/authState.svelte'

  const PUBLIC_PAGE_COMPONENTS = {
    login: LoginPage,
  } as const

  const APP_PAGE_COMPONENTS = {
    hub: HubPage,
    character: CharacterListPage,
    'character-create': CharacterDetailPage,
    'character-detail': CharacterDetailPage,
    cards: CardLibraryPage,
    decks: DeckListPage,
    presets: PresetListPage,
    'card-detail': CardDetailPage,
    inventory: RulesReferencePage,
    'deck-editor': DeckEditorPage,
    'preset-editor': PresetEditorPage,
    lobby: SessionEntryPage,
    'player-lobby': PlayerLobbyPage,
    'gm-lobby': GmLobbyPage,
    combat: CombatCommandPage,
  } as const

  let current = $state(resolvePage(window.location.pathname))

  async function handleLogout() {
    try {
      await authState.logout()
    } finally {
      history.replaceState({}, '', pathBuilders.login())
      updateCurrentPage(pathBuilders.login())
    }
  }

  function getAccessiblePage(pathname: string) {
    const requestedPage = resolvePage(pathname)

    if (!authState.initialized) {
      return {
        requestedPage,
        finalPage: requestedPage,
      }
    }

    if (!authState.isAuthenticated && requestedPage.area === 'app') {
      return {
        requestedPage,
        finalPage: resolvePage(pathBuilders.login()),
      }
    }

    if (authState.isAuthenticated && requestedPage.path === pathBuilders.login()) {
      return {
        requestedPage,
        finalPage: resolvePage(pathBuilders.hub()),
      }
    }

    return {
      requestedPage,
      finalPage: requestedPage,
    }
  }

  function updateCurrentPage(pathname: string, mode: 'push' | 'replace' = 'replace') {
    const { requestedPage, finalPage } = getAccessiblePage(pathname)
    const currentPath = normalizePath(window.location.pathname)
    const nextPath = finalPage.path
    const shouldRedirect = currentPath !== nextPath

    if (shouldRedirect) {
      const historyMethod: 'pushState' | 'replaceState' =
        requestedPage.path === finalPage.path && mode === 'push' ? 'pushState' : 'replaceState'

      history[historyMethod]({}, '', nextPath)
    }

    current = finalPage
  }

  function navigate(path: string) {
    updateCurrentPage(path, 'push')
  }

  function syncFromLocation() {
    updateCurrentPage(window.location.pathname)
  }

  function handleDocumentClick(event: MouseEvent) {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return
    }

    const target = event.target
    if (!(target instanceof Element)) return

    const link = target.closest<HTMLAnchorElement>('a[data-nav]')
    if (!link) return

    const href = link.getAttribute('href')
    if (!href || !href.startsWith('/')) return

    event.preventDefault()
    navigate(href)
  }

  function resolvePublicPageComponent(key: PageKey) {
    return PUBLIC_PAGE_COMPONENTS[key as keyof typeof PUBLIC_PAGE_COMPONENTS] ?? LoginPage
  }

  function resolveAppPageComponent(key: PageKey) {
    return APP_PAGE_COMPONENTS[key as keyof typeof APP_PAGE_COMPONENTS] ?? HubPage
  }

  $effect(() => {
    void authState.bootstrap()
  })

  $effect(() => {
    const initialized = authState.initialized
    const isAuthenticated = authState.isAuthenticated

    if (!initialized) return

    void isAuthenticated
    updateCurrentPage(window.location.pathname)
  })

  $effect(() => {
    window.addEventListener('popstate', syncFromLocation)
    document.addEventListener('click', handleDocumentClick)

    return () => {
      window.removeEventListener('popstate', syncFromLocation)
      document.removeEventListener('click', handleDocumentClick)
    }
  })
</script>

{#if !authState.initialized}
  <PublicEntryLayout>
    <section class="app-bootstrap">
      <div class="app-bootstrap__panel">
        <p class="app-bootstrap__eyebrow">Session Restore</p>
        <h2>Checking archive access</h2>
        <p class="app-bootstrap__copy">
          Restoring the current session before routing into the public or internal archive.
        </p>
      </div>
    </section>
  </PublicEntryLayout>
{:else if current.area === 'public'}
  {@const PublicPage = resolvePublicPageComponent(current.key)}

  <PublicEntryLayout>
    {#key current.path}
      <PublicPage />
    {/key}
  </PublicEntryLayout>
{:else}
  {@const AppPage = resolveAppPageComponent(current.key)}

  <AppLayout
    pages={APP_NAV_ITEMS}
    {current}
    currentUsername={authState.user?.username ?? null}
    authMessage={authState.error}
    logoutPending={authState.loading}
    onLogout={handleLogout}
    onNavigate={navigate}
  >
    {#key current.path}
      <AppPage />
    {/key}
  </AppLayout>
{/if}

<style>
  .app-bootstrap {
    width: min(100%, 32rem);
    margin: 0 auto;
  }

  .app-bootstrap__panel {
    border: 1px solid var(--color-border);
    background: var(--color-bg-panel-soft);
    padding: 1.5rem;
    display: grid;
    gap: 0.75rem;
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.12);
  }

  .app-bootstrap__eyebrow,
  .app-bootstrap__copy {
    margin: 0;
  }

  .app-bootstrap__eyebrow {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  .app-bootstrap__panel h2 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.3rem);
    line-height: 1.05;
  }

  .app-bootstrap__copy {
    color: var(--color-text-soft);
    line-height: 1.7;
  }
</style>
