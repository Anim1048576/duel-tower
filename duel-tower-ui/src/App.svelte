<script lang="ts">
  import { onMount } from 'svelte'
  import Home from './routes/Home.svelte'
  import Session from './routes/Session.svelte'
  import Lobby from './routes/Lobby.svelte'
  import Character from './routes/Character.svelte'
  import Node from './routes/Node.svelte'
  import Combat from './routes/Combat.svelte'
  import DeckEdit from './routes/DeckEdit.svelte'
  import Inventory from './routes/Inventory.svelte'
  import Results from './routes/Results.svelte'
  import Logs from './routes/Logs.svelte'

  import { route, startRouter, navigate } from './lib/router'
  import { session, resetSession } from './stores/session'
  import { content, ensureCards } from './stores/content'
  import { combat } from './stores/combat'
  import { toasts, dismissToast } from './stores/log'

  type Phase = 'session' | 'lobby' | 'adventure' | 'combat' | 'results'

  type NavItem = {
    path: string
    label: string
    phases: Phase[]
    disabledReason?: string
  }

  onMount(() => {
    startRouter()
    ensureCards()
  })

  $: cur = $route
  $: code = $session.code
  $: hasSession = Boolean(code)
  $: inCombat = Boolean($combat.state?.combat)
  $: phase = !hasSession ? 'session' : inCombat ? 'combat' : 'lobby'

  function isPathActive(path: string) {
    return cur === path || (path.includes(':id') && cur.startsWith('/character/'))
  }

  function itemStatus(item: NavItem) {
    if (!item.phases.includes(phase)) {
      return { disabled: true, reason: `현재 phase(${phase})에서는 이동 불가` }
    }
    if (!hasSession && item.path !== '/session') {
      return { disabled: true, reason: '세션 생성/참가가 먼저 필요' }
    }
    return { disabled: false, reason: item.disabledReason ?? '' }
  }

  $: navItems = [
    { path: '/session', label: 'Session', phases: ['session', 'lobby', 'adventure', 'combat', 'results'] },
    { path: '/lobby', label: 'Lobby', phases: ['lobby', 'adventure', 'combat', 'results'] },
    { path: '/character/1', label: 'Character', phases: ['lobby', 'adventure'] },
    { path: '/node', label: 'Node', phases: ['adventure'] },
    { path: '/combat', label: 'Combat', phases: ['combat', 'lobby'], disabledReason: !inCombat ? '전투 시작 전: 레이아웃 미리보기만 가능' : '' },
    { path: '/deck-edit', label: 'Deck Edit', phases: ['lobby', 'adventure'] },
    { path: '/inventory', label: 'Inventory', phases: ['lobby', 'adventure'] },
    { path: '/results', label: 'Results', phases: ['results', 'combat'] },
    { path: '/logs', label: 'Logs', phases: ['session', 'lobby', 'adventure', 'combat', 'results'] },
  ] satisfies NavItem[]
</script>

<header class="topbar">
  <div class="brand" role="banner">
    <div class="logo">DT</div>
    <div>
      <div class="brandTitle">Duel Tower</div>
      <div class="brandSub">SPA UI · /ui · phase: <span class="mono">{phase}</span></div>
    </div>
  </div>

  <nav class="nav" aria-label="primary">
    {#each navItems as item (item.path)}
      {@const status = itemStatus(item)}
      <button
        class="navItem"
        class:isActive={isPathActive(item.path)}
        disabled={status.disabled}
        title={status.reason}
        on:click={() => navigate(item.path)}>
        {item.label}{#if status.disabled && status.reason} · {status.reason}{/if}
      </button>
    {/each}
  </nav>

  <div class="topRight">
    {#if $content.status === 'ok'}
      <span class="pill">API · OK</span>
    {:else if $content.status === 'loading'}
      <span class="pill">API · …</span>
    {:else}
      <span class="pill">API · ERR</span>
    {/if}

    {#if code}
      <span class="pill mono">세션 {code}</span>
    {/if}

    <button class="iconBtn" title="세션 초기화" on:click={() => resetSession()}>↺</button>
  </div>
</header>

<main class="page">
  <div class="container">
    {#if $route === '/'}
      <Home />
    {:else if $route === '/session'}
      <Session />
    {:else if $route === '/lobby'}
      <Lobby />
    {:else if $route.startsWith('/character/')}
      <Character id={$route.split('/')[2] || ''} />
    {:else if $route === '/node'}
      <Node />
    {:else if $route === '/combat'}
      <Combat />
    {:else if $route === '/deck-edit'}
      <DeckEdit />
    {:else if $route === '/inventory'}
      <Inventory />
    {:else if $route === '/results'}
      <Results />
    {:else if $route === '/logs'}
      <Logs />
    {:else}
      <div class="card">
        <div class="row wrap">
          <div class="grow">404 · <span class="muted mono">{cur}</span></div>
          <button class="btn" on:click={() => navigate('/session')}>세션으로</button>
        </div>
      </div>
    {/if}
  </div>
</main>

<div class="toast" aria-live="polite" aria-relevant="additions">
  {#each $toasts as t (t.id)}
    <div class="toastItem" on:click={() => dismissToast(t.id)}>
      <div class="t">{new Date(t.at).toLocaleTimeString()}</div>
      <div class="m">
        <b>{t.title}</b>
        {#if t.message}
          <div class="muted" style="margin-top:6px">{t.message}</div>
        {/if}
      </div>
    </div>
  {/each}
</div>
