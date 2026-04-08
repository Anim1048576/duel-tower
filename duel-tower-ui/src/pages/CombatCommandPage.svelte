<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const status = {
    round: '03',
    turn: 'Player phase',
    actionPoints: '2',
    threat: 'Rising',
  }

  const playerUnits = [
    {
      name: 'Ashen Knight',
      role: 'Front guard',
      hp: '42 / 52',
      state: { label: 'Guard Up', tone: 'success' as const },
    },
    {
      name: 'Mira of Cinders',
      role: 'Control caster',
      hp: '27 / 34',
      state: { label: 'Focused', tone: 'accent' as const },
    },
  ]

  const enemyUnits = [
    {
      name: 'Warden Beast',
      role: 'Elite threat',
      hp: '55 / 68',
      state: { label: 'Marked', tone: 'warning' as const },
    },
    {
      name: 'Ash Sworn',
      role: 'Support foe',
      hp: '18 / 26',
      state: { label: 'Weakened', tone: 'muted' as const },
    },
  ]

  const commandOptions = [
    { title: 'Shield Push', note: 'Protect the front line and force target focus.' },
    { title: 'Burn Sigil', note: 'Apply pressure and trigger marked damage windows.' },
    { title: 'Recover Tempo', note: 'Hold AP for a safer next turn.' },
  ]

  const combatLog = [
    'Round 03 opened with player initiative.',
    'Ashen Knight maintained guard on the front line.',
    'Warden Beast gained threat after the last exchange.',
  ]

  const handCards = [
    { name: 'Ward Line', type: 'Defense', cost: '1' },
    { name: 'Cinder Thread', type: 'Control', cost: '1' },
    { name: 'Break March', type: 'Finisher', cost: '2' },
  ]

  // TODO: Replace this fixed page with encounter-based routing such as /combat/:encounterId.
</script>

