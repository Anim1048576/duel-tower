<script lang="ts">
  import { onDestroy, onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { joinSession, explainApiError } from '../lib/api'
  import { combat, ensureJoined, refreshState, startPolling, stopPolling, command } from '../stores/combat'
  import { session } from '../stores/session'
  import { ensureCards } from '../stores/content'
  import { pushToast } from '../stores/log'

  let addPlayerId = ''
  let busy = false

  onMount(async () => {
    await ensureCards()
    await ensureJoined()
    await refreshState()
    startPolling(1200)
  })

  onDestroy(() => stopPolling())

  $: state = $combat.state
  $: players = state?.players ? Object.values(state.players) : []
  $: inCombat = Boolean(state?.combat)
  $: canStart = Boolean($session.code && !inCombat)
  $: gmHint = $session.gmId ? `GM: ${$session.gmId}` : 'GM 정보: (이 브라우저에서 만든 세션이 아니면 알 수 없음)'

  async function doStartCombat() {
    busy = true
    try {
      const res = await command({ type: 'START_COMBAT' })
      if (res?.accepted) {
        pushToast('전투 시작', '전투 화면으로 이동')
        navigate('/combat')
      }
    } finally {
      busy = false
    }
  }

  async function doAddPlayer() {
    if (!addPlayerId.trim()) return
    busy = true
    try {
      await joinSession($session.code, addPlayerId.trim())
      addPlayerId = ''
      await refreshState()
      pushToast('참가자 추가', 'OK')
    } catch (e) {
      pushToast('추가 실패', explainApiError(e))
    } finally {
      busy = false
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      pushToast('복사됨', text)
    } catch {
      pushToast('복사 실패')
    }
  }
</script>

<section class="split">
  <div>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div>
          <div class="h2">로비</div>
          <div class="hint">{$session.code ? `세션 ${$session.code}` : '세션이 없음 · 홈에서 생성/참가'}</div>
        </div>
        <div class="row wrap" style="justify-content:flex-end">
          {#if $session.code}
            <button class="btn" on:click={() => copy($session.code)}>코드 복사</button>
          {/if}
          <button class="btn" on:click={() => refreshState()}>새로고침</button>
          <button class="btn" on:click={() => navigate('/')}>홈</button>
        </div>
      </div>

      {#if $combat.lastError}
        <div class="spacer"></div>
        <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
          <div class="logHead">API</div>
          <div class="logBody">{$combat.lastError}</div>
        </div>
      {/if}
    </div>

    <div class="spacer"></div>

    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">참가자 ({players.length})</div>
        <div class="row wrap" style="justify-content:flex-end">
          <span class="badge">{gmHint}</span>
        </div>
      </div>
      <div class="spacer"></div>
      {#if !players.length}
        <div class="muted">아직 참가자가 없음. (join API를 못 탔거나, 세션이 비어있음)</div>
      {:else}
        <div class="table">
          {#each players as p (p.playerId)}
            <div class="tr">
              <div class="grow">
                <div class="row wrap" style="gap:8px">
                  <b class="mono">{p.playerId}</b>
                  {#if p.playerId === $session.meId}
                    <span class="badge ok">ME</span>
                  {/if}
                  {#if $session.gmId && p.playerId === $session.gmId}
                    <span class="badge ok">GM</span>
                  {/if}
                </div>
                <div class="hint">덱 {p.deck.length} · 손 {p.hand.length} · 묘지 {p.grave.length} · EX {p.exCard ? '1' : '0'}</div>
              </div>
              {#if p.pendingDecision}
                <span class="badge no">결정 필요</span>
              {/if}
            </div>
          {/each}
        </div>
      {/if}
      <div class="spacer"></div>
      <form class="row wrap" on:submit|preventDefault={doAddPlayer}>
        <input class="input mono" style="width:220px" bind:value={addPlayerId} placeholder="테스트용: 플레이어 추가" />
        <button class="btn" type="submit" disabled={busy || !$session.code || !addPlayerId.trim()}>추가</button>
      </form>
    </div>
  </div>

  <aside>
    <div class="card">
      <div class="cardTitle">다음 단계</div>
      <div class="spacer"></div>
      <div class="choices">
        <div class="choice" on:click={() => navigate('/presets')}>
          <div class="choiceTitle">프리셋</div>
          <div class="choiceDesc">카드 풀 확인 + 덱 구성(프론트 저장)</div>
        </div>
        <div class="choice" on:click={() => navigate('/node')}>
          <div class="choiceTitle">노드</div>
          <div class="choiceDesc">런 진행 목업(경로 선택 UX 테스트)</div>
        </div>
        <div class="choice" on:click={() => navigate('/combat')}>
          <div class="choiceTitle">전투 화면</div>
          <div class="choiceDesc">현재 세션 상태를 렌더링</div>
        </div>
      </div>
      <div class="spacer"></div>

      <button class="btn primary" disabled={busy || !canStart} on:click={doStartCombat}>
        전투 시작
      </button>
      {#if inCombat}
        <div class="hint">이미 전투가 시작됨 · <button class="btn" style="margin-top:10px" on:click={() => navigate('/combat')}>전투로</button></div>
      {:else}
        <div class="hint">GM 권한이 아니면 서버가 403을 반환한다.</div>
      {/if}
    </div>
  </aside>
</section>
