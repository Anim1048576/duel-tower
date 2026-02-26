<script lang="ts">
  import { navigate } from '../lib/router'
  import { createSession, explainApiError, joinSession } from '../lib/api'
  import { session, setGmId, setLastError, setMeId, setSessionCode } from '../stores/session'
  import { ensureCards } from '../stores/content'
  import { refreshState } from '../stores/combat'
  import { pushToast } from '../stores/log'

  let meId = ''
  let joinCode = ''
  let busy = false

  $: if ($session.meId && meId === '') meId = $session.meId
  $: if ($session.code && joinCode === '') joinCode = $session.code

  async function doCreate() {
    busy = true
    setLastError(undefined)
    try {
      await ensureCards()
      setMeId(meId)
      const res = await createSession(meId.trim())
      setSessionCode(res.code)
      setGmId(res.gmId)

      // creator should also join as a player
      await joinSession(res.code, meId.trim())
      await refreshState()
      pushToast('세션 생성', res.code)
      navigate('/lobby')
    } catch (e) {
      setLastError(explainApiError(e))
      pushToast('생성 실패', explainApiError(e))
    } finally {
      busy = false
    }
  }

  async function doJoin() {
    busy = true
    setLastError(undefined)
    try {
      await ensureCards()
      setMeId(meId)
      const code = joinCode.trim().toUpperCase()
      setSessionCode(code)
      await joinSession(code, meId.trim())
      await refreshState()
      pushToast('세션 참가', code)
      navigate('/lobby')
    } catch (e) {
      setLastError(explainApiError(e))
      pushToast('참가 실패', explainApiError(e))
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

<section class="hero">
  <div class="row wrap" style="justify-content:space-between; gap:12px">
    <div>
      <div class="h1">Duel Tower</div>
      <p class="lead">세션 만들기 → 로비 → 전투. API는 <span class="mono">/api</span>로 프록시된다.</p>
    </div>
    <div class="row wrap" style="justify-content:flex-end">
      <span class="pill">내 ID</span>
      <input class="input mono" style="width:220px" bind:value={meId} placeholder="playerId (예: gm / p-1234)" />
      <button class="btn" on:click={() => setMeId(meId)}>저장</button>
    </div>
  </div>

  {#if $session.lastError}
    <div class="spacer"></div>
    <div class="card" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
      <div class="cardTitle">에러</div>
      <div class="muted" style="margin-top:8px">{$session.lastError}</div>
    </div>
  {/if}

  <div class="grid2">
    <div class="card">
      <div class="cardTitle">세션 만들기</div>
      <div class="hint">GM ID는 <span class="mono">{meId || 'me'}</span>로 사용된다.</div>
      <div class="spacer"></div>
      <button class="btn primary" disabled={busy || !meId.trim()} on:click={doCreate}>
        {busy ? '처리 중…' : '세션 만들기'}
      </button>
    </div>

    <div class="card">
      <div class="cardTitle">세션 참가</div>
      <div class="hint">세션 코드(8자리)를 입력하고 참가.</div>
      <div class="spacer"></div>
      <form class="row wrap" on:submit|preventDefault={doJoin}>
        <input class="input mono" style="width:220px" bind:value={joinCode} placeholder="세션 코드" />
        <button class="btn" type="submit" disabled={busy || !meId.trim() || !joinCode.trim()}>
          {busy ? '처리 중…' : '참가'}
        </button>
        {#if joinCode.trim()}
          <button class="btn" type="button" on:click={() => copy(joinCode.trim().toUpperCase())}>코드 복사</button>
        {/if}
      </form>
    </div>
  </div>

  <div class="spacer"></div>
  <ul class="list">
    <li>개발 모드: Vite가 <span class="mono">/api</span>를 <span class="mono">http://localhost:9009</span>로 프록시한다.</li>
    <li>빌드: <span class="mono">duel-tower-ui</span>에서 <span class="mono">npm run build</span> 하면 <span class="mono">src/main/resources/static/ui</span>로 출력된다.</li>
  </ul>
</section>
