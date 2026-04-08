<script lang="ts">
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const CHARACTER_HANDOFF_KEY = 'duel-tower:selected-character-id'

  const characterRecords = {
    'char-ash': {
      name: 'Ashen Knight',
      role: 'Frontline guardian',
      summary: 'Built for shield timing, guard rotation, and stable expedition pacing.',
      tags: [
        { label: 'Ready', tone: 'success' as const },
        { label: 'Front', tone: 'muted' as const },
        { label: 'Mock', tone: 'accent' as const },
      ],
      stats: [
        { value: '18', label: 'Level', note: 'Mock progression state' },
        { value: '92', label: 'Guard', note: 'Shield-focused mitigation score' },
        { value: '3', label: 'Active bonds', note: 'Party synergy links' },
      ],
      traits: ['Reliable taunt sequencing', 'High shield upkeep', 'Low burst mobility'],
      loadout: ['Black iron shield', 'Archive oath blade', 'Expedition ward sigil'],
      memo: 'Preferred anchor for defensive expedition decks and stable player onboarding.',
    },
    'char-mira': {
      name: 'Mira of Cinders',
      role: 'Control caster',
      summary: 'Focused on burn tempo, control pivots, and measured recovery windows.',
      tags: [
        { label: 'Assigned', tone: 'warning' as const },
        { label: 'Mage', tone: 'muted' as const },
        { label: 'Mock', tone: 'accent' as const },
      ],
      stats: [
        { value: '16', label: 'Level', note: 'Mock progression state' },
        { value: '74', label: 'Control', note: 'Status and tempo disruption score' },
        { value: '2', label: 'Bound decks', note: 'Current deck dependencies' },
      ],
      traits: ['Burn pattern control', 'Strong timing windows', 'Limited recovery margin'],
      loadout: ['Cinder focus rod', 'Ashglass codex', 'Soot-line catalyst'],
      memo: 'Best used when the party can protect a slower setup turn.',
    },
    'char-rune': {
      name: 'Rune Archivist',
      role: 'Support analyst',
      summary: 'Optimized for utility timing, scouting support, and status cleanse coverage.',
      tags: [
        { label: 'Idle', tone: 'accent' as const },
        { label: 'Support', tone: 'muted' as const },
        { label: 'Mock', tone: 'accent' as const },
      ],
      stats: [
        { value: '14', label: 'Level', note: 'Mock progression state' },
        { value: '81', label: 'Utility', note: 'Support and cleanse score' },
        { value: '0', label: 'Assigned decks', note: 'Available for next build pass' },
      ],
      traits: ['Strong scouting tools', 'Low burst output', 'Flexible utility routing'],
      loadout: ['Archive field kit', 'Quiet seal lantern', 'Recovery thread scroll'],
      memo: 'Useful reserve unit for expedition prep and status-heavy encounters.',
    },
  } as const

  const defaultCharacterId = 'char-ash'
  const selectedCharacterId =
    typeof window === 'undefined'
      ? defaultCharacterId
      : window.sessionStorage.getItem(CHARACTER_HANDOFF_KEY) ?? defaultCharacterId

  const character =
    characterRecords[selectedCharacterId as keyof typeof characterRecords] ??
    characterRecords[defaultCharacterId]

  // TODO: Expand the fixed route /characters/detail into /characters/:id when data IDs are available.
</script>

<div class="detail-page">
  <SectionFrame
    eyebrow="Selected Record"
    title={character.name}
    description="Character Detail / Edit is currently wired to the default selected roster entry from the list page."
  >
    <div class="detail-page__hero">
      <div class="detail-page__hero-copy">
        <p>{character.role}</p>
        <h3>{character.summary}</h3>
      </div>

      <div class="detail-page__hero-tags">
        {#each character.tags as tag}
          <TagChip label={tag.label} tone={tag.tone} />
        {/each}
      </div>
    </div>

    <div class="detail-page__stats">
      {#each character.stats as stat}
        <StatBlock value={stat.value} label={stat.label} note={stat.note} />
      {/each}
    </div>
  </SectionFrame>

  <div class="detail-page__grid">
    <SectionFrame
      title="Combat profile"
      description="The final edit form will replace this read-first profile summary."
    >
      <div class="detail-page__list-block">
        <div>
          <strong>Role</strong>
          <p>Primary tank with safe opening patterns and low variance defense turns.</p>
        </div>
        <div>
          <strong>Traits</strong>
          <ul>
            {#each character.traits as trait}
              <li>{trait}</li>
            {/each}
          </ul>
        </div>
      </div>
    </SectionFrame>

    <SectionFrame
      title="Loadout and notes"
      description="Equipment, notes, and edit actions remain read-only until the API and form contract are finalized."
    >
      <div class="detail-page__list-block">
        <div>
          <strong>Loadout</strong>
          <ul>
            {#each character.loadout as item}
              <li>{item}</li>
            {/each}
          </ul>
        </div>
        <div>
          <strong>Memo</strong>
          <p>{character.memo}</p>
        </div>
      </div>
    </SectionFrame>
  </div>

  <SectionFrame
    title="Edit queue"
    description="This action strip shows where later batches will connect save, assign, and progression updates."
  >
    <div class="detail-page__actions">
      <a class="detail-page__link-action" data-nav href="/characters">Back to roster list</a>
      <button type="button" disabled>Save changes (TODO)</button>
      <button type="button" disabled>Assign to deck (TODO)</button>
    </div>
    <div class="detail-page__todo">
      <p>TODO: Replace the fixed mock record with API-backed character detail data.</p>
      <p>TODO: Convert read-only sections into actual edit controls once the contract is ready.</p>
    </div>
  </SectionFrame>
</div>

<style>
  .detail-page,
  .detail-page__grid,
  .detail-page__list-block,
  .detail-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .detail-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .detail-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .detail-page__hero-copy p,
  .detail-page__hero-copy h3,
  .detail-page__todo p {
    margin: 0;
  }

  .detail-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .detail-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .detail-page__hero-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .detail-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .detail-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__list-block > div {
    display: grid;
    gap: 0.65rem;
  }

  .detail-page__list-block strong {
    font-size: 0.92rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .detail-page__list-block p,
  .detail-page__list-block ul {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .detail-page__list-block ul {
    padding-left: 1.1rem;
  }

  .detail-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .detail-page__link-action,
  .detail-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .detail-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .detail-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .detail-page__todo p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .detail-page__stats,
    .detail-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
