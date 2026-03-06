<script lang="ts">
  import ResourceBar from '../lib/components/ResourceBar.svelte'
  import DetailDrawer, { type DetailItem } from '../lib/components/DetailDrawer.svelte'
  import DetailPill from '../lib/components/DetailPill.svelte'

  const profile = {
    name: '티그',
    className: '스트라이커',
    level: 18,
    region: '재의 평원',
  }

  const lifeStats = [
    { label: '체력', value: 68, max: 100 },
    { label: '집중', value: 42, max: 60 },
    { label: '기동력', value: 24, max: 30 },
  ]

  const combatStats = [
    { label: '공격력', value: 32, max: 50 },
    { label: '방어력', value: 21, max: 40 },
    { label: '치명률', value: 17, max: 30 },
  ]

  const traits: DetailItem[] = [
    { kind: 'status', name: '강심장', summary: '공포 내성 +25%', description: '제어 효과에 대한 내성이 높아지며 전투 시작 시 저항을 1회 획득합니다.', tags: ['패시브', '생존'] },
    { kind: 'status', name: '속전속결', summary: '턴 종료 시 기동력 회복', description: '이번 턴에 공격 카드를 2장 이상 사용했다면 기동력을 3 회복합니다.', tags: ['패시브', '템포'] },
  ]

  const deckSummary: DetailItem[] = [
    { kind: 'card', name: '급소 찌르기', summary: '비용 1 · 단일 대상 12 피해', description: '대상에게 12 피해를 주고 약화가 있다면 4 추가 피해를 줍니다.', tags: ['공격', '콤보'], stats: [{ label: '코스트', value: 1 }, { label: '희귀도', value: '일반' }] },
    { kind: 'card', name: '수비 태세', summary: '비용 1 · 방어 10', description: '방어를 10 획득하고 다음 턴 시작 시 집중을 2 회복합니다.', tags: ['방어', '유지'], stats: [{ label: '코스트', value: 1 }, { label: '희귀도', value: '일반' }] },
    { kind: 'card', name: '추격 베기', summary: '비용 2 · 연타', description: '8 피해를 2회 가합니다. 적이 약화 상태면 추가로 1회 타격합니다.', tags: ['공격', '연타'], stats: [{ label: '코스트', value: 2 }, { label: '희귀도', value: '희귀' }] },
  ]

  const statuses: DetailItem[] = [
    { kind: 'status', name: '재생', summary: '턴 시작 시 체력 4 회복', description: '다음 3턴 동안 턴 시작 시 체력을 4 회복합니다.', tags: ['버프'], stats: [{ label: '남은 턴', value: 3 }] },
    { kind: 'status', name: '출혈', summary: '카드 사용 시 피해 2', description: '카드를 사용할 때마다 2 고정 피해를 받습니다.', tags: ['디버프'], stats: [{ label: '중첩', value: 2 }] },
  ]

  let selectedItem: DetailItem | null = null
</script>

<div class="page">
  <section class="panel">
    <div class="panelTitle">프로필</div>
    <div class="profileGrid">
      <div><b>{profile.name}</b></div>
      <div>직업: {profile.className}</div>
      <div>레벨: {profile.level}</div>
      <div>지역: {profile.region}</div>
    </div>
  </section>

  <section class="panel">
    <div class="panelTitle">생활능력</div>
    <div class="statsGrid">
      {#each lifeStats as stat (stat.label)}
        <ResourceBar label={stat.label} value={stat.value} max={stat.max} />
      {/each}
    </div>
  </section>

  <section class="panel">
    <div class="panelTitle">전투능력</div>
    <div class="statsGrid">
      {#each combatStats as stat (stat.label)}
        <ResourceBar label={stat.label} value={stat.value} max={stat.max} />
      {/each}
    </div>
  </section>

  <section class="panel desktopOnly">
    <div class="panelTitle">특성</div>
    <div class="chips">
      {#each traits as trait (trait.name)}
        <DetailPill item={trait} on:select={(e) => (selectedItem = e.detail.item)} />
      {/each}
    </div>
  </section>

  <section class="panel desktopOnly">
    <div class="panelTitle">덱 요약</div>
    <div class="chips">
      {#each deckSummary as card (card.name)}
        <DetailPill item={card} tone="info" on:select={(e) => (selectedItem = e.detail.item)} />
      {/each}
    </div>
  </section>

  <section class="panel">
    <div class="panelTitle">상태</div>
    <div class="chips">
      {#each statuses as status (status.name)}
        <DetailPill item={status} tone={status.name === '출혈' ? 'danger' : 'ok'} on:select={(e) => (selectedItem = e.detail.item)} />
      {/each}
    </div>
  </section>

  <section class="panel mobileOnly">
    <div class="panelTitle">모바일 축약 뷰</div>
    <ul class="compactList">
      <li>핵심 수치: 체력 68 / 집중 42</li>
      <li>현재 상태: 재생, 출혈</li>
      <li>최근 로그: 추격 베기 사용 · 16 피해</li>
    </ul>
  </section>
</div>

<DetailDrawer open={Boolean(selectedItem)} item={selectedItem} on:close={() => (selectedItem = null)} />

<style>
  .page{display:flex;flex-direction:column;gap:12px}
  .profileGrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
  .statsGrid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
  .chips{display:flex;gap:8px;flex-wrap:wrap}
  .compactList{margin:0;padding-left:18px;color:var(--text-muted)}
  .mobileOnly{display:none}

  @media (max-width: 900px){
    .statsGrid{grid-template-columns:1fr}
  }

  @media (max-width: 700px){
    .desktopOnly{display:none}
    .mobileOnly{display:block}
  }
</style>
