<script lang="ts">
  import TagChip from './TagChip.svelte'

  export type ParticipantSlotTone = 'accent' | 'muted' | 'success' | 'warning'

  type Props = {
    slot: string
    name: string
    state: string
    tone?: ParticipantSlotTone
    note?: string
  }

  let { slot, name, state, tone = 'muted', note = '' }: Props = $props()
</script>

<article class="participant-slot">
  <div class="participant-slot__head">
    <strong>{slot}</strong>
    <TagChip label={state} tone={tone} />
  </div>
  <h4>{name}</h4>

  {#if note}
    <p>{note}</p>
  {/if}
</article>

<style>
  .participant-slot {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    padding: 1rem;
    display: grid;
    gap: 0.75rem;
  }

  .participant-slot__head {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    align-items: center;
  }

  .participant-slot strong {
    font-size: 0.85rem;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .participant-slot h4,
  .participant-slot p {
    margin: 0;
  }

  .participant-slot h4 {
    font-size: 1rem;
  }

  .participant-slot p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }
</style>
