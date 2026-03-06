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

  let meId = ''
  let joinCode = ''
  let busy = false

  $: if ($session.meId && meId === '') meId = $session.meId
  $: if ($session.code && joinCode === '') joinCode = $session.code

  $: selectedPreset = $presets.presets.find((p) => p.id === $presets.selectedId) ?? $presets.presets[0]

  async function doCreate() {
    busy = true
    setLastError(undefined)
    try {
      await ensureCards()
      setMeId(meId)
      const res = await createSession(meId.trim())
      setSessionCode(res.code)
      setGmId(res.gmId)
      setGmToken(res.gmToken)
      const joinRes = await joinSession(res.code, meId.trim(), selectedPreset?.passiveIds ?? [])
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
      setMeId(meId)
      const code = joinCode.trim().toUpperCase()
      setSessionCode(code)
      setGmToken('')
      const joinRes = await joinSession(code, meId.trim(), selectedPreset?.passiveIds ?? [])
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

  <div class="row wrap" style="justify-content:flex-start; gap:12px;">
    <span class="pill">내 ID</span>
    <input class="input mono" style="width:220px" bind:value={meId} placeholder="playerId" />
    <button class="btn" on:click={() => setMeId(meId)}>저장</button>
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
      <button class="btn primary" disabled={busy || !meId.trim()} on:click={doCreate}>{busy ? '처리 중…' : '생성'}</button>
    </div>

    <div class="card">
      <div class="cardTitle">세션 참가</div>
      <div class="hint">코드 입력 후 참가</div>
      <div class="spacer"></div>
      <form class="row wrap" on:submit|preventDefault={doJoin}>
        <input class="input mono" style="width:220px" bind:value={joinCode} placeholder="세션 코드" />
        <button class="btn" type="submit" disabled={busy || !meId.trim() || !joinCode.trim()}>{busy ? '처리 중…' : '참가'}</button>
        {#if joinCode.trim()}
          <button class="btn" type="button" on:click={() => copy(joinCode.trim().toUpperCase())}>코드 복사</button>
        {/if}
      </form>
    </div>
  </div>
</PageSkeleton>
