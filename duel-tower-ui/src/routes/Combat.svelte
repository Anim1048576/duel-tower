<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { explainApiError } from '../lib/api'
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
  let pendingError = ''
  let discardSelection: string[] = []
  let tieOrderDraft: string[] = []

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

  $: myPendingDecision = me?.pendingDecision ?? null
  $: hasPendingDecision = Boolean(myPendingDecision)
  $: pendingGuide = hasPendingDecision ? '결정 처리를 완료해야 일반 행동을 사용할 수 있습니다.' : ''
  $: if (myPendingDecision?.type !== 'DISCARD_TO_HAND_LIMIT') discardSelection = []
  $: if (myPendingDecision?.type === 'INITIATIVE_TIE_ORDER') {
    tieOrderDraft = syncTieOrderDraft(tieOrderDraft, myPendingDecision.actorKeys ?? [])
  } else {
    tieOrderDraft = []
  }

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

  $: discardLimit = Math.max(0, Number(myPendingDecision?.limit ?? 0))
  $: canSubmitDiscard = myPendingDecision?.type === 'DISCARD_TO_HAND_LIMIT' && discardSelection.length === discardLimit
  $: canSubmitTieOrder =
    myPendingDecision?.type === 'INITIATIVE_TIE_ORDER' &&
    tieOrderDraft.length > 0 &&
    sameMembers(tieOrderDraft, myPendingDecision.actorKeys ?? [])

  function syncTieOrderDraft(current: string[], source: string[]) {
    if (!current.length) return [...source]
    const sourceSet = new Set(source)
    const kept = current.filter((actor) => sourceSet.has(actor))
    const missing = source.filter((actor) => !kept.includes(actor))
    return [...kept, ...missing]
  }

  function sameMembers(a: string[], b: string[]) {
    if (a.length !== b.length) return false
    const counts = new Map<string, number>()
    for (const item of a) counts.set(item, (counts.get(item) ?? 0) + 1)
    for (const item of b) {
      const next = (counts.get(item) ?? 0) - 1
      if (next < 0) return false
      counts.set(item, next)
    }
    return [...counts.values()].every((v) => v === 0)
  }

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
    if (hasPendingDecision || next.disabledReason) return
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

  function toggleDiscardSelection(cardId: string) {
    const selected = discardSelection.includes(cardId)
    if (selected) {
      discardSelection = discardSelection.filter((id) => id !== cardId)
      return
    }
    if (discardSelection.length >= discardLimit) return
    discardSelection = [...discardSelection, cardId]
  }

  function moveTieActor(index: number, offset: number) {
    const nextIndex = index + offset
    if (nextIndex < 0 || nextIndex >= tieOrderDraft.length) return
    const next = [...tieOrderDraft]
    const [item] = next.splice(index, 1)
    next.splice(nextIndex, 0, item)
    tieOrderDraft = next
  }

  async function sendPendingDecisionCommand(
    payload: {
      type: string
      discardIds?: string[]
      /** Required when type === 'RESOLVE_INITIATIVE_TIE'. */
      tieGroupIndex?: number
      /** Required when type === 'RESOLVE_INITIATIVE_TIE'. */
      orderedActorKeys?: string[]
    },
  ) {
    if (busy) return
    pendingError = ''
    busy = true
    try {
      const res = await command({ ...payload, expectedVersion: version })
      if (!res) {
        pendingError = explainApiError(new Error('커맨드 전송 실패'))
        return
      }
      if (!res.accepted) {
        pendingError = res.errors?.join('\n') || '커맨드가 거부되었습니다.'
        return
      }
      discardSelection = []
      await refreshState()
    } catch (e) {
      pendingError = explainApiError(e)
    } finally {
      busy = false
    }
  }

  async function submitDiscardToLimit() {
    if (!canSubmitDiscard) return
    await sendPendingDecisionCommand({
      type: 'DISCARD_TO_HAND_LIMIT',
      discardIds: discardSelection,
    })
  }

  async function submitTieOrder() {
    if (!canSubmitTieOrder) return
    await sendPendingDecisionCommand({
      type: 'RESOLVE_INITIATIVE_TIE',
      tieGroupIndex: myPendingDecision?.groupIndex,
      orderedActorKeys: tieOrderDraft,
    })
  }

  async function confirmAction() {
    if (!pendingAction || !me || busy || hasPendingDecision) return
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

  {#if hasPendingDecision}
    <section class="panel pendingPanel">
      <div class="panelTitle">결정 처리 필요</div>
      <div class="hint">{pendingGuide}</div>
      {#if myPendingDecision?.reason}
        <div class="hint">사유: {myPendingDecision.reason}</div>
      {/if}
      {#if pendingError}
        <div class="hint pendingError">{pendingError}</div>
      {/if}
      <div class="spacer"></div>

      {#if myPendingDecision?.type === 'DISCARD_TO_HAND_LIMIT'}
        <div class="hint">손패에서 {discardLimit}장을 선택해 버리세요. ({discardSelection.length}/{discardLimit})</div>
        <div class="row wrap" style="gap:8px; margin-top:8px">
          {#each handCards as card (card.instanceId)}
            {@const selected = discardSelection.includes(card.instanceId)}
            <button class={`btn ${selected ? 'primary' : ''}`} disabled={!selected && discardSelection.length >= discardLimit} on:click={() => toggleDiscardSelection(card.instanceId)}>
              {selected ? '선택됨' : '선택'} · {card.instanceId}
            </button>
          {/each}
        </div>
        <div class="spacer"></div>
        <div class="row" style="justify-content:flex-end">
          <button class="btn primary" disabled={!canSubmitDiscard || busy} on:click={submitDiscardToLimit}>DISCARD_TO_HAND_LIMIT 전송</button>
        </div>
      {:else if myPendingDecision?.type === 'INITIATIVE_TIE_ORDER'}
        <div class="hint">동률 그룹 #{myPendingDecision.groupIndex ?? 0} 순서를 조정하세요.</div>
        <div class="stack" style="margin-top:8px">
          {#each tieOrderDraft as actorKey, i (actorKey)}
            <div class="ti row" style="justify-content:space-between">
              <b>{i + 1}. {actorKey}</b>
              <div class="row" style="gap:8px">
                <button class="btn" disabled={i === 0} on:click={() => moveTieActor(i, -1)}>▲</button>
                <button class="btn" disabled={i === tieOrderDraft.length - 1} on:click={() => moveTieActor(i, 1)}>▼</button>
              </div>
            </div>
          {/each}
        </div>
        <div class="spacer"></div>
        <div class="row" style="justify-content:flex-end">
          <button class="btn primary" disabled={!canSubmitTieOrder || busy} on:click={submitTieOrder}>RESOLVE_INITIATIVE_TIE 전송</button>
        </div>
      {:else}
        <div class="hint">현재 결정 타입({myPendingDecision?.type}) UI는 준비 중입니다.</div>
      {/if}
    </section>

    <div class="spacer"></div>
  {/if}

  <div class="layoutTop">
    <TeamPanel side="enemy" title="TeamPanel enemy" players={enemyPlayers} summons={enemySummons} {validTargets} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />

    <ActionCenter {stage} action={pendingAction} {busy} on:cancel={cancelAction} on:confirm={confirmAction} />

    <TeamPanel side="ally" title="TeamPanel ally" players={allyPlayers} summons={allySummons} {validTargets} selectedTarget={selectedTargetKey} on:selectTarget={(e) => onSelectTarget(e.detail)} />
  </div>

  <div class="spacer"></div>

  <div class="layoutBottom">
    <HandZone cards={handCards} cardDefs={$content.cardsById} actionByCardId={playActionByCardId} actionLocked={hasPendingDecision} lockReason={pendingGuide} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:play={(e) => beginAction(e.detail.action)} />
    <ExZone card={exCard} cardDef={exCard ? $content.cardsById[exCard.defId] ?? null : null} action={exAction} actionLocked={hasPendingDecision} lockReason={pendingGuide} on:inspect={(e) => (selectedCardId = e.detail.cardId)} on:useEx={(e) => beginAction(e.detail.action)} />
    <FieldZone cards={fieldCards} cardDefs={$content.cardsById} on:inspect={(e) => (selectedCardId = e.detail.cardId)} />
    <SummonZone summons={allySummons} actionBySummonId={summonActionById} actionLocked={hasPendingDecision} lockReason={pendingGuide} on:summonAction={(e) => beginAction(e.detail.action)} />
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
  .pendingPanel{border-color:rgba(255, 93, 116, 0.45); background:rgba(255, 93, 116, 0.08)}
  .pendingError{color:var(--danger)}
  .wrap{flex-wrap:wrap}
  .stack{display:flex; flex-direction:column; gap:8px}
  @media (max-width: 1200px){
    .layoutTop,.layoutBottom{grid-template-columns:1fr}
  }
</style>
