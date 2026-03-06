<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { combat, command, refreshState } from '../stores/combat'
  import { session } from '../stores/session'
  import { content, ensureCards } from '../stores/content'
  import { logs } from '../stores/log'
  import { toTargetRef } from '../lib/adapters/combatAdapter'
  import type { ActionDescriptor, CardInstance } from '../lib/model'
  import CombatHeader from '../lib/combat/CombatHeader.svelte'
  import TeamPanel from '../lib/combat/TeamPanel.svelte'
  import ActionCenter from '../lib/combat/ActionCenter.svelte'
  import HandZone from '../lib/combat/HandZone.svelte'
  import ExZone from '../lib/combat/ExZone.svelte'
  import FieldZone from '../lib/combat/FieldZone.svelte'
  import SummonZone from '../lib/combat/SummonZone.svelte'
  import CardDetailDrawer from '../lib/components/CardDetailDrawer.svelte'
  import type { ActionStage, PendingAction } from '../lib/combat/types'

  let busy = false
  let showLogs = false
  let stage: ActionStage = 'idle'
  let pendingAction: PendingAction | null = null
  let selectedCardId: string | null = null

  const DEV = Boolean(import.meta.env.DEV)

  onMount(async () => {
    await ensureCards()
    await refreshState()
  })

  $: state = $combat.state
  $: players = state?.players ? Object.values(state.players) : []
  $: meId = $session.meId
  $: me = state?.players?.[meId] ?? players[0]
  $: combatState = state?.combat
  $: inCombat = Boolean(combatState)
  $: version = state?.version

  $: allyPlayers = players.filter((p) => p.playerId === (me?.playerId ?? '')).map((p) => ({ ...p, side: 'ally' as const }))
  $: enemyPlayers = players.filter((p) => p.playerId !== (me?.playerId ?? '')).map((p) => ({ ...p, side: 'enemy' as const }))

  $: summons = combatState?.summons ?? []
  $: allySummons = summons.filter((s) => s.owner === me?.playerId)
  $: enemySummons = summons.filter((s) => s.owner !== me?.playerId)

  $: cardById = state?.cards ?? {}
  $: handCards = mapCardInstances(me?.hand ?? [])
  $: fieldCards = mapCardInstances(me?.field ?? [])
  $: exCard = me?.exCard ? cardById[me.exCard] ?? null : null

  $: selectedCard = selectedCardId ? cardById[selectedCardId] ?? null : null
  $: selectedCardDef = selectedCard ? $content.cardsById[selectedCard.defId] ?? null : null

  $: initiative = combatState?.turnOrder?.length ? combatState.turnOrder.map((p, i) => `${i === combatState.currentTurnIndex ? '▶ ' : ''}${p}`).join(' → ') : '—'
  $: selectedTargetKey = pendingAction?.target ? targetKey(pendingAction.target) : null
  $: validTargets = pendingAction?.validTargets ?? []

  $: myActions = me?.availableActions ?? []
  $: playActionByCardId = indexActionByCardId(myActions.filter((a) => a.kind === 'play'))
  $: exAction = myActions.find((a) => a.kind === 'useEx') ?? null
  $: summonActionById = indexActionBySummonId(myActions.filter((a) => a.kind === 'summon'))

  function mapCardInstances(ids: string[]): CardInstance[] {
    return ids.map((id) => cardById[id]).filter(Boolean)
  }

  function indexActionByCardId(actions: ActionDescriptor[]) {
    const map: Record<string, ActionDescriptor | undefined> = {}
    for (const action of actions) if (action.cardId) map[action.cardId] = action
    return map
  }

  function indexActionBySummonId(actions: ActionDescriptor[]) {
    const map: Record<string, ActionDescriptor | undefined> = {}
    for (const action of actions) if (action.summonId) map[action.summonId] = action
    return map
  }

  function beginAction(next: ActionDescriptor) {
    if (next.disabledReason) return
    pendingAction = { ...next }
    stage = next.requiresTarget ? 'targeting' : 'confirming'
  }

  function onSelectTarget(target: NonNullable<PendingAction['target']>) {
    if (stage !== 'targeting' || !pendingAction) return
    if (!pendingAction.validTargets.some((t) => targetKey(t) === targetKey(target))) return
    pendingAction = { ...pendingAction, target }
    stage = 'confirming'
  }

  function cancelAction() {
    stage = 'idle'
    pendingAction = null
  }

  function targetKey(target: NonNullable<PendingAction['target']>) {
    return target.type === 'player' ? `player:${target.playerId}` : `summon:${target.playerId}:${target.summonId}`
  }

  async function confirmAction() {
    if (!pendingAction || !me || busy) return
    busy = true
    try {
      await command({
        type: pendingAction.commandType,
        cardId: pendingAction.cardId,
        summonId: pendingAction.summonId,
        expectedVersion: version,
        targets: pendingAction.target ? [toTargetRef(pendingAction.target)] : undefined,
      })
      cancelAction()
    } finally {
      busy = false
    }
  }
