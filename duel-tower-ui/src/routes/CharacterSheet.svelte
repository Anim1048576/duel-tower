<script lang="ts">
  import { onMount } from 'svelte'
  import {
    createCharacterProfile,
    deleteCharacterProfile,
    explainApiError,
    listCardDefs,
    listCharacterProfiles,
    listPassives,
    type CardDef,
    type CharacterGender,
    type CharacterProfileRequest,
    type CharacterProfileResponse,
    type PassiveDefinition,
    updateCharacterProfile,
  } from '../lib/api'

  type AxisLawChaos = '질서' | '중립' | '혼돈'
  type AxisMoral = '선' | '중용' | '악'

  const DISPOSITION_AXIS_1: AxisLawChaos[] = ['질서', '중립', '혼돈']
  const DISPOSITION_AXIS_2: AxisMoral[] = ['선', '중용', '악']
  const emptyForm: CharacterProfileRequest = {
    name: '',
    gender: 'OTHER',
    age: null,
    wish: '',
    disposition: '중립/중용',
    oneLiner: '',
    story: '',
    physical: 1,
    technique: 1,
    sense: 1,
    willpower: 1,
    trait1: null,
    trait2: null,
    ownedCards: '[]',
    currentSkillDeck: null,
    exCard: '{}',
  }

  let loading = false
  let saving = false
  let deleting = false
  let error = ''
  let profiles: CharacterProfileResponse[] = []
  let cardDefs: CardDef[] = []
  let passiveDefs: PassiveDefinition[] = []
  let selectedId: number | null = null
  let form: CharacterProfileRequest = { ...emptyForm }

  let dispositionAxis1: AxisLawChaos = '중립'
  let dispositionAxis2: AxisMoral = '중용'
  let noAge = true
  let selectedTraits: string[] = []
  let selectedOwnedCards: string[] = []
  let selectedExCardId = ''

  $: cardOptions = cardDefs.map((card) => ({ id: card.id, label: `${card.id} · ${card.name}` }))
  $: passiveOptions = passiveDefs.map((passive) => ({
    id: passive.id,
    label: `${passive.id} · ${passive.name}`,
    description: passive.description ?? '',
  }))

  function cloneForm(p: CharacterProfileResponse): CharacterProfileRequest {
    return {
      name: p.name,
      gender: p.gender,
      age: p.age,
      wish: p.wish,
      disposition: p.disposition,
      oneLiner: p.oneLiner,
      story: p.story,
      physical: p.physical,
      technique: p.technique,
      sense: p.sense,
      willpower: p.willpower,
      trait1: p.trait1,
      trait2: p.trait2,
      ownedCards: p.ownedCards,
      currentSkillDeck: p.currentSkillDeck,
      exCard: p.exCard,
    }
  }

  function parseDisposition(disposition: string): [AxisLawChaos, AxisMoral] {
    const [raw1, raw2] = disposition.split('/').map((v) => v.trim())
    const axis1 = DISPOSITION_AXIS_1.includes(raw1 as AxisLawChaos) ? (raw1 as AxisLawChaos) : '중립'
    const axis2 = DISPOSITION_AXIS_2.includes(raw2 as AxisMoral) ? (raw2 as AxisMoral) : '중용'
    return [axis1, axis2]
  }

  function parseIdArray(raw: string): string[] {
    try {
      const parsed = JSON.parse(raw)
      if (!Array.isArray(parsed)) return []
      return parsed.map(String).filter((id) => id.trim().length > 0)
    } catch {
      return []
    }
  }

  function parseExCardId(raw: string): string {
    try {
      const parsed = JSON.parse(raw)
      if (typeof parsed === 'string') return parsed
      if (parsed && typeof parsed === 'object' && typeof parsed.id === 'string') return parsed.id
      return ''
    } catch {
      return ''
    }
  }

  function syncUiFromForm() {
    const [a1, a2] = parseDisposition(form.disposition)
    dispositionAxis1 = a1
    dispositionAxis2 = a2
    noAge = form.age == null
    selectedTraits = [form.trait1, form.trait2].filter((v): v is string => Boolean(v && v.trim()))
    selectedOwnedCards = parseIdArray(form.ownedCards)
    selectedExCardId = parseExCardId(form.exCard)
  }

  function syncFormFromUi() {
    form = {
      ...form,
      disposition: `${dispositionAxis1}/${dispositionAxis2}`,
      age: noAge ? null : form.age ?? 0,
      trait1: selectedTraits[0] ?? null,
      trait2: selectedTraits[1] ?? null,
      ownedCards: JSON.stringify(selectedOwnedCards),
      exCard: selectedExCardId ? JSON.stringify({ id: selectedExCardId }) : '{}',
    }
  }

  async function refresh() {
    loading = true
    error = ''
    try {
      const [profilesRes, cardsRes, passivesRes] = await Promise.all([listCharacterProfiles(), listCardDefs(), listPassives()])
      profiles = profilesRes
      cardDefs = cardsRes
      passiveDefs = passivesRes
      if (selectedId && !profiles.some((p) => p.id === selectedId)) {
        selectedId = null
        form = { ...emptyForm }
        syncUiFromForm()
      }
    } catch (e) {
      error = explainApiError(e)
    } finally {
      loading = false
    }
  }

  function selectProfile(id: number) {
    const found = profiles.find((p) => p.id === id)
    if (!found) return
    selectedId = found.id
    form = cloneForm(found)
    syncUiFromForm()
  }

  function resetToNew() {
    selectedId = null
    form = { ...emptyForm }
    syncUiFromForm()
  }

  async function saveProfile() {
    saving = true
    error = ''
    syncFormFromUi()
    try {
      if (selectedId) {
        await updateCharacterProfile(selectedId, form)
      } else {
        const created = await createCharacterProfile(form)
        selectedId = created.id
      }
      await refresh()
      if (selectedId) {
        const found = profiles.find((p) => p.id === selectedId)
        if (found) {
          form = cloneForm(found)
          syncUiFromForm()
        }
      }
    } catch (e) {
      error = explainApiError(e)
    } finally {
      saving = false
    }
  }

  async function removeProfile() {
    if (!selectedId) return
    deleting = true
    error = ''
    try {
      await deleteCharacterProfile(selectedId)
      resetToNew()
      await refresh()
    } catch (e) {
      error = explainApiError(e)
    } finally {
      deleting = false
    }
  }

  function updateNumberField<K extends 'age' | 'physical' | 'technique' | 'sense' | 'willpower'>(key: K, value: string) {
    const parsed = Number(value)
    form = { ...form, [key]: Number.isFinite(parsed) ? parsed : 0 }
  }

  function updateGender(value: string) {
    form = { ...form, gender: value as CharacterGender }
  }

  function toggleTrait(trait: string) {
    if (selectedTraits.includes(trait)) {
      selectedTraits = selectedTraits.filter((t) => t !== trait)
      syncFormFromUi()
      return
    }
    if (selectedTraits.length >= 2) return
    selectedTraits = [...selectedTraits, trait]
    syncFormFromUi()
  }

  function toggleOwnedCardSelection(id: string) {
    const exists = selectedOwnedCards.includes(id)
    selectedOwnedCards = exists ? selectedOwnedCards.filter((v) => v !== id) : [...selectedOwnedCards, id]
    syncFormFromUi()
  }

  function updateExCard(value: string) {
    selectedExCardId = value
    syncFormFromUi()
  }

  function toggleNoAge(checked: boolean) {
    noAge = checked
    if (checked) form = { ...form, age: null }
    else if (form.age == null) form = { ...form, age: 0 }
    syncFormFromUi()
  }

  onMount(async () => {
    await refresh()
    syncUiFromForm()
  })
