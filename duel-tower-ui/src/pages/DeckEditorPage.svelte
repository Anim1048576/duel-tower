<script lang="ts">
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  const DECK_HANDOFF_KEY = 'duel-tower:selected-deck-id'

  const deckRecords = {
    'deck-vanguard': {
      name: 'Vanguard Ember',
      role: 'Frontline pressure deck',
      summary: 'Tuned around early board pressure and safe hand cycling.',
      stats: [
        { value: '12', label: 'Cards', note: 'Current mock deck size' },
        { value: '3', label: 'Core slots', note: 'Protected from major changes' },
        { value: '1', label: 'Draft slot', note: 'Next revision target' },
      ],
      slots: [
        {
          id: 'slot-01',
          title: 'Guard Breaker',
          subtitle: 'Starter attack',
          meta: 'Cost 1 | Opens the pressure line',
          note: 'Anchors the first aggressive turn.',
          tags: [{ label: 'Locked', tone: 'muted' as const }],
        },
        {
          id: 'slot-02',
          title: 'Ash Ward',
          subtitle: 'Defensive bridge',
          meta: 'Cost 2 | Covers the recovery gap',
          note: 'Supports the tank opener without losing tempo.',
          tags: [{ label: 'Core', tone: 'success' as const }],
        },
        {
          id: 'slot-03',
          title: 'Flare March',
          subtitle: 'Tempo finisher',
          meta: 'Cost 3 | Converts board lead into reach',
          note: 'Flexible slot for the next editor pass.',
          tags: [{ label: 'Draft', tone: 'warning' as const }],
        },
      ],
    },
    'deck-siege': {
      name: 'Siege Ledger',
      role: 'Midrange control deck',
      summary: 'Built for stable curves, measured board control, and safer late turns.',
      stats: [
        { value: '12', label: 'Cards', note: 'Current mock deck size' },
        { value: '4', label: 'Core slots', note: 'Shared utility package' },
        { value: '0', label: 'Draft slot', note: 'No unstable slots right now' },
      ],
      slots: [
        {
          id: 'slot-11',
          title: 'Siege Mark',
          subtitle: 'Control opener',
          meta: 'Cost 1 | Slows enemy tempo',
          note: 'Keeps the early board stable without overcommitting.',
          tags: [{ label: 'Core', tone: 'success' as const }],
        },
        {
          id: 'slot-12',
          title: 'Ledger Wall',
          subtitle: 'Midgame stabilizer',
          meta: 'Cost 2 | Shared defense utility',
          note: 'Supports slower expedition pacing and safe recovery.',
          tags: [{ label: 'Core', tone: 'success' as const }],
        },
        {
          id: 'slot-13',
          title: 'Seal Collapse',
          subtitle: 'Late finisher',
          meta: 'Cost 3 | Converts control into closeout pressure',
          note: 'Reliable closer once the board is secured.',
          tags: [{ label: 'Ready', tone: 'accent' as const }],
        },
      ],
    },
    'deck-frost': {
      name: 'Frost Seal Draft',
      role: 'Experimental build',
      summary: 'Held open for the next iteration and missing two committed slots.',
      stats: [
        { value: '10', label: 'Cards', note: 'Current mock deck size' },
        { value: '2', label: 'Open slots', note: 'Needs the next design pass' },
        { value: '1', label: 'Draft note', note: 'Priority for next editor cycle' },
      ],
      slots: [
        {
          id: 'slot-21',
          title: 'Cold Trace',
          subtitle: 'Draft opener',
          meta: 'Cost 1 | Placeholder control line',
          note: 'May be replaced once the final frost package lands.',
          tags: [{ label: 'Draft', tone: 'warning' as const }],
        },
        {
          id: 'slot-22',
          title: 'Empty slot',
          subtitle: 'Awaiting fill',
          meta: 'Cost ? | Unassigned slot',
          note: 'Reserved for future API-backed card search and assignment.',
          tags: [{ label: 'Open', tone: 'accent' as const }],
        },
        {
          id: 'slot-23',
          title: 'Seal Drift',
          subtitle: 'Experimental follow-up',
          meta: 'Cost 2 | Flexible utility',
          note: 'Current placeholder for late draft balancing.',
          tags: [{ label: 'Draft', tone: 'warning' as const }],
        },
      ],
    },
  } as const

  const defaultDeckId = 'deck-vanguard'
  const selectedDeckId =
    typeof window === 'undefined'
      ? defaultDeckId
      : window.sessionStorage.getItem(DECK_HANDOFF_KEY) ?? defaultDeckId

  const deck = deckRecords[selectedDeckId as keyof typeof deckRecords] ?? deckRecords[defaultDeckId]
  const slots = [...deck.slots]

  let selectedId = $state(slots[0]?.id ?? '')

  const selectedSlot = $derived.by(() => slots.find((item) => item.id === selectedId) ?? slots[0] ?? null)

  // TODO: Expand the fixed route /decks/editor into /decks/:id/editor when deck IDs are available.
