<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { content, ensureCards } from '../stores/content'
  import { session } from '../stores/session'
  import { combat, ensureJoined, refreshState, startPolling, stopPolling, command } from '../stores/combat'
  import { logs, clearLogs, pushToast } from '../stores/log'

  let selectedCard: string | null = null
  let discardSet = new Set<string>()
  let showDebug = false

  onMount(async () => {
    await ensureCards()
    await ensureJoined()
    await refreshState()
    startPolling(900)
  })
  onDestroy(() => stopPolling())

  $: state = $combat.state
  $: cs = state?.combat
  $: me = state?.players?.[$session.meId]
  $: isMyTurn = Boolean(cs && me && cs.currentTurnPlayer === me.playerId)

  function instDef(instId: string) {
    const ci = state?.cards?.[instId]
    if (!ci) return null
    return $content.cardsById[ci.defId] ?? null
  }

  function label(instId: string) {
    const ci = state?.cards?.[instId]
    if (!ci) return instId.slice(0, 8)
    const def = $content.cardsById[ci.defId]
    return def ? `${def.name}` : ci.defId
  }

  function cost(instId: string) {
    const ci = state?.cards?.[instId]
    if (!ci) return 0
    return $content.cardsById[ci.defId]?.cost ?? 0
  }

  function isImmovable(instId: string) {
    return instDef(instId)?.keywords?.includes('부동') ?? false
  }

  function toggleDiscard(id: string) {
    if (isImmovable(id)) {
      pushToast('부동', '부동 카드는 버릴 수 없음')
      return
    }
    const next = new Set(discardSet)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    discardSet = next
  }

  $: pending = me?.pendingDecision
  $: needDiscard = pending?.type === 'DISCARD_TO_HAND_LIMIT' && pending.limit != null && me
    ? Math.max(0, me.hand.length - pending.limit)
    : 0
  $: discardOk = needDiscard > 0 && discardSet.size === needDiscard

  async function doDraw(n: number) {
    await command({ type: 'DRAW', count: n })
  }

  async function doEndTurn() {
    await command({ type: 'END_TURN' })
  }

  async function doUseEx() {
    await command({ type: 'USE_EX' })
  }

  async function doPlay() {
    if (!selectedCard) {
      pushToast('카드 선택', '손패에서 카드 선택')
      return
    }
    await command({ type: 'PLAY_CARD', cardId: selectedCard })
    selectedCard = null
  }

  async function doHandSwap() {
    if (!selectedCard) {
      pushToast('카드 선택', '버릴 카드 1장을 선택')
      return
    }
    await command({ type: 'HAND_SWAP', discardIds: [selectedCard] })
    selectedCard = null
  }

  async function doDiscardToLimit() {
    if (!discardOk) return
    await command({ type: 'DISCARD_TO_HAND_LIMIT', discardIds: Array.from(discardSet) })
    discardSet = new Set()
  }

  async function doStartCombat() {
    await command({ type: 'START_COMBAT' })
  }
</script>

