<script lang="ts">
  import ContentStatePanel from '../../components/ContentStatePanel.svelte'
  import SectionFrame from '../../components/SectionFrame.svelte'
  import TagChip from '../../components/TagChip.svelte'

  type TagTone = 'accent' | 'muted' | 'success' | 'warning'

  type DeckPoolOwnedCardItem = {
    key: string
    ownedCardId: string
    cardId: string
    title: string
    subtitle: string
    tags: {
      label: string
      tone: TagTone
    }[]
    inDraftDeck: boolean
    canAdd: boolean
    reasonCodes: string[]
    unresolved: boolean
  }

  type DeckPoolGroupItem = {
    key: string
    cardId: string
    title: string
    subtitle: string
    tags: {
      label: string
      tone: TagTone
    }[]
    currentDeckCount: number
    totalOwnedCount: number
    availableOwnedCount: number
    canAdd: boolean
    reasonCodes: string[]
    ownedCards: DeckPoolOwnedCardItem[]
  }

  let {
    groups,
    controlsDisabled,
    previewPending,
    onAdd,
  }: {
    groups: DeckPoolGroupItem[]
    controlsDisabled: boolean
    previewPending: boolean
    onAdd: (ownedCardId: string) => void
  } = $props()

  function formatReasonCode(reasonCode: string) {
    return reasonCode
      .split('_')
      .filter(Boolean)
      .map((token) => token.charAt(0) + token.slice(1).toLowerCase())
      .join(' ')
  }
</script>

<SectionFrame
  title="Card pool"
  description="Card groups and add availability come directly from the latest server preview. Each owned card copy keeps its own add state."
>
  {#if !groups.length}
    <ContentStatePanel
      title={previewPending ? 'Refreshing preview' : 'No card groups'}
      message={previewPending
        ? 'Waiting for the current server preview before rendering the latest add availability.'
        : 'No card pool groups are available for the current draft.'}
    />
  {:else}
    <div class="deck-pool-panel">
      {#each groups as group}
        <article class="deck-pool-panel__group">
          <div class="deck-pool-panel__header">
            <div>
              <h4>{group.title}</h4>
              <p>{group.subtitle}</p>
            </div>
            <div class="deck-pool-panel__counts">
              <TagChip label={`Deck ${group.currentDeckCount}`} tone="accent" />
              <TagChip label={`Owned ${group.totalOwnedCount}`} tone="muted" />
              <TagChip label={`Open ${group.availableOwnedCount}`} tone={group.availableOwnedCount > 0 ? 'success' : 'warning'} />
            </div>
          </div>

          <div class="deck-pool-panel__tags">
            {#each group.tags as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
            {#each group.reasonCodes as reasonCode}
              <TagChip label={formatReasonCode(reasonCode)} tone="warning" />
            {/each}
          </div>

          <div class="deck-pool-panel__owned-cards">
            {#each group.ownedCards as ownedCard}
              <button
                type="button"
                class="deck-pool-panel__owned-card"
                disabled={controlsDisabled || !ownedCard.canAdd || previewPending}
                onclick={() => onAdd(ownedCard.ownedCardId)}
              >
                <strong>{ownedCard.title}</strong>
                <span>{ownedCard.subtitle}</span>
                <small>{ownedCard.ownedCardId}</small>
                <div class="deck-pool-panel__owned-card-tags">
                  {#each ownedCard.tags as tag}
                    <TagChip label={tag.label} tone={tag.tone} />
                  {/each}
                  {#if ownedCard.inDraftDeck}
                    <TagChip label="In draft" tone="muted" />
                  {/if}
                  {#if ownedCard.unresolved}
                    <TagChip label="Unresolved" tone="warning" />
                  {/if}
                  {#each ownedCard.reasonCodes as reasonCode}
                    <TagChip label={formatReasonCode(reasonCode)} tone="warning" />
                  {/each}
                </div>
              </button>
            {/each}
          </div>
        </article>
      {/each}
    </div>
  {/if}
</SectionFrame>

<style>
  .deck-pool-panel {
    display: grid;
    gap: 1rem;
  }

  .deck-pool-panel__group {
    display: grid;
    gap: 0.85rem;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .deck-pool-panel__header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: start;
    flex-wrap: wrap;
  }

  .deck-pool-panel__header h4,
  .deck-pool-panel__header p {
    margin: 0;
  }

  .deck-pool-panel__header p {
    color: var(--color-text-muted);
    line-height: 1.5;
  }

  .deck-pool-panel__counts,
  .deck-pool-panel__tags,
  .deck-pool-panel__owned-card-tags {
    display: flex;
    gap: 0.45rem;
    flex-wrap: wrap;
  }

  .deck-pool-panel__owned-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
    gap: 0.75rem;
  }

  .deck-pool-panel__owned-card {
    display: grid;
    gap: 0.45rem;
    padding: 0.85rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.32);
    color: var(--color-text);
    text-align: left;
  }

  .deck-pool-panel__owned-card strong,
  .deck-pool-panel__owned-card span,
  .deck-pool-panel__owned-card small {
    margin: 0;
  }

  .deck-pool-panel__owned-card span,
  .deck-pool-panel__owned-card small {
    color: var(--color-text-muted);
    line-height: 1.45;
  }
</style>
