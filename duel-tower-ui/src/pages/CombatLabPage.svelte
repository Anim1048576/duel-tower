<script lang="ts">
  import type { LabDiceResponse, LabEffectProbeResponse } from '../lib/api/labTypes'
  import LabDicePanel from '../lib/components/lab/LabDicePanel.svelte'
  import LabEffectProbePanel from '../lib/components/lab/LabEffectProbePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  type LabTab = 'dice' | 'effect-probe'

  let activeTab = $state<LabTab>('dice')
  let lastDiceResult = $state<LabDiceResponse | null>(null)
  let effectCardCount = $state(0)
  let lastProbeResult = $state<LabEffectProbeResponse | null>(null)

  const tabs: Array<{ key: LabTab; label: string }> = [
    { key: 'dice', label: 'Dice' },
    { key: 'effect-probe', label: 'Effect Probe' },
  ]
</script>

<div class="combat-lab-page">
  <SectionFrame
    eyebrow="Lab"
    title="Combat Lab"
    description="Experiment with dice, card effects, and status combinations through backend Lab APIs."
  >
    <div class="combat-lab-page__hero">
      <div class="combat-lab-page__copy">
        <p>Effect Lab</p>
        <h3>Inspect backend Lab API responses without running a combat session.</h3>
        <span>Guide: docs/combat-lab.md</span>
      </div>

      <div class="combat-lab-page__tags">
        <TagChip label="Dice" tone="accent" />
        <TagChip label="Effect Probe" tone="success" />
        <TagChip label="Build Test" tone="warning" />
      </div>
    </div>

    <div class="combat-lab-page__notice" role="note">
      <strong>Lab scope</strong>
      <p>
        Combat Lab does not run PlayCardCommand, spend AP, verify hand ownership, advance turns,
        or process the full pending-decision flow. It is for observing backend dice and CardEffect
        results in isolation.
      </p>
    </div>

    <div class="combat-lab-page__stats">
      <StatBlock
        value={lastDiceResult ? lastDiceResult.rollCount : '-'}
        label="Dice rolls"
        note="Last backend dice response"
      />
      <StatBlock
        value={effectCardCount}
        label="Probe cards"
        note="Loaded from /api/lab/effects/cards"
      />
      <StatBlock
        value={lastProbeResult ? (lastProbeResult.valid ? 'Valid' : 'Invalid') : '-'}
        label="Probe state"
        note="Last CardEffect.validate result"
      />
    </div>
  </SectionFrame>

  <section class="combat-lab-page__tabs" aria-label="Combat Lab tabs">
    {#each tabs as tab}
      <button
        type="button"
        class:combat-lab-page__tab--active={activeTab === tab.key}
        aria-pressed={activeTab === tab.key}
        onclick={() => (activeTab = tab.key)}
      >
        {tab.label}
      </button>
    {/each}
  </section>

  {#if activeTab === 'dice'}
    <LabDicePanel onResultChange={(result) => (lastDiceResult = result)} />
  {:else}
    <LabEffectProbePanel
      onCardsChange={(cards) => (effectCardCount = cards.length)}
      onResultChange={(result) => (lastProbeResult = result)}
    />
  {/if}
</div>

<style>
  .combat-lab-page {
    display: grid;
    gap: 1.5rem;
  }

  .combat-lab-page__hero {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .combat-lab-page__copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .combat-lab-page__copy p,
  .combat-lab-page__copy h3,
  .combat-lab-page__notice p {
    margin: 0;
  }

  .combat-lab-page__copy p {
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-lab-page__copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.65rem, 2.4vw, 2.25rem);
    line-height: 1.15;
  }

  .combat-lab-page__copy span {
    color: var(--color-text-soft);
    font-size: 0.88rem;
  }

  .combat-lab-page__notice {
    display: grid;
    gap: 0.35rem;
    padding: 0.9rem 1rem;
    border: 1px solid rgba(199, 167, 125, 0.34);
    background: rgba(199, 167, 125, 0.08);
  }

  .combat-lab-page__notice strong {
    font-size: 0.78rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-lab-page__notice p {
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .combat-lab-page__tags,
  .combat-lab-page__tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .combat-lab-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .combat-lab-page__tabs button {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .combat-lab-page__tab--active {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  @media (max-width: 960px) {
    .combat-lab-page__stats {
      grid-template-columns: 1fr;
    }
  }
</style>
