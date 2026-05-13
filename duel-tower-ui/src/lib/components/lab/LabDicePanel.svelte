<script lang="ts">
  import { rollLabDice } from '../../api/lab'
  import type { LabDiceRequest, LabDiceResponse } from '../../api/labTypes'
  import { getApiErrorMessage } from '../../api/types'
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import StatBlock from '../StatBlock.svelte'
  import TagChip from '../TagChip.svelte'

  type Props = {
    onResultChange?: (result: LabDiceResponse | null) => void
  }

  let { onResultChange }: Props = $props()

  const DEFAULT_NOTATION = '1d6'
  const DEFAULT_ROLL_COUNT = 20
  const DICE_EXAMPLES = ['3D6KH2+2D4MAX+1', '4D6DL2', '1D6+2D4+3']

  let notation = $state(DEFAULT_NOTATION)
  let rollCount = $state(DEFAULT_ROLL_COUNT)
  let seed = $state('')
  let loading = $state(false)
  let errorMessage = $state<string | null>(null)
  let result = $state<LabDiceResponse | null>(null)

  const seedLabel = $derived.by(() =>
    result?.seed !== null && result?.seed !== undefined ? String(result.seed) : 'Not provided',
  )
  const expectedNote = $derived.by(() => {
    if (!result) return 'Server expected value'
    if (result.expectedAvailable === false) {
      return result.expectedNote ?? 'Expected value is not available for this notation.'
    }
    if (result.expectedNumerator === undefined || result.expectedDenominator === undefined) {
      return 'Server expected value'
    }
    return `Rational ${result.expectedNumerator}/${result.expectedDenominator}`
  })
  const resultNotation = $derived.by(() => result?.normalizedNotation ?? result?.notation ?? '')
  const specSummary = $derived.by(() => {
    if (!result?.spec) return 'Expression'
    return `${result.spec.count}d${result.spec.sides}`
  })
  const specNote = $derived.by(() => {
    if (!result?.spec) return 'Composite or selector notation'
    return `Modifier ${result.spec.modifier >= 0 ? '+' : ''}${result.spec.modifier}`
  })

  function createRequest(): LabDiceRequest {
    const request: LabDiceRequest = {
      notation,
      rollCount,
    }

    const normalizedSeed = seed.trim()
    if (normalizedSeed) {
      const parsedSeed = Number(normalizedSeed)
      if (!Number.isFinite(parsedSeed)) {
        throw new Error('Seed must be a number.')
      }
      request.seed = parsedSeed
    }

    return request
  }

  async function submitDice() {
    loading = true
    errorMessage = null

    try {
      const response = await rollLabDice(createRequest())
      result = response
      onResultChange?.(response)
    } catch (error) {
      errorMessage = getApiErrorMessage(error, 'Unable to run the dice lab request.')
    } finally {
      loading = false
    }
  }

  function clearDice() {
    notation = DEFAULT_NOTATION
    rollCount = DEFAULT_ROLL_COUNT
    seed = ''
    errorMessage = null
    result = null
    onResultChange?.(null)
  }
</script>

