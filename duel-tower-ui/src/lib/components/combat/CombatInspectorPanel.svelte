<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import TagChip from '../TagChip.svelte'
  import type { CombatInspectorViewModel } from './types'

  type Props = {
    inspectorView: CombatInspectorViewModel | null
  }

  let { inspectorView }: Props = $props()
</script>

<div class="combat-inspector-panel">
  {#if inspectorView}
    <article class="combat-inspector-panel__surface">
      <div class="combat-inspector-panel__hero">
        <div
          class="combat-inspector-panel__portrait"
          class:combat-inspector-panel__portrait--enemy={inspectorView.kind === 'entity' && inspectorView.portraitVariant === 'enemy'}
          aria-hidden="true"
        >
          <span>{inspectorView.portraitLabel ?? '??'}</span>
        </div>

        <div class="combat-inspector-panel__hero-copy">
          <strong>{inspectorView.source === 'pinned' ? 'Pinned detail' : 'Hover preview'}</strong>
          <h3>{inspectorView.title}</h3>
          <p>{inspectorView.categoryLabel} | {inspectorView.subtitle}</p>
        </div>
      </div>

      {#if inspectorView.kind === 'entity'}
        <div class="combat-inspector-panel__metric-grid">
          {#each inspectorView.metrics as metric}
            <div class="combat-inspector-panel__metric-card">
              <strong>{metric.value}</strong>
              <span>{metric.label}</span>
              <p>{metric.note}</p>
            </div>
          {/each}
        </div>

        <div class="combat-inspector-panel__tag-row">
          {#each inspectorView.statusTags as tag}
            <TagChip label={tag.label} tone={tag.tone} />
          {/each}
        </div>

        <div class="combat-inspector-panel__copy-block">
          <strong>Summary</strong>
          {#each inspectorView.summaryLines as line}
            <p>{line}</p>
          {/each}
        </div>

        {#if inspectorView.detailLines.length > 0}
          <div class="combat-inspector-panel__copy-block">
            <strong>Details</strong>
            {#each inspectorView.detailLines as line}
              <p>{line}</p>
            {/each}
          </div>
        {/if}
      {:else}
        <div class="combat-inspector-panel__copy-block">
          <strong>Card body</strong>
          <p>{inspectorView.description}</p>
        </div>

        <div class="combat-inspector-panel__copy-block">
          <strong>Cost or type</strong>
          <p>{inspectorView.costOrType}</p>
        </div>

        {#if inspectorView.keywordTags.length > 0}
          <div class="combat-inspector-panel__tag-row">
            {#each inspectorView.keywordTags as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>
        {/if}

        <div class="combat-inspector-panel__copy-block">
          <strong>Rule summary</strong>
          {#each inspectorView.ruleLines as line}
            <p>{line}</p>
          {/each}
        </div>

        <div class="combat-inspector-panel__copy-block">
          <strong>Current availability</strong>
          <div class="combat-inspector-panel__tag-row">
            {#each inspectorView.selectionSummaries as summary}
              <TagChip label={summary.label} tone={summary.tone} />
            {/each}
          </div>
        </div>
      {/if}
    </article>
  {:else}
    <ContentStatePanel
      title="Inspector empty"
      message="상세 대상을 선택하세요. Hover는 임시 미리보기이고, click은 고정 상세 보기입니다."
    />
  {/if}
</div>

<style>
  .combat-inspector-panel,
  .combat-inspector-panel__surface,
  .combat-inspector-panel__hero,
  .combat-inspector-panel__hero-copy,
  .combat-inspector-panel__metric-grid,
  .combat-inspector-panel__tag-row,
  .combat-inspector-panel__copy-block {
    display: grid;
    gap: 1rem;
  }

  .combat-inspector-panel__surface,
  .combat-inspector-panel__metric-card {
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(160deg, rgba(33, 31, 29, 0.88), rgba(16, 14, 12, 0.76)),
      rgba(12, 11, 10, 0.28);
  }

  .combat-inspector-panel__surface {
    padding: 1rem;
  }

  .combat-inspector-panel__hero {
    grid-template-columns: 5.5rem minmax(0, 1fr);
    align-items: start;
  }

  .combat-inspector-panel__portrait {
    min-height: 7rem;
    border: 1px solid rgba(226, 193, 155, 0.22);
    background:
      radial-gradient(circle at 50% 24%, rgba(226, 193, 155, 0.18), transparent 40%),
      linear-gradient(155deg, rgba(16, 14, 12, 0.5), rgba(33, 31, 29, 0.94));
    display: grid;
    place-items: center;
  }

  .combat-inspector-panel__portrait--enemy {
    border-color: rgba(255, 180, 171, 0.38);
    background:
      radial-gradient(circle at 50% 24%, rgba(255, 179, 175, 0.18), transparent 40%),
      linear-gradient(155deg, rgba(55, 25, 24, 0.74), rgba(21, 19, 17, 0.94));
  }

  .combat-inspector-panel__portrait span {
    font-family: var(--font-display);
    font-size: 1.4rem;
    color: var(--combat-secondary, var(--color-accent));
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .combat-inspector-panel__hero-copy strong,
  .combat-inspector-panel__copy-block strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-inspector-panel__hero-copy h3,
  .combat-inspector-panel__hero-copy p,
  .combat-inspector-panel__copy-block p {
    margin: 0;
  }

  .combat-inspector-panel__hero-copy h3 {
    font-family: var(--font-display);
    font-size: 1.3rem;
  }

  .combat-inspector-panel__hero-copy p,
  .combat-inspector-panel__copy-block p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  .combat-inspector-panel__metric-grid {
    grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  }

  .combat-inspector-panel__metric-card {
    padding: 0.85rem;
  }

  .combat-inspector-panel__metric-card strong,
  .combat-inspector-panel__metric-card span,
  .combat-inspector-panel__metric-card p {
    display: block;
    margin: 0;
  }

  .combat-inspector-panel__metric-card span,
  .combat-inspector-panel__metric-card p {
    color: var(--combat-text-soft, var(--color-text-soft));
  }

  .combat-inspector-panel__tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  @media (max-width: 720px) {
    .combat-inspector-panel__hero {
      grid-template-columns: 1fr;
    }
  }
</style>
