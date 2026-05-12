<script lang="ts">
  import { onMount } from 'svelte'
  import { getLabEffectCards, probeLabEffect } from '../../api/lab'
  import type {
    LabEffectCardOptionDto,
    LabEffectProbeRequest,
    LabEffectProbeResponse,
  } from '../../api/labTypes'
  import { getApiErrorMessage } from '../../api/types'
  import ContentStatePanel from '../ContentStatePanel.svelte'
  import LabProbeResultPanel from './LabProbeResultPanel.svelte'
  import SectionFrame from '../SectionFrame.svelte'
  import TagChip from '../TagChip.svelte'

  type ProbeTargetKind = 'ENEMY' | 'PLAYER'

  type Props = {
    onCardsChange?: (cards: LabEffectCardOptionDto[]) => void
    onResultChange?: (result: LabEffectProbeResponse | null) => void
  }

  let { onCardsChange, onResultChange }: Props = $props()

  let cardsLoading = $state(false)
  let cardsErrorMessage = $state<string | null>(null)
  let cards = $state<LabEffectCardOptionDto[]>([])
  let selectedCardId = $state('')

  let actorAttackPower = $state(7)
  let actorHealPower = $state(5)
  let actorHp = $state(20)
  let actorMaxHp = $state(20)
  let actorStatusesJson = $state('{}')

  let targetKind = $state<ProbeTargetKind>('ENEMY')
  let targetId = $state('dummy_enemy')
  let targetHp = $state(30)
  let targetMaxHp = $state(30)
  let targetStatusesJson = $state('{}')

  let choiceId = $state('')
  let discardIdsJson = $state('[]')
  let selectedIdsJson = $state('[]')
  let seed = $state('')

  let probeLoading = $state(false)
  let formErrors = $state<string[]>([])
  let probeErrorMessage = $state<string | null>(null)
  let probeResult = $state<LabEffectProbeResponse | null>(null)
  let lastRequest = $state<LabEffectProbeRequest | null>(null)

  const selectedCard = $derived.by(() =>
    cards.find((card) => card.cardId === selectedCardId) ?? null,
  )

  onMount(() => {
    void loadCards()
  })

  async function loadCards() {
    cardsLoading = true
    cardsErrorMessage = null

    try {
      const response = await getLabEffectCards()
      cards = response
      selectedCardId = response.some((card) => card.cardId === selectedCardId)
        ? selectedCardId
        : response[0]?.cardId ?? ''
      onCardsChange?.(response)
    } catch (error) {
      cards = []
      selectedCardId = ''
      onCardsChange?.([])
      cardsErrorMessage = getApiErrorMessage(error, 'Unable to load Lab effect cards.')
    } finally {
      cardsLoading = false
    }
  }

  function parseJsonInput<T>(
    label: string,
    raw: string,
    guard: (value: unknown) => value is T,
    expected: string,
    errors: string[],
  ): T | null {
    try {
      const parsed: unknown = JSON.parse(raw)
      if (!guard(parsed)) {
        errors.push(`${label} must be ${expected}.`)
        return null
      }
      return parsed
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Invalid JSON'
      errors.push(`${label} JSON is invalid: ${message}`)
      return null
    }
  }

  function isStatusRecord(value: unknown): value is Record<string, number> {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false
    return Object.entries(value).every(
      ([key, entry]) => typeof key === 'string' && typeof entry === 'number' && Number.isFinite(entry),
    )
  }

  function isStringArray(value: unknown): value is string[] {
    return Array.isArray(value) && value.every((entry) => typeof entry === 'string')
  }

  function normalizedOptionalText(value: string) {
    const normalized = value.trim()
    return normalized.length > 0 ? normalized : null
  }

  function normalizedOptionalNumber(label: string, value: string, errors: string[]) {
    const normalized = value.trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    if (!Number.isFinite(parsed)) {
      errors.push(`${label} must be a number.`)
      return null
    }
    return parsed
  }

  function buildRequest(validateOnly: boolean): LabEffectProbeRequest | null {
    const errors: string[] = []

    if (!selectedCardId) {
      errors.push('Card is required.')
    }

    const actorStatuses = parseJsonInput(
      'Actor statuses',
      actorStatusesJson,
      isStatusRecord,
      'a JSON object with numeric values',
      errors,
    )
    const targetStatuses = parseJsonInput(
      'Target statuses',
      targetStatusesJson,
      isStatusRecord,
      'a JSON object with numeric values',
      errors,
    )
    const discardIds = parseJsonInput(
      'Discard IDs',
      discardIdsJson,
      isStringArray,
      'a JSON string array',
      errors,
    )
    const selectedIds = parseJsonInput(
      'Selected IDs',
      selectedIdsJson,
      isStringArray,
      'a JSON string array',
      errors,
    )
    const normalizedSeed = normalizedOptionalNumber('Seed', seed, errors)
    const normalizedChoiceId = normalizedOptionalText(choiceId)
    const normalizedTargetId = normalizedOptionalText(targetId) ?? 'dummy_enemy'

    if (errors.length > 0 || !actorStatuses || !targetStatuses || !discardIds || !selectedIds) {
      formErrors = errors
      return null
    }

    formErrors = []
    return {
      cardId: selectedCardId,
      actor: {
        attackPower: actorAttackPower,
        healPower: actorHealPower,
        hp: actorHp,
        maxHp: actorMaxHp,
        statuses: actorStatuses,
      },
      target: {
        kind: targetKind,
        id: normalizedTargetId,
        hp: targetHp,
        maxHp: targetMaxHp,
        statuses: targetStatuses,
      },
      selection: {
        targets: [{ kind: targetKind, id: normalizedTargetId }],
        discardIds,
        selectedIds,
        choiceId: normalizedChoiceId,
      },
      seed: normalizedSeed,
      validateOnly,
    }
  }

  async function runProbe(validateOnly: boolean) {
    const request = buildRequest(validateOnly)
    if (!request) return

    probeLoading = true
    probeErrorMessage = null
    lastRequest = request

    try {
      const response = await probeLabEffect(request)
      probeResult = response
      onResultChange?.(response)
    } catch (error) {
      probeErrorMessage = getApiErrorMessage(error, 'Unable to run the effect probe request.')
    } finally {
      probeLoading = false
    }
  }

  function applyPreset(preset: 'basic' | 'low' | 'high') {
    if (preset === 'basic') {
      actorAttackPower = 7
      actorHealPower = 5
      actorHp = 20
      actorMaxHp = 20
      targetKind = 'ENEMY'
      targetId = 'dummy_enemy'
      targetHp = 30
      targetMaxHp = 30
      return
    }

    if (preset === 'low') {
      actorAttackPower = 3
      actorHealPower = 3
      return
    }

    actorAttackPower = 15
    actorHealPower = 15
  }
