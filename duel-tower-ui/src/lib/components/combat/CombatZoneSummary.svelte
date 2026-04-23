<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import TagChip from '../TagChip.svelte'
  import type { CombatPlayerViewModel, ResolvedCombatCardViewModel } from './types'

  type Props = {
    visiblePlayerView: CombatPlayerViewModel | null
    selectedFieldIds: readonly string[]
    onToggleSelectedId: (instanceId: string) => void
    canToggleSelectedId: (instanceId: string) => boolean
  }

  let { visiblePlayerView, selectedFieldIds, onToggleSelectedId, canToggleSelectedId }: Props = $props()

  function archiveTone(card: ResolvedCombatCardViewModel) {
    return card.unresolved ? 'warning' : 'muted'
  }
</script>

<div class="combat-zone-summary">
  <strong>Current player zones</strong>

  {#if visiblePlayerView}
    <div class="combat-zone-summary__zone-grid">
      <div class="combat-zone-summary__zone-panel">
        <strong>Field</strong>
        {#if visiblePlayerView.fieldCards.length > 0}
          {#each visiblePlayerView.fieldCards as card}
            <article
              class="combat-zone-summary__card-row"
              class:selected={selectedFieldIds.includes(card.instanceId)}
            >
              <div>
                <span>{card.title}</span>
                <small>{card.subtitle}</small>
              </div>
              <div class="combat-zone-summary__tag-row">
                <TagChip label={card.unresolved ? 'Unresolved' : 'Field'} tone={card.unresolved ? 'warning' : 'success'} />
                <button
                  type="button"
                  class="combat-zone-summary__inline-button"
                  disabled={!selectedFieldIds.includes(card.instanceId) && !canToggleSelectedId(card.instanceId)}
                  onclick={() => onToggleSelectedId(card.instanceId)}
                >
                  {selectedFieldIds.includes(card.instanceId) ? 'Unmark field id' : 'Select field id'}
                </button>
              </div>
            </article>
          {/each}
        {:else}
          <p>No field cards are active for this player.</p>
        {/if}
      </div>

      <div class="combat-zone-summary__zone-panel">
        <strong>Grave and excluded</strong>
        {#each visiblePlayerView.graveCards as card}
          <article class="combat-zone-summary__card-row">
            <div>
              <span>{card.title}</span>
              <small>Grave | {card.subtitle}</small>
            </div>
            <TagChip label={card.unresolved ? 'Unresolved' : 'Grave'} tone={archiveTone(card)} />
          </article>
        {/each}

        {#each visiblePlayerView.excludedCards as card}
          <article class="combat-zone-summary__card-row">
            <div>
              <span>{card.title}</span>
              <small>Excluded | {card.subtitle}</small>
            </div>
            <TagChip label={card.unresolved ? 'Unresolved' : 'Excluded'} tone={archiveTone(card)} />
          </article>
        {/each}

        {#if visiblePlayerView.graveCards.length === 0 && visiblePlayerView.excludedCards.length === 0}
          <p>표시할 묘지/제외 카드가 없습니다.</p>
        {/if}
      </div>
    </div>
  {:else}
    <ContentStatePanel
      title="Zone summary unavailable"
      message="표시할 플레이어 상태가 없습니다."
    />
  {/if}
</div>

<style>
  .combat-zone-summary,
  .combat-zone-summary__zone-grid {
    display: grid;
    gap: 1rem;
  }

  .combat-zone-summary,
  .combat-zone-summary__zone-panel,
  .combat-zone-summary__card-row {
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(160deg, rgba(33, 31, 29, 0.88), rgba(16, 14, 12, 0.76)),
      rgba(12, 11, 10, 0.28);
  }

  .combat-zone-summary,
  .combat-zone-summary__zone-panel {
    padding: 1rem;
  }

  .combat-zone-summary__zone-panel {
    display: grid;
    gap: 0.9rem;
  }

  .combat-zone-summary > strong,
  .combat-zone-summary__zone-panel > strong,
  .combat-zone-summary__zone-panel p {
    margin: 0;
  }

  .combat-zone-summary > strong,
  .combat-zone-summary__zone-panel > strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-zone-summary__zone-panel p,
  .combat-zone-summary__card-row small {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  .combat-zone-summary__card-row {
    padding: 0.85rem;
    display: flex;
    justify-content: space-between;
    gap: 0.8rem;
    align-items: flex-start;
    transition:
      border-color 120ms ease,
      background 120ms ease,
      transform 120ms ease;
  }

  .combat-zone-summary__card-row:hover {
    border-color: rgba(226, 193, 155, 0.38);
    transform: translateX(-2px);
  }

  .combat-zone-summary__card-row span,
  .combat-zone-summary__card-row small {
    display: block;
    margin: 0;
  }

  .combat-zone-summary__tag-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .combat-zone-summary__inline-button {
    min-height: 2.25rem;
    padding: 0.5rem 0.75rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
    color: var(--combat-text, var(--color-text));
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .combat-zone-summary__inline-button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .combat-zone-summary__inline-button:not(:disabled):active {
    transform: scale(0.98);
  }

  .combat-zone-summary :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.38);
    box-shadow: 0 0 0 1px rgba(255, 179, 175, 0.12);
  }
</style>
