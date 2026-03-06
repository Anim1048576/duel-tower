<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardInstance } from '../model'

  const dispatch = createEventDispatcher<{ inspect: { cardId: string } }>()

  export let cards: CardInstance[] = []
  export let cardDefs: Record<string, { name?: string }> = {}
</script>

<section class="panel">
  <div class="row" style="justify-content:space-between">
    <div class="panelTitle">FieldZone</div>
    <span class="badge">{cards.length}</span>
  </div>
  <div class="spacer"></div>
  {#if !cards.length}
    <div class="hint">필드 카드 없음</div>
  {:else}
    <div class="cardRow">
      {#each cards as c (c.instanceId)}
        <button class="gcard" on:click={() => dispatch('inspect', { cardId: c.instanceId })}>
          <div class="gcardTitle">{cardDefs[c.defId]?.name ?? c.defId}</div>
          <div class="gcardSub mono">{c.instanceId}</div>
        </button>
      {/each}
    </div>
  {/if}
</section>
