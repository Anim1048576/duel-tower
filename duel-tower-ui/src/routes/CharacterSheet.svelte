<script lang="ts">
  import { onMount } from 'svelte'
  import {
    createCharacterProfile,
    deleteCharacterProfile,
    explainApiError,
    listCharacterProfiles,
    type CharacterGender,
    type CharacterProfileRequest,
    type CharacterProfileResponse,
    updateCharacterProfile,
  } from '../lib/api'

  const emptyForm: CharacterProfileRequest = {
    name: '',
    gender: 'OTHER',
    age: 17,
    wish: '',
    disposition: '',
    oneLiner: '',
    story: '',
    physical: 1,
    technique: 1,
    sense: 1,
    willpower: 1,
    trait1: '',
    trait2: '',
    ownedCards: '[]',
    currentSkillDeck: '[]',
    exCard: '{}',
  }

  let loading = false
  let saving = false
  let deleting = false
  let error = ''
  let profiles: CharacterProfileResponse[] = []
  let selectedId: number | null = null
  let form: CharacterProfileRequest = { ...emptyForm }

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

  async function refresh() {
    loading = true
    error = ''
    try {
      profiles = await listCharacterProfiles()
      if (selectedId && !profiles.some((p) => p.id === selectedId)) {
        selectedId = null
        form = { ...emptyForm }
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
  }

  function resetToNew() {
    selectedId = null
    form = { ...emptyForm }
  }

  async function saveProfile() {
    saving = true
    error = ''
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
        if (found) form = cloneForm(found)
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
    const next = value as CharacterGender
    form = { ...form, gender: next }
  }

  onMount(refresh)
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
            <span>{p.gender} · {p.age}세</span>
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
      <label>나이 <input class="input" type="number" value={form.age} on:input={(e) => updateNumberField('age', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>성향 <input class="input" bind:value={form.disposition} /></label>
      <label class="full">소원 <input class="input" bind:value={form.wish} /></label>
      <label class="full">한줄 대사 <input class="input" bind:value={form.oneLiner} /></label>
      <label class="full">스토리 <textarea class="textarea" rows="4" bind:value={form.story}></textarea></label>

      <label>Physical <input class="input" type="number" value={form.physical} on:input={(e) => updateNumberField('physical', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Technique <input class="input" type="number" value={form.technique} on:input={(e) => updateNumberField('technique', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Sense <input class="input" type="number" value={form.sense} on:input={(e) => updateNumberField('sense', (e.currentTarget as HTMLInputElement).value)} /></label>
      <label>Willpower <input class="input" type="number" value={form.willpower} on:input={(e) => updateNumberField('willpower', (e.currentTarget as HTMLInputElement).value)} /></label>

      <label>Trait 1 <input class="input" bind:value={form.trait1} /></label>
      <label>Trait 2 <input class="input" bind:value={form.trait2} /></label>
      <label class="full">Owned Cards(JSON) <textarea class="textarea" rows="3" bind:value={form.ownedCards}></textarea></label>
      <label class="full">Current Skill Deck(JSON) <textarea class="textarea" rows="3" bind:value={form.currentSkillDeck}></textarea></label>
      <label class="full">EX Card(JSON) <textarea class="textarea" rows="3" bind:value={form.exCard}></textarea></label>
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
  .full{grid-column:1 / -1}

  @media (max-width: 900px){
    .page{grid-template-columns:1fr}
    .formGrid{grid-template-columns:1fr}
  }
</style>
