<script lang="ts">
  import TagChip from '../TagChip.svelte'
  import type { CombatCommandRequirementViewModel } from './types'

  type Props = {
    requirementView: CombatCommandRequirementViewModel | null
    sourceLabel: string | null
    detailLoading: boolean
    detailError: string | null
    selectedBoardObjectLabels: readonly string[]
    selectedDiscardIds: readonly string[]
    selectedFieldIds: readonly string[]
    selectedCount: number | null
    onClearTargets: () => void
    onClearSelectionInputs: () => void
  }

  let {
    requirementView,
    sourceLabel,
    detailLoading,
    detailError,
    selectedBoardObjectLabels,
    selectedDiscardIds,
    selectedFieldIds,
    selectedCount,
    onClearTargets,
    onClearSelectionInputs,
  }: Props = $props()
</script>

<div class="target-selection-panel">
  <strong>Selected command input</strong>
  <p>Command source: {requirementView?.sourceLabel ?? sourceLabel ?? 'Select a card or EX first'}</p>
  <p>Target rule: {requirementView?.targetSummary ?? 'No command-specific target rule loaded yet.'}</p>
  <p>Board-object rule: {requirementView?.boardObjectSummary ?? 'No board-object selection requirement loaded yet.'}</p>
  <p>Discard rule: {requirementView?.discardSummary ?? 'No extra hand discard required'}</p>
  <p>Field selection rule: {requirementView?.fieldSelectionSummary ?? 'No extra field selection required'}</p>
  <p>Choice rule: {requirementView?.choiceSummary ?? 'No explicit choice requirement'}</p>

  {#if requirementView?.boardCountChoiceOptions.length}
    <p>
      Count choice:
      {#if requirementView.boardCountChoiceRequired}
        choose one of {requirementView.boardCountChoiceOptions.join(', ')}
      {:else}
        auto-resolved from server hint
      {/if}
      {#if selectedCount != null}
        | current {selectedCount}
      {/if}
      {#if requirementView.boardCandidateCount != null}
        | candidates {requirementView.boardCandidateCount}
      {/if}
    </p>
  {/if}

  {#if detailLoading}
    <p>Loading card detail for the selected command source.</p>
  {:else if detailError}
    <p>{detailError}</p>
  {/if}

  <div class="target-selection-panel__tag-row">
    {#if selectedBoardObjectLabels.length > 0}
      {#each selectedBoardObjectLabels as label}
        <TagChip label={label} tone="warning" />
      {/each}
    {:else}
      <TagChip label="No board-object selection" tone="muted" />
    {/if}
  </div>

  <div class="target-selection-panel__tag-row">
    {#if selectedDiscardIds.length > 0}
      {#each selectedDiscardIds as discardId}
        <TagChip label={`Discard ${discardId}`} tone="accent" />
      {/each}
    {:else}
      <TagChip label="No discard ids" tone="muted" />
    {/if}
  </div>

  <div class="target-selection-panel__tag-row">
    {#if selectedFieldIds.length > 0}
      {#each selectedFieldIds as selectedId}
        <TagChip label={`Field ${selectedId}`} tone="accent" />
      {/each}
    {:else}
      <TagChip label="No field ids" tone="muted" />
    {/if}
  </div>

  <div class="target-selection-panel__actions">
    <button type="button" onclick={() => onClearTargets()}>
      Clear targets
    </button>
    <button type="button" onclick={() => onClearSelectionInputs()}>
      Clear follow-up inputs
    </button>
  </div>
</div>

<style>
  .target-selection-panel,
  .target-selection-panel__tag-row,
  .target-selection-panel__actions {
    display: grid;
    gap: 0.75rem;
  }

  .target-selection-panel__tag-row,
  .target-selection-panel__actions {
    display: flex;
    flex-wrap: wrap;
  }

  .target-selection-panel strong,
  .target-selection-panel p {
    margin: 0;
  }

  .target-selection-panel strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .target-selection-panel p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  .target-selection-panel__actions button {
    min-height: 2.5rem;
    padding: 0.6rem 0.85rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
    color: var(--combat-text, var(--color-text));
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .target-selection-panel__actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .target-selection-panel__actions button:not(:disabled):active {
    transform: scale(0.98);
  }
</style>
