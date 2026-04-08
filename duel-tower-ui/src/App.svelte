<script lang="ts">
  import AppLayout from './layout/AppLayout.svelte'
  import CharacterDetailPage from './pages/CharacterDetailPage.svelte'
  import PublicEntryLayout from './layout/PublicEntryLayout.svelte'
  import CharacterListPage from './pages/CharacterListPage.svelte'
  import CombatCommandPage from './pages/CombatCommandPage.svelte'
  import DeckEditorPage from './pages/DeckEditorPage.svelte'
  import DeckListPage from './pages/DeckListPage.svelte'
  import GmLobbyPage from './pages/GmLobbyPage.svelte'
  import HubPage from './pages/HubPage.svelte'
  import LoginPage from './pages/LoginPage.svelte'
  import PlayerLobbyPage from './pages/PlayerLobbyPage.svelte'
  import SessionEntryPage from './pages/SessionEntryPage.svelte'
  import { APP_NAV_ITEMS, resolvePage, type PageKey } from './lib/navigation'

  const PUBLIC_PAGE_COMPONENTS = {
    login: LoginPage,
  } as const

  const APP_PAGE_COMPONENTS = {
    hub: HubPage,
    character: CharacterListPage,
    'character-detail': CharacterDetailPage,
    decks: DeckListPage,
    'deck-editor': DeckEditorPage,
    lobby: SessionEntryPage,
    'player-lobby': PlayerLobbyPage,
    'gm-lobby': GmLobbyPage,
    combat: CombatCommandPage,
  } as const

  let current = $state(resolvePage(window.location.pathname))

  function navigate(path: string) {
    if (path === current.path) return
    history.pushState({}, '', path)
    current = resolvePage(window.location.pathname)
  }

  function syncFromLocation() {
    current = resolvePage(window.location.pathname)
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
    window.addEventListener('popstate', syncFromLocation)
    document.addEventListener('click', handleDocumentClick)

    return () => {
      window.removeEventListener('popstate', syncFromLocation)
      document.removeEventListener('click', handleDocumentClick)
    }
  })
</script>

{#if current.area === 'public'}
  {@const PublicPage = resolvePublicPageComponent(current.key)}

  <PublicEntryLayout>
    <PublicPage />
  </PublicEntryLayout>
{:else}
  {@const AppPage = resolveAppPageComponent(current.key)}

  <AppLayout pages={APP_NAV_ITEMS} {current} onNavigate={navigate}>
    <AppPage />
  </AppLayout>
{/if}
