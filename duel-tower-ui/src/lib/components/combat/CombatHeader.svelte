<script lang="ts">
  import type { RunRecentResultDto } from '../../api/sessionTypes'
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import StatBlock from '../StatBlock.svelte'
  import TagChip from '../TagChip.svelte'
  import type { CombatStatusViewModel } from './types'

  type Props = {
    statusView: CombatStatusViewModel
    accessRoleLabel: string
    headerExpanded: boolean
    combatStateLive: boolean
    currentTurnStateLabel: string
    recentResultsLoading: boolean
    recentResultsErrorMessage: string | null
    latestRecentResult: RunRecentResultDto | null
    accessNoticeMessage: string | null
    catalogErrorMessage: string | null
    commandErrorMessage: string | null
    commandRejectedMessage: string | null
    commandSuccessMessage: string | null
    onToggleExpanded: () => void
  }

  let {
    statusView,
    accessRoleLabel,
    headerExpanded,
    combatStateLive,
    currentTurnStateLabel,
    recentResultsLoading,
    recentResultsErrorMessage,
    latestRecentResult,
    accessNoticeMessage,
    catalogErrorMessage,
    commandErrorMessage,
    commandRejectedMessage,
    commandSuccessMessage,
    onToggleExpanded,
  }: Props = $props()

  const priorityMessage = $derived.by(() => {
    if (commandErrorMessage) {
      return {
        title: 'Command request failed',
        message: commandErrorMessage,
        tone: 'error' as const,
      }
    }

    if (commandRejectedMessage) {
      return {
        title: 'Command rejected',
        message: commandRejectedMessage,
        tone: 'error' as const,
      }
    }

    if (accessNoticeMessage) {
      return {
        title: 'Combat access status',
        message: accessNoticeMessage,
        tone: undefined,
      }
    }

    if (catalogErrorMessage) {
      return {
        title: 'Card archive unavailable',
        message: catalogErrorMessage,
        tone: undefined,
      }
    }

    return null
  })

  const secondaryMessage = $derived.by(() => {
    if (priorityMessage) {
      return commandSuccessMessage
    }

    return commandSuccessMessage ?? accessNoticeMessage ?? catalogErrorMessage
  })
</script>

<SectionFrame
  eyebrow="Combat Status"
  title="Combat HUD"
  description="Always-visible status stays thin. Overview and recent feedback are available on demand."