</script>

<div class="editor-page">
  <SectionFrame
    eyebrow="Selected Deck"
    title={deck.name}
    description="Deck editor is currently wired to the default deck selected from the list page."
  >
    <div class="editor-page__hero">
      <div class="editor-page__hero-copy">
        <p>{deck.role}</p>
        <h3>{deck.summary}</h3>
      </div>

      <div class="editor-page__hero-tags">
        <TagChip label="Active" tone="success" />
        <TagChip label="Batch 3" tone="accent" />
      </div>
    </div>

    <div class="editor-page__stats">
      {#each deck.stats as stat}
        <StatBlock value={stat.value} label={stat.label} note={stat.note} />
      {/each}
    </div>
  </SectionFrame>

  <div class="editor-page__grid">
    <SectionFrame
      title="Deck slots"
      description="EntityListPane is reused here to keep deck slot browsing consistent with the list pages."
    >
      <EntityListPane items={slots} selectedId={selectedId} onSelect={(id) => (selectedId = id)} />
    </SectionFrame>

    <SectionFrame
      title="Selected slot"
      description="This is the next handoff point for the real editor controls and card picker."
    >
      {#if selectedSlot}
        <div class="editor-page__slot-detail">
          <div>
            <h3>{selectedSlot.title}</h3>
            <p>{selectedSlot.subtitle}</p>
          </div>

          <div class="editor-page__slot-tags">
            {#each selectedSlot.tags ?? [] as tag}
              <TagChip label={tag.label} tone={tag.tone} />
            {/each}
          </div>

          <p>{selectedSlot.meta}</p>
          <p>{selectedSlot.note}</p>

          <div class="editor-page__todo">
            <p>TODO: Replace mock slot data with editor state from the API.</p>
            <p>TODO: Add card picker, reorder, and validation flows.</p>
          </div>
        </div>
      {/if}
    </SectionFrame>
  </div>

  <SectionFrame
    title="Editor actions"
    description="This strip marks where save, duplicate, and assign actions will land in later batches."
  >
    <div class="editor-page__actions">
      <a class="editor-page__link-action" data-nav href="/decks">Back to deck list</a>
      <button type="button" disabled>Save deck (TODO)</button>
      <button type="button" disabled>Assign deck (TODO)</button>
    </div>
  </SectionFrame>
</div>

<style>
  .editor-page,
  .editor-page__grid,
  .editor-page__slot-detail,
  .editor-page__todo {
    display: grid;
    gap: 1.5rem;
  }

  .editor-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .editor-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .editor-page__hero-copy p,
  .editor-page__hero-copy h3,
  .editor-page__slot-detail p,
  .editor-page__todo p {
    margin: 0;
  }

  .editor-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .editor-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .editor-page__hero-tags,
  .editor-page__slot-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .editor-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .editor-page__grid {
    grid-template-columns: minmax(0, 1.05fr) minmax(19rem, 0.95fr);
  }

  .editor-page__slot-detail {
    align-content: start;
  }

  .editor-page__slot-detail h3 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 1.45rem;
  }

  .editor-page__slot-detail > div:first-child p,
  .editor-page__slot-detail > p {
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .editor-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .editor-page__todo p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .editor-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .editor-page__link-action,
  .editor-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .editor-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  @media (max-width: 960px) {
    .editor-page__stats,
    .editor-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
