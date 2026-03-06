<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardInstance } from '../model'

  const dispatch = createEventDispatcher<{ useEx: { cardId: string }; inspect: { cardId: string } }>()

  export let card: CardInstance | null = null
  export let cardName = '—'
  export let disabled = false
</script>

<section class="panel">
  <div class="panelTitle">ExZone</div>
  <div class="spacer"></div>
  {#if !card}
    <div class="hint">EX 카드 없음</div>
  {:else}
    <div class="ti">
      <div><b>{cardName}</b></div>
      <div class="hint mono">{card.instanceId}</div>
      <div class="spacer"></div>
      <div class="row">
        <button class="btn" on:click={() => dispatch('inspect', { cardId: card.instanceId })}>상세</button>
        <button class="btn" disabled={disabled} on:click={() => dispatch('useEx', { cardId: card.instanceId })}>EX 사용</button>
      </div>
    </div>
  {/if}
</section>
