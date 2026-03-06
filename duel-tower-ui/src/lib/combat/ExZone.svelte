<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardDef, CardInstance } from '../model'
  import CardSummaryTile from '../components/CardSummaryTile.svelte'

  const dispatch = createEventDispatcher<{ useEx: { cardId: string }; inspect: { cardId: string } }>()

  export let card: CardInstance | null = null
  export let cardDef: CardDef | null = null
  export let disabled = false
</script>

<section class="panel">
  <div class="panelTitle">ExZone</div>
  <div class="spacer"></div>
  {#if !card}
    <div class="hint">EX 카드 없음</div>
  {:else}
    <CardSummaryTile def={cardDef} instance={card} on:inspect={(e) => dispatch('inspect', e.detail)}>
      <div class="spacer"></div>
      <button class="btn" disabled={disabled} on:click={() => dispatch('useEx', { cardId: card.instanceId })}>EX 사용</button>
    </CardSummaryTile>
  {/if}
</section>
