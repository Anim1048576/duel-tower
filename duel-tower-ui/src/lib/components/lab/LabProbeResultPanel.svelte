<script lang="ts">
  import type {
    LabEffectProbeRequest,
    LabEffectProbeResponse,
    LabProbeEntityChangesDto,
    LabProbeSnapshotDto,
    LabProbeTargetChangesDto,
  } from '../../api/labTypes'
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import StatBlock from '../StatBlock.svelte'
  import TagChip from '../TagChip.svelte'

  type Props = {
    request: LabEffectProbeRequest | null
    response: LabEffectProbeResponse | null
  }

  let { request, response }: Props = $props()

  type SnapshotEntity = {
    id: string
    kind?: string
    hp: number
    maxHp: number
    statuses: Record<string, number>
  }

  const targetStatusChangeCount = $derived.by(() =>
    response?.changes.targets.reduce((sum, target) => sum + target.statusChanges.length, 0) ?? 0,
  )
  const totalStatusChangeCount = $derived.by(() =>
    (response?.changes.actor.statusChanges.length ?? 0) + targetStatusChangeCount,
  )
  const primaryTargetChange = $derived.by(() => response?.changes.targets[0] ?? null)
  const primaryTargetBefore = $derived.by(() =>
    response && primaryTargetChange
      ? findTarget(response.before, primaryTargetChange)
      : null,
  )
  const primaryTargetAfter = $derived.by(() =>
    response && primaryTargetChange
      ? findTarget(response.after, primaryTargetChange)
      : null,
  )
  const rawRequestText = $derived.by(() =>
    request ? JSON.stringify(request, null, 2) : 'No request has been sent yet.',
  )
  const rawResponseText = $derived.by(() =>
    response ? JSON.stringify(response, null, 2) : 'No response yet.',
  )

  function findTarget(snapshot: LabProbeSnapshotDto, change: LabProbeTargetChangesDto) {
    return snapshot.targets.find(
      (target) => target.kind === change.kind && target.id === change.id,
    ) ?? null
  }

  function formatHp(before: SnapshotEntity | null, after: SnapshotEntity | null) {
    if (!before || !after) return 'No target'
    return `${before.hp} -> ${after.hp}`
  }

  function formatHpChange(value: number) {
    if (value > 0) return `+${value}`
    return String(value)
  }

  function changeTone(value: number) {
    if (value > 0) return 'success'
    if (value < 0) return 'warning'
    return 'muted'
  }

  function statusValue(entity: SnapshotEntity | null, statusId: string) {
    return entity?.statuses[statusId] ?? 0
  }
</script>

<SectionFrame
  title="Effect Probe result"
  description="Summarizes backend validate, resolve, changes, events, and notes without recalculation."
