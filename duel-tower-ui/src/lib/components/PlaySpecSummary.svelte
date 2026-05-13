<script lang="ts">
  import type { CardPlaySpec } from '../api/contentTypes'
  import { formatContentEnumLabel } from '../content/display'
  import TagChip from './TagChip.svelte'

  type SummaryRow = {
    label: string
    value: string
  }

  type RequirementSummary = {
    title: string
    detail: string
    chips: string[]
  }

  type PlaySpecSummaryView = {
    rows: SummaryRow[]
    requirements: RequirementSummary[]
    emptyTitle: string
    emptyMessage: string
  }

  type Props = {
    playSpec: CardPlaySpec
  }

  let { playSpec }: Props = $props()

  function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object' && !Array.isArray(value)
  }

  function getString(value: unknown) {
    return typeof value === 'string' && value.trim() ? value.trim() : null
  }

  function getNumber(value: unknown) {
    return typeof value === 'number' && Number.isFinite(value) ? value : null
  }

  function getBoolean(value: unknown) {
    return typeof value === 'boolean' ? value : null
  }

  function getArray(value: unknown) {
    return Array.isArray(value) ? value : []
  }

  function formatRange(minValue: unknown, maxValue: unknown) {
    const minSelections = getNumber(minValue)
    const maxSelections = getNumber(maxValue)

    if (minSelections === null && maxSelections === null) {
      return 'Selection count is not specified.'
    }

    if (minSelections === maxSelections) {
      return `Select exactly ${minSelections}.`
    }

    if (minSelections === 0 && maxSelections !== null) {
      return `Select up to ${maxSelections}.`
    }

    if (minSelections !== null && maxSelections !== null) {
      return `Select ${minSelections} to ${maxSelections}.`
    }

    return minSelections !== null ? `Select at least ${minSelections}.` : `Select up to ${maxSelections}.`
  }

  function formatEnumList(value: unknown) {
    return getArray(value)
      .map((item) => getString(item))
      .filter((item): item is string => item !== null)
      .map((item) => formatContentEnumLabel(item))
  }

  function summarizeTarget(targetSpec: unknown): SummaryRow[] {
    if (!isRecord(targetSpec)) {
      return []
    }

    const target = getString(targetSpec.target)
    const requiredSelection = getBoolean(targetSpec.requiredSelection)
    const rows: SummaryRow[] = []

    if (target && target !== 'NONE') {
      rows.push({
        label: 'Target',
        value: formatContentEnumLabel(target),
      })
    }

    if (requiredSelection !== null) {
      rows.push({
        label: 'Target selection',
        value: requiredSelection ? 'Player must choose a target.' : 'No target selection required.',
      })
    }

    return rows
  }

  function summarizeRequirement(requirement: unknown): RequirementSummary | null {
    if (!isRecord(requirement)) {
      return null
    }

    const type = getString(requirement.type)

    switch (type) {
      case 'discard_from_hand': {
        const count = getNumber(requirement.count) ?? 1
        const filter = getString(requirement.filter)
        const excludesSource = getBoolean(requirement.excludeSourceCard)
        const chips = [`Count ${count}`]

        if (filter) {
          chips.push(formatContentEnumLabel(filter))
        }

        if (excludesSource) {
          chips.push('Excludes source card')
        }

        return {
          title: 'Discard from hand',
          detail: `Discard ${count} card${count === 1 ? '' : 's'} before playing this card.`,
          chips,
        }
      }

      case 'select_board_objects': {
        const relation = getString(requirement.relation)
        const filter = getString(requirement.filter)
        const kinds = formatEnumList(requirement.kinds)
        const excludesSource = getBoolean(requirement.excludeSourceCard)
        const chips = [
          relation ? formatContentEnumLabel(relation) : null,
          filter ? formatContentEnumLabel(filter) : null,
          ...kinds,
          excludesSource ? 'Excludes source card' : null,
        ].filter((item): item is string => item !== null)

        return {
          title: 'Select board objects',
          detail: formatRange(requirement.minSelections, requirement.maxSelections),
          chips,
        }
      }

      case 'select_field_cards': {
        const scope = getString(requirement.scope)
        const filter = getString(requirement.filter)
        const excludesSource = getBoolean(requirement.excludeSourceCard)
        const chips = [
          scope ? formatContentEnumLabel(scope) : null,
          filter ? formatContentEnumLabel(filter) : null,
          excludesSource ? 'Excludes source card' : null,
        ].filter((item): item is string => item !== null)

        return {
          title: 'Select field cards',
          detail: formatRange(requirement.minSelections, requirement.maxSelections),
          chips,
        }
      }

      case 'choice': {
        const label = getString(requirement.label)
        const id = getString(requirement.id)
        const options = getArray(requirement.options).filter(isRecord)
        const chips = [
          id ? `Choice ${id}` : null,
          `${options.length} option${options.length === 1 ? '' : 's'}`,
        ].filter((item): item is string => item !== null)

        return {
          title: label ?? 'Choose an option',
          detail: formatRange(requirement.minSelections, requirement.maxSelections),
          chips,
        }
      }

      default:
        if (!type) {
          return null
        }

        return {
          title: formatContentEnumLabel(type),
          detail: 'This card has an additional play requirement.',
          chips: [],
        }
    }
  }

  function summarizePlaySpec(value: CardPlaySpec): PlaySpecSummaryView {
    if (value === null) {
      return {
        rows: [],
        requirements: [],
        emptyTitle: 'No play rules recorded',
        emptyMessage: 'This card does not expose additional play timing or selection rules.',
      }
    }

    if (typeof value === 'string') {
      return {
        rows: [],
        requirements: [],
        emptyTitle: 'Play rules need developer review',
        emptyMessage: 'This play spec uses a raw text format and cannot be summarized for normal display.',
      }
    }

    const rows = summarizeTarget(value.target)
    const requirements = getArray(value.extraRequirements)
      .map(summarizeRequirement)
      .filter((item): item is RequirementSummary => item !== null)

    if (rows.length === 0 && requirements.length === 0) {
      return {
        rows,
        requirements,
        emptyTitle: 'No recognizable play rules',
        emptyMessage: 'There are no user-facing target or requirement details to show.',
      }
    }

    return {
      rows,
      requirements,
      emptyTitle: '',
      emptyMessage: '',
    }
  }

  function formatRawPlaySpec(value: CardPlaySpec) {
    if (value === null) {
      return null
    }

    if (typeof value === 'string') {
      return value.trim() || null
    }

    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return null
    }
  }

  const summary = $derived.by(() => summarizePlaySpec(playSpec))
  const rawPlaySpec = $derived.by(() => formatRawPlaySpec(playSpec))
  const hasSummary = $derived.by(() => summary.rows.length > 0 || summary.requirements.length > 0)
