<script lang="ts">
  import Router from 'svelte-spa-router'
  import Home from './routes/Home.svelte'
  import Lobby from './routes/Lobby.svelte'
  import Presets from './routes/Presets.svelte'
  import Node from './routes/Node.svelte'
  import Combat from './routes/Combat.svelte'

  const routes = {
    '/': Home,
    '/lobby': Lobby,
    '/presets': Presets,
    '/node': Node,
    '/combat': Combat,
  }
</script>

<Router {routes} />