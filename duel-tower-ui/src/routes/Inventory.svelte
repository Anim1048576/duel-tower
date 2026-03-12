<script lang="ts">
  import DetailDrawer, { type DetailItem } from '../lib/components/DetailDrawer.svelte'
  import DetailPill from '../lib/components/DetailPill.svelte'
  import { combat } from '../stores/combat'

  type Slot = {
    id: string
    name: string
    count: number
    bound: boolean
    battleUsable: boolean
    detail: DetailItem
  }

  function toDetail(item: any): DetailItem {
    return {
      kind: item.battleUsable ? 'card' : 'status',
      name: item.name,
      summary: item.summary,
      description: item.description,
      tags: item.tags ?? [],
    }
  }

  $: inventory = $combat.state?.run?.inventory
  $: slots = (inventory?.items ?? []).map((item) => ({
    id: item.id,
    name: item.name,
    count: item.count,
    bound: item.bound,
    battleUsable: item.battleUsable,
    detail: toDetail(item),
  })) as Slot[]

  let selectedItem: DetailItem | null = null
</script>

<div class="inventoryPage">
  <section class="panel">
    <div class="panelTitle">보유 자원</div>
    <div class="resourceRow">
      <span class="chip">열쇠 {inventory?.keys ?? 0}</span>
      <span class="chip">상자 {inventory?.chests ?? 0}</span>
      <span class="chip">소지금 {(inventory?.gold ?? 0).toLocaleString()}G</span>
    </div>
  </section>

  <section class="panel">
    <div class="panelTitle">인벤토리 ({slots.length} 슬롯)</div>
    {#if !$combat.state}
      <div class="hint">세션 상태를 불러오는 중...</div>
    {:else if !slots.length}
      <div class="hint">표시할 인벤토리 항목이 없다.</div>
    {:else}
      <div class="slotGrid">
        {#each slots as slot (slot.id)}
          <article class="slot">
            <div class="slotHead">
              <b>{slot.id}</b>
              <span class="count">x{slot.count}</span>
            </div>
            <div class="slotName">{slot.name}</div>
            <div class="flags">
              <span class="flag" class:bound={slot.bound}>{slot.bound ? '귀속' : '비귀속'}</span>
              <span class="flag" class:usable={slot.battleUsable}>{slot.battleUsable ? '전투 사용 가능' : '전투 사용 불가'}</span>
            </div>
            <DetailPill item={slot.detail} tone={slot.battleUsable ? 'info' : 'neutral'} on:select={(e) => (selectedItem = e.detail.item)} />
          </article>
        {/each}
      </div>
    {/if}
  </section>
</div>

<DetailDrawer open={Boolean(selectedItem)} item={selectedItem} on:close={() => (selectedItem = null)} />

<style>
  .inventoryPage{display:flex;flex-direction:column;gap:12px}
  .resourceRow{display:flex;gap:8px;flex-wrap:wrap}
  .chip{padding:6px 10px;border-radius:999px;border:1px solid var(--line-default);background:var(--surface-2);font-size:12px}
  .slotGrid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}
  .slot{padding:10px;border:1px solid var(--line-default);border-radius:12px;background:var(--surface-2);display:flex;flex-direction:column;gap:8px}
  .slotHead{display:flex;justify-content:space-between;align-items:center}
  .slotName{font-size:14px}
  .count{font-size:12px;color:var(--text-muted)}
  .flags{display:flex;flex-direction:column;gap:4px}
  .flag{font-size:11px;padding:3px 6px;border-radius:999px;border:1px solid var(--line-default);width:max-content;color:var(--text-muted)}
  .bound{border-color:var(--line-info);color:var(--state-info)}
  .usable{border-color:var(--line-ok);color:var(--state-ok)}

  @media (max-width: 1200px){
    .slotGrid{grid-template-columns:repeat(2,minmax(0,1fr))}
  }
  @media (max-width: 700px){
    .slotGrid{grid-template-columns:1fr}
  }
</style>