<div class="lab-dice-panel">
  <SectionFrame
    title="Dice request"
    description="서버 Lab Dice API로 주사위 표기를 실험합니다."
  >
    <form class="lab-dice-panel__form" onsubmit={(event) => {
      event.preventDefault()
      void submitDice()
    }}>
      <label>
        <span>Notation</span>
        <input bind:value={notation} autocomplete="off" placeholder="3D6KH2+2D4MAX+1" />
      </label>

      <div class="lab-dice-panel__examples" aria-label="Dice notation examples">
        {#each DICE_EXAMPLES as example}
          <button
            type="button"
            class="lab-dice-panel__example"
            onclick={() => {
              notation = example
            }}
            disabled={loading}
          >
            {example}
          </button>
        {/each}
      </div>

      <label>
        <span>Roll count</span>
        <input bind:value={rollCount} type="number" min="0" max="1000" />
      </label>

      <label>
        <span>Seed</span>
        <input bind:value={seed} autocomplete="off" inputmode="numeric" placeholder="optional" />
      </label>

      <div class="lab-dice-panel__actions">
        <button type="submit" disabled={loading}>
          {loading ? 'Rolling...' : 'Roll'}
        </button>
        <button
          type="button"
          class="lab-dice-panel__secondary-action"
          onclick={clearDice}
          disabled={loading}
        >
          Clear
        </button>
      </div>
    </form>

    {#if errorMessage}
      <ContentStatePanel
        title="Dice request failed"
        message={errorMessage}
        tone="error"
      >
        <p>Last successful result stays visible until another request succeeds or Clear is pressed.</p>
      </ContentStatePanel>
    {/if}
  </SectionFrame>

  <SectionFrame
    title="Dice response"
    description="최소값, 최대값, 기대값, rolls, histogram은 서버 응답을 그대로 표시합니다."
  >
    {#if result}
      <div class="lab-dice-panel__result">
        <div class="lab-dice-panel__summary">
          <StatBlock value={resultNotation} label="Notation" note="Server normalized input" />
          <StatBlock value={specSummary} label="Spec" note={specNote} />
          <StatBlock value={result.expected} label="Expected" note={expectedNote} />
          <StatBlock value={result.rollCount} label="Roll count" note={`Seed ${seedLabel}`} />
        </div>

        <div class="lab-dice-panel__chips" aria-label="Dice metadata">
          {#if result.spec}
            <TagChip label={`Count ${result.spec.count}`} tone="accent" />
            <TagChip label={`Sides ${result.spec.sides}`} tone="success" />
            <TagChip label={`Modifier ${result.spec.modifier}`} tone="warning" />
          {:else if result.expression?.terms?.length}
            {#each result.expression.terms as term}
              <TagChip
                label={term.display}
                tone={term.kind === 'DICE' ? 'accent' : 'warning'}
              />
            {/each}
          {/if}
          <TagChip label={`Min ${result.min}`} tone="muted" />
          <TagChip label={`Max ${result.max}`} tone="muted" />
          {#if result.expectedAvailable === false}
            <TagChip label="Expected N/A" tone="muted" />
          {/if}
        </div>

        {#if result.expression?.terms?.length}
          <div class="lab-dice-panel__section">
            <div class="lab-dice-panel__section-header">
              <h3>Expression</h3>
              <span>{result.expression.terms.length} terms</span>
            </div>

            <div class="lab-dice-panel__terms" aria-label="Parsed dice expression terms">
              {#each result.expression.terms as term}
                <div class="lab-dice-panel__term">
                  <strong>{term.display}</strong>
                  <span>{term.kind}</span>
                  {#if term.kind === 'DICE'}
                    <small>
                      {term.count}D{term.sides}
                      {#if term.selector}
                        · {term.selector}{term.selectorAmount}
                      {/if}
                    </small>
                  {:else}
                    <small>Value {term.value}</small>
                  {/if}
                </div>
              {/each}
            </div>
          </div>
        {/if}

        {#if result.expectedAvailable === false && result.expectedNote}
          <ContentStatePanel
            title="Expected value is not available"
            message={result.expectedNote}
          />
        {/if}

        <div class="lab-dice-panel__section">
          <div class="lab-dice-panel__section-header">
            <h3>Rolls</h3>
            <span>{result.rolls.length} returned</span>
          </div>

          {#if result.rolls.length > 0}
            <ol class="lab-dice-panel__rolls" aria-label="Roll results">
              {#each result.rolls as roll, index}
                <li>
                  <span>#{index + 1}</span>
                  <strong>{roll}</strong>
                </li>
              {/each}
            </ol>
          {:else}
            <p class="lab-dice-panel__empty">rollCount=0 요청이라 roll 결과가 없습니다.</p>
          {/if}
        </div>

        <div class="lab-dice-panel__section">
          <div class="lab-dice-panel__section-header">
            <h3>Histogram</h3>
            <span>{result.histogram.length} values</span>
          </div>

          {#if result.histogram.length > 0}
            <table class="lab-dice-panel__histogram">
              <thead>
                <tr>
                  <th scope="col">Value</th>
                  <th scope="col">Count</th>
                </tr>
              </thead>
              <tbody>
                {#each result.histogram as entry}
                  <tr>
                    <td>{entry.value}</td>
                    <td>{entry.count}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          {:else}
            <p class="lab-dice-panel__empty">서버 histogram 응답이 비어 있습니다.</p>
          {/if}
        </div>
      </div>
    {:else}
      <ContentStatePanel
        title="No dice response yet"
        message="notation, rollCount, seed를 입력하고 Roll을 누르면 서버 응답이 표시됩니다."
      />
    {/if}
  </SectionFrame>
</div>

<style>
  .lab-dice-panel,
  .lab-dice-panel__form,
  .lab-dice-panel__result,
  .lab-dice-panel__section {
    display: grid;
    gap: 1.5rem;
  }

  .lab-dice-panel {
    grid-template-columns: minmax(18rem, 0.78fr) minmax(0, 1.22fr);
    align-items: start;
  }

  .lab-dice-panel__form label {
    display: grid;
    gap: 0.4rem;
  }

  .lab-dice-panel__form span,
  .lab-dice-panel__section-header span {
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .lab-dice-panel__form input {
    min-height: 2.65rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    padding: 0.55rem 0.7rem;
  }

  .lab-dice-panel__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .lab-dice-panel__examples {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .lab-dice-panel__example {
    min-height: 2rem;
    padding: 0.35rem 0.55rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    color: var(--color-text-muted);
    font-size: 0.74rem;
  }

  .lab-dice-panel__actions button {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .lab-dice-panel__actions button:disabled {
    opacity: 0.68;
  }

  .lab-dice-panel__secondary-action {
    border-color: var(--color-border) !important;
    background: rgba(12, 11, 10, 0.28) !important;
  }

  .lab-dice-panel__summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1rem;
  }

  .lab-dice-panel__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .lab-dice-panel__section {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .lab-dice-panel__section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .lab-dice-panel__section-header h3,
  .lab-dice-panel__empty {
    margin: 0;
  }

  .lab-dice-panel__section-header h3 {
    font-family: var(--font-display);
    font-size: 1.25rem;
  }

  .lab-dice-panel__rolls {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(4.75rem, 1fr));
    gap: 0.5rem;
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .lab-dice-panel__rolls li {
    min-height: 3.25rem;
    display: grid;
    align-content: center;
    gap: 0.15rem;
    padding: 0.55rem 0.65rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .lab-dice-panel__rolls span {
    color: var(--color-text-muted);
    font-size: 0.72rem;
  }

  .lab-dice-panel__rolls strong {
    font-family: var(--font-display);
    font-size: 1.1rem;
  }

  .lab-dice-panel__terms {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(8rem, 1fr));
    gap: 0.6rem;
  }

  .lab-dice-panel__term {
    min-height: 4.75rem;
    display: grid;
    align-content: center;
    gap: 0.2rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.2);
    padding: 0.65rem 0.75rem;
  }

  .lab-dice-panel__term strong {
    font-family: var(--font-display);
    font-size: 1rem;
  }

  .lab-dice-panel__term span,
  .lab-dice-panel__term small {
    color: var(--color-text-muted);
    font-size: 0.72rem;
  }

  .lab-dice-panel__histogram {
    width: 100%;
    border-collapse: collapse;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
  }

  .lab-dice-panel__histogram th,
  .lab-dice-panel__histogram td {
    padding: 0.7rem 0.85rem;
    border-bottom: 1px solid var(--color-border);
    text-align: left;
  }

  .lab-dice-panel__histogram th {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .lab-dice-panel__empty {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 1120px) {
    .lab-dice-panel,
    .lab-dice-panel__summary {
      grid-template-columns: 1fr;
    }
  }
</style>
