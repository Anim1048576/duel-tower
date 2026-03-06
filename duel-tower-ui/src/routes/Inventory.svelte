<script lang="ts">
  import DetailDrawer, { type DetailItem } from '../lib/components/DetailDrawer.svelte'
  import DetailPill from '../lib/components/DetailPill.svelte'

  type Slot = {
    id: number
    name: string
    count: number
    bound: boolean
    battleUsable: boolean
    detail: DetailItem
  }

  const slots: Slot[] = [
    {
      id: 1,
      name: '소형 회복 물약',
      count: 8,
      bound: false,
      battleUsable: true,
      detail: { kind: 'card', name: '소형 회복 물약', summary: '전투 중 사용 가능 · 체력 20 회복', description: '즉시 체력을 20 회복합니다. 턴 소모 없이 사용됩니다.', tags: ['소모품', '회복'] },
    },
    {
      id: 2,
      name: '해독제',
      count: 3,
      bound: true,
      battleUsable: true,
      detail: { kind: 'card', name: '해독제', summary: '전투 중 사용 가능 · 디버프 해제', description: '출혈/중독 등 해로운 상태효과 1개를 제거합니다.', tags: ['소모품', '정화'] },
    },
    {
      id: 3,
      name: '단단한 가죽끈',
      count: 12,
      bound: false,
      battleUsable: false,
      detail: { kind: 'status', name: '단단한 가죽끈', summary: '제작 재료', description: '장비 제작에 사용되는 기본 재료입니다.', tags: ['재료'] },
    },
    {
      id: 4,
      name: '긴급 연막탄',
      count: 2,
      bound: true,
      battleUsable: true,
      detail: { kind: 'card', name: '긴급 연막탄', summary: '전투 중 사용 가능 · 회피 상승', description: '현재 턴 동안 회피율이 크게 상승합니다.', tags: ['전투 아이템'] },
    },
    {
      id: 5,
      name: '강화석 파편',
      count: 16,
      bound: false,
      battleUsable: false,
      detail: { kind: 'status', name: '강화석 파편', summary: '강화 재료', description: '장비 강화 수치에 따라 다량으로 요구됩니다.', tags: ['재료'] },
    },
  ]

  const keys = 2
  const chests = 1
  const gold = 12450

  let selectedItem: DetailItem | null = null
</script>

<div class="inventoryPage">
  <section class="panel">
    <div class="panelTitle">보유 자원</div>
    <div class="resourceRow">
      <span class="chip">열쇠 {keys}</span>
      <span class="chip">상자 {chests}</span>
      <span class="chip">소지금 {gold.toLocaleString()}G</span>
    </div>
  </section>

  <section class="panel">
    <div class="panelTitle">인벤토리 (5 슬롯)</div>
    <div class="slotGrid">
      {#each slots as slot (slot.id)}
        <article class="slot">
          <div class="slotHead">
            <b>슬롯 {slot.id}</b>
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
