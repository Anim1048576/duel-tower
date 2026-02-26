<script lang="ts">
  import { onMount } from 'svelte'
  import { content, ensureCards } from '../stores/content'
  import { presets, createPreset, deletePreset, renamePreset, selectPreset, setDeck, setEx } from '../stores/presets'
  import { pushToast } from '../stores/log'

  let q = ''
  let nameDraft = ''

  onMount(async () => {
    await ensureCards()
  })

  $: selected = $presets.presets.find((p) => p.id === $presets.selectedId) ?? $presets.presets[0]
  $: if (selected && nameDraft === '') nameDraft = selected.name

  function iconFor(effectId?: string) {
    const e = (effectId || '').toUpperCase()
    const base = import.meta.env.BASE_URL
    if (e.includes('DMG') || e.includes('ATTACK')) return `${base}assets/skills/attack.png`
    if (e.includes('GUARD')) return `${base}assets/skills/guard.png`
    if (e.includes('RECOVER') || e.includes('HEAL')) return `${base}assets/skills/recovery.png`
    return null
  }

  function addToDeck(defId: string) {
    if (!selected) return
    if (selected.deck.length >= 12) {
      pushToast('덱 제한', '덱은 12장')
      return
    }
    setDeck(selected.id, [...selected.deck, defId])
  }

  function removeFromDeck(idx: number) {
    if (!selected) return
    const next = selected.deck.slice()
    next.splice(idx, 1)
    setDeck(selected.id, next)
  }

  function setExCard(defId: string) {
    if (!selected) return
    setEx(selected.id, defId)
  }

  function clearEx() {
    if (!selected) return
    setEx(selected.id, null)
  }

  $: filtered = $content.cards
    .filter((c) => {
      const s = q.trim().toLowerCase()
      if (!s) return true
      return (
        c.id.toLowerCase().includes(s) ||
        (c.name || '').toLowerCase().includes(s) ||
        (c.effectId || '').toLowerCase().includes(s) ||
        (c.text || '').toLowerCase().includes(s)
      )
    })
    .slice(0, 60)

  function exportJson() {
    if (!selected) return
    const payload = { name: selected.name, deck: selected.deck, ex: selected.ex }
    navigator.clipboard.writeText(JSON.stringify(payload, null, 2)).then(
      () => pushToast('프리셋 JSON 복사됨'),
      () => pushToast('복사 실패')
    )
  }
</script>

<section class="split">
  <div>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div>
          <div class="h2">프리셋</div>
          <div class="hint">카드 풀을 보고 덱(12)+EX(1)을 구성한다. (현재는 프론트 저장)</div>
        </div>
        <div class="row wrap" style="justify-content:flex-end">
          <input class="input" style="width:240px" bind:value={q} placeholder="검색 (이름/ID/효과)" />
          <button class="btn" on:click={() => ensureCards()}>카드 재로딩</button>
        </div>
      </div>

      {#if $content.lastError}
        <div class="spacer"></div>
        <div class="ti" style="border-color: rgba(255,93,116,.35); background: rgba(255,93,116,.06)">
          <div class="logHead">API</div>
          <div class="logBody">{$content.lastError}</div>
        </div>
      {/if}
    </div>

    <div class="spacer"></div>

    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">카드 풀 ({$content.cards.length})</div>
        <div class="row wrap" style="justify-content:flex-end">
          <span class="badge">덱 {selected?.deck.length ?? 0}/12</span>
          <span class="badge">EX {selected?.ex ? '1' : '0'}/1</span>
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
            <div class="gcardSub mono">{c.id} · {c.effectId ?? '-'}</div>
            <div class="gcardTags">
              {#if c.token}<span class="tag d">TOKEN</span>{/if}
              {#each c.keywords as k (k)}
                <span class="tag p">{k}</span>
              {/each}
            </div>
            {#if iconFor(c.effectId)}
              <img
                src={iconFor(c.effectId) || ''}
                alt=""
                style="position:absolute; right:10px; bottom:10px; width:26px; height:26px; opacity:.9"
              />
            {/if}
          </div>
        {/each}
      </div>

      <div class="hint">카드를 클릭하면 덱에 추가된다. (현재는 defId만 저장)</div>
    </div>
  </div>

  <aside>
    <div class="card">
      <div class="row wrap" style="justify-content:space-between">
        <div class="cardTitle">프리셋 목록</div>
        <button class="btn" on:click={() => { createPreset(); nameDraft=''; pushToast('프리셋 생성') }}>+ 새 프리셋</button>
      </div>
      <div class="spacer"></div>
      <div class="turnList">
        {#each $presets.presets as p (p.id)}
          <div class="turnItem" class:isActive={p.id === $presets.selectedId} on:click={() => { selectPreset(p.id); nameDraft = p.name }}>
            <div>
              <b>{p.name}</b>
              <div class="hint">덱 {p.deck.length}/12 · EX {p.ex ? '1' : '0'}</div>
            </div>
            <span class="badge mono">{p.id.slice(0, 6)}</span>
          </div>
        {/each}
      </div>

      {#if selected}
        <div class="spacer"></div>
        <label class="label">이름</label>
        <div class="row wrap">
          <input class="input" bind:value={nameDraft} />
          <button class="btn" on:click={() => renamePreset(selected.id, nameDraft)}>저장</button>
          <button class="btn danger" on:click={() => deletePreset(selected.id)}>삭제</button>
        </div>

        <div class="spacer"></div>
        <div class="kvs">
          <div class="kv">
            <div class="k">EX 카드</div>
            <div class="v mono">{selected.ex ?? '—'}</div>
            <div class="row wrap" style="margin-top:10px">
              <button class="btn" on:click={clearEx} disabled={!selected.ex}>비우기</button>
            </div>
          </div>
          <div class="kv">
            <div class="k">내보내기</div>
            <div class="v">클립보드로 JSON 복사</div>
            <div class="row wrap" style="margin-top:10px">
              <button class="btn" on:click={exportJson}>Export</button>
            </div>
          </div>
        </div>

        <div class="spacer"></div>
        <div class="cardTitle">덱 (클릭해서 제거)</div>
        <div class="spacer"></div>
        <div class="cardRow">
          {#each selected.deck as defId, i (defId + ':' + i)}
            <button class="btn" on:click={() => removeFromDeck(i)}>
              <span class="mono">{defId}</span>
              {#if $content.cardsById[defId]}
                · {$content.cardsById[defId].name}
              {/if}
            </button>
          {/each}
        </div>

        <div class="spacer"></div>
        <div class="cardTitle">EX 선택 (카드 클릭)</div>
        <div class="hint">카드 풀에서 카드를 <b>우클릭</b>할 수 있으면 좋겠지만, 일단 버튼으로 처리.</div>
        <div class="row wrap" style="margin-top:10px">
          {#each $content.cards.slice(0, 8) as c (c.id)}
            <button class="btn" on:click={() => setExCard(c.id)}>
              EX: {c.name}
            </button>
          {/each}
        </div>
      {/if}
    </div>
  </aside>
</section>
