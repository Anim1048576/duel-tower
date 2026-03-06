<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardDef, CardInstance } from '../model'

  const dispatch = createEventDispatcher<{ close: void }>()

  export let open = false
  export let card: CardInstance | null = null
  export let def: CardDef | null = null
</script>

{#if open}
  <section class="drawer panel">
    <div class="row" style="justify-content:space-between">
      <div class="panelTitle">CardDetailDrawer</div>
      <button class="btn" on:click={() => dispatch('close')}>닫기</button>
    </div>
    <div class="spacer"></div>
    {#if !card}
      <div class="hint">카드를 선택해 주세요.</div>
    {:else}
      <div class="ti">
        <div><b>{def?.name ?? card.defId}</b></div>
        <div class="hint mono">instance: {card.instanceId}</div>
        <div class="hint mono">owner: {card.ownerId} · zone: {card.zone}</div>
        <div class="hint">비용 {def?.cost ?? '—'} · 키워드 {(def?.keywords ?? []).join(', ') || '—'}</div>
        {#if def?.text}<div class="logBody">{def.text}</div>{/if}
      </div>
    {/if}
  </section>
{/if}

<style>
  .drawer{position:fixed; left:16px; right:16px; bottom:16px; z-index:40}
</style>
