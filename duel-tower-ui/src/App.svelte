<script lang="ts">
  import { onMount } from 'svelte'
  import Home from './routes/Home.svelte'
  import Lobby from './routes/Lobby.svelte'
  import Presets from './routes/Presets.svelte'
  import Decks from './routes/Decks.svelte'
  import DeckDetail from './routes/DeckDetail.svelte'
  import Node from './routes/Node.svelte'
  import Combat from './routes/Combat.svelte'

  import { route, startRouter, navigate } from './lib/router'
  import { session, resetSession } from './stores/session'
  import { content, ensureCards } from './stores/content'
  import { toasts, dismissToast } from './stores/log'

  onMount(() => {
    startRouter()
    ensureCards()
  })

  $: cur = $route
  $: code = $session.code
</script>

<header class="topbar">
  <div class="brand" role="banner">
    <div class="logo">DT</div>
    <div>
      <div class="brandTitle">Duel Tower</div>
      <div class="brandSub">SPA UI · /ui</div>
    </div>
  </div>

  <nav class="nav" aria-label="primary">
    <a class="navItem" class:isActive={cur === '/'} href="/ui/" on:click|preventDefault={() => navigate('/')}>홈</a>
    <a class="navItem" class:isActive={cur === '/lobby'} href="/ui/lobby" on:click|preventDefault={() => navigate('/lobby')}>로비</a>
    <a class="navItem" class:isActive={cur === '/presets'} href="/ui/presets" on:click|preventDefault={() => navigate('/presets')}>프리셋</a>
    <a class="navItem" class:isActive={cur === '/decks' || cur.startsWith('/decks/')} href="/ui/decks" on:click|preventDefault={() => navigate('/decks')}>덱</a>
    <a class="navItem" class:isActive={cur === '/node'} href="/ui/node" on:click|preventDefault={() => navigate('/node')}>노드</a>
    <a class="navItem" class:isActive={cur === '/combat'} href="/ui/combat" on:click|preventDefault={() => navigate('/combat')}>전투</a>
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
    {:else if $route === '/lobby'}
      <Lobby />
    {:else if $route === '/presets'}
      <Presets />
    {:else if $route === '/decks'}
      <Decks />
    {:else if $route.startsWith('/decks/')}
      <DeckDetail id={$route.split('/')[2] || ''} />
    {:else if $route === '/node'}
      <Node />
    {:else if $route === '/combat'}
      <Combat />
    {:else}
      <div class="card">
        <div class="row wrap">
          <div class="grow">404 · <span class="muted mono">{cur}</span></div>
          <button class="btn" on:click={() => navigate('/')}>홈으로</button>
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
