<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'

  const statusItems = [
    {
      label: 'Session-bound screens',
      value: 'Inventory와 Shop은 최근 세션을 기준으로 표시됩니다.',
      tone: 'warning' as const,
    },
    {
      label: 'Always-available reference',
      value: 'Reference는 세션 없이도 사용할 수 있습니다.',
      tone: 'success' as const,
    },
    {
      label: 'Fast return paths',
      value: '주요 화면으로 바로 이동할 수 있습니다.',
      tone: 'accent' as const,
    },
  ]

  const quickLinks = [
    {
      title: 'Session Entry',
      description: '세션을 만들거나 다시 참가합니다.',
      href: pathBuilders.sessionEntry(),
      action: 'Open session gate',
      badge: 'Recommended first step',
      tone: 'accent' as const,
    },
    {
      title: 'Inventory',
      description: '보유 자원을 확인합니다.',
      href: pathBuilders.inventory(),
      action: 'Open inventory',
      badge: 'Needs session context',
      tone: 'warning' as const,
    },
    {
      title: 'Shop',
      description: '상점 상품을 확인하고 구매합니다.',
      href: pathBuilders.shop(),
      action: 'Open shop',
      badge: 'Needs session context',
      tone: 'success' as const,
    },
    {
      title: 'Reference',
      description: '키워드, 상태, 패시브를 확인합니다.',
      href: pathBuilders.reference(),
      action: 'Open reference',
      badge: 'Always available',
      tone: 'muted' as const,
    },
  ]

  const guidance = [
    'Inventory나 Shop은 Session Entry에서 먼저 세션을 선택하세요.',
    'Inventory는 보유 자원, Shop은 구매, Reference는 규칙 확인용입니다.',
    '세션 화면이 열리지 않으면 Session Entry에서 다시 들어가세요.',
  ]
</script>

<div class="hub-page">
  <SectionFrame
    eyebrow="Central Ledger"
    title="Duel Tower Hub"
    description="주요 화면으로 이동합니다."
  >
    <div class="hub-page__hero">
      <div class="hub-page__hero-copy">
        <p class="hub-page__hero-label">Hub Overview</p>
        <h3>필요한 화면으로 이동하세요</h3>
        <p>
          Inventory와 Shop은 세션 정보가 필요합니다. Reference는 바로 열 수 있습니다.
        </p>
      </div>

      <div class="hub-page__hero-tags">
        <TagChip label="Quick Links" tone="accent" />
        <TagChip label="Session-aware" tone="warning" />
      </div>
    </div>
  </SectionFrame>

  <div class="hub-page__grid">
    <SectionFrame
      title="Current UX Focus"
      description="화면별 용도입니다."
    >
      <ul class="hub-page__status-list">
        {#each statusItems as item}
          <li>
            <div>
              <strong>{item.label}</strong>
              <span>{item.value}</span>
            </div>
            <TagChip label={item.label} tone={item.tone} />
          </li>
        {/each}
      </ul>
    </SectionFrame>

    <SectionFrame
      title="Quick Routes"
      description="원하는 화면을 선택합니다."
    >
      <div class="hub-page__link-grid">
        {#each quickLinks as item}
          <a class="hub-page__link-card" data-nav href={item.href}>
            <div class="hub-page__link-head">
              <strong>{item.title}</strong>
              <TagChip label={item.badge} tone={item.tone} />
            </div>
            <p>{item.description}</p>
            <span>{item.action}</span>
          </a>
        {/each}
      </div>
    </SectionFrame>

    <SectionFrame
      title="Route Notes"
      description="자주 쓰는 이동 순서입니다."
    >
      <div class="hub-page__memo">
        {#each guidance as memo}
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
    max-width: 44rem;
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

  .hub-page__hero-tags,
  .hub-page__link-head {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .hub-page__status-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 0.85rem;
  }

  .hub-page__status-list li,
  .hub-page__link-card,
  .hub-page__memo p {
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
  .hub-page__link-card strong {
    display: block;
    margin-bottom: 0.3rem;
    font-size: 0.98rem;
  }

  .hub-page__status-list span,
  .hub-page__link-card p,
  .hub-page__memo p,
  .hub-page__link-card span {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .hub-page__link-grid {
    display: grid;
    gap: 0.9rem;
  }

  .hub-page__link-card {
    display: grid;
    gap: 0.65rem;
    text-decoration: none;
    transition:
      border-color 160ms ease,
      background-color 160ms ease,
      transform 160ms ease;
  }

  .hub-page__link-card:hover {
    border-color: var(--color-border-strong);
    background: rgba(226, 193, 155, 0.06);
    transform: translateY(-2px);
  }

  .hub-page__link-card span {
    color: var(--color-accent);
    font-size: 0.82rem;
    font-weight: 600;
    letter-spacing: 0.04em;
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
