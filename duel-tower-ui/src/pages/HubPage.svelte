<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const readiness = [
    { label: '허브 셸', value: '준비', tone: 'success' as const },
    { label: '인증 연동', value: '연결됨', tone: 'success' as const },
    { label: '세션 실시간', value: '대기', tone: 'muted' as const },
  ]

  const nextFronts = [
    { title: '모험가 명부', note: '목록과 선택 흐름 우선 구현' },
    { title: '전술 목록', note: '카드 목록과 필터 기본형 연결' },
    { title: '세션 입장', note: '허브에서 로비로 이어지는 진입점 확보' },
  ]

  // TODO: Replace these placeholders with the hub overview API response.
</script>

<div class="hub-page">
  <SectionFrame
    eyebrow="Central Ledger"
    title="현재 전선"
    description="Duel Tower Hub (Fixed)를 바탕으로, 내부 앱 진입 후 가장 먼저 보게 될 요약 패널 구조를 정리합니다."
  >
    <div class="hub-page__hero">
      <div class="hub-page__hero-copy">
        <p class="hub-page__hero-label">Batch 1 Scope</p>
        <h3>로그인 이후 흐름을 받는 내부 공통 셸</h3>
        <p>
          허브는 이후 명부, 전술, 세션, 전투 화면이 공통으로 기대는 진입점입니다. 이번 배치에서는
          정보 구조와 시각적 톤만 우선 고정합니다.
        </p>
      </div>

      <div class="hub-page__hero-tags">
        <TagChip label="Auth Ready" tone="success" />
        <TagChip label="Shell First" tone="accent" />
      </div>
    </div>
  </SectionFrame>

  <div class="hub-page__grid">
    <SectionFrame
      title="준비 상태"
      description="실제 서버 응답 대신 배치 진행 상태를 최소 mock 데이터로만 표시합니다."
    >
      <ul class="hub-page__status-list">
        {#each readiness as item}
          <li>
            <div>
              <strong>{item.label}</strong>
              <span>{item.value}</span>
            </div>
            <TagChip label={item.value} tone={item.tone} />
          </li>
        {/each}
      </ul>
    </SectionFrame>

    <SectionFrame
      title="다음 연결 대상"
      description="다음 배치에서 실제 페이지 구현을 붙일 우선순위 후보입니다."
    >
      <ul class="hub-page__front-list">
        {#each nextFronts as item}
          <li>
            <strong>{item.title}</strong>
            <p>{item.note}</p>
          </li>
        {/each}
      </ul>
    </SectionFrame>

    <SectionFrame
      title="통합 메모"
      description="데이터 계약이 정리되면 이 섹션부터 허브 요약 API와 작업 큐 응답을 연결할 수 있습니다."
    >
      <div class="hub-page__memo">
        <p>TODO: 허브 요약, 사용자 세션, 진행 중 원정 데이터를 한 번에 묶는 응답 모델 정의</p>
        <p>TODO: 좌측 내비 활성 상태와 접근 권한을 실제 사용자 상태에 따라 계산</p>
      </div>
    </SectionFrame>
  </div>
</div>

<style>
  .hub-page,
  .hub-page__grid {
    display: grid;
    gap: 1.5rem;
  }

  .hub-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hub-page__hero {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 1.25rem;
  }

  .hub-page__hero-copy {
    max-width: 42rem;
    display: grid;
    gap: 0.8rem;
  }

  .hub-page__hero-label {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 0.75rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  .hub-page__hero-copy h3 {
    margin: 0;
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
  }

  .hub-page__hero-copy p:last-child {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.75;
  }

  .hub-page__hero-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .hub-page__status-list,
  .hub-page__front-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 0.85rem;
  }

  .hub-page__status-list li,
  .hub-page__front-list li {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.32);
    padding: 0.95rem 1rem;
  }

  .hub-page__status-list li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
  }

  .hub-page__status-list strong,
  .hub-page__front-list strong {
    display: block;
    margin-bottom: 0.3rem;
    font-size: 0.98rem;
  }

  .hub-page__status-list span,
  .hub-page__front-list p,
  .hub-page__memo p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .hub-page__memo {
    display: grid;
    gap: 0.75rem;
  }

  @media (max-width: 960px) {
    .hub-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
