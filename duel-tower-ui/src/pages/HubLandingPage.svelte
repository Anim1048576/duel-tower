<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'

  const statusItems = [
    {
      label: 'Session-bound screens',
      value: 'Inventory and Shop follow the session you opened most recently.',
      tone: 'warning' as const,
    },
    {
      label: 'Always-available reference',
      value: 'Reference stays useful even before a session starts.',
      tone: 'success' as const,
    },
    {
      label: 'Fast return paths',
      value: 'Hub, Session, Inventory, and Shop stay one click apart.',
      tone: 'accent' as const,
    },
  ]

  const quickLinks = [
    {
      title: 'Session Entry',
      description: 'Create or rejoin a session before opening run-bound screens.',
      href: pathBuilders.sessionEntry(),
      action: 'Open session gate',
      badge: 'Recommended first step',
      tone: 'accent' as const,
    },
    {
      title: 'Inventory',
      description: 'Check gold, keys, chests, and carried supplies for the active expedition.',
      href: pathBuilders.inventory(),
      action: 'Open inventory',
      badge: 'Needs session context',
      tone: 'warning' as const,
    },
    {
      title: 'Shop',
      description: 'Browse merchant offers and buy while the expedition is stopped on an event node.',
      href: pathBuilders.shop(),
      action: 'Open shop',
      badge: 'Needs session context',
      tone: 'success' as const,
    },
    {
      title: 'Reference',
      description: 'Look up keywords, statuses, and passives without leaving the archive.',
      href: pathBuilders.reference(),
      action: 'Open reference',
      badge: 'Always available',
      tone: 'muted' as const,
    },
  ]

  const guidance = [
    'Open Session Entry first when you need Inventory or Shop to restore the correct expedition context.',
    'Use Inventory for carried stock and resources, Shop for merchant offers, and Reference for rules lookup.',
    'If a session-bound page says its context is unavailable, returning to Session Entry is the fastest reset path.',
  ]
</script>

<div class="hub-page">
  <SectionFrame
    eyebrow="Central Ledger"
    title="Duel Tower Hub"
    description="Start from here when you need a quick route into the active expedition, merchant stop, or rules reference."
  >
    <div class="hub-page__hero">
      <div class="hub-page__hero-copy">
        <p class="hub-page__hero-label">Hub Overview</p>
        <h3>Keep expedition screens, merchant access, and rules lookup clearly separated</h3>
        <p>
          The hub now calls out which screens need a live session and which ones can open on their own.
          Inventory focuses on what the expedition already carries, Shop focuses on what can be bought,
          and Reference stays available as a pure rules lookup.
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
      description="These notes explain how the expedition screens fit into the rest of the app."
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
      description="Use these entry cards when moving between session setup, expedition stock, merchant offers, and rules lookup."
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
      description="A short checklist for the most common movement between the new run-bound pages."
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
