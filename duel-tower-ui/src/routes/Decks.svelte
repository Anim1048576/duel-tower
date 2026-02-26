<script lang="ts">
  import { onMount } from 'svelte'
  import { navigate } from '../lib/router'
  import { createDeck, deleteDeck, explainApiError, listDecks, type DeckResponse, type DeckType } from '../lib/api'
  import { content, ensureCards } from '../stores/content'
  import { pushToast } from '../stores/log'

  let decks: DeckResponse[] = []
  let loading = false
  let lastError: string | undefined

  // create draft
  let type: DeckType = 'PLAYER'
  let name = ''
  let q = ''
  let draft: Record<string, number> = {}

  $: draftTotal = Object.values(draft).reduce((a, b) => a + (b || 0), 0)
  $: copyOk = Object.values(draft).every((c) => (c || 0) <= 3)
  $: canCreate =
    !loading &&
    (type === 'ENEMY'
      ? true
      : draftTotal === 12 && copyOk && Object.values(draft).every((c) => (c || 0) > 0))

  $: filtered = $content.cards
    .filter((c) => {
      const s = q.trim().toLowerCase()
      if (!s) return true
      return (
        c.id.toLowerCase().includes(s) ||
        (c.name || '').toLowerCase().includes(s) ||
        (c.text || '').toLowerCase().includes(s)
      )
    })
    .slice(0, 60)

  function addToDraft(defId: string) {
    draft = { ...draft, [defId]: (draft[defId] || 0) + 1 }
  }

  function subFromDraft(defId: string) {
    const cur = draft[defId] || 0
    if (cur <= 1) {
      const next = { ...draft }
      delete next[defId]
      draft = next
      return
    }
    draft = { ...draft, [defId]: cur - 1 }
  }

  function clearDraft() {
    draft = {}
  }

  function toSpecs() {
    return Object.entries(draft)
      .filter(([, c]) => (c || 0) > 0)
      .map(([cardId, count]) => ({ cardId, count }))
  }

  async function refresh() {
    loading = true
    lastError = undefined
    try {
      decks = await listDecks()
    } catch (e) {
      lastError = explainApiError(e)
      pushToast('덱 목록 실패', lastError)
    } finally {
      loading = false
    }
  }

  async function doCreate() {
    loading = true
    lastError = undefined
    try {
      const res = await createDeck({ name: name?.trim() || null, type, cards: toSpecs() })
      pushToast('덱 생성', `${res.id} · ${res.name}`)
      await refresh()
      clearDraft()
      name = ''
      navigate(`/decks/${res.id}`)
    } catch (e) {
      lastError = explainApiError(e)
      pushToast('덱 생성 실패', lastError)
    } finally {
      loading = false
    }
  }

  async function doDelete(id: number) {
    if (!confirm(`덱 ${id}를 삭제할까?`)) return
    loading = true
    try {
      await deleteDeck(id)
      pushToast('덱 삭제', String(id))
      await refresh()
    } catch (e) {
      pushToast('삭제 실패', explainApiError(e))
    } finally {
      loading = false
    }
  }

  onMount(async () => {
    await ensureCards()
    await refresh()
  })
</script>

<section>
  <div class="card">
    <div class="row wrap" style="justify-content:space-between">
      <div>
        <div class="h2">덱 (DB)</div>
        <div class="hint">서버에 저장되는 카드 뭉치. PLAYER는 12장 고정/동일카드 3장 제한.</div>
      </div>
      <div class="row wrap" style="justify-content:flex-end">
        <button class="btn" on:click={refresh} disabled={loading}>새로고침</button>
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

  <div class="spacer"></div>

  <section class="split">
    <div>
      <div class="card">
        <div class="row wrap" style="justify-content:space-between">
          <div class="cardTitle">새 덱 만들기</div>
          <div class="row wrap" style="justify-content:flex-end">
            <span class="badge">총 {draftTotal}{type === 'PLAYER' ? '/12' : ''}</span>
            {#if type === 'PLAYER'}
              <span class="badge {copyOk ? 'ok' : 'no'}">복제 {copyOk ? 'OK' : '초과'}</span>
            {/if}
          </div>
        </div>

        <div class="spacer"></div>
        <div class="row wrap">
          <div class="grow">
            <label class="label">이름</label>
            <input class="input" bind:value={name} placeholder="예: starter-12 / boss-deck" />
          </div>
          <div style="width:180px">
            <label class="label">타입</label>
            <select class="input" bind:value={type}>
              <option value="PLAYER">PLAYER</option>
              <option value="ENEMY">ENEMY</option>
            </select>
          </div>
        </div>

        <div class="spacer"></div>
        <div class="row wrap">
          <button class="btn primary" on:click={doCreate} disabled={!canCreate}>생성</button>
          <button class="btn" on:click={clearDraft} disabled={loading || draftTotal === 0}>초기화</button>
          {#if type === 'PLAYER' && draftTotal !== 12}
            <span class="hint">PLAYER 덱은 총합이 12장이 되어야 생성 가능.</span>
          {/if}
        </div>

        <div class="spacer"></div>
        <div class="cardTitle">드래프트 (클릭으로 -1)</div>
        <div class="spacer"></div>
        <div class="cardRow">
          {#if Object.keys(draft).length === 0}
            <span class="muted">아래 카드 풀에서 클릭해서 추가</span>
          {:else}
            {#each Object.entries(draft) as [defId, cnt] (defId)}
              <button class="btn" on:click={() => subFromDraft(defId)}>
                <span class="mono">{defId}</span>
                · {($content.cardsById[defId]?.name ?? '—')} × {cnt}
              </button>
            {/each}
          {/if}
        </div>

        <div class="spacer"></div>
        <div class="row wrap" style="justify-content:space-between">
          <div class="cardTitle">카드 풀</div>
          <div class="row wrap" style="justify-content:flex-end">
            <input class="input" style="width:240px" bind:value={q} placeholder="검색 (이름/ID/텍스트)" />
            <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
          </div>
        </div>

        <div class="spacer"></div>
        <div class="searchGrid">
          {#each filtered as c (c.id)}
            <div class="gcard" on:click={() => addToDraft(c.id)}>
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

        <div class="hint">카드를 클릭하면 드래프트에 1장 추가된다.</div>
      </div>
    </div>

    <aside>
      <div class="card">
        <div class="row wrap" style="justify-content:space-between">
          <div class="cardTitle">덱 목록 ({decks.length})</div>
          <span class="badge">{loading ? '…' : 'ready'}</span>
        </div>
        <div class="spacer"></div>

        <div class="turnList">
          {#each decks as d (d.id)}
            <div class="turnItem" on:click={() => navigate(`/decks/${d.id}`)}>
              <div>
                <b>{d.name}</b>
                <div class="hint">{d.type} · {d.totalCards}장</div>
              </div>
              <div class="row" style="gap:6px">
                <span class="badge mono">{d.id}</span>
                <button class="iconBtn" title="삭제" on:click|stopPropagation={() => doDelete(d.id)}>✕</button>
              </div>
            </div>
          {/each}
        </div>
        <div class="hint">항목 클릭: 상세 보기</div>
      </div>
    </aside>
  </section>
</section>
