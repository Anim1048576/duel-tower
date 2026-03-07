<script lang="ts">
  import { navigate } from '../lib/router'
  import { createSession, explainApiError, joinSession } from '../lib/api'
  import { copyToClipboard } from '../lib/clipboard'
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { session, setGmId, setGmToken, setLastError, setMeId, setPlayerToken, setSessionCode } from '../stores/session'
  import { ensureCards } from '../stores/content'
  import { refreshState } from '../stores/combat'
  import { pushToast } from '../stores/log'
  import { presets } from '../stores/presets'
  import { auth, doLogin, doSignup } from '../stores/auth'

  let meId = ''
  let joinCode = ''
  let busy = false
  let authBusy = false
  let authMode: 'login' | 'signup' = 'login'
  let username = ''
  let email = ''
  let password = ''

  $: if ($auth.status === 'authenticated') meId = $auth.username
  $: if ($auth.status !== 'authenticated' && $session.meId && meId === '') meId = $session.meId
  $: if ($session.code && joinCode === '') joinCode = $session.code

  $: selectedPreset = $presets.presets.find((p) => p.id === $presets.selectedId) ?? $presets.presets[0]
  $: if ($auth.status === 'authenticated') setMeId($auth.username)

  async function submitAuth() {
    authBusy = true
    try {
      const result = authMode === 'signup'
        ? await doSignup(username, email, password)
        : await doLogin(username, password)

      if (!result.ok) {
        pushToast(authMode === 'signup' ? '회원가입 실패' : '로그인 실패', result.message)
        return
      }

      setMeId(username.trim())
      pushToast(authMode === 'signup' ? '회원가입 완료' : '로그인 완료', username.trim())
    } finally {
      authBusy = false
    }
  }

  async function doCreate() {
    busy = true
    setLastError(undefined)
    try {
      await ensureCards()
      setMeId($auth.username)
      const res = await createSession($auth.username.trim())
      setSessionCode(res.code)
      setGmId(res.gmId)
      setGmToken(res.gmToken)
      const joinRes = await joinSession(
        res.code,
        $auth.username.trim(),
        selectedPreset?.passiveIds ?? [],
        selectedPreset?.deck?.length ? selectedPreset.deck : undefined,
        selectedPreset?.ex ?? undefined,
      )
      setPlayerToken(joinRes.playerToken)
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
      setMeId($auth.username)
      const code = joinCode.trim().toUpperCase()
      setSessionCode(code)
      setGmToken('')
      const joinRes = await joinSession(
        code,
        $auth.username.trim(),
        selectedPreset?.passiveIds ?? [],
        selectedPreset?.deck?.length ? selectedPreset.deck : undefined,
        selectedPreset?.ex ?? undefined,
      )
      setPlayerToken(joinRes.playerToken)
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
    const ok = await copyToClipboard(text)
    if (ok) pushToast('복사됨', text)
    else pushToast('복사 실패')
  }
</script>

<PageSkeleton title="Session" summary="세션 생성/참가 전용 페이지">
  <button slot="actions" class="btn" on:click={() => navigate('/lobby')} disabled={!$session.code}>허브 이동</button>

  <div class="card" style="margin-bottom:12px;">
    <div class="cardTitle">로그인</div>
    {#if $auth.status === 'authenticated'}
      <div class="hint">현재 로그인: <span class="mono">{$auth.username}</span></div>
    {:else}
      <div class="row wrap" style="gap:8px; align-items:center;">
        <button class="btn" class:primary={authMode === 'login'} on:click={() => (authMode = 'login')} type="button">로그인</button>
        <button class="btn" class:primary={authMode === 'signup'} on:click={() => (authMode = 'signup')} type="button">회원가입</button>
      </div>
      <div class="spacer"></div>
      <form class="row wrap" style="gap:8px;" on:submit|preventDefault={submitAuth}>
        <input class="input mono" style="width:180px" bind:value={username} placeholder="username" />
        {#if authMode === 'signup'}
          <input class="input" style="width:220px" bind:value={email} placeholder="email" type="email" />
        {/if}
        <input class="input" style="width:180px" bind:value={password} placeholder="password" type="password" />
        <button class="btn primary" type="submit" disabled={authBusy || !username.trim() || !password.trim() || (authMode === 'signup' && !email.trim())}>
          {authBusy ? '처리 중…' : authMode === 'signup' ? '회원가입' : '로그인'}
        </button>
      </form>
      {#if $auth.lastError}
        <div class="hint" style="margin-top:8px; color:var(--state-danger);">{$auth.lastError}</div>
      {/if}
    {/if}
  </div>

  <div class="row wrap" style="justify-content:flex-start; gap:12px;">
    <span class="pill">내 ID</span>
    <input class="input mono" style="width:220px" bind:value={meId} placeholder="playerId" readonly />
    <span class="hint">로그인 계정과 자동 동기화됩니다.</span>
  </div>

  {#if $session.lastError}
    <div class="spacer"></div>
    <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
      <div class="logHead">에러</div>
      <div class="logBody">{$session.lastError}</div>
    </div>
  {/if}

  <div class="spacer"></div>
  <div class="grid2">
    <div class="card">
      <div class="cardTitle">세션 생성</div>
      <div class="hint">GM ID로 <span class="mono">{meId || 'me'}</span> 사용</div>
      <div class="spacer"></div>
      <button class="btn primary" disabled={busy || $auth.status !== 'authenticated' || !meId.trim()} on:click={doCreate}>{busy ? '처리 중…' : '생성'}</button>
    </div>

    <div class="card">
      <div class="cardTitle">세션 참가</div>
      <div class="hint">코드 입력 후 참가</div>
      <div class="spacer"></div>
      <form class="row wrap" on:submit|preventDefault={doJoin}>
        <input class="input mono" style="width:220px" bind:value={joinCode} placeholder="세션 코드" />
        <button class="btn" type="submit" disabled={busy || $auth.status !== 'authenticated' || !meId.trim() || !joinCode.trim()}>{busy ? '처리 중…' : '참가'}</button>
        {#if joinCode.trim()}
          <button class="btn" type="button" on:click={() => copy(joinCode.trim().toUpperCase())}>코드 복사</button>
        {/if}
      </form>
    </div>
  </div>
</PageSkeleton>
