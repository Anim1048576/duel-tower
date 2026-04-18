<script lang="ts">
  import SectionFrame from '../SectionFrame.svelte'
  import type { PendingDecisionDto } from '../../api/sessionTypes'
  import CombatLogPanel from './CombatLogPanel.svelte'
  import CombatResultsPanel from './CombatResultsPanel.svelte'
  import CommandActionPanel from './CommandActionPanel.svelte'
  import CombatZoneSummary from './CombatZoneSummary.svelte'
  import { COMBAT_SIDEBAR_TABS } from './types'
  import type {
    CombatCommandRequirementViewModel,
    CombatFeedEntry,
    CombatInspectorEntityReference,
    CombatPlayerViewModel,
    CombatRecentResultEntry,
    CombatSidebarTab,
    CommandOptionViewModel,
  } from './types'

  type Props = {
    activeTab: CombatSidebarTab
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
    selectedTargetLabels: readonly string[]
    selectedDiscardIds: readonly string[]
    selectedFieldIds: readonly string[]
    pendingDecision: PendingDecisionDto | null
    unsupportedPendingDecisionMessage: string | null
    pendingCandidateIds: readonly string[]
    orderedTieActorKeys: readonly string[]
    canResolvePendingCommand: boolean
    visiblePlayerView: CombatPlayerViewModel | null
    eventEntries: readonly CombatFeedEntry[]
    eventsLoading: boolean
    eventsErrorMessage: string | null
    logEntries: readonly CombatFeedEntry[]
    logsLoading: boolean
    logsErrorMessage: string | null
    recentResultEntries: readonly CombatRecentResultEntry[]
    recentResultsLoading: boolean
    recentResultsErrorMessage: string | null
    inspectorPlaceholderEntity: CombatInspectorEntityReference | null
    inspectorPlaceholderCardId: string | null
    onTabChange: (tab: CombatSidebarTab) => void
    onCommandButtonClick: (commandType: string) => void
    onClearTargets: () => void
    onClearSelectionInputs: () => void
    onTogglePendingSelectedId: (value: string) => void
    onToggleOrderedActorKey: (actorKey: string) => void
    onResolvePendingDecision: () => void
    onToggleSelectedId: (instanceId: string) => void
    onRetryEvents: () => void
    onRetryLogs: () => void
    onRetryResults: () => void
  }

  let {
    activeTab,
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
    selectedTargetLabels,
    selectedDiscardIds,
    selectedFieldIds,
    pendingDecision,
    unsupportedPendingDecisionMessage,
    pendingCandidateIds,
    orderedTieActorKeys,
    canResolvePendingCommand,
    visiblePlayerView,
    eventEntries,
    eventsLoading,
    eventsErrorMessage,
    logEntries,
    logsLoading,
    logsErrorMessage,
    recentResultEntries,
    recentResultsLoading,
    recentResultsErrorMessage,
    inspectorPlaceholderEntity,
    inspectorPlaceholderCardId,
    onTabChange,
    onCommandButtonClick,
    onClearTargets,
    onClearSelectionInputs,
    onTogglePendingSelectedId,
    onToggleOrderedActorKey,
    onResolvePendingDecision,
    onToggleSelectedId,
    onRetryEvents,
    onRetryLogs,
    onRetryResults,
  }: Props = $props()

  const tabLabels: Record<CombatSidebarTab, string> = {
    command: 'Command',
    log: 'Log',
    result: 'Result',
    inspector: 'Inspector',
  }

  function inspectorPlaceholderSummary(entity: CombatInspectorEntityReference | null) {
    if (!entity) {
      return 'No hovered or pinned entity yet.'
    }

    if (entity.kind === 'summon') {
      return `Summon ${entity.id} owned by ${entity.owner}`
    }

    return `${entity.kind === 'player' ? 'Player' : 'Enemy'} ${entity.id}`
  }
</script>

<SectionFrame
  title="Combat context and command"
  description="The sidebar now separates command, history, results, and future inspector context without changing the command flow."
