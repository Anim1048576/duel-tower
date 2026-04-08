<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import ParticipantSlot from '../lib/components/ParticipantSlot.svelte'
  import { getSelectionHandoff, selectionHandoffKeys } from '../lib/selectionHandoff'

  const sessionRecords = {
    'session-ember-01': {
      title: 'Ember Table 01',
      subtitle: 'Preparation phase',
      code: 'TOWER-EMBER-01',
      stats: [
        { value: '6', label: 'Seat cap', note: 'Six-player expansion target' },
        { value: '3', label: 'Joined', note: 'Current mock attendance' },
        { value: '1', label: 'Ready', note: 'Ready confirmation count' },
      ],
      participants: [
        { slot: 'P1', name: 'You', state: 'Ready', tone: 'success' as const },
        { slot: 'P2', name: 'Mira', state: 'Joined', tone: 'accent' as const },
        { slot: 'P3', name: 'Rune', state: 'Joined', tone: 'accent' as const },
        { slot: 'P4', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
        { slot: 'P5', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
        { slot: 'P6', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
      ],
    },
    'session-night-02': {
      title: 'Night Watch 02',
      subtitle: 'Draft phase',
      code: 'TOWER-NIGHT-02',
      stats: [
        { value: '6', label: 'Seat cap', note: 'Six-player expansion target' },
        { value: '3', label: 'Joined', note: 'Current mock attendance' },
        { value: '0', label: 'Ready', note: 'Draft table still staging' },
      ],
      participants: [
        { slot: 'P1', name: 'You', state: 'Joined', tone: 'accent' as const },
        { slot: 'P2', name: 'Mira', state: 'Joined', tone: 'accent' as const },
        { slot: 'P3', name: 'Archive Keeper', state: 'GM Ready', tone: 'success' as const },
        { slot: 'P4', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
        { slot: 'P5', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
        { slot: 'P6', name: 'Open slot', state: 'Waiting', tone: 'muted' as const },
      ],
    },
    'session-sealed-03': {
      title: 'Sealed Tower 03',
      subtitle: 'Ready to launch',
      code: 'TOWER-SEALED-03',
      stats: [
        { value: '6', label: 'Seat cap', note: 'Six-player expansion target' },
        { value: '6', label: 'Joined', note: 'Table is full' },
        { value: '5', label: 'Ready', note: 'Waiting on the final confirmation' },
      ],
      participants: [
        { slot: 'P1', name: 'Ashen Knight', state: 'Ready', tone: 'success' as const },
        { slot: 'P2', name: 'Mira', state: 'Ready', tone: 'success' as const },
        { slot: 'P3', name: 'Rune', state: 'Ready', tone: 'success' as const },
        { slot: 'P4', name: 'Vela', state: 'Ready', tone: 'success' as const },
        { slot: 'P5', name: 'Garr', state: 'Ready', tone: 'success' as const },
        { slot: 'P6', name: 'You', state: 'Joined', tone: 'accent' as const },
      ],
    },
  } as const

  const defaultSessionId = 'session-ember-01'
  const selectedSessionId = getSelectionHandoff(selectionHandoffKeys.sessionId, defaultSessionId)

  const session =
    sessionRecords[selectedSessionId as keyof typeof sessionRecords] ?? sessionRecords[defaultSessionId]
  const participants = session.participants

  // TODO: Expand the fixed route /lobby/player into /lobby/:code/player when session codes are wired.
</script>

<div class="player-lobby-page">
  <SectionFrame
    eyebrow="Session Summary"
    title={session.title}
    description="Player lobby is currently wired to the default selected session from the entry page."
  >
    <div class="player-lobby-page__summary">
      <div class="player-lobby-page__summary-copy">
        <p>{session.subtitle}</p>
        <h3>Code: {session.code}</h3>
      </div>

      <div class="player-lobby-page__summary-tags">
        <TagChip label="Player View" tone="accent" />
        <TagChip label="Batch 3" tone="accent" />
      </div>
    </div>

    <div class="player-lobby-page__stats">
      {#each session.stats as stat}
        <StatBlock value={stat.value} label={stat.label} note={stat.note} />
      {/each}
    </div>
  </SectionFrame>

  <div class="player-lobby-page__main">
    <SectionFrame
      title="Participant slots"
      description="This slot grid is separated so the GM lobby can later extend the same structure with controls."
    >
      <div class="player-lobby-page__slots">
        {#each participants as participant}
          <ParticipantSlot
            slot={participant.slot}
            name={participant.name}
            state={participant.state}
            tone={participant.tone}
            note="TODO: Show selected character, deck, and readiness details from live lobby state."
          />
        {/each}
      </div>
    </SectionFrame>

    <SectionFrame
      title="Lobby guide"
      description="Right-side guide panel stays independent so the GM lobby can replace or extend it later."
    >
      <div class="player-lobby-page__guide">
        <p>1. Confirm your seat, selected character, and assigned deck.</p>
        <p>2. Wait for table readiness and session phase confirmation.</p>
        <p>3. GM controls will be added in the next lobby batch on a separate page variant.</p>
      </div>
      <div class="player-lobby-page__todo">
        <p>TODO: Add live readiness, seat ownership, and lobby event feed.</p>
        <p>TODO: Split shared player/GM lobby data from role-specific actions.</p>
      </div>
    </SectionFrame>
  </div>

  <SectionFrame
    title="Action zone"
    description="Bottom action strip is kept separate for future ready checks, leave flow, and launch transitions."
  >
    <div class="player-lobby-page__actions">
      <a class="player-lobby-page__link-action" data-nav href="/lobby">Back to session entry</a>
      <button type="button" disabled>Ready up (TODO)</button>
      <button type="button" disabled>Leave session (TODO)</button>
    </div>
  </SectionFrame>
</div>

<style>
  .player-lobby-page,
  .player-lobby-page__main,
  .player-lobby-page__guide,
  .player-lobby-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .player-lobby-page__summary {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .player-lobby-page__summary-copy {
    display: grid;
    gap: 0.5rem;
  }

  .player-lobby-page__summary-copy p,
  .player-lobby-page__summary-copy h3,
  .player-lobby-page__guide p,
  .player-lobby-page__todo p {
    margin: 0;
  }

  .player-lobby-page__summary-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .player-lobby-page__summary-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .player-lobby-page__summary-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .player-lobby-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .player-lobby-page__main {
    grid-template-columns: minmax(0, 1.35fr) minmax(18rem, 0.65fr);
    align-items: start;
  }

  .player-lobby-page__slots {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .player-lobby-page__guide p,
  .player-lobby-page__todo p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .player-lobby-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .player-lobby-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .player-lobby-page__link-action,
  .player-lobby-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .player-lobby-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  @media (max-width: 960px) {
    .player-lobby-page__stats,
    .player-lobby-page__main,
    .player-lobby-page__slots {
      grid-template-columns: 1fr;
    }
  }
</style>
