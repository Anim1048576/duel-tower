<script lang="ts">
  import { get } from 'svelte/store'
  import { onMount } from 'svelte'
  import { content, ensureCards } from '../stores/content'
  import { combat, refreshState } from '../stores/combat'
  import { session } from '../stores/session'
  import { explainApiError, updateSessionDeck } from '../lib/api'
  import type { CharacterView, PlayerOwnedCard } from '../lib/model'

  type DeckEntry = {
    ownedCardId: string
    cardId: string
    index: number
  }

  type PoolGroup = {
    cardId: string
    name: string
    cost: number
    text?: string
    keywords: string[]
    token: boolean
    totalOwned: number
    selectedCount: number
    availableCount: number
    entries: DeckPoolEntry[]
  }

  type DeckPoolEntry = {
    owned: PlayerOwnedCard
    inDeck: boolean
    removable: boolean
    label: string
    note: string
  }

  const DECK_SIZE = 12
  const MAX_COPIES = 3
  const MAX_CHANGED_CARDS = 2

  let q = ''
  let deckOwnedCardIds: string[] = []
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

  $: ownedCards = (currentPlayer?.ownedCards ?? []).filter((owned): owned is PlayerOwnedCard => Boolean(owned?.ownedCardId && owned.cardId))
  $: ownedById = new Map(ownedCards.map((owned) => [owned.ownedCardId, owned]))
  $: initialDeckOwnedCardIds = deriveDeckOwnedCardIdsFromState(currentPlayer ?? null)

  $: if (currentState && currentPlayer && initializedForKey !== stateKey) {
    deckOwnedCardIds = [...initialDeckOwnedCardIds]
    statusMsg = '세션 상태 기준 덱을 불러왔어요.'
    errorMsg = ''
    initializedForKey = stateKey
  }

  $: deckEntries = deckOwnedCardIds.map((ownedCardId, index) => {
    const owned = ownedById.get(ownedCardId)
    return {
      ownedCardId,
      cardId: owned?.cardId ?? '',
      index,
    }
  })

  $: deckCountsByCardId = deckEntries.reduce<Record<string, number>>((acc, entry) => {
    if (!entry.cardId) return acc
    acc[entry.cardId] = (acc[entry.cardId] || 0) + 1
    return acc
  }, {})

  $: ownedCountsByCardId = ownedCards.reduce<Record<string, number>>((acc, owned) => {
    acc[owned.cardId] = (acc[owned.cardId] || 0) + 1
    return acc
  }, {})

  $: changedCards = countChangedCards(initialDeckOwnedCardIds, deckOwnedCardIds)
  $: currentDeckOwnedCardIdSet = new Set(initialDeckOwnedCardIds)
  $: deckOwnedCardIdSet = new Set(deckOwnedCardIds)
  $: groupedPool = buildPoolGroups(ownedCards, deckOwnedCardIdSet, deckCountsByCardId)
    .filter((group) => matchesQuery(group, q))
    .slice(0, 80)

  $: lockedDeckEntries = deckEntries.filter((entry) => {
    const owned = ownedById.get(entry.ownedCardId)
    return owned?.lockedInDeck
  })

  $: deckStatus = (() => {
    if (!currentPlayer) return '세션 플레이어 정보를 찾을 수 없어요.'
    if (currentPlayer.forgettingRequired) {
      return `보유 카드 제한 초과: ${currentPlayer.ownedCardCount ?? ownedCards.length}/${currentPlayer.maxOwnedCardCount ?? '—'}`
    }
    if (deckOwnedCardIds.length !== DECK_SIZE) {
      return `덱은 ${DECK_SIZE}장이어야 합니다. (현재 ${deckOwnedCardIds.length}장)`
    }
    if (changedCards > MAX_CHANGED_CARDS) {
      return `현재 변경 카드 수 ${changedCards}장 · 최대 ${MAX_CHANGED_CARDS}장까지 변경 가능`
    }
    return `변경 카드 수 ${changedCards}/${MAX_CHANGED_CARDS}`
  })()

  function deriveDeckOwnedCardIdsFromState(player: CharacterView | null): string[] {
    if (!player) return []
    if (Array.isArray(player.deckOwnedCardIds) && player.deckOwnedCardIds.length > 0) {
      return player.deckOwnedCardIds.map(String).slice(0, DECK_SIZE)
    }
    return []
  }

  function countChangedCards(currentDeck: string[], nextDeck: string[]) {
    const next = new Set(nextDeck)
    let changed = 0
    for (const ownedCardId of currentDeck) {
      if (!next.has(ownedCardId)) changed += 1
    }
    return changed
  }

  function buildPoolGroups(owned: PlayerOwnedCard[], inDeck: Set<string>, deckCounts: Record<string, number>): PoolGroup[] {
    const groups = new Map<string, PlayerOwnedCard[]>()
    for (const entry of owned) {
      const current = groups.get(entry.cardId) ?? []
      current.push(entry)
      groups.set(entry.cardId, current)
    }

    return [...groups.entries()]
      .map(([cardId, entries]) => {
        const card = $content.cardsById[cardId]
        const sortedEntries = [...entries].sort(compareOwnedCards)
        return {
          cardId,
          name: card?.name ?? cardId,
          cost: card?.cost ?? 0,
          text: card?.text,
          keywords: card?.keywords ?? [],
          token: Boolean(card?.token),
          totalOwned: entries.length,
          selectedCount: deckCounts[cardId] || 0,
          availableCount: Math.max(0, Math.min(MAX_COPIES, entries.length) - (deckCounts[cardId] || 0)),
          entries: sortedEntries.map((ownedCard) => {
            const active = inDeck.has(ownedCard.ownedCardId)
            return {
              owned: ownedCard,
              inDeck: active,
              removable: !ownedCard.lockedInDeck,
              label: buildOwnedLabel(ownedCard),
              note: buildOwnedNote(ownedCard),
            }
          }),
        }
      })
      .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
  }

  function matchesQuery(group: PoolGroup, query: string) {
    const needle = query.trim().toLowerCase()
    if (!needle) return true
    return group.cardId.toLowerCase().includes(needle)
      || group.name.toLowerCase().includes(needle)
      || (group.text || '').toLowerCase().includes(needle)
      || group.entries.some((entry) => entry.owned.ownedCardId.toLowerCase().includes(needle))
  }

  function compareOwnedCards(a: PlayerOwnedCard, b: PlayerOwnedCard) {
    return ownedSortRank(a) - ownedSortRank(b)
      || a.ownedCardId.localeCompare(b.ownedCardId)
  }

  function ownedSortRank(card: PlayerOwnedCard) {
    if (card.lockedInDeck) return 0
    if (card.weakened) return 1
    if (card.strengthened) return 2
    return 3
  }

  function buildOwnedLabel(card: PlayerOwnedCard) {
    const tags = []
    if (card.lockedInDeck) tags.push('잠금')
    if (card.strengthened) tags.push('강화')
    if (card.weakened) tags.push('약화')
    for (const modifier of card.modifiers ?? []) {
      tags.push(`${modifier.modifierId}${modifier.value ? ` ${modifier.value}` : ''}`)
    }
    return tags.length ? tags.join(' · ') : '일반 사본'
  }

  function buildOwnedNote(card: PlayerOwnedCard) {
    if (card.lockedInDeck) return '현재 덱에 남겨야 하는 사본'
    if (!card.forgettable && card.notForgettableReason) return card.notForgettableReason
    return card.ownedCardId
  }

  function canAddOwnedCard(ownedCardId: string) {
    const owned = ownedById.get(ownedCardId)
    if (!owned) return '현재 캐릭터가 보유하지 않은 카드입니다.'
    if (deckOwnedCardIdSet.has(ownedCardId)) return '이미 덱에 포함된 사본입니다.'
    if (deckOwnedCardIds.length >= DECK_SIZE) return `덱은 정확히 ${DECK_SIZE}장이어야 해요.`
    const copyCount = deckCountsByCardId[owned.cardId] || 0
    if (copyCount >= MAX_COPIES) return `같은 카드는 최대 ${MAX_COPIES}장까지 넣을 수 있어요. (${owned.cardId})`
    return ''
  }

  function addOwnedCard(ownedCardId: string) {
    const reason = canAddOwnedCard(ownedCardId)
    if (reason) {
      errorMsg = reason
      return
    }
    deckOwnedCardIds = [...deckOwnedCardIds, ownedCardId]
    statusMsg = ''
    errorMsg = ''
  }

  function canRemoveOwnedCard(entry: DeckEntry) {
    const owned = ownedById.get(entry.ownedCardId)
    if (!owned) return '사본 정보를 찾을 수 없어요.'
    if (owned.lockedInDeck) return '잠금된 사본은 현재 덱에서 제거할 수 없어요.'
    return ''
  }

  function removeOwnedCard(entry: DeckEntry) {
    const reason = canRemoveOwnedCard(entry)
    if (reason) {
      errorMsg = reason
      return
    }
    deckOwnedCardIds = deckOwnedCardIds.filter((ownedCardId, idx) => !(idx === entry.index && ownedCardId === entry.ownedCardId))
    statusMsg = ''
    errorMsg = ''
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
    if (currentPlayer?.forgettingRequired) {
      errorMsg = '카드 잊기 상태를 먼저 해결해야 덱을 수정할 수 있어요.'
      return
    }
    if (deckOwnedCardIds.length !== DECK_SIZE) {
      errorMsg = `덱은 ${DECK_SIZE}장이어야 합니다. (현재 ${deckOwnedCardIds.length}장)`
      return
    }
    if (changedCards > MAX_CHANGED_CARDS) {
      errorMsg = `현재 덱에서는 최대 ${MAX_CHANGED_CARDS}장만 교체할 수 있어요. (현재 ${changedCards}장 변경)`
      return
    }

    saving = true
    statusMsg = ''
    errorMsg = ''
    try {
      await updateSessionDeck(s.code, meId, deckOwnedCardIds, s.playerToken)
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
          <div class="hint">세션의 ownedCardId를 기준으로 덱을 수정하고 서버에 저장한다.</div>
        </div>
        <div class="row wrap" style="justify-content:flex-end">
          <input class="input" style="width:260px" bind:value={q} placeholder="검색 (이름/ID/텍스트/ownedCardId)" />
          <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
          <button class="btn" on:click={() => refreshState()}>세션 새로고침</button>
          <button class="btn primary" on:click={saveDeck} disabled={saving}>저장</button>
        </div>
      </div>

      <div class="spacer"></div>
      <div class="hint">제약: <b>보유 사본(ownedCardId)</b>만 추가 가능 · <b>deckEditable 노드</b>에서만 수정 가능 · <b>본인 덱</b>만 수정 가능 · <b>12장 정확히</b> · <b>동일 카드 최대 3장</b> · <b>현재 덱 기준 최대 2장 교체</b></div>

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
        <div class="cardTitle">카드 풀 ({groupedPool.length})</div>
        <div class="row wrap" style="justify-content:flex-end">
          <span class="badge">덱 {deckOwnedCardIds.length}/{DECK_SIZE}</span>
          <span class="badge">보유 {ownedCards.length}장</span>
          <span class="badge">{deckStatus}</span>
        </div>
      </div>
      <div class="spacer"></div>

      <div class="searchGrid ownedGrid">
        {#each groupedPool as group (group.cardId)}
          <div class="gcard stackCard">
            <div class="row" style="justify-content:space-between; align-items:flex-start">
              <div class="gcardTitle">{group.name}</div>
              <span class="badge">{group.cost}</span>
            </div>
            <div class="gcardSub mono">{group.cardId}</div>
            <div class="hint">보유 {group.totalOwned}장 · 덱 {group.selectedCount}장 · 추가 가능 {group.availableCount}장</div>
            <div class="gcardTags">
              {#if group.token}<span class="tag d">TOKEN</span>{/if}
              {#each group.keywords as keyword (keyword)}
                <span class="tag p">{keyword}</span>
              {/each}
            </div>
            <div class="ownedCopies">
              {#each group.entries as entry (entry.owned.ownedCardId)}
                <button
                  class:inDeck={entry.inDeck}
                  class:locked={entry.owned.lockedInDeck}
                  class="ownedCopy"
                  disabled={entry.inDeck}
                  on:click={() => addOwnedCard(entry.owned.ownedCardId)}
                >
                  <div class="row wrap" style="justify-content:space-between; gap:6px">
                    <span class="mono">{entry.owned.ownedCardId}</span>
                    {#if entry.inDeck}<span class="badge ok">덱 포함</span>{/if}
                  </div>
                  <div class="copyLabel">{entry.label}</div>
                  <div class="copyNote">{entry.note}</div>
                </button>
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
      <div class="hint">현재 덱 원본 기준 변경 {changedCards}/{MAX_CHANGED_CARDS}</div>
      {#if lockedDeckEntries.length}
        <div class="hint">잠금 사본: {lockedDeckEntries.map((entry) => entry.ownedCardId).join(', ')}</div>
      {/if}
      <div class="spacer"></div>
      <div class="deckList">
        {#each deckEntries as entry (entry.ownedCardId + ':' + entry.index)}
          {@const owned = ownedById.get(entry.ownedCardId)}
          <button class="deckEntry" class:locked={Boolean(owned?.lockedInDeck)} on:click={() => removeOwnedCard(entry)}>
            <div class="row wrap" style="justify-content:space-between; gap:8px">
              <div>
                <div class="mono">{entry.ownedCardId}</div>
                <div>{owned ? ($content.cardsById[owned.cardId]?.name ?? owned.cardId) : entry.cardId || '알 수 없는 카드'}</div>
              </div>
              <span class="badge">#{entry.index + 1}</span>
            </div>
            <div class="hint mono">{owned?.cardId ?? '미확인 cardId'}</div>
            {#if owned}
              <div class="copyLabel">{buildOwnedLabel(owned)}</div>
              {#if owned.lockedInDeck}
                <div class="copyNote">잠금된 사본이라 현재 덱에서 제거할 수 없어요.</div>
              {:else if !owned.forgettable && owned.notForgettableReason}
                <div class="copyNote">{owned.notForgettableReason}</div>
              {/if}
            {/if}
          </button>
        {/each}
        {#if deckEntries.length === 0}
          <div class="hint">덱 카드가 비어 있습니다. 카드 풀에서 추가해 주세요.</div>
        {/if}
      </div>
      <div class="spacer"></div>
      <div class="hint">보유 카드 종류 수: {Object.keys(ownedCountsByCardId).length}종</div>
    </div>
  </aside>
</section>

<style>
  .ownedGrid { align-items: start; }
  .stackCard { display:flex; flex-direction:column; gap:10px; }
  .ownedCopies { display:flex; flex-direction:column; gap:8px; }
  .ownedCopy, .deckEntry {
    width:100%;
    text-align:left;
    border:1px solid var(--line);
    background:rgba(255,255,255,.03);
    border-radius:12px;
    padding:10px;
    color:inherit;
  }
  .ownedCopy { cursor:pointer; }
  .ownedCopy:disabled { cursor:not-allowed; opacity:.72; }
  .ownedCopy.inDeck, .deckEntry.locked, .ownedCopy.locked {
    border-color: rgba(109,255,177,.35);
    background: rgba(109,255,177,.06);
  }
  .deckList { display:flex; flex-direction:column; gap:10px; }
  .copyLabel { font-size:13px; font-weight:600; margin-top:4px; }
  .copyNote { font-size:12px; color:var(--muted); margin-top:4px; }
</style>
