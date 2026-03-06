<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardDef, CardInstance } from '../model'
  import CardSummaryTile from '../components/CardSummaryTile.svelte'

  const dispatch = createEventDispatcher<{ inspect: { cardId: string } }>()

  export let cards: CardInstance[] = []
  export let cardDefs: Record<string, CardDef> = {}
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
        <CardSummaryTile def={cardDefs[c.defId]} instance={c} on:inspect={(e) => dispatch('inspect', e.detail)} />
      {/each}
    </div>
  {/if}
</section>
