<script lang="ts">
  import ContentStatePanel from '../../components/ContentStatePanel.svelte'
  import SectionFrame from '../../components/SectionFrame.svelte'
  import TagChip from '../../components/TagChip.svelte'

  type TagTone = 'accent' | 'muted' | 'success' | 'warning'

  type DeckEntryItem = {
    key: string
    ownedCardId: string
    cardId: string
    title: string
    subtitle: string
    tags: {
      label: string
      tone: TagTone
    }[]
    canRemove: boolean
    reasonCodes: string[]
    lockedInDeck: boolean
    inSavedDeck: boolean
    previewPending: boolean
    unresolved: boolean
  }

  let {
    entries,
    controlsDisabled,
    onRemove,
  }: {
    entries: DeckEntryItem[]
    controlsDisabled: boolean
    onRemove: (ownedCardId: string) => void
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
  title="Current deck"
  description="현재 덱 카드 목록입니다."
>
  {#if !entries.length}
    <ContentStatePanel
      title="No deck entries"
      message="표시할 카드가 없습니다."
    />
  {:else}
    <div class="current-deck-panel">
      {#each entries as entry}
        <article class="current-deck-panel__entry">
          <div class="current-deck-panel__copy">
            <div class="current-deck-panel__title-row">
              <h4>{entry.title}</h4>
              <span>{entry.ownedCardId}</span>
            </div>
            <p>{entry.subtitle}</p>
            <div class="current-deck-panel__tags">
              {#each entry.tags as tag}
                <TagChip label={tag.label} tone={tag.tone} />
              {/each}
              {#if entry.lockedInDeck}
                <TagChip label="Locked" tone="warning" />
              {/if}
              {#if entry.inSavedDeck}
                <TagChip label="Saved" tone="muted" />
              {/if}
              {#if entry.unresolved}
                <TagChip label="Unresolved" tone="warning" />
              {/if}
              {#if entry.previewPending}
                <TagChip label="Preview pending" tone="muted" />
              {/if}
            </div>
            {#if entry.reasonCodes.length}
              <div class="current-deck-panel__reasons">
                {#each entry.reasonCodes as reasonCode}
                  <TagChip label={formatReasonCode(reasonCode)} tone="warning" />
                {/each}
              </div>
            {/if}
          </div>

          <button
            type="button"
            disabled={controlsDisabled || !entry.canRemove || entry.previewPending}
            onclick={() => onRemove(entry.ownedCardId)}
          >
            Remove
          </button>
        </article>
      {/each}
    </div>
  {/if}
</SectionFrame>

<style>
  .current-deck-panel {
    display: grid;
    gap: 0.9rem;
  }

  .current-deck-panel__entry {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 1rem;
    padding: 0.95rem 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    align-items: start;
  }

  .current-deck-panel__copy {
    display: grid;
    gap: 0.6rem;
  }

  .current-deck-panel__title-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
    align-items: baseline;
  }

  .current-deck-panel__title-row h4,
  .current-deck-panel__copy p,
  .current-deck-panel__title-row span {
    margin: 0;
  }

  .current-deck-panel__title-row h4 {
    font-size: 1rem;
    font-weight: 600;
  }

  .current-deck-panel__title-row span,
  .current-deck-panel__copy p {
    color: var(--color-text-muted);
    line-height: 1.5;
  }

  .current-deck-panel__title-row span {
    font-size: 0.82rem;
    letter-spacing: 0.05em;
  }

  .current-deck-panel__tags,
  .current-deck-panel__reasons {
    display: flex;
    flex-wrap: wrap;
    gap: 0.45rem;
  }

  .current-deck-panel__entry button {
    min-height: 2.85rem;
    min-width: 7rem;
    padding: 0.7rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  @media (max-width: 720px) {
    .current-deck-panel__entry {
      grid-template-columns: 1fr;
    }

    .current-deck-panel__entry button {
      width: 100%;
    }
  }
</style>
