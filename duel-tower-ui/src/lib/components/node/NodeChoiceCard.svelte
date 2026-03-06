<script lang="ts">
  import StatusBadge from '../StatusBadge.svelte'

  export type NodePhase = 'judgement' | 'combat' | 'event'

  export type NodeChoice = {
    id: string
    name: string
    typeLabel: string
    rule: string
    phase: NodePhase
    danger: 'low' | 'mid' | 'high'
    disabled?: boolean
    disabledReason?: string
  }

  export let node: NodeChoice
  export let onSelect: ((node: NodeChoice) => void) | undefined = undefined

  const dangerLabel: Record<NodeChoice['danger'], string> = {
    low: '낮음',
    mid: '보통',
    high: '높음',
  }

  function click() {
    if (node.disabled) return
    onSelect?.(node)
  }
</script>

<button class="card" class:isDisabled={node.disabled} disabled={node.disabled} on:click={click}>
  <div class="top">
    <b>{node.name}</b>
    <div class="badges">
      <span class="chip mono">{node.typeLabel}</span>
      <StatusBadge tone={node.danger === 'high' ? 'danger' : node.danger === 'mid' ? 'info' : 'ok'} label={`위험도 ${dangerLabel[node.danger]}`} />
      {#if node.disabled}
        <span class="chip no">진행 불가</span>
      {/if}
    </div>
  </div>
  <div class="rule">{node.rule}</div>
  {#if node.disabledReason}
    <div class="hint">{node.disabledReason}</div>
  {/if}
</button>

<style>
  .card{width:100%; text-align:left; border:1px solid var(--line-default); border-radius:16px; padding:12px; background:var(--surface-2); color:inherit}
  .card:hover{border-color:var(--line-info); background:var(--state-info-bg)}
  .card.isDisabled{opacity:.7; cursor:not-allowed}
  .top{display:flex; justify-content:space-between; gap:12px; align-items:flex-start}
  .badges{display:flex; gap:6px; flex-wrap:wrap; justify-content:flex-end}
  .rule{margin-top:8px; color:var(--text-muted)}
  .chip.no{border-color:var(--line-danger); color:var(--state-danger)}
</style>
