<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import {
    addDeckCards,
    deleteDeck,
    explainApiError,
    getDeck,
    updateDeck,
    type DeckResponse,
    type DeckType,
  } from '../lib/api'
  import { content, ensureCards } from '../stores/content'
  import { pushToast } from '../stores/log'

  export let id = ''

  let deck: DeckResponse | null = null
  let loading = false
  let lastError: string | undefined

  // quick add
  let addCardId = ''
  let addCount = 1

  // edit draft (full save)
  let draftName = ''
  let draftType: DeckType = 'PLAYER'
  let draft: Record<string, number> = {}
  let initDone = false

  $: draftTotal = Object.values(draft).reduce((a, b) => a + (b || 0), 0)
  $: copyOk = Object.values(draft).every((c) => (c || 0) <= 3)
  $: canSave =
    !loading &&
    (draftType === 'ENEMY'
      ? true
      : draftTotal === 12 && copyOk && Object.values(draft).every((c) => (c || 0) > 0))

  function deckToDraft(d: DeckResponse) {
    draftName = d.name
    draftType = d.type
    const m: Record<string, number> = {}
    for (const c of d.cards || []) {
      if (!c?.cardId) continue
      m[c.cardId] = (m[c.cardId] || 0) + (c.count || 0)
    }
    draft = m
    initDone = true
  }

  function toSpecs() {
    return Object.entries(draft)
      .filter(([, c]) => (c || 0) > 0)
      .map(([cardId, count]) => ({ cardId, count }))
  }

  function addOne(defId: string) {
    draft = { ...draft, [defId]: (draft[defId] || 0) + 1 }
  }

  function subOne(defId: string) {
    const cur = draft[defId] || 0
    if (cur <= 1) {
      const next = { ...draft }
      delete next[defId]
      draft = next
      return
    }
    draft = { ...draft, [defId]: cur - 1 }
  }

  async function load() {
    loading = true
    lastError = undefined
    try {
      const did = Number(id)
      deck = await getDeck(did)
      deckToDraft(deck)
      if (!addCardId && $content.cards.length) addCardId = $content.cards[0].id
    } catch (e) {
      lastError = explainApiError(e)
      pushToast('덱 로드 실패', lastError)
    } finally {
      loading = false
    }
  }

  async function doQuickAdd() {
    if (!deck) return
    loading = true
    try {
      const did = Number(id)
      const res = await addDeckCards(did, { cards: [{ cardId: addCardId, count: Math.max(1, Number(addCount) || 1) }] })
      deck = res
      deckToDraft(res)
      pushToast('추가됨', `${addCardId} × ${addCount}`)
    } catch (e) {
      pushToast('추가 실패', explainApiError(e))
    } finally {
      loading = false
    }
  }

  async function doSaveAll() {
    if (!deck) return
    loading = true
    try {
      const did = Number(id)
      const res = await updateDeck(did, {
        name: draftName?.trim() || null,
        type: draftType,
        cards: toSpecs(),
      })
      deck = res
      deckToDraft(res)
      pushToast('저장됨', `${res.totalCards}장`)
    } catch (e) {
      pushToast('저장 실패', explainApiError(e))
    } finally {
      loading = false
    }
  }

  async function doDelete() {
    if (!deck) return
    if (!confirm(`덱 ${deck.id}를 삭제할까?`)) return
    loading = true
    try {
      await deleteDeck(deck.id)
      pushToast('덱 삭제', String(deck.id))
      navigate('/decks')
    } catch (e) {
      pushToast('삭제 실패', explainApiError(e))
    } finally {
      loading = false
    }
  }

  onMount(async () => {
    await ensureCards()
    await load()
  })

  $: if (deck && !initDone) deckToDraft(deck)
</script>

