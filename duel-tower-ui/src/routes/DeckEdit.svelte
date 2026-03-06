<script lang="ts">
  import { get } from 'svelte/store'
  import { onMount } from 'svelte'
  import { content, ensureCards } from '../stores/content'
  import { combat, refreshState } from '../stores/combat'
  import { session } from '../stores/session'
  import { explainApiError, updateSessionDeck } from '../lib/api'
  import type { CharacterView, SessionSnapshot } from '../lib/model'

  let q = ''
  let deckCardIds: string[] = []
  let initializedForKey = ''
  let statusMsg = ''
  let errorMsg = ''
  let saving = false

  onMount(async () => {
    await Promise.all([ensureCards(), refreshState()])
  })

  $: meId = ($session.meId || '').trim()
  $: currentState = $combat.state
  $: currentPlayer = meId ? currentState?.players?.[meId] : null
  $: stateKey = `${currentState?.sessionId ?? ''}:${meId}:${currentState?.version ?? 0}`

  $: if (currentState && currentPlayer && initializedForKey !== stateKey) {
    const next = deriveDeckCardIdsFromState(currentPlayer, currentState.cards)
    if (next.length) {
      deckCardIds = next
      statusMsg = '세션 상태 기준 덱을 불러왔어요.'
      errorMsg = ''
    }
    initializedForKey = stateKey
  }

  $: counts = deckCardIds.reduce<Record<string, number>>((acc, id) => {
    acc[id] = (acc[id] || 0) + 1
    return acc
  }, {})

  $: filtered = $content.cards
    .filter((c) => {
      const s = q.trim().toLowerCase()
      if (!s) return true
      return c.id.toLowerCase().includes(s) || (c.name || '').toLowerCase().includes(s) || (c.text || '').toLowerCase().includes(s)
    })
    .slice(0, 80)

  function deriveDeckCardIdsFromState(
    player: CharacterView,
    cardsByInstId: SessionSnapshot['cards'],
  ): string[] {
    const zoneOrder = [...(player.deck ?? []), ...(player.hand ?? []), ...(player.grave ?? []), ...(player.field ?? []), ...(player.excluded ?? [])]
    return zoneOrder
      .map((instId) => cardsByInstId?.[instId]?.defId)
      .filter((id): id is string => typeof id === 'string' && id.length > 0)
      .slice(0, 12)
  }

  function addToDeck(defId: string) {
    if (deckCardIds.length >= 12) {
      errorMsg = '덱은 정확히 12장이어야 해요.'
      return
    }
    const copyCount = counts[defId] || 0
    if (copyCount >= 3) {
      errorMsg = `같은 카드는 최대 3장까지 넣을 수 있어요. (${defId})`
      return
    }
    deckCardIds = [...deckCardIds, defId]
    statusMsg = ''
    errorMsg = ''
  }

  function removeAt(idx: number) {
    const next = deckCardIds.slice()
    next.splice(idx, 1)
    deckCardIds = next
    statusMsg = ''
  }

  async function saveDeck() {
    const s = get(session)
    if (!s.code || !meId) {
      errorMsg = '세션 코드와 플레이어 ID가 필요해요.'
      return
    }
    if (!s.playerToken) {
      errorMsg = '플레이어 토큰이 없어요. 세션 참가를 다시 시도해 주세요.'
      return
    }
    if (deckCardIds.length !== 12) {
      errorMsg = `덱은 12장이어야 합니다. (현재 ${deckCardIds.length}장)`
      return
    }

    saving = true
    statusMsg = ''
    errorMsg = ''
    try {
      await updateSessionDeck(s.code, meId, deckCardIds, s.playerToken)
      await refreshState()
      statusMsg = '덱 저장 완료. 세션 상태를 동기화했어요.'
    } catch (e) {
      errorMsg = explainApiError(e)
    } finally {
      saving = false
    }
  }
</script>

<section class="split">
  <div>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div>
          <div class="h2">덱 편집</div>
          <div class="hint">현재 세션 플레이어 덱을 수정하고 서버에 저장한다.</div>
        </div>
        <div class="row wrap" style="justify-content:flex-end">
          <input class="input" style="width:260px" bind:value={q} placeholder="검색 (이름/ID/텍스트)" />
          <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
          <button class="btn" on:click={() => refreshState()}>세션 새로고침</button>
          <button class="btn primary" on:click={saveDeck} disabled={saving}>저장</button>
        </div>
      </div>

      <div class="spacer"></div>
      <div class="hint">제약: <b>deckEditable 노드</b>에서만 수정 가능 · <b>본인 덱</b>만 수정 가능 · <b>12장 정확히</b> · <b>동일 카드 최대 3장</b></div>

      {#if $content.lastError}
        <div class="spacer"></div>
        <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
          <div class="logHead">Card API</div>
          <div class="logBody">{$content.lastError}</div>
        </div>
      {/if}

      {#if errorMsg}
        <div class="spacer"></div>
        <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
          <div class="logHead">저장 실패</div>
          <div class="logBody">{errorMsg}</div>
        </div>
      {/if}

      {#if statusMsg}
        <div class="spacer"></div>
        <div class="ti" style="border-color: rgba(90,221,164,.35); background: rgba(90,221,164,.06)">
          <div class="logHead">상태</div>
          <div class="logBody">{statusMsg}</div>
        </div>
      {/if}
    </div>

    <div class="spacer"></div>

    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">카드 풀 ({$content.cards.length})</div>
        <div class="row wrap" style="justify-content:flex-end">
          <span class="badge">덱 {deckCardIds.length}/12</span>
        </div>
      </div>
      <div class="spacer"></div>

      <div class="searchGrid">
        {#each filtered as c (c.id)}
          <div class="gcard" on:click={() => addToDeck(c.id)}>
            <div class="row" style="justify-content:space-between; align-items:flex-start">
              <div class="gcardTitle">{c.name}</div>
              <span class="badge">{c.cost}</span>
            </div>
            <div class="gcardSub mono">{c.id}</div>
            <div class="gcardTags">
              {#if c.token}<span class="tag d">TOKEN</span>{/if}
              {#if counts[c.id]}<span class="tag p">선택 {counts[c.id]}장</span>{/if}
              {#each c.keywords as k (k)}
                <span class="tag p">{k}</span>
              {/each}
            </div>
          </div>
        {/each}
      </div>
    </div>
  </div>

  <aside>
    <div class="card">
      <div class="cardTitle">내 덱 (클릭해서 제거)</div>
      <div class="hint">플레이어: <span class="mono">{meId || '없음'}</span></div>
      <div class="spacer"></div>
      <div class="cardRow">
        {#each deckCardIds as defId, i (`${defId}:${i}`)}
          <button class="btn" on:click={() => removeAt(i)}>
            <span class="mono">{defId}</span>
            {#if $content.cardsById[defId]}
              · {$content.cardsById[defId].name}
            {/if}
          </button>
        {/each}
        {#if deckCardIds.length === 0}
          <div class="hint">덱 카드가 비어 있습니다. 카드 풀에서 추가해 주세요.</div>
        {/if}
      </div>
    </div>
  </aside>
</section>
