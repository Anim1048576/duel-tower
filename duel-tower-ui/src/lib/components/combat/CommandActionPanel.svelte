<script lang="ts">
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import TargetSelectionPanel from './TargetSelectionPanel.svelte'
  import type {
    CombatCommandRequirementViewModel,
    CombatPendingDecisionViewModel,
    CommandOptionViewModel,
  } from './types'

  type Props = {
    commandOptions: readonly CommandOptionViewModel[]
    commandPending: string | null
    selectedCommandType: string | null
    commandGuardMessage: string
    isCurrentTurnPlayer: boolean
    hasPendingDecision: boolean
    exAvailable: boolean
    recentCommandEventCount: number
    requirementView: CombatCommandRequirementViewModel | null
    sourceLabel: string | null
    detailLoading: boolean
    detailError: string | null
    selectedBoardObjectLabels: readonly string[]
    selectedDiscardIds: readonly string[]
    selectedFieldIds: readonly string[]
    pendingDecision: CombatPendingDecisionViewModel | null
    unsupportedPendingDecisionMessage: string | null
    pendingCandidateIds: readonly string[]
    orderedTieActorKeys: readonly string[]
    canResolvePendingCommand: boolean
    selectedCount: number | null
    selectedReason: string
    boardCountChoiceOptions: readonly number[]
    boardCountChoiceRequired: boolean
    onCommandButtonClick: (commandType: string) => void
    onClearTargets: () => void
    onClearSelectionInputs: () => void
    onSelectedCountChange: (value: string) => void
    onSelectedReasonChange: (value: string) => void
    onTogglePendingSelectedId: (value: string) => void
    onToggleOrderedActorKey: (actorKey: string) => void
    onResolvePendingDecision: () => void
  }

  let {
    commandOptions,
    commandPending,
    selectedCommandType,
    commandGuardMessage,
    isCurrentTurnPlayer,
    hasPendingDecision,
    exAvailable,
    recentCommandEventCount,
    requirementView,
    sourceLabel,
    detailLoading,
    detailError,
    selectedBoardObjectLabels,
    selectedDiscardIds,
    selectedFieldIds,
    pendingDecision,
    unsupportedPendingDecisionMessage,
    pendingCandidateIds,
    orderedTieActorKeys,
    canResolvePendingCommand,
    selectedCount,
    selectedReason,
    boardCountChoiceOptions,
    boardCountChoiceRequired,
    onCommandButtonClick,
    onClearTargets,
    onClearSelectionInputs,
    onSelectedCountChange,
    onSelectedReasonChange,
    onTogglePendingSelectedId,
    onToggleOrderedActorKey,
    onResolvePendingDecision,
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

<div
  class="command-action-panel"
  class:command-action-panel--blocked={!isCurrentTurnPlayer}
  class:command-action-panel--pending={hasPendingDecision}
>
  <div class="command-action-panel__heading">
    <strong>Command foundation</strong>
    <span>{isCurrentTurnPlayer ? 'Command channel open' : 'Read-only or not your turn'}</span>
  </div>

  <div class="command-action-panel__command-list">
    {#each commandOptions as option}
      <button
        type="button"
        disabled={option.disabled || commandPending !== null}
        class:selected={selectedCommandType === option.id}
        onclick={() => onCommandButtonClick(option.id)}
      >
        <span>{commandPending === option.id ? `${option.title}...` : option.title}</span>
        <small>{option.note}</small>
      </button>
    {/each}
  </div>

  <ContentStatePanel
    title="Current command guards"
    message={commandGuardMessage}
  >
    <p>Current turn matches runtime player: {isCurrentTurnPlayer ? 'Yes' : 'No'}</p>
    <p>Pending decision: {hasPendingDecision ? 'Present' : 'None'}</p>
    <p>EX available: {exAvailable ? 'Yes' : 'No'}</p>
    <p>Recent command events buffered: {recentCommandEventCount}</p>
  </ContentStatePanel>

  <div class="command-action-panel__zone-panel">
    <TargetSelectionPanel
      requirementView={requirementView}
      sourceLabel={sourceLabel}
      detailLoading={detailLoading}
      detailError={detailError}
      selectedBoardObjectLabels={selectedBoardObjectLabels}
      selectedDiscardIds={selectedDiscardIds}
      selectedFieldIds={selectedFieldIds}
      {selectedCount}
      onClearTargets={onClearTargets}
      onClearSelectionInputs={onClearSelectionInputs}
    />
  </div>

  <div class="command-action-panel__zone-panel">
    <strong>Command helper inputs</strong>
    {#if boardCountChoiceOptions.length > 0}
      <label class="command-action-panel__field-control">
        <span>{boardCountChoiceRequired ? 'Choose board-object count first' : 'Board-object count'}</span>
        <div class="command-action-panel__tag-row">
          {#each boardCountChoiceOptions as option}
            <button
              type="button"
              class="command-action-panel__inline-button"
              class:selected={selectedCount === option}
              onclick={() => onSelectedCountChange(String(option))}
            >
              {option}
            </button>
          {/each}
        </div>
      </label>
    {/if}

    <label class="command-action-panel__field-control">
      <span>Selected count</span>
      <input type="number" min="1" value={selectedCount ?? 1} oninput={handleCountInput} />
    </label>

    <label class="command-action-panel__field-control">
      <span>Selected reason</span>
      <textarea
        rows="3"
        value={selectedReason}
        placeholder="Reason for the next command or pending resolution"
        oninput={handleReasonInput}
      ></textarea>
    </label>

    <div class="command-action-panel__actions">
      <button type="button" onclick={() => onClearTargets()}>
        Clear targets
      </button>
      <button type="button" onclick={() => onClearSelectionInputs()}>
        Clear helper inputs
      </button>
    </div>
  </div>

  {#if pendingDecision}
    <div class="command-action-panel__zone-panel command-action-panel__zone-panel--pending">
      <strong>Pending decision</strong>
      <p>Type: {pendingDecision.type ?? 'Unavailable'}</p>
      <p>Reason: {pendingDecision.reason ?? 'None'}</p>
      <p>Limit: {pendingDecision.limit ?? 'N/A'} | Pick count: {pendingDecision.pickCount ?? 'N/A'}</p>
      <p>Destination: {pendingDecision.destination ?? 'N/A'} | Shuffle after pick: {pendingDecision.shuffleAfterPick ? 'Yes' : 'No'}</p>
      <p>Group index: {pendingDecision.groupIndex ?? 'N/A'}</p>
      <p>Actor keys: {pendingDecision.actorKeys.join(', ') || 'None'}</p>
      <p>Selected hand discards: {selectedDiscardIds.length} | Selected candidate ids: {pendingCandidateIds.length} | Ordered tie actors: {orderedTieActorKeys.length}</p>

      {#if unsupportedPendingDecisionMessage}
        <ContentStatePanel
          title="Pending decision is read-only"
          message={unsupportedPendingDecisionMessage}
        />
      {:else}
        {#if pendingDecision.candidateIds.length > 0}
          <div class="command-action-panel__tag-row">
            {#each pendingDecision.candidateIds as candidateId}
              <button
                type="button"
                class="command-action-panel__inline-button"
                class:selected={pendingCandidateIds.includes(candidateId)}
                onclick={() => onTogglePendingSelectedId(candidateId)}
              >
                {pendingCandidateIds.includes(candidateId) ? `Selected ${candidateId}` : candidateId}
              </button>
            {/each}
          </div>
        {/if}

        {#if pendingDecision.actorKeys.length > 0}
          <div class="command-action-panel__tag-row">
            {#each pendingDecision.actorKeys as actorKey}
              <button
                type="button"
                class="command-action-panel__inline-button"
                class:selected={orderedTieActorKeys.includes(actorKey)}
                onclick={() => onToggleOrderedActorKey(actorKey)}
              >
                {orderedTieActorKeys.includes(actorKey) ? `Ordered ${actorKey}` : actorKey}
              </button>
            {/each}
          </div>
        {/if}

        <div class="command-action-panel__actions">
          <button
            type="button"
            disabled={!canResolvePendingCommand || commandPending !== null}
            onclick={() => onResolvePendingDecision()}
          >
            {commandPending && selectedCommandType === pendingDecision.type
              ? 'Resolving pending decision...'
              : 'Resolve pending decision'}
          </button>
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .command-action-panel,
  .command-action-panel__heading,
  .command-action-panel__command-list,
  .command-action-panel__zone-panel,
  .command-action-panel__field-control {
    display: grid;
    gap: 1rem;
  }

  .command-action-panel__command-list {
    gap: 0.75rem;
  }

  .command-action-panel__heading {
    gap: 0.25rem;
    padding-bottom: 0.65rem;
    border-bottom: 1px solid rgba(226, 193, 155, 0.18);
  }

  .command-action-panel__heading span {
    color: var(--combat-muted, var(--color-text-muted));
    font-size: 0.78rem;
  }

  .command-action-panel__command-list button,
  .command-action-panel__inline-button,
  .command-action-panel__actions button,
  .command-action-panel__field-control input,
  .command-action-panel__field-control textarea {
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
    color: var(--combat-text, var(--color-text));
  }

  .command-action-panel__command-list button {
    padding: 0.85rem 0.95rem;
    display: grid;
    gap: 0.4rem;
    text-align: left;
    position: relative;
    overflow: hidden;
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .command-action-panel__command-list button::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: var(--combat-secondary, var(--color-accent));
    opacity: 0.2;
  }

  .command-action-panel__command-list button:not(:disabled):hover {
    transform: translateX(-2px);
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .command-action-panel__command-list button:not(:disabled):active,
  .command-action-panel__inline-button:not(:disabled):active,
  .command-action-panel__actions button:not(:disabled):active {
    transform: scale(0.98);
  }

  .command-action-panel__command-list button:disabled {
    opacity: 0.42;
  }

  .command-action-panel__command-list button.selected {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.44);
    box-shadow: var(--combat-focus, 0 0 0 1px rgba(255, 179, 175, 0.42));
  }

  .command-action-panel__command-list button.selected::before {
    opacity: 1;
    background: var(--combat-primary, var(--color-accent-strong));
  }

  .command-action-panel__command-list button span,
  .command-action-panel strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .command-action-panel__command-list button small,
  .command-action-panel__zone-panel p,
  .command-action-panel__field-control span {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  .command-action-panel__zone-panel {
    padding: 1rem;
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(160deg, rgba(44, 41, 39, 0.84), rgba(16, 14, 12, 0.76)),
      rgba(12, 11, 10, 0.28);
  }

  .command-action-panel__zone-panel strong,
  .command-action-panel__zone-panel p {
    margin: 0;
  }

  .command-action-panel__field-control input,
  .command-action-panel__field-control textarea {
    width: 100%;
    padding: 0.75rem 0.85rem;
    outline: none;
  }

  .command-action-panel__field-control input:focus,
  .command-action-panel__field-control textarea:focus {
    border-color: rgba(226, 193, 155, 0.5);
    box-shadow: 0 0 0 1px rgba(226, 193, 155, 0.16);
  }

  .command-action-panel__zone-panel--pending {
    border-color: rgba(255, 179, 175, 0.56);
    background:
      linear-gradient(160deg, rgba(107, 24, 26, 0.46), rgba(21, 19, 17, 0.88)),
      rgba(12, 11, 10, 0.28);
    box-shadow: 0 0 0 1px rgba(255, 179, 175, 0.12), 0 18px 60px rgba(107, 24, 26, 0.18);
  }

  .command-action-panel__tag-row,
  .command-action-panel__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .command-action-panel__inline-button,
  .command-action-panel__actions button {
    min-height: 2.5rem;
    padding: 0.55rem 0.8rem;
    transition:
      transform 120ms ease,
      border-color 120ms ease,
      background 120ms ease;
  }

  .command-action-panel__inline-button:not(:disabled):hover,
  .command-action-panel__actions button:not(:disabled):hover {
    border-color: rgba(226, 193, 155, 0.45);
    background: rgba(226, 193, 155, 0.1);
  }

  .command-action-panel--blocked {
    filter: saturate(0.86);
  }

  .command-action-panel--pending .command-action-panel__heading {
    border-bottom-color: rgba(255, 179, 175, 0.42);
  }

  .command-action-panel :global(.selected) {
    border-color: rgba(255, 179, 175, 0.62);
    background: rgba(107, 24, 26, 0.38);
  }
</style>
