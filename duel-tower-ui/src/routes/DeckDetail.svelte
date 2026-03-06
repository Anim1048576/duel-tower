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
  import CardSummaryTile from '../lib/components/CardSummaryTile.svelte'
  import StatusBadge from '../lib/components/StatusBadge.svelte'
  import DisabledReason from '../lib/components/DisabledReason.svelte'
  import LogLineItem from '../lib/components/LogLineItem.svelte'
  import { requestConfirm } from '../stores/ui'
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
  let originalDraft: Record<string, number> = {}
  let initDone = false

  let maxEditableCards = 4
  let protectedCardIds: string[] = []
  let curseNodeLocked = false

  $: draftTotal = Object.values(draft).reduce((a, b) => a + (b || 0), 0)
  $: normalEntries = Object.entries(draft).filter(([cardId]) => !isExCard(cardId))
  $: exEntries = Object.entries(draft).filter(([cardId]) => isExCard(cardId))
  $: normalTotal = normalEntries.reduce((sum, [, count]) => sum + (count || 0), 0)
  $: exTotal = exEntries.reduce((sum, [, count]) => sum + (count || 0), 0)

  $: changedCopies = Object.keys({ ...originalDraft, ...draft }).reduce((sum, cardId) => {
    const before = originalDraft[cardId] || 0
    const after = draft[cardId] || 0
    return sum + Math.abs(after - before)
  }, 0)

  $: removedProtectedCards = protectedCardIds.filter((cardId) => (originalDraft[cardId] || 0) > 0 && (draft[cardId] || 0) === 0)

  $: duplicateViolations = normalEntries
    .filter(([, count]) => (count || 0) > 3)
    .map(([cardId, count]) => `${$content.cardsById[cardId]?.name ?? cardId} × ${count}`)

  $: deckValidation = (() => {
    const totalCardsMessage =
      draftType === 'PLAYER' && normalTotal !== 12 ? `일반 스킬 덱은 12장 고정이다. 현재 ${normalTotal}장.` : ''

    const duplicateMessage = duplicateViolations.length
      ? `중복 제한(최대 3장) 위반: ${duplicateViolations.join(', ')}`
      : ''

    const mutableCountMessage =
      changedCopies > maxEditableCards
        ? `변경 가능 장수(${maxEditableCards})를 초과했다. 현재 ${changedCopies}장 변경.`
        : ''

    const protectedRemovalMessage = removedProtectedCards.length
      ? `제거 금지 카드가 빠졌다: ${removedProtectedCards.join(', ')}`
      : ''

    const curseNodeLockMessage = curseNodeLocked && changedCopies > 0 ? '저주 노드 잠금 상태에서는 덱 수정을 할 수 없다.' : ''

    const exSectionMessage = exTotal !== 1 ? `EX 카드는 별도 섹션에서 정확히 1장 선택해야 한다. 현재 ${exTotal}장.` : ''

    const violations = [
      totalCardsMessage,
      duplicateMessage,
      mutableCountMessage,
      protectedRemovalMessage,
      curseNodeLockMessage,
      exSectionMessage,
    ].filter(Boolean)

    return {
      totalCardsMessage,
      duplicateMessage,
      mutableCountMessage,
      protectedRemovalMessage,
      curseNodeLockMessage,
      exSectionMessage,
      violations,
      canSave: !loading && violations.length === 0,
      disabledReason: violations[0] ?? '',
    }
  })()

  $: draftDiffSummary = (() => {
    const keys = Object.keys({ ...originalDraft, ...draft })
    const added: string[] = []
    const removed: string[] = []
    const changed: string[] = []

    for (const cardId of keys) {
      const before = originalDraft[cardId] || 0
      const after = draft[cardId] || 0
      if (before === after) continue
      const name = $content.cardsById[cardId]?.name ?? cardId
      if (before === 0 && after > 0) added.push(`${name} +${after}`)
      else if (before > 0 && after === 0) removed.push(`${name} -${before}`)
      else changed.push(`${name} ${before}→${after}`)
    }

    return { added, removed, changed }
  })()

  function deckToDraft(d: DeckResponse) {
    draftName = d.name
    draftType = d.type
    const m: Record<string, number> = {}
    for (const c of d.cards || []) {
      if (!c?.cardId) continue
      m[c.cardId] = (m[c.cardId] || 0) + (c.count || 0)
    }
    draft = m
    originalDraft = { ...m }
    initDone = true
  }

  function isExCard(cardId: string) {
    const def = $content.cardsById[cardId]
    return def?.type === 'EX' || cardId.toUpperCase().includes('_EX')
  }

  function toSpecs() {
    return Object.entries(draft)
      .filter(([, c]) => (c || 0) > 0)
      .map(([cardId, count]) => ({ cardId, count }))
  }

  function addOne(defId: string) {
    if (isExCard(defId)) {
      const next = { ...draft }
      for (const [cardId] of Object.entries(next)) {
        if (isExCard(cardId)) delete next[cardId]
      }
      next[defId] = 1
      draft = next
      return
    }
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
    const ok = await requestConfirm({ title: `덱 ${deck.id} 삭제`, message: '삭제 후 되돌릴 수 없습니다.', confirmLabel: '삭제', tone: 'danger' })
    if (!ok) return
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
    const params = new URLSearchParams(window.location.search)
    maxEditableCards = Math.max(0, Number(params.get('maxChanges') || 4))
    protectedCardIds = (params.get('protected') || '')
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean)
    curseNodeLocked = params.get('curseLocked') === '1'

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
      <LogLineItem at="now" level="API" title="요청 실패" message={lastError} />
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
            <StatusBadge label={`일반 ${normalTotal}${draftType === 'PLAYER' ? '/12' : ''}`} />
            <StatusBadge label={`EX ${exTotal}/1`} />
            <StatusBadge tone={deckValidation.violations.length === 0 ? 'ok' : 'danger'} label={`검증 ${deckValidation.violations.length === 0 ? '통과' : '실패'}`} />
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
        <div class="validationList">
          <div class="ruleItem {deckValidation.totalCardsMessage ? 'bad' : 'ok'}">총 장수: {deckValidation.totalCardsMessage || 'OK'}</div>
          <div class="ruleItem {deckValidation.duplicateMessage ? 'bad' : 'ok'}">중복: {deckValidation.duplicateMessage || 'OK'}</div>
          <div class="ruleItem {deckValidation.mutableCountMessage ? 'bad' : 'ok'}">변경 가능 장수: {deckValidation.mutableCountMessage || 'OK'}</div>
          <div class="ruleItem {deckValidation.protectedRemovalMessage ? 'bad' : 'ok'}">제거 금지 카드: {deckValidation.protectedRemovalMessage || 'OK'}</div>
          <div class="ruleItem {deckValidation.curseNodeLockMessage ? 'bad' : 'ok'}">저주 노드 잠금: {deckValidation.curseNodeLockMessage || 'OK'}</div>
        </div>

        <div class="spacer"></div>
        <div class="card" style="background: rgba(0,0,0,.12)">
          <div class="cardTitle">저장 전 변경 요약</div>
          <div class="hint">추가 {draftDiffSummary.added.length} · 제거 {draftDiffSummary.removed.length} · 수량 변경 {draftDiffSummary.changed.length}</div>
          <div class="spacer"></div>
          <div class="hint">추가: {draftDiffSummary.added.length ? draftDiffSummary.added.join(', ') : '없음'}</div>
          <div class="hint">제거: {draftDiffSummary.removed.length ? draftDiffSummary.removed.join(', ') : '없음'}</div>
          <div class="hint">변경: {draftDiffSummary.changed.length ? draftDiffSummary.changed.join(', ') : '없음'}</div>
        </div>

        <div class="spacer"></div>
        <div>
          <button class="btn primary" on:click={doSaveAll} disabled={!deckValidation.canSave}>저장</button>
          <DisabledReason show={!deckValidation.canSave} reason={deckValidation.disabledReason} />
        </div>
      </div>
    </div>

    <div class="spacer"></div>

    <section class="split">
      <div>
        <div class="card">
          <div class="row wrap" style="justify-content:space-between">
            <div class="cardTitle">좌측 · 보유 카드 (클릭 +1)</div>
            <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
          </div>
          <div class="spacer"></div>
          <div class="hint">일반 스킬 카드</div>
          <div class="searchGrid">
            {#each $content.cards.filter((c) => !isExCard(c.id)).slice(0, 60) as c (c.id)}
              <CardSummaryTile def={c} on:inspect={() => addOne(c.id)} />
            {/each}
          </div>
          <div class="spacer"></div>
          <div class="hint">EX 카드 (별도 섹션 고정)</div>
          <div class="cardRow">
            {#each $content.cards.filter((c) => isExCard(c.id)) as c (c.id)}
              <button class="btn" on:click={() => addOne(c.id)}>{c.name}</button>
            {/each}
          </div>
          <div class="hint">편집 드래프트에만 반영되고, 저장 버튼을 눌러야 서버에 반영된다.</div>
        </div>
      </div>

      <aside>
        <div class="card">
          <div class="cardTitle">우측 · 현재 덱 (클릭 -1)</div>
          <div class="spacer"></div>
          <div class="hint">EX 카드</div>
          <div class="cardRow">
            {#if exEntries.length === 0}
              <span class="muted">선택된 EX 없음</span>
            {:else}
              {#each exEntries as [defId, cnt] (defId)}
                <button class="btn" on:click={() => subOne(defId)}>
                  <span class="mono">{defId}</span>
                  · {($content.cardsById[defId]?.name ?? '—')} × {cnt}
                </button>
              {/each}
            {/if}
          </div>
          <div class="spacer"></div>
          <div class="hint">일반 스킬 덱</div>
          <div class="cardRow">
            {#if normalEntries.length === 0}
              <span class="muted">비어 있음</span>
            {:else}
              {#each normalEntries as [defId, cnt] (defId)}
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

<style>
  .validationList{display:grid; gap:8px}
  .ruleItem{padding:8px 10px; border-radius:8px; border:1px solid var(--line-default)}
  .ruleItem.ok{color:var(--state-ok); border-color:rgba(109,255,177,.35)}
  .ruleItem.bad{color:var(--state-danger); border-color:rgba(255,93,116,.35)}
</style>