<div class="combat-page">
  <SectionFrame
    eyebrow="Combat Status"
    title="Combat Command"
    description="Battle state, command planning, and action review stay in one shell for now, with clear panels for later extraction."
  >
    <div class="combat-page__status-bar">
      <div class="combat-page__status-stats">
        <StatBlock value={status.round} label="Round" note="Current exchange count" />
        <StatBlock value={status.turn} label="Turn" note="Active initiative owner" />
        <StatBlock value={status.actionPoints} label="AP" note="Available command budget" />
      </div>

      <div class="combat-page__status-tags">
        <TagChip label={status.threat} tone="warning" />
        <TagChip label="Mock Battle" tone="accent" />
      </div>
    </div>
  </SectionFrame>

  <div class="combat-page__main">
    <div class="combat-page__field">
      <SectionFrame
        title="Player side"
        description="This panel can later split into party roster cards, target state, and defensive stance modules."
      >
        <div class="combat-page__unit-list">
          {#each playerUnits as unit}
            <article class="combat-page__unit-card">
              <div class="combat-page__unit-head">
                <div>
                  <h3>{unit.name}</h3>
                  <p>{unit.role}</p>
                </div>
                <TagChip label={unit.state.label} tone={unit.state.tone} />
              </div>
              <p>HP {unit.hp}</p>
              <p>TODO: Add buffs, assigned deck actions, and initiative state.</p>
            </article>
          {/each}
        </div>
      </SectionFrame>

      <SectionFrame
        title="Enemy side"
        description="Enemy status remains separate so future targeting and phase logic can evolve without touching the player panel."
      >
        <div class="combat-page__unit-list">
          {#each enemyUnits as unit}
            <article class="combat-page__unit-card combat-page__unit-card--enemy">
              <div class="combat-page__unit-head">
                <div>
                  <h3>{unit.name}</h3>
                  <p>{unit.role}</p>
                </div>
                <TagChip label={unit.state.label} tone={unit.state.tone} />
              </div>
              <p>HP {unit.hp}</p>
              <p>TODO: Add resistances, enemy intent, and break thresholds.</p>
            </article>
          {/each}
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title="Command and log"
      description="The right-side panel is deliberately split into command planning and event history for later extraction."
    >
      <div class="combat-page__sidebar">
        <div class="combat-page__command-panel">
          <strong>Command panel</strong>
          <div class="combat-page__command-list">
            {#each commandOptions as command}
              <button type="button" disabled>
                <span>{command.title}</span>
                <small>{command.note}</small>
              </button>
            {/each}
          </div>
        </div>

        <div class="combat-page__log-panel">
          <strong>Action log</strong>
          <ul>
            {#each combatLog as entry}
              <li>{entry}</li>
            {/each}
          </ul>
          <p>TODO: Replace mock log lines with streamed battle events.</p>
        </div>
      </div>
    </SectionFrame>
  </div>

  <SectionFrame
    title="Hand and action bar"
    description="This bottom strip is isolated so card hand, selected action, and confirmation controls can later become their own module."
  >
    <div class="combat-page__hand-bar">
      <div class="combat-page__hand-cards">
        {#each handCards as card}
          <article class="combat-page__hand-card">
            <p>{card.type}</p>
            <h4>{card.name}</h4>
            <span>Cost {card.cost}</span>
          </article>
        {/each}
      </div>

      <div class="combat-page__action-summary">
        <strong>Selected action</strong>
        <p>Shield Push is highlighted as the next safe opener for the mock state.</p>
        <div class="combat-page__action-buttons">
          <button type="button" disabled>Commit action (TODO)</button>
          <button type="button" disabled>End turn (TODO)</button>
        </div>
      </div>
    </div>
  </SectionFrame>
</div>

<style>
  .combat-page,
  .combat-page__field,
  .combat-page__sidebar,
  .combat-page__unit-list,
  .combat-page__command-list,
  .combat-page__hand-bar,
  .combat-page__action-summary {
    display: grid;
    gap: 1.5rem;
  }

  .combat-page__status-bar {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .combat-page__status-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
    flex: 1 1 38rem;
  }

  .combat-page__status-tags,
  .combat-page__action-buttons {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .combat-page__main {
    display: grid;
    grid-template-columns: minmax(0, 1.35fr) minmax(20rem, 0.65fr);
    gap: 1.5rem;
    align-items: start;
  }

  .combat-page__unit-card,
  .combat-page__hand-card,
  .combat-page__command-list button {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
  }

  .combat-page__unit-card,
  .combat-page__hand-card {
    display: grid;
    gap: 0.75rem;
  }

  .combat-page__unit-card--enemy {
    border-color: rgba(199, 167, 125, 0.28);
  }

  .combat-page__unit-head {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: flex-start;
  }

  .combat-page__unit-head h3,
  .combat-page__unit-head p,
  .combat-page__unit-card > p,
  .combat-page__log-panel p,
  .combat-page__action-summary p,
  .combat-page__hand-card p,
  .combat-page__hand-card h4,
  .combat-page__hand-card span,
  .combat-page__log-panel ul {
    margin: 0;
  }

  .combat-page__unit-head h3,
  .combat-page__hand-card h4 {
    font-family: var(--font-display);
    font-size: 1.1rem;
  }

  .combat-page__unit-head p,
  .combat-page__unit-card > p,
  .combat-page__log-panel p,
  .combat-page__action-summary p,
  .combat-page__hand-card p,
  .combat-page__hand-card span,
  .combat-page__log-panel li,
  .combat-page__command-list button small {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .combat-page__command-panel,
  .combat-page__log-panel {
    display: grid;
    gap: 1rem;
  }

  .combat-page__command-panel strong,
  .combat-page__log-panel strong,
  .combat-page__action-summary strong {
    font-size: 0.82rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .combat-page__command-list button {
    text-align: left;
    display: grid;
    gap: 0.45rem;
  }

  .combat-page__command-list button span {
    font-size: 0.98rem;
    color: var(--color-text);
  }

  .combat-page__log-panel ul {
    padding-left: 1.15rem;
    display: grid;
    gap: 0.55rem;
  }

  .combat-page__hand-bar {
    grid-template-columns: minmax(0, 1.3fr) minmax(19rem, 0.7fr);
    align-items: start;
  }

  .combat-page__hand-cards {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .combat-page__action-buttons button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  @media (max-width: 1080px) {
    .combat-page__main,
    .combat-page__hand-bar {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 960px) {
    .combat-page__status-stats,
    .combat-page__hand-cards {
      grid-template-columns: 1fr;
    }
  }
</style>