>
  <div class="combat-sidebar">
    <div class="combat-sidebar__tab-list" role="tablist" aria-label="Combat sidebar tabs">
      {#each COMBAT_SIDEBAR_TABS as tab}
        <button
          type="button"
          class="combat-sidebar__tab-button"
          class:combat-sidebar__tab-button--active={activeTab === tab}
          role="tab"
          aria-selected={activeTab === tab}
          onclick={() => onTabChange(tab)}
        >
          {tabLabels[tab]}
        </button>
      {/each}
    </div>

    <div class="combat-sidebar__tab-panel" role="tabpanel">
      {#if activeTab === 'command'}
        <div class="combat-sidebar__tab-stack">
          <CommandActionPanel
            {commandOptions}
            {commandPending}
            {selectedCommandType}
            {commandGuardMessage}
            {isCurrentTurnPlayer}
            {hasPendingDecision}
            {exAvailable}
            {recentCommandEventCount}
            requirementView={requirementView}
            sourceLabel={sourceLabel}
            detailLoading={detailLoading}
            detailError={detailError}
            selectedTargetLabels={selectedTargetLabels}
            selectedDiscardIds={selectedDiscardIds}
            selectedFieldIds={selectedFieldIds}
            pendingDecision={pendingDecision}
            unsupportedPendingDecisionMessage={unsupportedPendingDecisionMessage}
            pendingCandidateIds={pendingCandidateIds}
            orderedTieActorKeys={orderedTieActorKeys}
            canResolvePendingCommand={canResolvePendingCommand}
            onCommandButtonClick={onCommandButtonClick}
            onClearTargets={onClearTargets}
            onClearSelectionInputs={onClearSelectionInputs}
            onTogglePendingSelectedId={onTogglePendingSelectedId}
            onToggleOrderedActorKey={onToggleOrderedActorKey}
            onResolvePendingDecision={onResolvePendingDecision}
          />

          <CombatZoneSummary
            {visiblePlayerView}
            selectedFieldIds={selectedFieldIds}
            onToggleSelectedId={onToggleSelectedId}
          />
        </div>
      {:else if activeTab === 'log'}
        <div class="combat-sidebar__tab-stack">
          <CombatLogPanel
            title="Recent events"
            loading={eventsLoading}
            loadingTitle="Loading events"
            loadingMessage="Restoring recent combat events for the current session."
            errorTitle="Events unavailable"
            errorMessage={eventsErrorMessage}
            retryLabel="Retry events"
            emptyTitle="No recent events"
            emptyMessage="No combat events have been restored for this session yet."
            entries={eventEntries}
            onRetry={onRetryEvents}
          />

          <CombatLogPanel
            title="Recent logs"
            loading={logsLoading}
            loadingTitle="Loading logs"
            loadingMessage="Restoring recent combat logs for the current session."
            errorTitle="Logs unavailable"
            errorMessage={logsErrorMessage}
            retryLabel="Retry logs"
            emptyTitle="No recent logs"
            emptyMessage="No combat log messages have been restored for this session yet."
            entries={logEntries}
            onRetry={onRetryLogs}
          />
        </div>
      {:else if activeTab === 'result'}
        <CombatResultsPanel
          loading={recentResultsLoading}
          errorMessage={recentResultsErrorMessage}
          entries={recentResultEntries}
          onRetry={onRetryResults}
        />
      {:else}
        <div class="combat-sidebar__inspector-placeholder">
          <strong>Inspector placeholder</strong>
          <p>Hover and pin driven inspector content will mount here in a later step.</p>
          <p>Entity focus: {inspectorPlaceholderSummary(inspectorPlaceholderEntity)}</p>
          <p>Hand card focus: {inspectorPlaceholderCardId ?? 'No hovered or pinned hand card yet.'}</p>
        </div>
      {/if}
    </div>
  </div>
</SectionFrame>

<style>
  .combat-sidebar,
  .combat-sidebar__tab-panel,
  .combat-sidebar__tab-stack,
  .combat-sidebar__inspector-placeholder {
    display: grid;
    gap: 1rem;
  }

  .combat-sidebar {
    max-height: calc(100vh - 8rem);
    overflow: auto;
    padding-right: 0.2rem;
    scrollbar-width: thin;
    scrollbar-color: rgba(226, 193, 155, 0.42) rgba(16, 14, 12, 0.7);
  }

  .combat-sidebar__tab-list {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .combat-sidebar__tab-button,
  .combat-sidebar__inspector-placeholder {
    border: 1px solid var(--combat-border, var(--color-border));
    background: rgba(16, 14, 12, 0.58);
  }

  .combat-sidebar__tab-button {
    min-height: 2.5rem;
    padding: 0.55rem 0.9rem;
    color: var(--combat-text, var(--color-text));
  }

  .combat-sidebar__tab-button--active {
    border-color: rgba(226, 193, 155, 0.42);
    background: rgba(226, 193, 155, 0.12);
  }

  .combat-sidebar__inspector-placeholder {
    padding: 1rem;
  }

  .combat-sidebar__inspector-placeholder strong,
  .combat-sidebar__inspector-placeholder p {
    margin: 0;
  }

  .combat-sidebar__inspector-placeholder strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-sidebar__inspector-placeholder p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.6;
  }

  @media (max-width: 1080px) {
    .combat-sidebar {
      max-height: none;
      overflow: visible;
      padding-right: 0;
    }
  }
</style>
