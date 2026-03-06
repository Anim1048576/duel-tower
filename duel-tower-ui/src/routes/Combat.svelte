<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { combat, command, refreshState } from '../stores/combat'
  import { session } from '../stores/session'
  import { content, ensureCards } from '../stores/content'
  import { logs } from '../stores/log'
  import type { CardInstance, PlayerState } from '../lib/model'
  import CombatHeader from '../lib/combat/CombatHeader.svelte'
  import TeamPanel from '../lib/combat/TeamPanel.svelte'
  import ActionCenter from '../lib/combat/ActionCenter.svelte'
  import HandZone from '../lib/combat/HandZone.svelte'
  import ExZone from '../lib/combat/ExZone.svelte'
  import FieldZone from '../lib/combat/FieldZone.svelte'
  import SummonZone from '../lib/combat/SummonZone.svelte'
  import CardDetailDrawer from '../lib/components/CardDetailDrawer.svelte'
  import LogLineItem from '../lib/components/LogLineItem.svelte'
  import type { ActionStage, CombatTarget, PendingAction } from '../lib/combat/types'

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
  $: me = state?.players?.[meId] ?? (players[0] as PlayerState | undefined)
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

  function mapCardInstances(ids: string[]): CardInstance[] {
    return ids.map((id) => cardById[id]).filter(Boolean)
  }

  function beginAction(next: PendingAction) {
    pendingAction = { ...next }
    stage = next.requiresTarget ? 'targeting' : 'confirming'
  }

  function onSelectTarget(target: CombatTarget) {
    if (stage !== 'targeting' || !pendingAction) return
    pendingAction = { ...pendingAction, target }
    stage = 'confirming'
  }

  function cancelAction() {
    stage = 'idle'
    pendingAction = null
  }

  function targetKey(target: CombatTarget) {
    return target.type === 'player' ? `player:${target.playerId}` : `summon:${target.playerId}:${target.summonId}`
  }

  async function confirmAction() {
    if (!pendingAction || !me || busy) return
    busy = true
    try {
      if (pendingAction.kind === 'play' && pendingAction.cardId) {
        await command({
          type: 'PLAY_CARD',
          cardId: pendingAction.cardId,
          expectedVersion: version,
          targets: pendingAction.target ? [toTargetRef(pendingAction.target)] : undefined,
        })
      }
      if (pendingAction.kind === 'useEx' && pendingAction.cardId) {
        await command({
          type: 'USE_EX',
          cardId: pendingAction.cardId,
          expectedVersion: version,
          targets: pendingAction.target ? [toTargetRef(pendingAction.target)] : undefined,
        })
      }
      if (pendingAction.kind === 'summon' && pendingAction.summonId) {
        await command({
          type: 'USE_SUMMON_ACTION',
          summonId: pendingAction.summonId,
          expectedVersion: version,
          targets: pendingAction.target ? [toTargetRef(pendingAction.target)] : undefined,
        })
      }
      cancelAction()
    } finally {
      busy = false
    }
  }

  function toTargetRef(target: CombatTarget) {
    if (target.type === 'player') return { playerId: target.playerId }
    return { summonOwnerPlayerId: target.playerId, summonInstanceId: target.summonId }
  }

  function onPlay(cardId: string) {
    beginAction({
      kind: 'play',
      cardId,
      sourcePlayerId: me?.playerId ?? '',
      requiresTarget: true,
      label: `카드 사용 · ${$content.cardsById[cardById[cardId]?.defId ?? '']?.name ?? cardId}`,
    })
  }

  function onUseEx(cardId: string) {
    beginAction({
      kind: 'useEx',
      cardId,
      sourcePlayerId: me?.playerId ?? '',
      requiresTarget: true,
      label: `EX 사용 · ${$content.cardsById[cardById[cardId]?.defId ?? '']?.name ?? cardId}`,
    })
  }

  function onSummonAction(summonId: string, ownerId: string) {
    beginAction({
      kind: 'summon',
      summonId,
      sourcePlayerId: ownerId,
      requiresTarget: true,
      label: `소환 행동 · ${summonId}`,
    })
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
    <TeamPanel side="enemy" title="TeamPanel enemy" players={enemyPlayers} summons={enemySummons} targetable={stage === 'targeting'} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />

    <ActionCenter {stage} action={pendingAction} {busy} on:cancel={cancelAction} on:confirm={confirmAction} />

    <TeamPanel side="ally" title="TeamPanel ally" players={allyPlayers} summons={allySummons} targetable={stage === 'targeting'} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />
  </div>

  <div class="spacer"></div>

  <div class="layoutBottom">
    <HandZone cards={handCards} cardDefs={$content.cardsById} disabled={!inCombat || stage !== 'idle'} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:play={(e) => onPlay(e.detail.cardId)} />
    <ExZone card={exCard} cardDef={exCard ? $content.cardsById[exCard.defId] ?? null : null} disabled={!inCombat || stage !== 'idle'} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:useEx={(e) => onUseEx(e.detail.cardId)} />
    <FieldZone cards={fieldCards} cardDefs={$content.cardsById} on:inspect={(e) => (selectedCardId = e.detail.cardId)} />
    <SummonZone summons={allySummons} ownerId={me?.playerId ?? ''} disabled={!inCombat || stage !== 'idle'} on:summonAction={(e) => onSummonAction(e.detail.summonId, e.detail.ownerId)} />
  </div>

  {#if showLogs}
    <div class="spacer"></div>
    <section class="panel">
      <div class="panelTitle">최근 로그</div>
      <div class="spacer"></div>
      <div class="log">
        {#if !$logs.length}
          <div class="hint">로그 없음</div>
        {:else}
          {#each $logs.slice(0, 20) as item (item.id)}
            <LogLineItem at={item.at} level={item.level} title={item.title} message={item.message ?? ''} />
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
  @media (max-width: 1200px){
    .layoutTop,.layoutBottom{grid-template-columns:1fr}
  }
</style>