</script>

<div class="play-spec-summary">
  {#if hasSummary}
    {#if summary.rows.length > 0}
      <div class="play-spec-summary__rows">
        {#each summary.rows as row}
          <div class="play-spec-summary__row">
            <span>{row.label}</span>
            <strong>{row.value}</strong>
          </div>
        {/each}
      </div>
    {/if}

    {#if summary.requirements.length > 0}
      <div class="play-spec-summary__requirements">
        {#each summary.requirements as requirement}
          <article class="play-spec-summary__requirement">
            <div>
              <h3>{requirement.title}</h3>
              <p>{requirement.detail}</p>
            </div>

            {#if requirement.chips.length > 0}
              <div class="play-spec-summary__chips">
                {#each requirement.chips as chip}
                  <TagChip label={chip} tone="muted" />
                {/each}
              </div>
            {/if}
          </article>
        {/each}
      </div>
    {/if}
  {:else}
    <div class="play-spec-summary__empty">
      <h3>{summary.emptyTitle}</h3>
      <p>{summary.emptyMessage}</p>
    </div>
  {/if}

  {#if rawPlaySpec}
    <details class="play-spec-summary__raw">
      <summary>Developer/raw data</summary>
      <pre>{rawPlaySpec}</pre>
    </details>
  {/if}
</div>

<style>
  .play-spec-summary {
    display: grid;
    gap: 1rem;
  }

  .play-spec-summary__rows,
  .play-spec-summary__requirements {
    display: grid;
    gap: 0.75rem;
  }

  .play-spec-summary__row,
  .play-spec-summary__requirement,
  .play-spec-summary__empty {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    padding: 0.9rem;
  }

  .play-spec-summary__row {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
  }

  .play-spec-summary__row span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .play-spec-summary__row strong {
    color: var(--color-text);
    font-weight: 600;
    text-align: right;
  }

  .play-spec-summary__requirement {
    display: grid;
    gap: 0.75rem;
  }

  .play-spec-summary__requirement h3,
  .play-spec-summary__requirement p,
  .play-spec-summary__empty h3,
  .play-spec-summary__empty p {
    margin: 0;
  }

  .play-spec-summary__requirement h3,
  .play-spec-summary__empty h3 {
    font-family: var(--font-display);
    font-size: 1.15rem;
    line-height: 1.2;
  }

  .play-spec-summary__requirement p,
  .play-spec-summary__empty p {
    color: var(--color-text-soft);
    line-height: 1.65;
  }

  .play-spec-summary__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .play-spec-summary__raw {
    border-top: 1px solid var(--color-border);
    padding-top: 0.85rem;
  }

  .play-spec-summary__raw summary {
    cursor: pointer;
    color: var(--color-text-muted);
    font-size: 0.78rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .play-spec-summary__raw pre {
    margin: 0.85rem 0 0;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(8, 7, 6, 0.42);
    color: var(--color-text-soft);
    font: 0.86rem/1.6 'Fira Code', 'Consolas', monospace;
    white-space: pre-wrap;
    word-break: break-word;
  }
</style>