</script>

<div class="lab-effect-probe-panel">
  <div class="lab-effect-probe-panel__setup">
    <SectionFrame
      title="Card"
      description="Load probeable cards from the backend and choose the CardEffect to inspect."
    >
      <div class="lab-effect-probe-panel__form">
        <button type="button" onclick={() => void loadCards()} disabled={cardsLoading}>
          {cardsLoading ? 'Loading cards...' : 'Reload cards'}
        </button>

        <label>
          <span>Card</span>
          <select bind:value={selectedCardId} disabled={cards.length === 0 || cardsLoading}>
            {#if cards.length === 0}
              <option value="">No cards loaded</option>
            {:else}
              {#each cards as card}
                <option value={card.cardId}>{card.cardId} - {card.name}</option>
              {/each}
            {/if}
          </select>
        </label>
      </div>

      {#if cardsErrorMessage}
        <ContentStatePanel title="Card list failed" message={cardsErrorMessage} tone="error" />
      {/if}

      {#if selectedCard}
        <article class="lab-effect-probe-panel__card">
          <div>
            <p>{selectedCard.cardId}</p>
            <h3>{selectedCard.name}</h3>
          </div>
          <div class="lab-effect-probe-panel__chips">
            <TagChip label={selectedCard.type} tone="accent" />
            <TagChip label={`Cost ${selectedCard.cost}`} tone="warning" />
            {#each selectedCard.tags as tag}
              <TagChip label={tag} tone="muted" />
            {/each}
          </div>
          <p>{selectedCard.text || 'No card text.'}</p>
        </article>
      {/if}
    </SectionFrame>

    <SectionFrame title="Presets" description="Presets only update request inputs. They do not run the API.">
      <div class="lab-effect-probe-panel__actions">
        <button type="button" onclick={() => applyPreset('basic')}>
          Basic attacker
        </button>
        <button type="button" onclick={() => applyPreset('low')}>
          Low stat
        </button>
        <button type="button" onclick={() => applyPreset('high')}>
          High stat
        </button>
      </div>
    </SectionFrame>

    <SectionFrame title="Actor" description="Probe actor stats. These values are sent to the backend request.">
      <div class="lab-effect-probe-panel__field-grid">
        <label>
          <span>Attack Power</span>
          <input bind:value={actorAttackPower} type="number" min="0" />
        </label>
        <label>
          <span>Heal Power</span>
          <input bind:value={actorHealPower} type="number" min="0" />
        </label>
        <label>
          <span>HP</span>
          <input bind:value={actorHp} type="number" min="1" />
        </label>
        <label>
          <span>Max HP</span>
          <input bind:value={actorMaxHp} type="number" min="1" />
        </label>
      </div>

      <label class="lab-effect-probe-panel__json-field">
        <span>Statuses JSON</span>
        <textarea
          bind:value={actorStatusesJson}
          spellcheck="false"
          rows="5"
          placeholder={'{\n  "축복": 2,\n  "고통": 4\n}'}
        ></textarea>
      </label>
    </SectionFrame>

    <SectionFrame title="Target" description="MVP target input. The selection target is built from this target.">
      <div class="lab-effect-probe-panel__field-grid">
        <label>
          <span>Kind</span>
          <select bind:value={targetKind}>
            <option value="ENEMY">ENEMY</option>
            <option value="PLAYER">PLAYER</option>
          </select>
        </label>
        <label>
          <span>ID</span>
          <input bind:value={targetId} autocomplete="off" placeholder="dummy_enemy" />
        </label>
        <label>
          <span>HP</span>
          <input bind:value={targetHp} type="number" min="1" />
        </label>
        <label>
          <span>Max HP</span>
          <input bind:value={targetMaxHp} type="number" min="1" />
        </label>
      </div>

      <label class="lab-effect-probe-panel__json-field">
        <span>Statuses JSON</span>
        <textarea
          bind:value={targetStatusesJson}
          spellcheck="false"
          rows="5"
          placeholder={'{\n  "보호": 3\n}'}
        ></textarea>
      </label>
    </SectionFrame>

    <SectionFrame title="Selection" description="Advanced request fields. Targets are derived from the target input above.">
      <details class="lab-effect-probe-panel__details">
        <summary>Advanced selection fields</summary>
        <div class="lab-effect-probe-panel__advanced">
          <label>
            <span>Choice ID</span>
            <input bind:value={choiceId} autocomplete="off" placeholder="optional" />
          </label>
          <label>
            <span>Seed</span>
            <input bind:value={seed} autocomplete="off" inputmode="numeric" placeholder="optional" />
          </label>
          <label class="lab-effect-probe-panel__json-field">
            <span>Discard IDs JSON</span>
            <textarea
              bind:value={discardIdsJson}
              spellcheck="false"
              rows="4"
              placeholder={'[\n  "card-instance-id"\n]'}
            ></textarea>
          </label>
          <label class="lab-effect-probe-panel__json-field">
            <span>Selected IDs JSON</span>
            <textarea
              bind:value={selectedIdsJson}
              spellcheck="false"
              rows="4"
              placeholder={'[\n  "card-instance-id"\n]'}
            ></textarea>
          </label>
        </div>
      </details>

      <div class="lab-effect-probe-panel__actions">
        <button
          type="button"
          onclick={() => void runProbe(true)}
          disabled={probeLoading || !selectedCardId}
        >
          {probeLoading ? 'Running...' : 'Validate'}
        </button>
        <button
          type="button"
          onclick={() => void runProbe(false)}
          disabled={probeLoading || !selectedCardId}
        >
          {probeLoading ? 'Running...' : 'Resolve'}
        </button>
      </div>

      {#if formErrors.length > 0}
        <ContentStatePanel
          title="Fix request input"
          message="The probe request was not sent because one or more inputs are invalid."
          tone="error"
        >
          <ul class="lab-effect-probe-panel__errors">
            {#each formErrors as error}
              <li>{error}</li>
            {/each}
          </ul>
        </ContentStatePanel>
      {/if}

      {#if probeErrorMessage}
        <ContentStatePanel title="Probe request failed" message={probeErrorMessage} tone="error" />
      {/if}
    </SectionFrame>
  </div>

  <LabProbeResultPanel request={lastRequest} response={probeResult} />
</div>

<style>
  .lab-effect-probe-panel,
  .lab-effect-probe-panel__setup,
  .lab-effect-probe-panel__form,
  .lab-effect-probe-panel__advanced {
    display: grid;
    gap: 1.5rem;
  }

  .lab-effect-probe-panel {
    grid-template-columns: minmax(19rem, 0.9fr) minmax(0, 1.1fr);
    align-items: start;
  }

  .lab-effect-probe-panel__field-grid {
    display: grid;
    gap: 1rem;
  }

  .lab-effect-probe-panel__field-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-bottom: 1rem;
  }

  .lab-effect-probe-panel label,
  .lab-effect-probe-panel__json-field {
    display: grid;
    gap: 0.4rem;
  }

  .lab-effect-probe-panel label span,
  .lab-effect-probe-panel__json-field span {
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .lab-effect-probe-panel input,
  .lab-effect-probe-panel select,
  .lab-effect-probe-panel textarea {
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    padding: 0.55rem 0.7rem;
  }

  .lab-effect-probe-panel input,
  .lab-effect-probe-panel select {
    min-height: 2.65rem;
  }

  .lab-effect-probe-panel textarea {
    min-height: 6rem;
    resize: vertical;
    font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
    line-height: 1.5;
  }

  .lab-effect-probe-panel button {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .lab-effect-probe-panel button:disabled {
    opacity: 0.68;
  }

  .lab-effect-probe-panel__actions,
  .lab-effect-probe-panel__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .lab-effect-probe-panel__card {
    display: grid;
    gap: 0.85rem;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .lab-effect-probe-panel__card h3,
  .lab-effect-probe-panel__card p,
  .lab-effect-probe-panel__errors {
    margin: 0;
  }

  .lab-effect-probe-panel__card h3 {
    font-family: var(--font-display);
    font-size: 1.2rem;
  }

  .lab-effect-probe-panel__card > div:first-child p {
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .lab-effect-probe-panel__card > p,
  .lab-effect-probe-panel__errors {
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .lab-effect-probe-panel__details {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    padding: 0.85rem;
  }

  .lab-effect-probe-panel__details summary {
    cursor: pointer;
    color: var(--color-text-soft);
    font-weight: 600;
  }

  .lab-effect-probe-panel__advanced {
    margin-top: 1rem;
  }

  .lab-effect-probe-panel__errors {
    padding-left: 1.2rem;
  }

  @media (max-width: 1120px) {
    .lab-effect-probe-panel {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 640px) {
    .lab-effect-probe-panel__field-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