<section>
  <div class="card">
    <div class="row wrap" style="justify-content:space-between">
      <div class="row wrap">
        <button class="btn" on:click={() => navigate('/decks')}>← 덱 목록</button>
        <div>
          <div class="h2">덱 상세</div>
          {#if deck}
            <div class="hint">ID {deck.id} · {deck.type} · {deck.totalCards}장</div>
          {/if}
        </div>
      </div>
      <div class="row wrap" style="justify-content:flex-end">
        <button class="btn" on:click={load} disabled={loading}>새로고침</button>
        <button class="btn danger" on:click={doDelete} disabled={loading || !deck}>삭제</button>
      </div>
    </div>

    {#if lastError}
      <div class="spacer"></div>
      <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
        <div class="logHead">API</div>
        <div class="logBody">{lastError}</div>
      </div>
    {/if}
  </div>

  {#if deck}
    <div class="spacer"></div>

    <div class="grid2">
      <div class="card">
        <div class="cardTitle">빠른 추가 (증분)</div>
        <div class="hint"><span class="mono">POST /api/content/decks/{deck.id}/cards/add</span> 사용</div>
        <div class="spacer"></div>
        <div class="row wrap">
          <div class="grow">
            <label class="label">카드</label>
            <select class="input" bind:value={addCardId}>
              {#each $content.cards as c (c.id)}
                <option value={c.id}>{c.id} · {c.name}</option>
              {/each}
            </select>
          </div>
          <div style="width:140px">
            <label class="label">수량</label>
            <input class="input" type="number" min="1" step="1" bind:value={addCount} />
          </div>
          <div style="align-self:flex-end">
            <button class="btn primary" on:click={doQuickAdd} disabled={loading || !addCardId}>추가</button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="row wrap" style="justify-content:space-between">
          <div class="cardTitle">편집 (전체 저장)</div>
          <div class="row wrap" style="justify-content:flex-end">
            <span class="badge">총 {draftTotal}{draftType === 'PLAYER' ? '/12' : ''}</span>
            {#if draftType === 'PLAYER'}
              <span class="badge {copyOk ? 'ok' : 'no'}">복제 {copyOk ? 'OK' : '초과'}</span>
            {/if}
          </div>
        </div>
        <div class="hint">이 섹션은 <span class="mono">PUT /api/content/decks/{deck.id}</span>로 통째로 저장한다.</div>
        <div class="spacer"></div>

        <div class="row wrap">
          <div class="grow">
            <label class="label">이름</label>
            <input class="input" bind:value={draftName} />
          </div>
          <div style="width:180px">
            <label class="label">타입</label>
            <select class="input" bind:value={draftType}>
              <option value="PLAYER">PLAYER</option>
              <option value="ENEMY">ENEMY</option>
            </select>
          </div>
        </div>

        <div class="spacer"></div>
        <div class="row wrap">
          <button class="btn primary" on:click={doSaveAll} disabled={!canSave}>저장</button>
          {#if draftType === 'PLAYER' && draftTotal !== 12}
            <span class="hint">PLAYER 덱은 총 12장이어야 저장 가능.</span>
          {/if}
        </div>
      </div>
    </div>

    <div class="spacer"></div>

    <section class="split">
      <div>
        <div class="card">
          <div class="row wrap" style="justify-content:space-between">
            <div class="cardTitle">카드 풀 (클릭 +1)</div>
            <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
          </div>
          <div class="spacer"></div>
          <div class="searchGrid">
            {#each $content.cards.slice(0, 60) as c (c.id)}
              <div class="gcard" on:click={() => addOne(c.id)}>
                <div class="row" style="justify-content:space-between; align-items:flex-start">
                  <div class="gcardTitle">{c.name}</div>
                  <span class="badge">{c.cost}</span>
                </div>
                <div class="gcardSub mono">{c.id}</div>
                <div class="gcardTags">
                  {#if c.token}<span class="tag d">TOKEN</span>{/if}
                  {#each c.keywords as k (k)}
                    <span class="tag p">{k}</span>
                  {/each}
                </div>
              </div>
            {/each}
          </div>
          <div class="hint">편집 드래프트에만 반영되고, 저장 버튼을 눌러야 서버에 반영된다.</div>
        </div>
      </div>

      <aside>
        <div class="card">
          <div class="cardTitle">현재 드래프트 (클릭 -1)</div>
          <div class="spacer"></div>
          <div class="cardRow">
            {#if Object.keys(draft).length === 0}
              <span class="muted">비어 있음</span>
            {:else}
              {#each Object.entries(draft) as [defId, cnt] (defId)}
                <button class="btn" on:click={() => subOne(defId)}>
                  <span class="mono">{defId}</span>
                  · {($content.cardsById[defId]?.name ?? '—')} × {cnt}
                </button>
              {/each}
            {/if}
          </div>
        </div>
      </aside>
    </section>
  {/if}
</section>
