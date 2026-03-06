<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardDef, CardInstance } from '../model'
  import KeywordTag from './KeywordTag.svelte'

  const dispatch = createEventDispatcher<{ close: void }>()

  export let open = false
  export let card: CardInstance | null = null
  export let def: CardDef | null = null
</script>

{#if open}
  <section class="drawer">
    <div class="inner">
      <div class="row">
        <b>카드 상세</b>
        <button class="btn" on:click={() => dispatch('close')}>닫기</button>
      </div>
      {#if card}
        <div class="title">{def?.name ?? card.defId}</div>
        <div class="meta mono">instance: {card.instanceId} · owner: {card.ownerId} · zone: {card.zone}</div>
        <div class="tags">
          {#each def?.keywords ?? [] as keyword (keyword)}
            <KeywordTag {keyword} />
          {/each}
        </div>
        {#if def?.text}<p>{def.text}</p>{/if}
      {:else}
        <div class="meta">카드를 선택해 주세요.</div>
      {/if}
    </div>
  </section>
{/if}

<style>
  .drawer{position:fixed; left:16px; right:16px; bottom:16px; z-index:40}
  .inner{padding:14px; border:1px solid var(--line-default); border-radius:16px; background: var(--surface-1)}
  .row{display:flex; justify-content:space-between; align-items:center}
  .title{font-weight:800; margin-top:10px}
  .meta{font-size:12px; color:var(--text-muted); margin-top:6px}
  .tags{display:flex; gap:6px; flex-wrap:wrap; margin-top:8px}
  p{margin:10px 0 0}
</style>
