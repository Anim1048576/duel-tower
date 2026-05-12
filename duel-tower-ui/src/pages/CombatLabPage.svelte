<script lang="ts">
  import { getLabEffectCards, probeLabEffect, rollLabDice } from '../lib/api/lab'
  import type {
    LabDiceResponse,
    LabEffectCardOptionDto,
    LabEffectProbeResponse,
  } from '../lib/api/labTypes'
  import { getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'

  type LabTab = 'dice' | 'effect-probe'

  let activeTab = $state<LabTab>('dice')

  let notation = $state('3d6+2')
  let rollCount = $state(20)
  let seed = $state('')
  let diceLoading = $state(false)
  let diceErrorMessage = $state<string | null>(null)
  let diceResult = $state<LabDiceResponse | null>(null)

  let cardsLoading = $state(false)
  let cardsErrorMessage = $state<string | null>(null)
  let effectCards = $state<LabEffectCardOptionDto[]>([])
  let selectedCardId = $state('')
  let probeLoading = $state(false)
  let probeErrorMessage = $state<string | null>(null)
  let probeResult = $state<LabEffectProbeResponse | null>(null)

  const tabs: Array<{ key: LabTab; label: string }> = [
    { key: 'dice', label: 'Dice' },
    { key: 'effect-probe', label: 'Effect Probe' },
  ]

  const selectedEffectCard = $derived.by(() =>
    effectCards.find((card) => card.cardId === selectedCardId) ?? effectCards[0] ?? null,
  )

  const diceResultText = $derived.by(() =>
    diceResult ? JSON.stringify(diceResult, null, 2) : 'No dice response yet.',
  )

  const probeResultText = $derived.by(() =>
    probeResult ? JSON.stringify(probeResult, null, 2) : 'No effect probe response yet.',
  )

  function normalizeOptionalNumber(value: string) {
    const normalized = value.trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
  }

  async function submitDice() {
    diceLoading = true
    diceErrorMessage = null

    try {
      diceResult = await rollLabDice({
        notation,
        rollCount,
        seed: normalizeOptionalNumber(seed),
      })
    } catch (error) {
      diceResult = null
      diceErrorMessage = getApiErrorMessage(error, 'Unable to run the dice lab request.')
    } finally {
      diceLoading = false
    }
  }

  async function loadEffectCards() {
    cardsLoading = true
    cardsErrorMessage = null

    try {
      effectCards = await getLabEffectCards()
      selectedCardId = effectCards.some((card) => card.cardId === selectedCardId)
        ? selectedCardId
        : effectCards[0]?.cardId ?? ''
    } catch (error) {
      effectCards = []
      selectedCardId = ''
      cardsErrorMessage = getApiErrorMessage(error, 'Unable to load Lab effect cards.')
    } finally {
      cardsLoading = false
    }
  }

  async function submitProbe() {
    if (!selectedEffectCard) return

    probeLoading = true
    probeErrorMessage = null

    try {
      probeResult = await probeLabEffect({
        cardId: selectedEffectCard.cardId,
        actor: {
          attackPower: 7,
          healPower: 5,
          hp: 20,
          maxHp: 20,
          statuses: {},
        },
        target: {
          kind: 'ENEMY',
          id: 'dummy_enemy',
          hp: 30,
          maxHp: 30,
          statuses: {},
        },
        selection: {
          targets: [{ kind: 'ENEMY', id: 'dummy_enemy' }],
          discardIds: [],
          selectedIds: [],
          choiceId: null,
        },
        validateOnly: true,
      })
    } catch (error) {
      probeResult = null
      probeErrorMessage = getApiErrorMessage(error, 'Unable to run the effect probe request.')
    } finally {
      probeLoading = false
    }
  }
</script>

<div class="combat-lab-page">
  <SectionFrame
    eyebrow="Lab"
    title="Combat Lab"
    description="주사위, 카드 효과, 상태 조합을 실험합니다."
  >
    <div class="combat-lab-page__hero">
      <div class="combat-lab-page__copy">
        <p>Effect Lab</p>
        <h3>Backend Lab API 응답을 확인하는 실험 공간입니다.</h3>
      </div>

      <div class="combat-lab-page__tags">
        <TagChip label="Dice" tone="accent" />
        <TagChip label="Effect Probe" tone="success" />
        <TagChip label="Build Test" tone="warning" />
      </div>
    </div>

    <div class="combat-lab-page__stats">
      <StatBlock
        value={diceResult ? diceResult.rollCount : '-'}
        label="Dice rolls"
        note="Last backend dice response"
      />
      <StatBlock
        value={effectCards.length}
        label="Probe cards"
        note="Loaded from /api/lab/effects/cards"
      />
      <StatBlock
        value={probeResult ? (probeResult.valid ? 'Valid' : 'Invalid') : '-'}
        label="Probe state"
        note="Last CardEffect.validate result"
      />
    </div>
  </SectionFrame>

  <section class="combat-lab-page__tabs" aria-label="Combat Lab tabs">
    {#each tabs as tab}
      <button
        type="button"
        class:combat-lab-page__tab--active={activeTab === tab.key}
        aria-pressed={activeTab === tab.key}
        onclick={() => (activeTab = tab.key)}
      >
        {tab.label}
      </button>
    {/each}
  </section>

  {#if activeTab === 'dice'}
    <div class="combat-lab-page__grid">
      <SectionFrame title="Dice request" description="서버 Dice API로 표기와 roll 요청을 보냅니다.">
        <form class="combat-lab-page__form" onsubmit={(event) => {
          event.preventDefault()
          void submitDice()
        }}>
          <label>
            <span>Notation</span>
            <input bind:value={notation} autocomplete="off" placeholder="3d6+2" />
          </label>

          <label>
            <span>Roll count</span>
            <input bind:value={rollCount} type="number" min="0" max="1000" />
          </label>

          <label>
            <span>Seed</span>
            <input bind:value={seed} autocomplete="off" placeholder="optional" />
          </label>

          <button type="submit" disabled={diceLoading}>
            {diceLoading ? 'Running...' : 'Roll with API'}
          </button>
        </form>

        {#if diceErrorMessage}
          <ContentStatePanel title="Dice request failed" message={diceErrorMessage} tone="error" />
        {/if}
      </SectionFrame>

      <SectionFrame title="Dice response" description="백엔드 응답을 그대로 표시합니다.">
        <pre class="combat-lab-page__json">{diceResultText}</pre>
      </SectionFrame>
    </div>
  {:else}
    <div class="combat-lab-page__grid">
      <SectionFrame title="Effect Probe setup" description="Probe 가능한 카드 목록을 서버에서 불러옵니다.">
        <div class="combat-lab-page__form">
          <button type="button" onclick={() => void loadEffectCards()} disabled={cardsLoading}>
            {cardsLoading ? 'Loading cards...' : 'Load effect cards'}
          </button>

          <label>
            <span>Card</span>
            <select bind:value={selectedCardId} disabled={effectCards.length === 0}>
              {#if effectCards.length === 0}
                <option value="">No cards loaded</option>
              {:else}
                {#each effectCards as card}
                  <option value={card.cardId}>{card.cardId} · {card.name}</option>
                {/each}
              {/if}
            </select>
          </label>

          <button
            type="button"
            onclick={() => void submitProbe()}
            disabled={!selectedEffectCard || probeLoading}
          >
            {probeLoading ? 'Probing...' : 'Run validate-only probe'}
          </button>
        </div>

        {#if cardsErrorMessage}
          <ContentStatePanel title="Card list failed" message={cardsErrorMessage} tone="error" />
        {/if}

        {#if probeErrorMessage}
          <ContentStatePanel title="Probe request failed" message={probeErrorMessage} tone="error" />
        {/if}
      </SectionFrame>

      <SectionFrame title="Effect Probe response" description="changes와 events는 서버 응답을 그대로 표시합니다.">
        <pre class="combat-lab-page__json">{probeResultText}</pre>
      </SectionFrame>
    </div>
  {/if}
</div>

<style>
  .combat-lab-page,
  .combat-lab-page__grid,
  .combat-lab-page__form {
    display: grid;
    gap: 1.5rem;
  }

  .combat-lab-page__hero {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .combat-lab-page__copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .combat-lab-page__copy p,
  .combat-lab-page__copy h3 {
    margin: 0;
  }

  .combat-lab-page__copy p,
  .combat-lab-page__form span {
    color: var(--color-text-muted);
    font-size: 0.74rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .combat-lab-page__copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.65rem, 2.4vw, 2.25rem);
    line-height: 1.15;
  }

  .combat-lab-page__tags,
  .combat-lab-page__tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .combat-lab-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .combat-lab-page__tabs button,
  .combat-lab-page__form button {
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .combat-lab-page__tab--active,
  .combat-lab-page__form button:not(:disabled) {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .combat-lab-page__grid {
    grid-template-columns: minmax(18rem, 0.8fr) minmax(0, 1.2fr);
    align-items: start;
  }

  .combat-lab-page__form label {
    display: grid;
    gap: 0.4rem;
  }

  .combat-lab-page__form input,
  .combat-lab-page__form select {
    min-height: 2.65rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    padding: 0.55rem 0.7rem;
  }

  .combat-lab-page__json {
    min-height: 18rem;
    max-height: 32rem;
    overflow: auto;
    margin: 0;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(8, 7, 6, 0.38);
    color: var(--color-text-soft);
    white-space: pre-wrap;
    word-break: break-word;
  }

  @media (max-width: 960px) {
    .combat-lab-page__stats,
    .combat-lab-page__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