</script>

<div class="page">
  <section class="panel listPanel">
    <div class="row top">
      <div class="panelTitle">Character 목록</div>
      <button class="btn" on:click={refresh} disabled={loading}>새로고침</button>
    </div>

    {#if loading}
      <div class="hint">로딩 중...</div>
    {:else if profiles.length === 0}
      <div class="hint">등록된 캐릭터가 없습니다.</div>
    {:else}
      <div class="list">
        {#each profiles as p (p.id)}
          <button class="item" class:active={p.id === selectedId} on:click={() => selectProfile(p.id)}>
            <b>#{p.id} {p.name}</b>
            <span>{p.gender} · {p.age == null ? '나이 미지정' : `${p.age}세`}</span>
          </button>
        {/each}
      </div>
    {/if}

    <button class="btn primary" on:click={resetToNew}>신규 캐릭터 작성</button>
  </section>

  <section class="panel formPanel">
    <div class="row top">
      <div class="panelTitle">Character Sheet</div>
      <div class="row">
        {#if selectedId}
          <button class="btn danger" on:click={removeProfile} disabled={deleting || saving}>삭제</button>
        {/if}
        <button class="btn primary" on:click={saveProfile} disabled={saving || deleting}>{selectedId ? '수정 저장' : '신규 저장'}</button>
      </div>
    </div>

    {#if error}
      <div class="hint">오류: {error}</div>
    {/if}

    <div class="formGrid">
      <label>이름 <input class="input" bind:value={form.name} /></label>
      <label>성별
        <select class="input" value={form.gender} on:change={(e) => updateGender((e.currentTarget as HTMLSelectElement).value)}>
          <option value="MALE">MALE</option>
          <option value="FEMALE">FEMALE</option>
          <option value="OTHER">OTHER</option>
        </select>
      </label>

      <label>나이
        <input class="input" type="number" value={form.age ?? ''} disabled={noAge} on:input={(e) => updateNumberField('age', (e.currentTarget as HTMLInputElement).value)} />
      </label>
      <label class="checkboxLabel">
        <span>나이 미지정</span>
        <input type="checkbox" checked={noAge} on:change={(e) => toggleNoAge((e.currentTarget as HTMLInputElement).checked)} />
      </label>

      <label>성향(질서/중립/혼돈)
        <select class="input" bind:value={dispositionAxis1} on:change={() => syncFormFromUi()}>
          {#each DISPOSITION_AXIS_1 as axis}
            <option value={axis}>{axis}</option>
          {/each}
        </select>
      </label>
      <label>성향(선/중용/악)
        <select class="input" bind:value={dispositionAxis2} on:change={() => syncFormFromUi()}>
          {#each DISPOSITION_AXIS_2 as axis}
            <option value={axis}>{axis}</option>
          {/each}
        </select>
      </label>

      <label class="full">소원 <input class="input" bind:value={form.wish} /></label>
      <label class="full">한줄 대사 <input class="input" bind:value={form.oneLiner} /></label>
      <label class="full">스토리 <textarea class="textarea" rows="4" bind:value={form.story}></textarea></label>

      <label>Physical <input class="input" type="number" value={form.physical} on:input={(e) => updateNumberField('physical', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Technique <input class="input" type="number" value={form.technique} on:input={(e) => updateNumberField('technique', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Sense <input class="input" type="number" value={form.sense} on:input={(e) => updateNumberField('sense', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Willpower <input class="input" type="number" value={form.willpower} on:input={(e) => updateNumberField('willpower', (e.currentTarget as HTMLInputElement).value)} /></label>

      <div class="full block">
        <div class="blockTitle">캐릭터 패시브 (최대 2개)</div>
        <div class="chipWrap">
          {#if passiveOptions.length === 0}
            <div class="hint">등록된 패시브가 없습니다.</div>
          {:else}
            {#each passiveOptions as passive}
              <button
                type="button"
                class="chip"
                class:active={selectedTraits.includes(passive.id)}
                disabled={!selectedTraits.includes(passive.id) && selectedTraits.length >= 2}
                on:click={() => toggleTrait(passive.id)}
                title={passive.description}
              >
                {passive.label}
              </button>
            {/each}
          {/if}
        </div>
      </div>

      <div class="full block">
        <div class="blockTitle">보유 카드 선택</div>
        <div class="checkGrid">
          {#each cardOptions as card}
            <label class="checkItem">
              <input type="checkbox" checked={selectedOwnedCards.includes(card.id)} on:change={() => toggleOwnedCardSelection(card.id)} />
              <span>{card.label}</span>
            </label>
          {/each}
        </div>
      </div>


      <label class="full">EX 카드
        <select class="input" value={selectedExCardId} on:change={(e) => updateExCard((e.currentTarget as HTMLSelectElement).value)}>
          <option value="">선택 안 함</option>
          {#each cardOptions as card}
            <option value={card.id}>{card.label}</option>
          {/each}
        </select>
      </label>
    </div>
  </section>
</div>

<style>
  .page{display:grid;grid-template-columns:320px 1fr;gap:12px}
  .top{justify-content:space-between}
  .listPanel{display:flex;flex-direction:column;gap:10px}
  .list{display:flex;flex-direction:column;gap:8px;max-height:520px;overflow:auto}
  .item{display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding:10px;border-radius:12px;border:1px solid var(--line);background:rgba(255,255,255,.02);color:var(--text)}
  .item.active{border-color:rgba(114,220,255,.5);background:rgba(114,220,255,.1)}
  .item span{color:var(--text-muted);font-size:12px}
  .formPanel{display:flex;flex-direction:column;gap:12px}
  .formGrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
  label{display:flex;flex-direction:column;gap:6px;font-size:12px;color:var(--text-muted)}
  .checkboxLabel{justify-content:flex-end}
  .checkboxLabel input{width:16px;height:16px}
  .full{grid-column:1 / -1}
  .block{display:flex;flex-direction:column;gap:8px}
  .blockTitle{font-size:12px;color:var(--text-muted)}
  .chipWrap{display:flex;flex-wrap:wrap;gap:8px}
  .chip{border:1px solid var(--line);background:rgba(255,255,255,.02);color:var(--text);border-radius:999px;padding:6px 10px;font-size:12px}
  .chip.active{border-color:rgba(114,220,255,.6);background:rgba(114,220,255,.14)}
  .chip:disabled{opacity:.45}
  .checkGrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;max-height:220px;overflow:auto;padding:8px;border:1px solid var(--line);border-radius:10px}
  .checkItem{display:flex;align-items:center;gap:8px;color:var(--text);font-size:12px}

  @media (max-width: 900px){
    .page{grid-template-columns:1fr}
    .formGrid{grid-template-columns:1fr}
    .checkGrid{grid-template-columns:1fr}
  }
</style>