<section class="combat">
  <aside class="panel">
    <div class="panelTitle">턴 오더</div>
    <div class="hint">{cs ? `Round ${cs.round}` : '전투 미시작'}</div>
    <div class="spacer"></div>

    {#if cs}
      <div class="turnList">
        {#each cs.turnOrder as pid (pid)}
          <div class="turnItem" class:isActive={pid === cs.currentTurnPlayer}>
            <div class="mono">{pid}</div>
            {#if pid === $session.meId}<span class="badge ok">ME</span>{/if}
          </div>
        {/each}
      </div>
    {:else}
      <div class="muted">로비에서 <b>전투 시작</b>을 누르거나 아래 버튼으로 시작해봐.</div>
      <div class="spacer"></div>
      <button class="btn primary" disabled={!$session.code} on:click={doStartCombat}>전투 시작</button>
    {/if}

    <div class="spacer"></div>
    <div class="kvs">
      <div class="kv">
        <div class="k">My Turn</div>
        <div class="v">{isMyTurn ? 'YES' : 'NO'}</div>
      </div>
      <div class="kv">
        <div class="k">Sync</div>
        <div class="v mono">{$combat.lastSyncAt ? new Date($combat.lastSyncAt).toLocaleTimeString() : '—'}</div>
      </div>
    </div>
    {#if $combat.lastError}
      <div class="spacer"></div>
      <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
        <div class="logHead">API</div>
        <div class="logBody">{$combat.lastError}</div>
      </div>
    {/if}
    <div class="spacer"></div>
    <button class="btn" on:click={() => refreshState()}>새로고침</button>
  </aside>

  <main class="board">
    <div class="boardTop">
      <div>
        <div class="big">전투</div>
        <div class="hint">
          {#if state}
            ver <span class="mono">{state.version}</span> · seed <span class="mono">{state.seed}</span>
          {:else}
            세션 상태 없음
          {/if}
        </div>
      </div>
      <div class="row wrap" style="justify-content:flex-end">
        <button class="btn" on:click={() => (showDebug = !showDebug)}>{showDebug ? '디버그 닫기' : '디버그'}</button>
      </div>
    </div>

    <div class="spacer"></div>

    <div class="panel">
      <div class="panelTitle">내 상태</div>
      {#if !me}
        <div class="hint">내 ID(<span class="mono">{$session.meId}</span>)로 join이 안 되어 있을 수 있음.</div>
      {:else}
        <div class="spacer"></div>
        <div class="kvs">
          <div class="kv"><div class="k">Deck</div><div class="v">{me.deck.length}</div></div>
          <div class="kv"><div class="k">Hand</div><div class="v">{me.hand.length}/{me.handLimit}</div></div>
          <div class="kv"><div class="k">Grave</div><div class="v">{me.grave.length}</div></div>
          <div class="kv"><div class="k">Field</div><div class="v">{me.field.length}/{me.fieldLimit}</div></div>
        </div>

        <div class="spacer"></div>
        <div class="row wrap">
          <button class="btn" disabled={!isMyTurn || Boolean(pending)} on:click={() => doDraw(1)}>드로우 +1</button>
          <button class="btn" disabled={!isMyTurn || Boolean(pending)} on:click={() => doDraw(2)}>드로우 +2</button>
          <button class="btn" disabled={!isMyTurn || Boolean(pending)} on:click={doHandSwap}>패 교환</button>
          <button class="btn" disabled={!isMyTurn || Boolean(pending)} on:click={doPlay}>카드 사용</button>
          <button class="btn" disabled={!isMyTurn || Boolean(pending) || me.exOnCooldown} on:click={doUseEx}>EX 사용</button>
          <button class="btn danger" disabled={!isMyTurn || Boolean(pending)} on:click={doEndTurn}>턴 종료</button>
        </div>

        {#if pending}
          <div class="spacer"></div>
          <div class="ti" style="border-color: rgba(93,214,255,.35); background: rgba(93,214,255,.06)">
            <div class="logHead">Pending Decision</div>
            <div class="logBody">
              <b class="mono">{pending.type}</b>
              {#if pending.reason}<div class="muted" style="margin-top:6px">{pending.reason}</div>{/if}
              {#if pending.type === 'DISCARD_TO_HAND_LIMIT'}
                <div class="hint">손패가 제한을 초과했다. <b>{needDiscard}</b>장 버리기</div>
              {/if}
            </div>
          </div>
        {/if}
      {/if}
    </div>

    {#if me}
      <div class="spacer"></div>

      <div class="handArea">
        <div class="row wrap" style="justify-content:space-between">
          <div class="cardTitle">손패</div>
          <div class="hint">카드를 클릭해서 선택 → 패교환/사용</div>
        </div>
        <div class="spacer"></div>
        <div class="cardRow">
          {#each me.hand as id (id)}
            <div
              class="gcard"
              class:isSelected={id === selectedCard}
              on:click={() => (selectedCard = id)}
            >
              <div class="row" style="justify-content:space-between; align-items:flex-start">
                <div class="gcardTitle">{label(id)}</div>
                <span class="badge">{cost(id)}</span>
              </div>
              <div class="gcardSub mono">{state?.cards?.[id]?.defId ?? '—'}</div>
              {#if instDef(id)?.text}
                <div class="choiceDesc" style="margin-top:10px">{instDef(id)?.text}</div>
              {/if}
            </div>
          {/each}
        </div>
      </div>

      {#if pending?.type === 'DISCARD_TO_HAND_LIMIT' && needDiscard > 0}
        <div class="spacer"></div>
        <div class="fieldArea">
          <div class="row wrap" style="justify-content:space-between">
            <div class="cardTitle">버릴 카드 선택</div>
            <span class="badge">{discardSet.size}/{needDiscard}</span>
          </div>
          <div class="spacer"></div>
          <div class="cardRow">
            {#each me.hand as id (id)}
              <button
                class="btn"
                class:primary={discardSet.has(id)}
                disabled={isImmovable(id)}
                on:click={() => toggleDiscard(id)}
              >
                {label(id)}{#if isImmovable(id)} <span class="badge">부동</span>{/if}
              </button>
            {/each}
          </div>
          <div class="spacer"></div>
          <button class="btn primary" disabled={!discardOk} on:click={doDiscardToLimit}>확정</button>
        </div>
      {/if}
    {/if}

    {#if showDebug && state}
      <div class="spacer"></div>
      <div class="card">
        <div class="cardTitle">SessionState (debug)</div>
        <pre class="mono" style="white-space: pre-wrap; margin:10px 0 0 0; color: var(--muted)">{JSON.stringify(state, null, 2)}</pre>
      </div>
    {/if}
  </main>

  <aside class="panel">
    <div class="panelTitle">로그</div>
    <div class="row wrap" style="justify-content:flex-end; margin-top:10px">
      <button class="btn" on:click={() => clearLogs()}>비우기</button>
    </div>
    <div class="spacer"></div>
    <div class="log">
      {#each $logs as it (it.id)}
        <div class="logItem">
          <div class="logHead">
            {new Date(it.at).toLocaleTimeString()} · {it.level.toUpperCase()} · {it.title}
          </div>
          {#if it.message}
            <div class="logBody">{it.message}</div>
          {/if}
        </div>
      {/each}
    </div>
  </aside>
</section>
