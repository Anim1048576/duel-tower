<script lang="ts">
  import SectionFrame from '../SectionFrame.svelte'
  import type { PendingDecisionDto } from '../../api/sessionTypes'
  import CombatLogPanel from './CombatLogPanel.svelte'
  import CombatResultsPanel from './CombatResultsPanel.svelte'
  import CommandActionPanel from './CommandActionPanel.svelte'
  import CombatZoneSummary from './CombatZoneSummary.svelte'
  import type {
    CombatCommandRequirementViewModel,
    CombatFeedEntry,
    CombatPlayerViewModel,
    CombatRecentResultEntry,
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
</script>

<SectionFrame
  title="Combat context and command"
  description="The sidebar keeps pending decisions, visible zones, and combat history close to the live battlefield summary without changing the command flow."
>
  <div class="combat-sidebar">
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

    <CombatResultsPanel
      loading={recentResultsLoading}
      errorMessage={recentResultsErrorMessage}
      entries={recentResultEntries}
      onRetry={onRetryResults}
    />
  </div>
</SectionFrame>

<style>
  .combat-sidebar {
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

  @media (max-width: 1080px) {
    .combat-sidebar {
      max-height: none;
      overflow: visible;
      padding-right: 0;
    }
  }
</style>