>
  <div class="combat-header" class:combat-header--readonly={accessRoleLabel === 'Read-only shell'}>
    <div class="combat-header__always-visible">
      <div class="combat-header__status-bar">
        <div class="combat-header__status-strip">
          <div class="combat-header__phase-block">
            <span>Phase</span>
            <strong>{statusView.phase ?? 'Pending'}</strong>
          </div>

          <div class="combat-header__inline-stats">
            <StatBlock
              value={statusView.round ?? 'Pending'}
              label="Round"
              note={statusView.round !== null ? 'Current combat round' : 'Combat state not active yet'}
            />
            <StatBlock
              value={statusView.currentTurnLabel}
              label="Current Turn"
              note={statusView.currentTurnNote}
            />
          </div>
        </div>

        <div class="combat-header__flow-summary">
          <strong>{currentTurnStateLabel}</strong>
          <p>{statusView.initiativeSummary}</p>
          <p>{statusView.turnOrderSummary}</p>
        </div>

        <div class="combat-header__status-meta">
          <div class="combat-header__status-tags">
            <TagChip label={currentTurnStateLabel} tone={statusView.currentTurnTone} />
            <TagChip label={combatStateLive ? 'Live combat' : 'Pre-combat'} tone={combatStateLive ? 'warning' : 'muted'} />
            <TagChip label={accessRoleLabel} tone={accessRoleLabel === 'Read-only shell' ? 'muted' : 'success'} />
          </div>

          <button type="button" class="combat-header__overview-toggle" onclick={() => onToggleExpanded()}>
            {headerExpanded ? 'Hide overview' : 'Show overview'}
          </button>
        </div>
      </div>

      {#if priorityMessage}
        <ContentStatePanel
          title={priorityMessage.title}
          message={priorityMessage.message}
          tone={priorityMessage.tone}
        />
      {:else if secondaryMessage}
        <div class="combat-header__inline-message">
          <strong>Latest notice</strong>
          <p>{secondaryMessage}</p>
        </div>
      {/if}
    </div>

    {#if headerExpanded}
      <div class="combat-header__expandable-overview">
        <div class="combat-header__overview-grid">
          <article class="combat-header__spotlight-card">
            <strong>Battlefield overview</strong>
            <h3>{statusView.battlefieldSummary}</h3>
            <p>{statusView.runSummary}</p>
            <p>{statusView.tieGroupSummary}</p>
          </article>

          <article class={`combat-header__spotlight-card combat-header__spotlight-card--${statusView.currentTurnTone}`}>
            <strong>Turn detail</strong>
            <h3>{statusView.currentTurnLabel}</h3>
            <p>{statusView.currentTurnNote}</p>
            <p>Session {statusView.sessionCode} | Version {statusView.version}</p>
          </article>

          <article class="combat-header__spotlight-card combat-header__spotlight-card--feedback">
            <strong>Recent feedback</strong>
            {#if recentResultsLoading}
              <h3>Loading recent results</h3>
              <p>Restoring the latest combat-facing result summary.</p>
            {:else if recentResultsErrorMessage}
              <h3>Recent result unavailable</h3>
              <p>{recentResultsErrorMessage}</p>
            {:else if latestRecentResult}
              <h3>{latestRecentResult.title}</h3>
              <p>{latestRecentResult.summary}</p>
              <p>{latestRecentResult.type} | {latestRecentResult.at ?? 'Time unavailable'}</p>
            {:else}
              <h3>No recent result yet</h3>
              <p>The current combat flow has not produced a recent-result summary yet.</p>
            {/if}
          </article>
        </div>
      </div>
    {/if}
  </div>
</SectionFrame>

<style>
  .combat-header,
  .combat-header__always-visible,
  .combat-header__expandable-overview,
  .combat-header__status-bar,
  .combat-header__status-strip,
  .combat-header__status-meta,
  .combat-header__status-tags,
  .combat-header__inline-stats,
  .combat-header__flow-summary,
  .combat-header__overview-grid,
  .combat-header__inline-message {
    display: grid;
    gap: 0.6rem;
  }

  .combat-header {
    position: relative;
  }

  .combat-header__status-bar {
    grid-template-columns: minmax(13rem, 0.8fr) minmax(0, 1fr) auto;
    align-items: center;
    padding: 0.7rem 0.85rem;
    border: 1px solid rgba(226, 193, 155, 0.18);
    background: linear-gradient(90deg, rgba(21, 19, 17, 0.94), rgba(44, 41, 39, 0.72));
    box-shadow: 0 12px 34px rgba(0, 0, 0, 0.22);
  }

  .combat-header__status-strip {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 0.8rem;
  }

  .combat-header__phase-block {
    display: grid;
    gap: 0.12rem;
    padding-left: 0.8rem;
    border-left: 3px solid var(--combat-secondary, var(--color-accent));
  }

  .combat-header__phase-block span,
  .combat-header__spotlight-card strong {
    font-size: 0.68rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-header__phase-block span {
    color: var(--combat-secondary, var(--color-accent));
  }

  .combat-header__phase-block strong {
    font-family: var(--font-display);
    font-size: clamp(1rem, 1.5vw, 1.35rem);
    color: var(--combat-text, var(--color-text));
  }

  .combat-header__inline-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.55rem;
  }

  .combat-header__flow-summary strong,
  .combat-header__flow-summary p,
  .combat-header__inline-message strong,
  .combat-header__inline-message p {
    margin: 0;
  }

  .combat-header__flow-summary strong,
  .combat-header__inline-message strong {
    color: var(--combat-secondary, var(--color-accent));
    font-size: 0.68rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-header__flow-summary p,
  .combat-header__inline-message p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.35;
    font-size: 0.84rem;
  }

  .combat-header__status-tags {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .combat-header__status-meta {
    justify-items: end;
    align-content: space-between;
  }

  .combat-header__overview-grid {
    grid-template-columns: minmax(14rem, 1fr) minmax(14rem, 1fr) minmax(16rem, 1.15fr);
    gap: 0.75rem;
  }

  .combat-header__overview-toggle {
    min-height: 2.1rem;
    padding: 0.4rem 0.7rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: rgba(226, 193, 155, 0.1);
    color: var(--combat-text, var(--color-text));
  }

  .combat-header__spotlight-card {
    position: relative;
    overflow: hidden;
    border: 1px solid var(--combat-border, var(--color-border));
    background:
      linear-gradient(160deg, rgba(44, 41, 39, 0.92), rgba(21, 19, 17, 0.86)),
      rgba(12, 11, 10, 0.28);
    padding: 0.85rem;
    display: grid;
    gap: 0.35rem;
  }

  .combat-header__spotlight-card::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: var(--combat-secondary, var(--color-accent));
    opacity: 0.55;
  }

  .combat-header__spotlight-card--accent {
    border-color: rgba(226, 193, 155, 0.44);
  }

  .combat-header__spotlight-card--success {
    border-color: rgba(188, 204, 173, 0.28);
  }

  .combat-header__spotlight-card--warning {
    border-color: rgba(255, 179, 175, 0.38);
  }

  .combat-header__spotlight-card--feedback {
    border-color: rgba(188, 204, 173, 0.28);
  }

  .combat-header--readonly .combat-header__status-bar {
    border-color: rgba(152, 143, 135, 0.28);
    filter: saturate(0.82);
  }

  .combat-header__spotlight-card strong,
  .combat-header__spotlight-card h3,
  .combat-header__spotlight-card p {
    margin: 0;
  }

  .combat-header__spotlight-card strong {
    color: var(--combat-secondary, var(--color-accent));
  }

  .combat-header__spotlight-card h3 {
    font-family: var(--font-display);
    font-size: clamp(1rem, 1.6vw, 1.25rem);
  }

  .combat-header__spotlight-card p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.45;
    font-size: 0.84rem;
  }

  .combat-header__inline-message {
    padding: 0.55rem 0.8rem;
    border: 1px solid rgba(152, 143, 135, 0.24);
    background: rgba(16, 14, 12, 0.54);
  }

  @media (max-width: 1180px) {
    .combat-header__status-bar,
    .combat-header__overview-grid {
      grid-template-columns: 1fr;
    }

    .combat-header__status-meta {
      justify-items: start;
    }

    .combat-header__status-tags {
      justify-content: flex-start;
    }
  }

  @media (max-width: 720px) {
    .combat-header__status-strip,
    .combat-header__inline-stats {
      grid-template-columns: 1fr;
    }
  }
</style>
