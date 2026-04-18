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
</script>

<SectionFrame
  eyebrow="Combat Status"
  title="Combat Command"
  description="The combat shell now emphasizes the current turn owner, battlefield pressure, and recent combat feedback before deeper command controls."
>
  <div class="combat-header" class:combat-header--readonly={accessRoleLabel === 'Read-only shell'}>
    <div class="combat-header__always-visible">
      <div class="combat-header__status-bar">
        <div class="combat-header__turn-ribbon">
          <span>Phase</span>
          <strong>{statusView.phase ?? 'Pending'}</strong>
          <small>{statusView.turnOrderSummary}</small>
        </div>

        <div class="combat-header__status-stats">
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
          <StatBlock
            value={statusView.version}
            label="Version"
            note={statusView.battlefieldSummary}
          />
        </div>

        <div class="combat-header__status-meta">
          <div class="combat-header__status-tags">
            <TagChip label={statusView.sessionCode} tone="accent" />
            <TagChip label={accessRoleLabel} tone={accessRoleLabel === 'Read-only shell' ? 'muted' : 'success'} />
            <TagChip label={combatStateLive ? 'Combat state live' : 'Pre-combat state'} tone={combatStateLive ? 'warning' : 'muted'} />
            <TagChip label={currentTurnStateLabel} tone={statusView.currentTurnTone} />
          </div>

          <button type="button" class="combat-header__overview-toggle" onclick={() => onToggleExpanded()}>
            {headerExpanded ? 'Collapse overview' : 'Expand overview'}
          </button>
        </div>
      </div>

      <div class="combat-header__message-stack">
        {#if accessNoticeMessage}
          <ContentStatePanel title="Combat access status" message={accessNoticeMessage} />
        {/if}

        {#if catalogErrorMessage}
          <ContentStatePanel title="Card archive unavailable" message={catalogErrorMessage} />
        {/if}

        {#if commandErrorMessage}
          <ContentStatePanel title="Command request failed" message={commandErrorMessage} tone="error" />
        {:else if commandRejectedMessage}
          <ContentStatePanel title="Command rejected" message={commandRejectedMessage} tone="error" />
        {:else if commandSuccessMessage}
          <ContentStatePanel title="Command accepted" message={commandSuccessMessage} />
        {/if}
      </div>
    </div>

    {#if headerExpanded}
      <div class="combat-header__expandable-overview">
        <div class="combat-header__overview-grid">
          <article class={`combat-header__spotlight-card combat-header__spotlight-card--${statusView.currentTurnTone}`}>
            <strong>Current turn</strong>
            <h3>{statusView.currentTurnLabel}</h3>
            <p>{statusView.currentTurnNote}</p>
            <p>Turn order: {statusView.turnOrderSummary}</p>
          </article>

          <article class="combat-header__spotlight-card">
            <strong>Battlefield</strong>
            <h3>{statusView.battlefieldSummary}</h3>
            <p>{statusView.initiativeSummary} | {statusView.tieGroupSummary}</p>
            <p>{statusView.runSummary}</p>
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
  .combat-header__status-meta,
  .combat-header__status-stats,
  .combat-header__status-tags,
  .combat-header__overview-grid,
  .combat-header__message-stack {
    display: grid;
    gap: 1rem;
  }

  .combat-header {
    position: relative;
  }

  .combat-header__status-bar {
    grid-template-columns: minmax(14rem, 0.72fr) minmax(0, 1fr) auto;
    align-items: start;
    padding: 1rem;
    border: 1px solid rgba(226, 193, 155, 0.18);
    background: linear-gradient(90deg, rgba(21, 19, 17, 0.94), rgba(44, 41, 39, 0.72));
    box-shadow: 0 18px 50px rgba(0, 0, 0, 0.26);
  }

  .combat-header__turn-ribbon {
    display: grid;
    gap: 0.2rem;
    padding: 0.7rem 1rem;
    border-left: 4px solid var(--combat-secondary, var(--color-accent));
    background: rgba(16, 14, 12, 0.62);
  }

  .combat-header__turn-ribbon span,
  .combat-header__turn-ribbon small,
  .combat-header__spotlight-card strong {
    font-size: 0.72rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .combat-header__turn-ribbon span {
    color: var(--combat-secondary, var(--color-accent));
  }

  .combat-header__turn-ribbon strong {
    font-family: var(--font-display);
    font-size: clamp(1.2rem, 2.2vw, 1.9rem);
    color: var(--combat-text, var(--color-text));
  }

  .combat-header__turn-ribbon small {
    color: var(--combat-text-soft, var(--color-text-soft));
  }

  .combat-header__status-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .combat-header__status-tags {
    display: flex;
    flex-wrap: wrap;
  }

  .combat-header__status-meta {
    justify-items: end;
    align-content: space-between;
  }

  .combat-header__overview-grid {
    grid-template-columns: minmax(14rem, 1fr) minmax(14rem, 1fr) minmax(16rem, 1.15fr);
  }

  .combat-header__overview-toggle {
    min-height: 2.5rem;
    padding: 0.55rem 0.9rem;
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
    padding: 1rem;
    display: grid;
    gap: 0.45rem;
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
    font-size: clamp(1.2rem, 2vw, 1.6rem);
  }

  .combat-header__spotlight-card p {
    color: var(--combat-text-soft, var(--color-text-soft));
    line-height: 1.65;
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
    .combat-header__status-stats {
      grid-template-columns: 1fr;
    }
  }
</style>
