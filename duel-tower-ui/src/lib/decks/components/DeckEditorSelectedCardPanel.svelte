<script lang="ts">
  import ContentStatePanel from '../../components/ContentStatePanel.svelte'
  import SectionFrame from '../../components/SectionFrame.svelte'
  import TagChip from '../../components/TagChip.svelte'
  import type { DeckEditorCardState } from '../editorModel'
  import {
    buildDeckCardNote,
    getDeckCardMetaLabel,
    getDeckCardTagItems,
  } from '../deckEditorView'

  let {
    selectedCardEntry,
    selectedCardPosition,
    totalEntries,
    controlsDisabled,
    onCardIdInput,
    onCountInput,
  }: {
    selectedCardEntry: DeckEditorCardState | null
    selectedCardPosition: number
    totalEntries: number
    controlsDisabled: boolean
    onCardIdInput: (value: string) => void
    onCountInput: (value: number) => void
  } = $props()
</script>

<SectionFrame
  title="Selected card"
  description="This panel only manages local input state for the currently selected entry."
>
  {#if selectedCardEntry}
    <div class="selected-card__detail">
      <div>
        <h3>{selectedCardEntry.cardId || 'Unnamed card reference'}</h3>
        <p>{getDeckCardMetaLabel(selectedCardEntry, selectedCardPosition)}</p>
      </div>

      <div class="selected-card__tags">
        {#each getDeckCardTagItems(selectedCardEntry, selectedCardPosition) as tag}
          <TagChip label={tag.label} tone={tag.tone} />
        {/each}
      </div>

      <p>{buildDeckCardNote(selectedCardEntry, selectedCardPosition, totalEntries)}</p>

      <fieldset class="selected-card__fieldset">
        <legend>Selected card draft</legend>

        <div class="selected-card__form-grid">
          <label class="selected-card__field selected-card__field--span-2">
            <span>Card id</span>
            <input
              type="text"
              value={selectedCardEntry.cardId}
              disabled={controlsDisabled}
              oninput={(event) => onCardIdInput((event.currentTarget as HTMLInputElement).value)}
            />
          </label>

          <label class="selected-card__field">
            <span>Count</span>
            <input
              type="number"
              min="1"
              step="1"
              value={selectedCardEntry.count}
              disabled={controlsDisabled}
              oninput={(event) => onCountInput((event.currentTarget as HTMLInputElement).valueAsNumber)}
            />
          </label>

          <div class="selected-card__field">
            <span>Entry key</span>
            <p class="selected-card__readonly">{selectedCardEntry.key}</p>
          </div>
        </div>
      </fieldset>

      <div class="selected-card__note">
        <p>Edits stay local until an action is invoked.</p>
        <p>Deck summary, dirty state, and validation freshness update immediately from the same local draft.</p>
      </div>
    </div>
  {:else}
    <ContentStatePanel
      title="No cards are assigned"
      message="This deck currently has no saved card entries."
    />
  {/if}
</SectionFrame>

<style>
  .selected-card__detail,
  .selected-card__note {
    display: grid;
    gap: 1.5rem;
  }

  .selected-card__detail {
    align-content: start;
  }

  .selected-card__detail h3,
  .selected-card__detail p,
  .selected-card__note p {
    margin: 0;
  }

  .selected-card__detail h3 {
    font-family: var(--font-display);
    font-size: 1.45rem;
  }

  .selected-card__detail > div:first-child p,
  .selected-card__detail > p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .selected-card__tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .selected-card__fieldset {
    display: grid;
    gap: 1rem;
    border: 1px solid var(--color-border);
    padding: 1rem;
    margin: 0;
  }

  .selected-card__fieldset legend {
    padding: 0 0.5rem;
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .selected-card__form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .selected-card__field {
    display: grid;
    gap: 0.5rem;
  }

  .selected-card__field--span-2 {
    grid-column: span 2;
  }

  .selected-card__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .selected-card__field input {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .selected-card__readonly {
    min-height: 3rem;
    display: flex;
    align-items: center;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    color: var(--color-text-soft);
  }

  .selected-card__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .selected-card__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .selected-card__form-grid {
      grid-template-columns: 1fr;
    }

    .selected-card__field--span-2 {
      grid-column: span 1;
    }
  }
</style>
