<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { ActionDescriptor, CardDef, CardInstance } from '../model'
  import CardSummaryTile from '../components/CardSummaryTile.svelte'
  import DisabledReason from '../components/DisabledReason.svelte'

  const dispatch = createEventDispatcher<{ useEx: { action: ActionDescriptor }; inspect: { cardId: string } }>()

  export let card: CardInstance | null = null
  export let cardDef: CardDef | null = null
  export let action: ActionDescriptor | null = null
  export let actionLocked = false
  export let lockReason = ''
</script>

<section class="panel">
  <div class="panelTitle">ExZone</div>
  <div class="spacer"></div>
  {#if !card}
    <div class="hint">EX 카드 없음</div>
  {:else}
    <CardSummaryTile def={cardDef} instance={card} on:inspect={(e) => dispatch('inspect', e.detail)}>
      <div class="spacer"></div>
      <button class="btn" disabled={actionLocked || Boolean(action?.disabledReason)} title={lockReason || action?.disabledReason || ''} on:click={() => action && dispatch('useEx', { action })}>EX 사용</button>
      <DisabledReason show={actionLocked || Boolean(action?.disabledReason)} reason={lockReason || action?.disabledReason || ''} />
    </CardSummaryTile>
  {/if}
</section>
