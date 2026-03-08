<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { createSession, explainApiError, joinSession, listCharacterProfiles, type CharacterProfileResponse } from '../lib/api'
  import { copyToClipboard } from '../lib/clipboard'
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { session, setGmId, setGmToken, setLastError, setMeId, setPlayerToken, setSessionCode } from '../stores/session'
  import { ensureCards } from '../stores/content'
  import { refreshState } from '../stores/combat'
  import { pushToast } from '../stores/log'
  import { auth, doLogin, doSignup } from '../stores/auth'

  let meId = ''
  let joinCode = ''
  let busy = false
  let authBusy = false
  let authMode: 'login' | 'signup' = 'login'
  let username = ''
  let password = ''
  let characterBusy = false
  let characterError = ''
  let characterProfiles: CharacterProfileResponse[] = []
  let selectedCharacterId = ""

  $: if ($auth.status === 'authenticated') meId = $auth.username
  $: if ($auth.status !== 'authenticated' && $session.meId && meId === '') meId = $session.meId
  $: if ($session.code && joinCode === '') joinCode = $session.code
  $: if ($auth.status === 'authenticated') setMeId($auth.username)

  $: selectedCharacter = characterProfiles.find((profile) => String(profile.id) === selectedCharacterId) ?? null


  function toPassiveIds(profile: CharacterProfileResponse): string[] {
    return [profile.trait1, profile.trait2]
      .map((value) => (value ?? '').trim())
      .filter((value) => /^P\d{3}$/.test(value))
  }


  async function loadCharacterProfiles() {
    if ($auth.status !== 'authenticated') return

    characterBusy = true
    characterError = ''
    try {
      characterProfiles = await listCharacterProfiles()
      if (!characterProfiles.some((profile) => String(profile.id) === selectedCharacterId)) {
        selectedCharacterId = characterProfiles[0] ? String(characterProfiles[0].id) : ""
      }
    } catch (e) {
      characterError = explainApiError(e)
    } finally {
      characterBusy = false
    }
  }

  async function submitAuth() {
    authBusy = true
    try {
      const result = authMode === 'signup'
        ? await doSignup(username, password)
        : await doLogin(username, password)

      if (!result.ok) {
        pushToast(authMode === 'signup' ? '회원가입 실패' : '로그인 실패', result.message)
        return
      }

      setMeId(username.trim())
      await loadCharacterProfiles()
      pushToast(authMode === 'signup' ? '회원가입 완료' : '로그인 완료', username.trim())
    } finally {
      authBusy = false
    }
  }

  async function doCreate() {
    if (!selectedCharacter) {
      pushToast('캐릭터 선택 필요', '로비 입장 전에 캐릭터를 먼저 선택해 주세요.')
      return
    }

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
        undefined,
        undefined,
        undefined,
        undefined,
        selectedCharacter.id,
      )
      setPlayerToken(joinRes.playerToken)
      await refreshState()
      pushToast('세션 생성', `${res.code} · ${selectedCharacter.name}`)
      navigate('/lobby')
    } catch (e) {
      setLastError(explainApiError(e))
      pushToast('생성 실패', explainApiError(e))
    } finally {
      busy = false
    }
  }

  async function doJoin() {
    if (!selectedCharacter) {
      pushToast('캐릭터 선택 필요', '로비 입장 전에 캐릭터를 먼저 선택해 주세요.')
      return
    }

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
        undefined,
        undefined,
        undefined,
        undefined,
        selectedCharacter.id,
      )
      setPlayerToken(joinRes.playerToken)
      await refreshState()
      pushToast('세션 참가', `${code} · ${selectedCharacter.name}`)
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

  onMount(async () => {
    if ($auth.status === 'authenticated') {
      await loadCharacterProfiles()
    }
  })
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
        <input class="input" style="width:180px" bind:value={password} placeholder="password" type="password" />
        <button class="btn primary" type="submit" disabled={authBusy || !username.trim() || !password.trim()}>
          {authBusy ? '처리 중…' : authMode === 'signup' ? '회원가입' : '로그인'}
        </button>
      </form>
      {#if $auth.lastError}
        <div class="hint" style="margin-top:8px; color:var(--state-danger);">{$auth.lastError}</div>
      {/if}
    {/if}
  </div>

  <div class="card" style="margin-bottom:12px;">
    <div class="cardTitle">입장 캐릭터 선택</div>
    <div class="hint">로비 입장 전에 이 세션에서 사용할 캐릭터를 선택하세요.</div>
    <div class="spacer"></div>
    <div class="row wrap" style="gap:8px; align-items:center;">
      <select class="input" style="min-width:260px" bind:value={selectedCharacterId} disabled={$auth.status !== 'authenticated' || characterBusy || characterProfiles.length === 0}>
        {#if characterProfiles.length === 0}
          <option value="">캐릭터 없음</option>
        {/if}
        {#each characterProfiles as profile}
          <option value={profile.id}>#{profile.id} · {profile.name}</option>
        {/each}
      </select>
      <button class="btn" type="button" on:click={loadCharacterProfiles} disabled={$auth.status !== 'authenticated' || characterBusy}>
        {characterBusy ? '불러오는 중…' : '목록 새로고침'}
      </button>
    </div>
    {#if selectedCharacter}
      <div class="hint" style="margin-top:8px;">
        선택됨: <span class="mono">{selectedCharacter.name}</span>
        · 패시브 {toPassiveIds(selectedCharacter).length || 0}개
        · 덱 {selectedCharacter.currentSkillDeck?.length ?? 0}장
      </div>
    {/if}
    {#if characterError}
      <div class="hint" style="margin-top:8px; color:var(--state-danger);">캐릭터 로드 실패: {characterError}</div>
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
      <button class="btn primary" disabled={busy || $auth.status !== 'authenticated' || !meId.trim() || !selectedCharacter} on:click={doCreate}>{busy ? '처리 중…' : '생성'}</button>
    </div>

    <div class="card">
      <div class="cardTitle">세션 참가</div>
      <div class="hint">코드 입력 후 참가</div>
      <div class="spacer"></div>
      <form class="row wrap" on:submit|preventDefault={doJoin}>
        <input class="input mono" style="width:220px" bind:value={joinCode} placeholder="세션 코드" />
        <button class="btn" type="submit" disabled={busy || $auth.status !== 'authenticated' || !meId.trim() || !joinCode.trim() || !selectedCharacter}>{busy ? '처리 중…' : '참가'}</button>
        {#if joinCode.trim()}
          <button class="btn" type="button" on:click={() => copy(joinCode.trim().toUpperCase())}>코드 복사</button>
        {/if}
      </form>
    </div>
  </div>
</PageSkeleton>