>
  {#if response}
    <div class="lab-probe-result">
      <div class="lab-probe-result__summary">
        <StatBlock value={response.cardName} label="Card" note={response.cardId} />
        <StatBlock
          value={response.valid ? 'Valid' : 'Invalid'}
          label="Validation"
          note={`${response.validationErrors.length} validation errors`}
        />
        <StatBlock
          value={response.resolved ? 'Resolved' : 'Not resolved'}
          label="Resolve"
          note="Backend CardEffect result"
        />
        <StatBlock
          value={formatHp(primaryTargetBefore, primaryTargetAfter)}
          label="Target HP"
          note={primaryTargetChange ? `Change ${formatHpChange(primaryTargetChange.hpChange)}` : 'No target change'}
        />
        <StatBlock
          value={`${response.before.actor.hp} -> ${response.after.actor.hp}`}
          label="Actor HP"
          note={`Change ${formatHpChange(response.changes.actor.hpChange)}`}
        />
        <StatBlock
          value={totalStatusChangeCount}
          label="Status changes"
          note={`${response.events.length} events`}
        />
      </div>

      <div class="lab-probe-result__chips" aria-label="Probe status">
        <TagChip label={response.valid ? 'Valid' : 'Invalid'} tone={response.valid ? 'success' : 'warning'} />
        <TagChip label={response.resolved ? 'Resolved' : 'Validate only'} tone={response.resolved ? 'accent' : 'muted'} />
        <TagChip label={`${response.events.length} events`} tone="muted" />
      </div>

      {#if response.validationErrors.length > 0}
        <section class="lab-probe-result__section">
          <h3>Validation Errors</h3>
          <ul class="lab-probe-result__list">
            {#each response.validationErrors as error}
              <li>{error}</li>
            {/each}
          </ul>
        </section>
      {/if}

      {#if response.probeError}
        <ContentStatePanel title="Probe error" message={response.probeError} tone="error" />
      {/if}

      <section class="lab-probe-result__section">
        <h3>Actor Changes</h3>
        <div class="lab-probe-result__change-card">
          <div>
            <span>HP</span>
            <strong>{response.before.actor.hp} -> {response.after.actor.hp}</strong>
            <TagChip
              label={formatHpChange(response.changes.actor.hpChange)}
              tone={changeTone(response.changes.actor.hpChange)}
            />
          </div>

          {#if response.changes.actor.statusChanges.length > 0}
            <ul class="lab-probe-result__changes">
              {#each response.changes.actor.statusChanges as change}
                <li>
                  <span>{change.statusId}</span>
                  <strong>
                    {statusValue(response.before.actor, change.statusId)}
                    ->
                    {statusValue(response.after.actor, change.statusId)}
                  </strong>
                </li>
              {/each}
            </ul>
          {:else}
            <p>No actor status changes returned.</p>
          {/if}
        </div>
      </section>

      <section class="lab-probe-result__section">
        <h3>Target Changes</h3>
        {#if response.changes.targets.length > 0}
          <div class="lab-probe-result__target-grid">
            {#each response.changes.targets as targetChange}
              {@const beforeTarget = findTarget(response.before, targetChange)}
              {@const afterTarget = findTarget(response.after, targetChange)}
              <article class="lab-probe-result__change-card">
                <header>
                  <div>
                    <span>{targetChange.kind}</span>
                    <strong>{targetChange.id}</strong>
                  </div>
                  <TagChip
                    label={formatHpChange(targetChange.hpChange)}
                    tone={changeTone(targetChange.hpChange)}
                  />
                </header>

                <div>
                  <span>HP</span>
                  <strong>{formatHp(beforeTarget, afterTarget)}</strong>
                </div>

                {#if targetChange.statusChanges.length > 0}
                  <ul class="lab-probe-result__changes">
                    {#each targetChange.statusChanges as change}
                      <li>
                        <span>{change.statusId}</span>
                        <strong>
                          {statusValue(beforeTarget, change.statusId)}
                          ->
                          {statusValue(afterTarget, change.statusId)}
                        </strong>
                      </li>
                    {/each}
                  </ul>
                {:else}
                  <p>No target status changes returned.</p>
                {/if}
              </article>
            {/each}
          </div>
        {:else}
          <p class="lab-probe-result__empty">No target changes returned.</p>
        {/if}
      </section>

      <section class="lab-probe-result__section">
        <h3>Events</h3>
        <details class="lab-probe-result__details" open={response.events.length <= 5}>
          <summary>{response.events.length} events from backend</summary>
          {#if response.events.length > 0}
            <ol class="lab-probe-result__events">
              {#each response.events as event}
                <li>
                  <span>{event.type}</span>
                  <p>{event.message || 'No message'}</p>
                  {#if event.data && Object.keys(event.data).length > 0}
                    <pre>{JSON.stringify(event.data, null, 2)}</pre>
                  {/if}
                </li>
              {/each}
            </ol>
          {:else}
            <p class="lab-probe-result__empty">No events returned.</p>
          {/if}
        </details>
      </section>

      <section class="lab-probe-result__section">
        <h3>Probe Notes</h3>
        <ul class="lab-probe-result__list">
          {#each response.notes as note}
            <li>{note}</li>
          {/each}
        </ul>
      </section>

      <section class="lab-probe-result__section">
        <h3>Raw JSON</h3>
        <details class="lab-probe-result__details">
          <summary>Request JSON</summary>
          <pre>{rawRequestText}</pre>
        </details>
        <details class="lab-probe-result__details">
          <summary>Response JSON</summary>
          <pre>{rawResponseText}</pre>
        </details>
      </section>
    </div>
  {:else}
    <ContentStatePanel
      title="No probe response yet"
      message="Choose a card, adjust actor and target inputs, then run Validate or Resolve."
    />
  {/if}
</SectionFrame>

<style>
  .lab-probe-result,
  .lab-probe-result__section {
    display: grid;
    gap: 1.25rem;
  }

  .lab-probe-result__summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .lab-probe-result__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .lab-probe-result__section {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .lab-probe-result__section h3,
  .lab-probe-result__list,
  .lab-probe-result__events,
  .lab-probe-result__empty,
  .lab-probe-result__change-card p {
    margin: 0;
  }

  .lab-probe-result__section h3 {
    font-family: var(--font-display);
    font-size: 1.2rem;
  }

  .lab-probe-result__target-grid {
    display: grid;
    gap: 0.85rem;
  }

  .lab-probe-result__change-card {
    display: grid;
    gap: 0.85rem;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .lab-probe-result__change-card header,
  .lab-probe-result__change-card > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .lab-probe-result__change-card span,
  .lab-probe-result__events span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .lab-probe-result__change-card strong {
    font-family: var(--font-display);
    font-size: 1.1rem;
  }

  .lab-probe-result__changes,
  .lab-probe-result__events {
    display: grid;
    gap: 0.5rem;
    padding: 0;
    list-style: none;
  }

  .lab-probe-result__changes li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    border-top: 1px solid var(--color-border);
    padding-top: 0.55rem;
  }

  .lab-probe-result__events li {
    display: grid;
    gap: 0.35rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    padding: 0.75rem;
  }

  .lab-probe-result__events p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.55;
  }

  .lab-probe-result__list {
    color: var(--color-text-soft);
    line-height: 1.6;
    padding-left: 1.2rem;
  }

  .lab-probe-result__empty,
  .lab-probe-result__change-card p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .lab-probe-result__details {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    padding: 0.85rem;
  }

  .lab-probe-result__details summary {
    cursor: pointer;
    color: var(--color-text-soft);
    font-weight: 600;
  }

  .lab-probe-result pre {
    max-height: 24rem;
    overflow: auto;
    margin: 0.85rem 0 0;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(8, 7, 6, 0.38);
    color: var(--color-text-soft);
    white-space: pre-wrap;
    word-break: break-word;
  }

  @media (max-width: 1120px) {
    .lab-probe-result__summary {
      grid-template-columns: 1fr;
    }
  }
</style>
