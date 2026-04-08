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
      controls: [
        'Confirm seat ownership and assigned roster entries.',
        'Lock the table once draft and ready checks are complete.',
        'Move to combat only after the party and GM state match.',
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
      controls: [
        'Hold launch until draft review finishes.',
        'Reassign unclaimed seats before the next phase.',
        'Publish readiness only after deck checks are complete.',
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
      controls: [
        'Resolve the final ready check.',
        'Freeze seat edits before launch.',
        'Hand off into the combat command screen.',
      ],
    },
  } as const

  const defaultSessionId = 'session-ember-01'
  const selectedSessionId = getSelectionHandoff(selectionHandoffKeys.sessionId, defaultSessionId)

  const session =
    sessionRecords[selectedSessionId as keyof typeof sessionRecords] ?? sessionRecords[defaultSessionId]
  const participants = session.participants

  // TODO: Expand the fixed route /lobby/gm into /lobby/:code/gm when session codes are wired.
</script>

<div class="gm-lobby-page">
  <SectionFrame
    eyebrow="Session Summary"
    title={session.title}
    description="GM lobby keeps the same overall shell as the player lobby and expands the right panel with control-focused tools."
  >
    <div class="gm-lobby-page__summary">
      <div class="gm-lobby-page__summary-copy">
        <p>{session.subtitle}</p>
        <h3>Code: {session.code}</h3>
      </div>

      <div class="gm-lobby-page__summary-tags">
        <TagChip label="GM View" tone="warning" />
        <TagChip label="Batch 4" tone="accent" />
      </div>
    </div>

    <div class="gm-lobby-page__stats">
      {#each session.stats as stat}
        <StatBlock value={stat.value} label={stat.label} note={stat.note} />
      {/each}
    </div>
  </SectionFrame>

  <div class="gm-lobby-page__main">
    <SectionFrame
      title="Participant slots"
      description="This slot grid mirrors the player view so the shared lobby structure can be consolidated later."
    >
      <div class="gm-lobby-page__slots">
        {#each participants as participant}
          <ParticipantSlot
            slot={participant.slot}
            name={participant.name}
            state={participant.state}
            tone={participant.tone}
            note="TODO: Show selected character, deck ownership, and kick/reassign controls from live lobby state."
          />
        {/each}
      </div>
    </SectionFrame>

    <SectionFrame
      title="GM control panel"
      description="This right-side panel is the only major extension beyond the player lobby skeleton."
    >
      <div class="gm-lobby-page__guide">
        {#each session.controls as step}
          <p>{step}</p>
        {/each}
      </div>

      <div class="gm-lobby-page__controls">
        <button type="button" disabled>Lock seats (TODO)</button>
        <button type="button" disabled>Confirm ready state (TODO)</button>
        <button type="button" disabled>Broadcast update (TODO)</button>
      </div>

      <div class="gm-lobby-page__todo">
        <p>TODO: Add live readiness controls, seat permissions, and launch validation.</p>
        <p>TODO: Split shared lobby state from player-only and GM-only actions.</p>
      </div>
    </SectionFrame>
  </div>

  <SectionFrame
    title="Action zone"
    description="Bottom action strip remains separate so the GM launch flow can evolve without changing the shared lobby skeleton."
  >
    <div class="gm-lobby-page__actions">
      <a class="gm-lobby-page__link-action gm-lobby-page__link-action--muted" data-nav href="/lobby">
        Back to session entry
      </a>
      <a class="gm-lobby-page__link-action" data-nav href="/combat">Open combat command</a>
      <button type="button" disabled>Start session (TODO)</button>
    </div>
  </SectionFrame>
</div>

<style>
  .gm-lobby-page,
  .gm-lobby-page__main,
  .gm-lobby-page__guide,
  .gm-lobby-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .gm-lobby-page__summary {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .gm-lobby-page__summary-copy {
    display: grid;
    gap: 0.5rem;
  }

  .gm-lobby-page__summary-copy p,
  .gm-lobby-page__summary-copy h3,
  .gm-lobby-page__guide p,
  .gm-lobby-page__todo p {
    margin: 0;
  }

  .gm-lobby-page__summary-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .gm-lobby-page__summary-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .gm-lobby-page__summary-tags,
  .gm-lobby-page__controls,
  .gm-lobby-page__actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .gm-lobby-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .gm-lobby-page__main {
    grid-template-columns: minmax(0, 1.35fr) minmax(18rem, 0.65fr);
    align-items: start;
  }

  .gm-lobby-page__slots {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .gm-lobby-page__guide p,
  .gm-lobby-page__todo p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .gm-lobby-page__controls button,
  .gm-lobby-page__link-action,
  .gm-lobby-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .gm-lobby-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .gm-lobby-page__link-action--muted {
    border-color: var(--color-border);
    background: rgba(12, 11, 10, 0.28);
  }

  .gm-lobby-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  @media (max-width: 960px) {
    .gm-lobby-page__stats,
    .gm-lobby-page__main,
    .gm-lobby-page__slots {
      grid-template-columns: 1fr;
    }
  }
</style>
