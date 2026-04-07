<script lang="ts">
  import AppLayout from './layout/AppLayout.svelte'
  import PlaceholderPage from './pages/PlaceholderPage.svelte'
  import { PAGES, resolvePage } from './lib/navigation'

  let current = $state(resolvePage(window.location.pathname))

  function navigate(path: string) {
    if (path === current.path) return
    history.pushState({}, '', path)
    current = resolvePage(window.location.pathname)
  }

  function syncFromLocation() {
    current = resolvePage(window.location.pathname)
  }

  $effect(() => {
    window.addEventListener('popstate', syncFromLocation)
    return () => window.removeEventListener('popstate', syncFromLocation)
  })
</script>

<AppLayout pages={PAGES} {current} onNavigate={navigate}>
  <PlaceholderPage title={current.title} description={current.description} />
</AppLayout>