</script>

<div class="combatPage">
  <CombatHeader
    round={combatState?.round ?? 0}
    turnPlayer={combatState?.currentTurnPlayer ?? '—'}
    {initiative}
    {showLogs}
    on:toggleLogs={() => (showLogs = !showLogs)}
  />

  <div class="spacer"></div>

  <div class="layoutTop">
    <TeamPanel side="enemy" title="TeamPanel enemy" players={enemyPlayers} summons={enemySummons} {validTargets} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />

    <ActionCenter {stage} action={pendingAction} {busy} on:cancel={cancelAction} on:confirm={confirmAction} />

    <TeamPanel side="ally" title="TeamPanel ally" players={allyPlayers} summons={allySummons} {validTargets} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />
  </div>

  <div class="spacer"></div>

  <div class="layoutBottom">
    <HandZone cards={handCards} cardDefs={$content.cardsById} actionByCardId={playActionByCardId} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:play={(e) => beginAction(e.detail.action)} />
    <ExZone card={exCard} cardDef={exCard ? $content.cardsById[exCard.defId] ?? null : null} action={exAction} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:useEx={(e) => beginAction(e.detail.action)} />
    <FieldZone cards={fieldCards} cardDefs={$content.cardsById} on:inspect={(e) => (selectedCardId = e.detail.cardId)} />
    <SummonZone summons={allySummons} actionBySummonId={summonActionById} on:summonAction={(e) => beginAction(e.detail.action)} />
  </div>

  {#if showLogs}
    <div class="spacer"></div>
    <section class="panel">
      <div class="panelTitle">최근 로그</div>
      <div class="spacer"></div>
      <div class="log">
        {#if !$combat.lastResolutionLogs.length && !$logs.length}
          <div class="hint">로그 없음</div>
        {:else}
          {#each $combat.lastResolutionLogs as item (item.id)}
            <details class="logItem" open>
              <summary>result summary · {item.summary}</summary>
              <details>
                <summary>detailed breakdown</summary>
                <pre>{item.breakdown}</pre>
              </details>
            </details>
          {/each}
          {#each $logs.slice(0, 20) as item (item.id)}
            <details class="logItem">
              <summary>result summary · {item.title}</summary>
              <details>
                <summary>detailed breakdown</summary>
                <pre>{item.message ?? ''}</pre>
              </details>
            </details>
          {/each}
        {/if}
      </div>
    </section>
  {/if}

  {#if DEV}
    <!-- temporary debug controls intentionally hidden behind dev flag -->
  {/if}

  <CardDetailDrawer open={Boolean(selectedCardId)} card={selectedCard} def={selectedCardDef} on:close={() => (selectedCardId = null)} />

  <div class="spacer"></div>
  <div class="row" style="justify-content:flex-end">
    <button class="btn" on:click={() => refreshState()}>새로고침</button>
    <button class="btn" on:click={() => navigate('/logs')}>로그 페이지</button>
  </div>
</div>

<style>
  .combatPage{display:flex; flex-direction:column}
  .layoutTop{display:grid; grid-template-columns: 320px 1fr 320px; gap:12px}
  .layoutBottom{display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap:12px}
  .logItem{padding:8px; border:1px solid var(--line-default); border-radius:12px; background:var(--surface-2); margin-bottom:8px}
  .logItem pre{white-space:pre-wrap}
  @media (max-width: 1200px){
    .layoutTop,.layoutBottom{grid-template-columns:1fr}
  }
</style>
