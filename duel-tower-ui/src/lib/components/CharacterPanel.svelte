<script lang="ts">
  import type { PlayerState } from '../model'
  import ResourceBar from './ResourceBar.svelte'
  import StatusBadge from './StatusBadge.svelte'

  export let player: PlayerState
  export let heading = ''
</script>

<section class="panel">
  <div class="row">
    <b class="mono">{heading || player.playerId}</b>
    {#if player.pendingDecision}
      <StatusBadge tone="danger" label="결정 필요" />
    {/if}
  </div>
  <div class="grid">
    <ResourceBar label="덱" value={player.deck.length} max={30} />
    <ResourceBar label="핸드" value={player.hand.length} max={player.handLimit || 12} />
    <ResourceBar label="필드" value={player.field.length} max={player.fieldLimit || 6} />
    <ResourceBar label="묘지" value={player.grave.length} max={30} />
  </div>
</section>

<style>
  .panel{padding:10px; border:1px solid var(--line-default); border-radius:14px; background: var(--surface-2)}
  .row{display:flex; align-items:center; justify-content:space-between; margin-bottom:8px}
  .grid{display:grid; gap:8px}
</style>
