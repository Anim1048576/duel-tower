<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardInstance } from '../model'

  const dispatch = createEventDispatcher<{ play: { cardId: string }; inspect: { cardId: string } }>()

  export let title = 'HandZone'
  export let cards: CardInstance[] = []
  export let cardDefs: Record<string, { name?: string }> = {}
  export let disabled = false
</script>

<section class="panel">
  <div class="row" style="justify-content:space-between">
    <div class="panelTitle">{title}</div>
    <span class="badge">{cards.length}</span>
  </div>
  <div class="spacer"></div>

  {#if !cards.length}
    <div class="hint">핸드 없음</div>
  {:else}
    <div class="cardRow">
      {#each cards as c (c.instanceId)}
        <div class="gcard">
          <div class="gcardTitle">{cardDefs[c.defId]?.name ?? c.defId}</div>
          <div class="gcardSub mono">{c.instanceId}</div>
          <div class="spacer"></div>
          <div class="row">
            <button class="btn" on:click={() => dispatch('inspect', { cardId: c.instanceId })}>상세</button>
            <button class="btn" disabled={disabled} on:click={() => dispatch('play', { cardId: c.instanceId })}>사용</button>
          </div>
        </div>
      {/each}
    </div>
  {/if}
</section>
