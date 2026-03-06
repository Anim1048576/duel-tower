<script lang="ts">
  import { onMount } from 'svelte'
  import PageSkeleton from '../lib/PageSkeleton.svelte'
  import { explainApiError, getCharacterProfile, type CharacterProfileResponse } from '../lib/api'

  export let id = ''

  let loading = false
  let error = ''
  let profile: CharacterProfileResponse | null = null

  function genderLabel(gender: CharacterProfileResponse['gender']) {
    if (gender === 'MALE') return '남성'
    if (gender === 'FEMALE') return '여성'
    return '기타'
  }

  function safeJsonSummary(raw: unknown) {
    if (raw == null) return '-'
    if (Array.isArray(raw)) return `배열 ${raw.length}개 항목`
    if (typeof raw === 'object') return `객체 ${Object.keys(raw as Record<string, unknown>).length}개 키`

    if (typeof raw !== 'string') return String(raw)

    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return `배열 ${parsed.length}개 항목`
      if (parsed && typeof parsed === 'object') return `객체 ${Object.keys(parsed).length}개 키`
      return String(parsed)
    } catch {
      return raw
    }
  }

  $: numericId = Number(id)

  async function loadProfile() {
    if (!Number.isFinite(numericId) || numericId <= 0) {
      profile = null
      error = '유효한 캐릭터 ID가 필요합니다.'
      return
    }

    loading = true
    error = ''
    try {
      profile = await getCharacterProfile(numericId)
    } catch (e) {
      profile = null
      error = explainApiError(e)
    } finally {
      loading = false
    }
  }

  onMount(loadProfile)
  $: if (id) loadProfile()
</script>

<PageSkeleton title={`Character #${id || '-'}`} summary="백엔드 캐릭터 프로필 기준 상세 정보">
  {#if loading}
    <div class="hint">캐릭터 정보를 불러오는 중...</div>
  {:else if error}
    <div class="hint">오류: {error}</div>
  {:else if profile}
    <div class="grid">
      <section class="panel">
        <div class="panelTitle">기본 정보</div>
        <div class="kv"><span>이름</span><b>{profile.name}</b></div>
        <div class="kv"><span>성별</span><b>{genderLabel(profile.gender)}</b></div>
        <div class="kv"><span>나이</span><b>{profile.age == null ? "불명" : `${profile.age}`}</b></div>
        <div class="kv"><span>성향</span><b>{profile.disposition}</b></div>
        <div class="kv"><span>소원</span><b>{profile.wish}</b></div>
      </section>

      <section class="panel">
        <div class="panelTitle">생활 능력치</div>
        <div class="kv"><span>Physical</span><b>{profile.physical}</b></div>
        <div class="kv"><span>Technique</span><b>{profile.technique}</b></div>
        <div class="kv"><span>Sense</span><b>{profile.sense}</b></div>
        <div class="kv"><span>Willpower</span><b>{profile.willpower}</b></div>
      </section>

      <section class="panel">
        <div class="panelTitle">전투 능력치</div>
        <div class="kv"><span>최대 HP</span><b>{profile.combatStats.maxHp}</b></div>
        <div class="kv"><span>최대 AP</span><b>{profile.combatStats.maxAp}</b></div>
        <div class="kv"><span>공격력</span><b>{profile.combatStats.attackPower}</b></div>
        <div class="kv"><span>치유력</span><b>{profile.combatStats.healPower}</b></div>
      </section>

      <section class="panel">
        <div class="panelTitle">캐릭터 표현</div>
        <div class="quote">“{profile.oneLiner}”</div>
        <div class="story">{profile.story}</div>
      </section>

      <section class="panel">
        <div class="panelTitle">패시브 / 덱 데이터</div>
        <div class="kv"><span>Passive 1</span><b>{profile.trait1 ?? "-"}</b></div>
        <div class="kv"><span>Passive 2</span><b>{profile.trait2 ?? "-"}</b></div>
        <div class="kv"><span>Owned Cards</span><b>{safeJsonSummary(profile.ownedCards)}</b></div>
        <div class="kv"><span>Current Skill Deck</span><b>{safeJsonSummary(profile.currentSkillDeck)}</b></div>
        <div class="kv"><span>EX Card</span><b>{safeJsonSummary(profile.exCard)}</b></div>
      </section>
    </div>
  {:else}
    <div class="hint">표시할 캐릭터가 없습니다.</div>
  {/if}
</PageSkeleton>

<style>
  .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
  .kv{display:flex;justify-content:space-between;gap:8px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.07)}
  .kv:last-child{border-bottom:0}
  .kv > span{color:var(--text-muted)}
  .quote{margin-top:8px;margin-bottom:10px;font-weight:700}
  .story{color:var(--text-muted);line-height:1.5;white-space:pre-wrap}

  @media (max-width: 900px){
    .grid{grid-template-columns:1fr}
  }
</style>
