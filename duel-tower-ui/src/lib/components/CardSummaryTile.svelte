<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import type { CardDef, CardInstance } from '../model'
  import KeywordTag from './KeywordTag.svelte'

  const dispatch = createEventDispatcher<{ inspect: { cardId: string } }>()

  export let def: CardDef | null = null
  export let instance: CardInstance | null = null
  export let clickable = true
</script>

<button class="tile" disabled={!clickable || (!instance && !def)} on:click={() => dispatch('inspect', { cardId: instance?.instanceId ?? def?.id ?? '' })}>
  <div class="top">
    <div class="name">{def?.name ?? instance?.defId ?? '—'}</div>
    <span class="cost">{def?.cost ?? 0}</span>
  </div>
  <div class="sub mono">{instance?.instanceId ?? def?.id ?? 'no-id'}</div>
  <div class="tags">
    {#if def?.token}<span class="token">TOKEN</span>{/if}
    {#each def?.keywords ?? [] as keyword (keyword)}
      <KeywordTag {keyword} />
    {/each}
  </div>
  <slot />
</button>

<style>
  .tile{width:100%; text-align:left; border-radius:18px; border:1px solid var(--line-default); padding:10px; background:linear-gradient(180deg, var(--surface-1), var(--surface-2)); color:inherit}
  .tile:enabled{cursor:pointer}
  .tile:hover:enabled{border-color: var(--line-info)}
  .top{display:flex; justify-content:space-between; gap:8px}
  .name{font-weight:900}
  .sub{margin-top:6px; font-size:12px; color:var(--text-muted)}
  .tags{margin-top:8px; display:flex; gap:6px; flex-wrap:wrap}
  .cost{padding:3px 7px; border-radius:999px; border:1px solid var(--line-default); font-size:12px}
  .token{font-size:11px; padding:4px 8px; border-radius:999px; border:1px solid var(--line-danger); color:var(--state-danger); background:var(--state-danger-bg)}
</style>
