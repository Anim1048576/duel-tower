<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { ActionDescriptor, CardDef, CardInstance } from '../model'
  import CardSummaryTile from '../components/CardSummaryTile.svelte'
  import DisabledReason from '../components/DisabledReason.svelte'

  const dispatch = createEventDispatcher<{ play: { action: ActionDescriptor }; inspect: { cardId: string } }>()

  export let title = 'HandZone'
  export let cards: CardInstance[] = []
  export let cardDefs: Record<string, CardDef> = {}
  export let actionByCardId: Record<string, ActionDescriptor | undefined> = {}
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
        {@const action = actionByCardId[c.instanceId]}
        <CardSummaryTile def={cardDefs[c.defId]} instance={c} on:inspect={(e) => dispatch('inspect', e.detail)}>
          <div class="spacer"></div>
          <button class="btn" disabled={Boolean(action?.disabledReason)} title={action?.disabledReason ?? ''} on:click={() => action && dispatch('play', { action })}>사용</button>
          <DisabledReason show={Boolean(action?.disabledReason)} reason={action?.disabledReason ?? ''} />
        </CardSummaryTile>
      {/each}
    </div>
  {/if}
</section>
