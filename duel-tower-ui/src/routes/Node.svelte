<script lang="ts">
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import StatusBadge from '../lib/components/StatusBadge.svelte'
  import { showRuleTooltip, hideRuleTooltip } from '../stores/ui'

  const nodes = [
    { id: 'N-1', name: '정찰 노드', rule: '전투 전 카드 1장 교체 가능', danger: false },
    { id: 'N-2', name: '엘리트 노드', rule: '적 소환수의 HP +2', danger: true },
  ]
</script>

<PageSkeleton title="Node" summary="노드 선택/이동 페이지 스켈레톤">
  <div class="list">
    {#each nodes as node (node.id)}
      <button class="item" on:mouseenter={(e) => showRuleTooltip(node.rule, e)} on:mouseleave={hideRuleTooltip}>
        <b>{node.name}</b>
        <StatusBadge tone={node.danger ? 'danger' : 'ok'} label={node.danger ? '위험' : '안정'} />
      </button>
    {/each}
  </div>
</PageSkeleton>

<style>
  .list{display:flex; flex-direction:column; gap:10px}
  .item{display:flex; justify-content:space-between; align-items:center; border:1px solid var(--line-default); border-radius:12px; padding:10px; background:var(--surface-2); color:inherit}
</style>
