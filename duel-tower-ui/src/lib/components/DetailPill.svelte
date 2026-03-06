<script lang="ts">
  import { createEventDispatcher } from 'svelte'
  import { hideRuleTooltip, showRuleTooltip } from '../../stores/ui'
  import type { DetailItem } from './DetailDrawer.svelte'

  const dispatch = createEventDispatcher<{ select: { item: DetailItem } }>()

  export let item: DetailItem
  export let tone: 'neutral' | 'info' | 'ok' | 'danger' = 'neutral'

  function onMouseEnter(event: MouseEvent) {
    showRuleTooltip(item.summary, event)
  }
</script>

<button
  class="pill"
  class:info={tone === 'info'}
  class:ok={tone === 'ok'}
  class:danger={tone === 'danger'}
  on:mouseenter={onMouseEnter}
  on:mousemove={onMouseEnter}
  on:mouseleave={hideRuleTooltip}
  on:click={() => dispatch('select', { item })}>
  {item.name}
</button>

<style>
  .pill{padding:5px 9px;border-radius:999px;border:1px solid var(--line-default);font-size:12px;background:var(--surface-2);color:var(--text-muted)}
  .info{border-color:var(--line-info);color:var(--state-info)}
  .ok{border-color:var(--line-ok);color:var(--state-ok)}
  .danger{border-color:var(--line-danger);color:var(--state-danger)}
</style>
