<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const readiness = [
    { label: '허브 화면', value: '이용 가능', tone: 'success' as const },
    { label: '계정 인증', value: '연결 완료', tone: 'success' as const },
    { label: '세션 흐름', value: '진행 중', tone: 'accent' as const },
  ]

  const nextFronts = [
    { title: '캐릭터', note: '보유 캐릭터와 프로필 구성을 확인하고 정리하는 영역입니다.' },
    { title: '덱', note: '카드 묶음과 편성 상태를 살펴보고 전투 준비를 이어갈 수 있습니다.' },
    { title: '프리셋', note: '로드아웃 조합을 저장하고 세션에 적용할 준비를 하는 보관함입니다.' },
    { title: '세션', note: '입장, 로비, 전투 흐름으로 이어지는 현재 운영 축입니다.' },
    { title: '전투 · 룰 · 참고 정보', note: '전투 진행 화면과 규칙 참고 흐름을 같은 축에서 확인할 수 있습니다.' },
  ]

  const memos = [
    '허브는 현재 사용 가능한 영역을 한 번에 훑고 다음 진입 지점을 고르기 위한 중앙 화면입니다.',
    '일부 섹션은 정적인 개요를 우선 보여주며, 실제 데이터 연결이 끝난 영역부터 순차적으로 화면 밀도를 높입니다.',
  ]
</script>

<div class="hub-page">
  <SectionFrame
    eyebrow="Central Ledger"
    title="운영 허브"
    description="로그인 이후 가장 먼저 마주하는 화면으로, Duel Tower의 주요 영역과 현재 접근 흐름을 한눈에 정리합니다."
  >
    <div class="hub-page__hero">
      <div class="hub-page__hero-copy">
        <p class="hub-page__hero-label">Hub Overview</p>
        <h3>기록, 준비, 세션 흐름을 한곳에서 이어가는 중앙 허브</h3>
        <p>
          허브는 캐릭터, 덱, 프리셋, 세션, 전투 관련 화면으로 이어지는 기준점입니다. 지금 사용할 수
          있는 영역을 빠르게 확인하고 다음 작업으로 자연스럽게 이동할 수 있도록 구성했습니다.
        </p>
      </div>

      <div class="hub-page__hero-tags">
        <TagChip label="인증 연결" tone="success" />
        <TagChip label="운영 허브" tone="accent" />
      </div>
    </div>
  </SectionFrame>

  <div class="hub-page__grid">
    <SectionFrame
      title="현재 상태"
      description="허브와 직접 맞닿아 있는 핵심 흐름을 짧게 정리한 개요입니다."
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
      title="주요 영역"
      description="현재 허브에서 자연스럽게 이어지는 화면 축을 기준으로 정리했습니다."
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
      title="허브 메모"
      description="이 화면은 상태 요약과 진입 안내를 중심으로 유지되며, 없는 기능을 과장하지 않습니다."
    >
      <div class="hub-page__memo">
        {#each memos as memo}
          <p>{memo}</p>
        {/each}
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
