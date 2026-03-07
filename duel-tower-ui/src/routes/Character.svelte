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

  function statPercent(value: number, max = 100) {
    if (!Number.isFinite(value)) return 0
    return Math.max(0, Math.min(100, Math.round((value / max) * 100)))
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
    <section class="hero panel">
      <div class="heroTop">
        <div>
          <div class="name">{profile.name}</div>
          <div class="sub">{genderLabel(profile.gender)} · {profile.age == null ? '나이 불명' : `${profile.age}세`}</div>
        </div>
        <div class="chips">
          <span class="chip">성향: {profile.disposition}</span>
          <span class="chip">소원: {profile.wish}</span>
        </div>
      </div>
      <div class="quote">“{profile.oneLiner}”</div>
      <div class="story">{profile.story}</div>
    </section>

    <div class="grid">
      <section class="panel">
        <div class="panelTitle">생활 능력치</div>
        <div class="statList">
          <div class="statRow">
            <div class="statHead"><span>Physical</span><b>{profile.physical}</b></div>
            <div class="meter"><div style={`width:${statPercent(profile.physical)}%`}></div></div>
          </div>
          <div class="statRow">
            <div class="statHead"><span>Technique</span><b>{profile.technique}</b></div>
            <div class="meter"><div style={`width:${statPercent(profile.technique)}%`}></div></div>
          </div>
          <div class="statRow">
            <div class="statHead"><span>Sense</span><b>{profile.sense}</b></div>
            <div class="meter"><div style={`width:${statPercent(profile.sense)}%`}></div></div>
          </div>
          <div class="statRow">
            <div class="statHead"><span>Willpower</span><b>{profile.willpower}</b></div>
            <div class="meter"><div style={`width:${statPercent(profile.willpower)}%`}></div></div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panelTitle">전투 능력치</div>
        <div class="kv"><span>최대 HP</span><b>{profile.combatStats.maxHp}</b></div>
        <div class="kv"><span>최대 AP</span><b>{profile.combatStats.maxAp}</b></div>
        <div class="kv"><span>공격력</span><b>{profile.combatStats.attackPower}</b></div>
        <div class="kv"><span>치유력</span><b>{profile.combatStats.healPower}</b></div>
      </section>

      <section class="panel span2">
        <div class="panelTitle">패시브 / 덱 데이터</div>
        <div class="deckGrid">
          <div class="tile">
            <div class="th">Passive 1</div>
            <div>{profile.trait1 ?? '-'}</div>
          </div>
          <div class="tile">
            <div class="th">Passive 2</div>
            <div>{profile.trait2 ?? '-'}</div>
          </div>
          <div class="tile">
            <div class="th">Owned Cards</div>
            <div>{safeJsonSummary(profile.ownedCards)}</div>
          </div>
          <div class="tile">
            <div class="th">Current Skill Deck</div>
            <div>{safeJsonSummary(profile.currentSkillDeck)}</div>
          </div>
          <div class="tile span2">
            <div class="th">EX Card</div>
            <div>{safeJsonSummary(profile.exCard)}</div>
          </div>
        </div>
      </section>
    </div>
  {:else}
    <div class="hint">표시할 캐릭터가 없습니다.</div>
  {/if}
</PageSkeleton>

<style>
  .hero{margin-bottom:12px}
  .heroTop{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap}
  .name{font-size:28px;font-weight:900;line-height:1.1}
  .sub{margin-top:4px;color:var(--text-muted)}
  .chips{display:flex;flex-wrap:wrap;gap:8px;justify-content:flex-end}
  .quote{margin-top:10px;margin-bottom:10px;font-weight:700;font-size:16px}
  .story{color:var(--text-muted);line-height:1.6;white-space:pre-wrap}

  .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
  .span2{grid-column:1 / -1}
  .kv{display:flex;justify-content:space-between;gap:8px;padding:8px 0;border-bottom:1px solid rgba(255,255,255,.07)}
  .kv:last-child{border-bottom:0}
  .kv > span{color:var(--text-muted)}

  .statList{display:flex;flex-direction:column;gap:10px;margin-top:8px}
  .statRow{display:flex;flex-direction:column;gap:6px}
  .statHead{display:flex;justify-content:space-between;gap:8px}
  .statHead span{color:var(--text-muted)}
  .meter{height:8px;border-radius:999px;background:rgba(255,255,255,.08);overflow:hidden}
  .meter > div{height:100%;border-radius:999px;background:linear-gradient(90deg, rgba(114,220,255,.95), rgba(124,255,198,.95))}

  .deckGrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:8px}
  .tile{padding:10px;border:1px solid rgba(255,255,255,.08);border-radius:12px;background:rgba(7,14,25,.32)}

  @media (max-width: 900px){
    .grid{grid-template-columns:1fr}
    .deckGrid{grid-template-columns:1fr}
    .span2{grid-column:auto}
  }
</style>
