<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import CombatHandCard from './CombatHandCard.svelte'
  import type { ResolvedCombatCardViewModel } from './types'

  type Props = {
    handCards: readonly ResolvedCombatCardViewModel[]
    selectedCardId: string | null
    selectedDiscardIds: readonly string[]
    selectedCommandType: string | null
    expectedVersion: number | null
    currentActorLabel: string
    visibleHandOwner: string | null
    selectedActor: string | null
    selectedEnemyId: string | null
    selectedCardLabel: string | null
    pendingDecisionType: string | null
    selectedTargetCount: number
    selectedIdCount: number
    orderedActorKeysSummary: string
    targetRefSummary: string
    selectedDiscardCount: number
    selectedFieldCount: number
    selectedCount: number | null
    pendingCandidateCount: number
    bufferedEventCount: number
    runNodeSummary: string
    selectedReason: string
    catalogLoading: boolean
    emptyMessage: string
    onSelectHandCard: (instanceId: string) => void
    onToggleDiscard: (instanceId: string) => void
    onSelectedCountChange: (value: string) => void
    onSelectedReasonChange: (value: string) => void
    onClearTargets: () => void
    onClearSelectionInputs: () => void
  }

  let {
    handCards,
    selectedCardId,
    selectedDiscardIds,
    selectedCommandType,
    expectedVersion,
    currentActorLabel,
    visibleHandOwner,
    selectedActor,
    selectedEnemyId,
    selectedCardLabel,
    pendingDecisionType,
    selectedTargetCount,
    selectedIdCount,
    orderedActorKeysSummary,
    targetRefSummary,
    selectedDiscardCount,
    selectedFieldCount,
    selectedCount,
    pendingCandidateCount,
    bufferedEventCount,
    runNodeSummary,
    selectedReason,
    catalogLoading,
    emptyMessage,
    onSelectHandCard,
    onToggleDiscard,
    onSelectedCountChange,
    onSelectedReasonChange,
    onClearTargets,
    onClearSelectionInputs,
  }: Props = $props()

  function handleCountInput(event: Event) {
    const target = event.currentTarget as HTMLInputElement
    onSelectedCountChange(target.value)
  }

  function handleReasonInput(event: Event) {
    const target = event.currentTarget as HTMLTextAreaElement
    onSelectedReasonChange(target.value)
  }
</script>

<SectionFrame
  title="Hand and action bar"
  description="The bottom strip keeps the visible hand and action context readable while command wiring stays otherwise unchanged."
>
  <div class="hand-bar">
    <div class="hand-bar__cards">
      {#if handCards.length > 0}
        {#each handCards as card}
          <CombatHandCard
            {card}
            selected={selectedCardId === card.instanceId}
            discardSelected={selectedDiscardIds.includes(card.instanceId)}
            onSelect={onSelectHandCard}
            onToggleDiscard={onToggleDiscard}
          />
        {/each}
      {:else}
        <ContentStatePanel
          title="No visible hand yet"
          message={catalogLoading
            ? 'Loading the card archive before resolving live hand cards.'
            : emptyMessage}
        />
      {/if}
    </div>

    <div class="hand-bar__summary">
      <strong>Selected action</strong>
      <p>Command: {selectedCommandType ?? 'Not selected'}</p>
      <p>Expected version: {expectedVersion ?? 'Unavailable'}</p>
      <p>Current actor: {currentActorLabel}</p>
      <p>Visible hand owner: {visibleHandOwner ?? 'Unavailable'}</p>
      <p>Selected actor: {selectedActor ?? 'Not selected'}</p>
      <p>Selected target: {selectedEnemyId ?? 'Target refs below'}</p>
      <p>Selected card: {selectedCardLabel ?? 'Not selected'}</p>
      <p>Pending decision: {pendingDecisionType ?? 'None'}</p>
      <p>Selected targets: {selectedTargetCount} | Selected ids: {selectedIdCount}</p>
      <p>Ordered actor keys: {orderedActorKeysSummary}</p>
      <p>Target refs: {targetRefSummary}</p>
      <p>Discard ids from hand: {selectedDiscardCount} | Field ids: {selectedFieldCount}</p>
      <p>Count: {selectedCount ?? 'N/A'} | Pending candidate ids: {pendingCandidateCount}</p>
      <p>Buffered events after command: {bufferedEventCount}</p>
      <p>{runNodeSummary}</p>

      <label class="hand-bar__field-control">
        <span>Selected count</span>
        <input type="number" min="1" value={selectedCount ?? 1} oninput={handleCountInput} />
      </label>

      <label class="hand-bar__field-control">
        <span>Selected reason</span>
        <textarea
          rows="3"
          value={selectedReason}
          placeholder="Reason for the next command or pending resolution"
          oninput={handleReasonInput}
        ></textarea>
      </label>

      <div class="hand-bar__actions">
        <button type="button" onclick={() => onClearTargets()}>
          Clear targets
        </button>
        <button type="button" onclick={() => onClearSelectionInputs()}>
          Clear helper inputs
        </button>
      </div>
    </div>
  </div>
</SectionFrame>

<style>
  .hand-bar,
  .hand-bar__cards,
  .hand-bar__summary,
  .hand-bar__field-control,
  .hand-bar__actions {
    display: grid;
    gap: 1rem;
  }

  .hand-bar {
    grid-template-columns: minmax(0, 2fr) minmax(18rem, 0.86fr);
    align-items: start;
    padding: 1rem;
    border: 1px solid rgba(226, 193, 155, 0.2);
    background:
      linear-gradient(180deg, rgba(21, 19, 17, 0.96), rgba(16, 14, 12, 0.95)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 -18px 60px rgba(0, 0, 0, 0.26);
  }

  .hand-bar__cards {
    grid-template-columns: repeat(auto-fit, minmax(10.5rem, 1fr));
    align-items: end;
  }

  .hand-bar__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .hand-bar__summary,
  .hand-bar__field-control input,
  .hand-bar__field-control textarea,
  .hand-bar__actions button {
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
  }

  .hand-bar__summary {
    padding: 1rem;
  }

  .hand-bar__summary p,
  .hand-bar__field-control span {
    margin: 0;
  }

  .hand-bar__summary p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  .hand-bar__summary strong,
  .hand-bar__field-control span {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .hand-bar__field-control input,
  .hand-bar__field-control textarea {
    width: 100%;
    padding: 0.75rem 0.85rem;
    color: var(--combat-text, var(--color-text));
    outline: none;
  }

  .hand-bar__field-control input:focus,
  .hand-bar__field-control textarea:focus {
    border-color: rgba(226, 193, 155, 0.5);
    box-shadow: 0 0 0 1px rgba(226, 193, 155, 0.16);
  }

  .hand-bar__actions button {
    min-height: 2.5rem;
    padding: 0.6rem 0.85rem;
    color: var(--combat-text, var(--color-text));
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .hand-bar__actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .hand-bar__actions button:not(:disabled):active {
    transform: scale(0.98);
  }

  @media (max-width: 1080px) {
    .hand-bar {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 720px) {
    .hand-bar {
      padding: 0.75rem;
    }
  }
</style>
