<script lang="ts">
  import { onMount } from 'svelte'
  import Home from './routes/Home.svelte'
  import Lobby from './routes/Lobby.svelte'
  import Presets from './routes/Presets.svelte'
  import Node from './routes/Node.svelte'
  import Combat from './routes/Combat.svelte'

  import { route, startRouter, navigate } from './lib/router'

  onMount(() => startRouter())
</script>

<header class="topbar">
  <nav class="nav">
    <a href="/ui/" on:click|preventDefault={() => navigate('/')}>홈</a>
    <a href="/ui/lobby" on:click|preventDefault={() => navigate('/lobby')}>로비</a>
    <a href="/ui/presets" on:click|preventDefault={() => navigate('/presets')}>프리셋</a>
    <a href="/ui/node" on:click|preventDefault={() => navigate('/node')}>노드</a>
    <a href="/ui/combat" on:click|preventDefault={() => navigate('/combat')}>전투</a>
  </nav>
</header>

<main class="page">
  {#if $route === '/'} <Home />
  {:else if $route === '/lobby'} <Lobby />
  {:else if $route === '/presets'} <Presets />
  {:else if $route === '/node'} <Node />
  {:else if $route === '/combat'} <Combat />
  {:else}
    <div class="card">404 · <button class="btn" on:click={() => navigate('/')}>홈으로</button></div>
  {/if}
</main>