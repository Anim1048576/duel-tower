<script lang="ts">
  import { onMount } from 'svelte'
  import {
    createCharacter,
    deleteCharacter,
    getCharacter,
    getCharacterCreateOptions,
    previewCharacterCombatStats,
    replaceCharacterCurrentSkillDeck,
    updateCharacter,
  } from '../lib/api/characters'
  import type {
    CharacterCombatStats,
    CharacterCreateOption,
    CharacterGender,
    CharacterProfileRequest,
    CharacterProfileResponse,
    OwnedCardRequest,
  } from '../lib/api/characterTypes'
  import { listCards, listPassives } from '../lib/api/content'
  import type { CardDefinition, PassiveDefinition } from '../lib/api/contentTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders, resolveRouteMatch } from '../lib/navigation'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    resolveRouteFirstSelection,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type CharacterDetailMode = 'create' | 'edit'

  type CharacterFormState = {
    name: string
    quote: string
    wish: string
    backstory: string
    age: string
    gender: CharacterGender | ''
    alignmentOrderChaos: string
    alignmentGoodEvil: string
    lifeStats: {
      body: string
      technique: string
      sense: string
      will: string
    }
    selectedTraitIds: string[]
    selectedHiddenTraitIds: string[]
    ownedSkillCardIds: string[]
    currentSkillDeckOwnedCardIds: string[]
    selectedExCardId: string
  }

  type CharacterNavigationState = {
    characterFeedback?: string
  }

  const fallbackGenderOptions: CharacterCreateOption[] = [
    { id: 'MALE', label: '남성', description: '' },
    { id: 'FEMALE', label: '여성', description: '' },
    { id: 'OTHER', label: '성별불명', description: '' },
  ]
  const fallbackOrderAxisOptions: CharacterCreateOption[] = [
    { id: '질서', label: '질서', description: '' },
    { id: '중립', label: '중립', description: '' },
    { id: '혼돈', label: '혼돈', description: '' },
  ]
  const fallbackMoralAxisOptions: CharacterCreateOption[] = [
    { id: '선', label: '선', description: '' },
    { id: '중용', label: '중용', description: '' },
    { id: '악', label: '악', description: '' },
  ]

  function createEmptyFormState(): CharacterFormState {
    return {
      name: '',
      quote: '',
      wish: '',
      backstory: '',
      age: '',
      gender: '',
      alignmentOrderChaos: '질서',
      alignmentGoodEvil: '선',
      lifeStats: {
        // 백엔드 DTO가 null을 허용하지 않는 생활 스테이터스 필드라 기본값은 0 문자열로 둔다.
        body: '0',
        technique: '0',
        sense: '0',
        will: '0',
      },
      selectedTraitIds: [],
      selectedHiddenTraitIds: [],
      ownedSkillCardIds: [],
      currentSkillDeckOwnedCardIds: [],
      selectedExCardId: '',
    }
  }

  function getCharacterRouteState() {
    if (typeof window === 'undefined') {
      return { mode: 'edit' as CharacterDetailMode, routeId: null as string | null }
    }

    const match = resolveRouteMatch(window.location.pathname)
    if (match?.page.key === 'character-create') {
      return { mode: 'create' as CharacterDetailMode, routeId: null as string | null }
    }
    if (match?.page.key !== 'character-detail') {
      return { mode: 'edit' as CharacterDetailMode, routeId: null as string | null }
    }
    return { mode: 'edit' as CharacterDetailMode, routeId: match.params.id ?? null }
  }

  function isCharacterApiId(value: string | null | undefined): value is string {
    return typeof value === 'string' && /^\d+$/.test(value.trim())
  }

  function parseNullableNumber(value: string) {
    const normalized = value.trim()
    if (!normalized) return null
    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
  }

  function parseRequiredNumber(value: string) {
    const parsed = parseNullableNumber(value)
    return parsed ?? null
  }

  function normalizeText(value: string) {
    return value.trim()
  }

  function normalizeOptionalText(value: string) {
    const normalized = value.trim()
    return normalized ? normalized : null
  }

  function buildDisposition(formState: CharacterFormState) {
    return `${formState.alignmentOrderChaos}/${formState.alignmentGoodEvil}`
  }

  function splitDisposition(disposition: string | null | undefined) {
    const [orderAxis, moralAxis] = String(disposition ?? '').split('/').map((item) => item.trim())
    return {
      orderAxis: orderAxis || '질서',
      moralAxis: moralAxis || '선',
    }
  }

  function makeOwnedCardId(cardId: string) {
    return `owned-${cardId}`
  }

  function buildOwnedCards(formState: CharacterFormState): OwnedCardRequest[] {
    return formState.ownedSkillCardIds.map((cardId) => ({
      ownedCardId: makeOwnedCardId(cardId),
      cardId,
      modifiers: [],
      strengthened: false,
      weakened: false,
      lockedInDeck: formState.currentSkillDeckOwnedCardIds.includes(makeOwnedCardId(cardId)),
      forgettable: true,
      notForgettableReason: null,
    }))
  }

  function buildCharacterPayload(formState: CharacterFormState): CharacterProfileRequest {
    return {
      name: normalizeText(formState.name),
      gender: formState.gender || null,
      age: parseNullableNumber(formState.age),
      wish: normalizeText(formState.wish),
      disposition: buildDisposition(formState),
      oneLiner: normalizeText(formState.quote),
      story: normalizeText(formState.backstory),
      physical: parseRequiredNumber(formState.lifeStats.body),
      technique: parseRequiredNumber(formState.lifeStats.technique),
      sense: parseRequiredNumber(formState.lifeStats.sense),
      willpower: parseRequiredNumber(formState.lifeStats.will),
      trait1: formState.selectedTraitIds[0] ?? null,
      trait2: formState.selectedTraitIds[1] ?? null,
      hiddenTraitIds: [...formState.selectedHiddenTraitIds],
      ownedCardList: buildOwnedCards(formState),
      exCardId: formState.selectedExCardId,
    }
  }

  function resolveCurrentDeckOwnedIds(character: CharacterProfileResponse) {
    const available = character.ownedCardList.map((ownedCard) => ({
      ownedCardId: ownedCard.ownedCardId,
      cardId: ownedCard.cardId,
      used: false,
    }))
    const resolved: string[] = []
    for (const cardId of character.currentSkillDeckPreviewCardIds) {
      const match = available.find((entry) => !entry.used && entry.cardId === cardId)
      if (match) {
        match.used = true
        resolved.push(match.ownedCardId)
      }
    }
    return resolved
  }

  function createFormStateFromResponse(character: CharacterProfileResponse): CharacterFormState {
    const disposition = splitDisposition(character.disposition)
    return {
      name: character.name,
      gender: character.gender,
      age: character.age === null ? '' : String(character.age),
      wish: character.wish,
      alignmentOrderChaos: disposition.orderAxis,
      alignmentGoodEvil: disposition.moralAxis,
      quote: character.oneLiner,
      backstory: character.story,
      lifeStats: {
        body: String(character.physical),
        technique: String(character.technique),
        sense: String(character.sense),
        will: String(character.willpower),
      },
      selectedTraitIds: [character.trait1, character.trait2].filter((value): value is string => Boolean(value)),
      selectedHiddenTraitIds: [...character.hiddenTraitIds],
      ownedSkillCardIds: character.ownedCardList.map((ownedCard) => ownedCard.cardId),
      currentSkillDeckOwnedCardIds: resolveCurrentDeckOwnedIds(character),
      selectedExCardId: character.exCardId ?? '',
    }
  }

  function stringifyDeveloperPayload(characterResponse: CharacterProfileResponse | null, formState: CharacterFormState) {
    if (characterResponse) {
      return JSON.stringify(
        {
          ...buildCharacterPayload(createFormStateFromResponse(characterResponse)),
          ownedCardList: characterResponse.ownedCardList,
        },
        null,
        2,
      )
    }
    return JSON.stringify(buildCharacterPayload(formState), null, 2)
  }

  function parseDeveloperJson(text: string): CharacterProfileRequest {
    try {
      return JSON.parse(text) as CharacterProfileRequest
    } catch {
      throw new Error('JSON 형식이 올바르지 않습니다.')
    }
  }

  function getGenderLabel(value: CharacterGender | '') {
    return genderOptions.find((option) => option.id === value)?.label ?? '성별불명'
  }

  function getCardLabel(cardId: string) {
    const card = [...skillCards, ...exCards].find((candidate) => candidate.id === cardId)
    return card ? `${card.name} (${card.id})` : cardId
  }

  function getOwnedDeckCandidates() {
    return form.ownedSkillCardIds.map((cardId) => ({
      ownedCardId: makeOwnedCardId(cardId),
      cardId,
      label: getCardLabel(cardId),
    }))
  }

  function ensureDeckSubset(nextOwnedSkillCardIds: string[]) {
    const allowedOwnedIds = new Set(nextOwnedSkillCardIds.map(makeOwnedCardId))
    form.currentSkillDeckOwnedCardIds = form.currentSkillDeckOwnedCardIds.filter((ownedCardId) =>
      allowedOwnedIds.has(ownedCardId),
    )
  }

  function toggleOwnedSkillCard(cardId: string) {
    if (form.ownedSkillCardIds.includes(cardId)) {
      form.ownedSkillCardIds = form.ownedSkillCardIds.filter((id) => id !== cardId)
    } else {
      form.ownedSkillCardIds = [...form.ownedSkillCardIds, cardId]
    }
    ensureDeckSubset(form.ownedSkillCardIds)
  }

  function toggleCurrentDeckCard(ownedCardId: string) {
    if (form.currentSkillDeckOwnedCardIds.includes(ownedCardId)) {
      form.currentSkillDeckOwnedCardIds = form.currentSkillDeckOwnedCardIds.filter((id) => id !== ownedCardId)
    } else {
      form.currentSkillDeckOwnedCardIds = [...form.currentSkillDeckOwnedCardIds, ownedCardId]
    }
  }

  function toggleTrait(traitId: string) {
    if (form.selectedTraitIds.includes(traitId)) {
      form.selectedTraitIds = form.selectedTraitIds.filter((id) => id !== traitId)
    } else if (form.selectedTraitIds.length < 2) {
      form.selectedTraitIds = [...form.selectedTraitIds, traitId]
    }
  }

  function toggleHiddenTrait(traitId: string) {
    if (form.selectedHiddenTraitIds.includes(traitId)) {
      form.selectedHiddenTraitIds = form.selectedHiddenTraitIds.filter((id) => id !== traitId)
    } else {
      form.selectedHiddenTraitIds = [...form.selectedHiddenTraitIds, traitId]
    }
  }

  function getNavigationFeedback() {
    if (typeof window === 'undefined') return null
    const state = history.state as CharacterNavigationState | null
    const feedback = state?.characterFeedback ?? null
    if (feedback) history.replaceState({}, '', window.location.pathname)
    return feedback
  }

  function navigateTo(path: string, mode: 'push' | 'replace' = 'push', state: CharacterNavigationState = {}) {
    if (typeof window === 'undefined') return
    history[mode === 'replace' ? 'replaceState' : 'pushState'](state, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  const routeState = getCharacterRouteState()
  const isCreateMode = routeState.mode === 'create'
  const characterSelection = !isCreateMode
    ? resolveRouteFirstSelection({
        routeValue: routeState.routeId,
        handoffValue: readSelectionHandoff(selectionHandoffKeys.characterId),
        isValid: isCharacterApiId,
      })
    : { value: null, source: 'none' as const, missingRouteValue: null }

  const requestedCharacterId = isCreateMode ? null : characterSelection.value
  const missingCharacterId = isCreateMode ? null : characterSelection.missingRouteValue

  let loading = $state(!isCreateMode && requestedCharacterId !== null)
  let saving = $state(false)
  let deleting = $state(false)
  let previewLoading = $state(false)
  let optionsLoading = $state(false)
  let cardLoading = $state(false)
  let traitLoading = $state(false)
  let developerUiOpen = $state(false)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let saveErrorMessage = $state<string | null>(null)
  let deleteErrorMessage = $state<string | null>(null)
  let optionErrorMessage = $state<string | null>(null)
  let cardErrorMessage = $state<string | null>(null)
  let traitErrorMessage = $state<string | null>(null)
  let previewErrorMessage = $state<string | null>(null)
  let saveMessage = $state<string | null>(null)
  const initialFormState = createEmptyFormState()
  let character = $state<CharacterProfileResponse | null>(null)
  let form = $state<CharacterFormState>(initialFormState)
  let developerJsonText = $state(stringifyDeveloperPayload(null, initialFormState))
  let previewBattleStats = $state<CharacterCombatStats | null>(null)
  let genderOptions = $state<CharacterCreateOption[]>(fallbackGenderOptions)
  let orderAxisOptions = $state<CharacterCreateOption[]>(fallbackOrderAxisOptions)
  let moralAxisOptions = $state<CharacterCreateOption[]>(fallbackMoralAxisOptions)
  let hiddenTraitOptions = $state<CharacterCreateOption[]>([])
  let traitOptions = $state<PassiveDefinition[]>([])
  let skillCards = $state<CardDefinition[]>([])
  let exCards = $state<CardDefinition[]>([])

  const displayedTitle = $derived.by(() =>
    isCreateMode ? form.name.trim() || '새 캐릭터 생성' : character?.name || form.name.trim() || '캐릭터 정보',
  )
  const displayedSummary = $derived.by(() => form.quote.trim() || '캐릭터의 기본 정보와 카드 구성을 입력합니다.')
  const selectedTraitCount = $derived.by(() => form.selectedTraitIds.length)
  const selectedOwnedCardCount = $derived.by(() => form.ownedSkillCardIds.length)
  const currentDeckCandidates = $derived.by(() => getOwnedDeckCandidates())

  function syncCharacterState(response: CharacterProfileResponse) {
    character = response
    form = createFormStateFromResponse(response)
    previewBattleStats = response.combatStats
    developerJsonText = stringifyDeveloperPayload(response, form)
    setSelectionHandoff(selectionHandoffKeys.characterId, String(response.id))
  }

  async function loadCharacterDetail(id: string) {
    loading = true
    notFound = false
    errorMessage = null
    saveErrorMessage = null
    deleteErrorMessage = null
    try {
      const response = await getCharacter(id)
      syncCharacterState(response)
    } catch (error) {
      character = null
      if (error instanceof ApiError && error.code === 'not_found') {
        notFound = true
      } else {
        errorMessage = getApiErrorMessage(error, '캐릭터 정보를 불러오지 못했습니다.')
      }
    } finally {
      loading = false
    }
  }

  async function loadCreationReferences() {
    optionsLoading = true
    cardLoading = true
    traitLoading = true
    optionErrorMessage = null
    cardErrorMessage = null
    traitErrorMessage = null

    try {
      const options = await getCharacterCreateOptions()
      genderOptions = options.genderOptions.length ? options.genderOptions : fallbackGenderOptions
      orderAxisOptions = options.orderAxisOptions.length ? options.orderAxisOptions : fallbackOrderAxisOptions
      moralAxisOptions = options.moralAxisOptions.length ? options.moralAxisOptions : fallbackMoralAxisOptions
      hiddenTraitOptions = options.hiddenTraitOptions
    } catch (error) {
      optionErrorMessage = getApiErrorMessage(error, '특성 목록을 불러오지 못했습니다.')
    } finally {
      optionsLoading = false
    }

    try {
      const [skillResponse, exResponse] = await Promise.all([listCards({ type: 'SKILL' }), listCards({ type: 'EX' })])
      skillCards = skillResponse
      exCards = exResponse
    } catch (error) {
      cardErrorMessage = getApiErrorMessage(error, '카드 목록을 불러오지 못했습니다.')
    } finally {
      cardLoading = false
    }

    try {
      traitOptions = await listPassives()
    } catch (error) {
      traitErrorMessage = getApiErrorMessage(error, '특성 목록을 불러오지 못했습니다.')
    } finally {
      traitLoading = false
    }
  }

  async function refreshPreview() {
    if (previewLoading) return
    previewLoading = true
    previewErrorMessage = null
    try {
      previewBattleStats = await previewCharacterCombatStats({
        physical: parseRequiredNumber(form.lifeStats.body),
        technique: parseRequiredNumber(form.lifeStats.technique),
        sense: parseRequiredNumber(form.lifeStats.sense),
        willpower: parseRequiredNumber(form.lifeStats.will),
      })
    } catch (error) {
      previewErrorMessage = getApiErrorMessage(error, '서버에서 전투 스테이터스를 계산하지 못했습니다.')
    } finally {
      previewLoading = false
    }
  }

  async function applyCurrentDeckIfNeeded(response: CharacterProfileResponse) {
    if (form.currentSkillDeckOwnedCardIds.length === 0) {
      return response
    }
    return replaceCharacterCurrentSkillDeck(response.id, {
      ownedCardIds: form.currentSkillDeckOwnedCardIds,
    })
  }

  async function saveWithPayload(payload: CharacterProfileRequest, applyFormCurrentDeck = true) {
    const response = isCreateMode
      ? await createCharacter(payload)
      : await updateCharacter(requestedCharacterId ?? '', payload)
    return applyFormCurrentDeck ? applyCurrentDeckIfNeeded(response) : response
  }

  async function handleFormSave(event?: SubmitEvent) {
    event?.preventDefault()
    if (saving || loading) return
    if (!form.name.trim()) {
      saveErrorMessage = '이름을 입력해주세요.'
      saveMessage = null
      return
    }

    saveErrorMessage = null
    deleteErrorMessage = null
    saveMessage = null
    saving = true

    try {
      const response = await saveWithPayload(buildCharacterPayload(form))
      syncCharacterState(response)
      notFound = false
      errorMessage = null
      saveMessage = isCreateMode ? '캐릭터를 생성했습니다.' : '캐릭터를 저장했습니다.'
      if (isCreateMode) {
        navigateTo(pathBuilders.characterDetail(String(response.id)), 'replace')
      }
    } catch (error) {
      saveErrorMessage = getApiErrorMessage(error, '캐릭터 생성에 실패했습니다. 입력값을 확인해주세요.')
    } finally {
      saving = false
    }
  }

  async function handleDeveloperJsonSave() {
    if (saving || loading) return
    saveErrorMessage = null
    saveMessage = null
    saving = true
    try {
      const response = await saveWithPayload(parseDeveloperJson(developerJsonText), false)
      syncCharacterState(response)
      saveMessage = isCreateMode ? '캐릭터를 생성했습니다.' : '캐릭터를 저장했습니다.'
      if (isCreateMode) {
        navigateTo(pathBuilders.characterDetail(String(response.id)), 'replace')
      }
    } catch (error) {
      saveErrorMessage = getApiErrorMessage(error, 'JSON 형식이 올바르지 않습니다.')
    } finally {
      saving = false
    }
  }

  async function handleDelete() {
    if (isCreateMode || !requestedCharacterId || saving || deleting || loading) return
    const confirmed = window.confirm(`${character?.name || '이 캐릭터'}를 삭제할까요? 되돌릴 수 없습니다.`)
    if (!confirmed) return

    deleteErrorMessage = null
    saveErrorMessage = null
    saveMessage = null
    deleting = true
    try {
      await deleteCharacter(requestedCharacterId)
      removeSelectionHandoff(selectionHandoffKeys.characterId)
      navigateTo(pathBuilders.characterList(), 'replace', { characterFeedback: '캐릭터를 삭제했습니다.' })
    } catch (error) {
      deleteErrorMessage = getApiErrorMessage(error, '캐릭터를 삭제하지 못했습니다.')
    } finally {
      deleting = false
    }
  }

  onMount(() => {
    saveMessage = getNavigationFeedback()
    void loadCreationReferences()
    if (isCreateMode) {
      loading = false
      void refreshPreview()
      return
    }
    if (!requestedCharacterId) {
      loading = false
      return
    }
    void loadCharacterDetail(requestedCharacterId)
  })
</script>

{#if loading}
  <div class="detail-page">
    <SectionFrame eyebrow="불러오는 중" title="캐릭터 정보 로딩" description="캐릭터 정보를 불러오고 있습니다.">
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>목록으로</a>
      </div>
    </SectionFrame>
  </div>
{:else if isCreateMode || character}
  <div class="detail-page">
    <SectionFrame
      eyebrow={isCreateMode ? '캐릭터 생성' : '캐릭터 편집'}
      title={displayedTitle}
      description={displayedSummary}
    >
      <div class="detail-page__hero">
        <div>
          <p>{buildDisposition(form)}</p>
          <h3>{form.name || '이름을 입력해주세요.'}</h3>
        </div>
        <div class="detail-page__hero-tags">
          <TagChip label={getGenderLabel(form.gender)} tone="muted" />
          <TagChip label={`선택됨 ${selectedTraitCount} / 2`} tone={selectedTraitCount ? 'accent' : 'muted'} />
          <TagChip label={`보유 카드 ${selectedOwnedCardCount}`} tone="muted" />
        </div>
      </div>
    </SectionFrame>

    <form class="detail-page__grid" onsubmit={handleFormSave}>
      <SectionFrame title="기본 정보" description="캐릭터의 기본 프로필을 입력합니다.">
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field">
              <span>이름</span>
              <input bind:value={form.name} name="name" type="text" autocomplete="off" />
            </label>
            <label class="detail-page__field">
              <span>나이</span>
              <input bind:value={form.age} name="age" type="number" step="1" />
            </label>
            <label class="detail-page__field detail-page__field--span-2">
              <span>한마디</span>
              <input bind:value={form.quote} name="quote" type="text" />
            </label>
            <label class="detail-page__field detail-page__field--span-2">
              <span>소원</span>
              <textarea bind:value={form.wish} name="wish" rows="3"></textarea>
            </label>
            <label class="detail-page__field detail-page__field--span-2">
              <span>백 스토리</span>
              <textarea bind:value={form.backstory} name="backstory" rows="6"></textarea>
            </label>
            <div class="detail-page__field">
              <span>성별</span>
              <div class="detail-page__choice-row">
                {#each genderOptions as option}
                  <label><input type="radio" bind:group={form.gender} value={option.id} /> {option.label}</label>
                {/each}
              </div>
            </div>
            <div class="detail-page__field">
              <span>성향</span>
              <div class="detail-page__choice-row">
                {#each orderAxisOptions as option}
                  <label><input type="radio" bind:group={form.alignmentOrderChaos} value={option.id} /> {option.label}</label>
                {/each}
              </div>
              <div class="detail-page__choice-row">
                {#each moralAxisOptions as option}
                  <label><input type="radio" bind:group={form.alignmentGoodEvil} value={option.id} /> {option.label}</label>
                {/each}
              </div>
            </div>
          </div>
        </fieldset>
      </SectionFrame>

      <SectionFrame title="생활 스테이터스" description="전투 스테이터스는 서버 계산 결과를 사용합니다.">
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field">
              <span>신체</span>
              <input bind:value={form.lifeStats.body} type="number" step="1" />
            </label>
            <label class="detail-page__field">
              <span>기술</span>
              <input bind:value={form.lifeStats.technique} type="number" step="1" />
            </label>
            <label class="detail-page__field">
              <span>감각</span>
              <input bind:value={form.lifeStats.sense} type="number" step="1" />
            </label>
            <label class="detail-page__field">
              <span>의지</span>
              <input bind:value={form.lifeStats.will} type="number" step="1" />
            </label>
          </div>
        </fieldset>
      </SectionFrame>

      <SectionFrame title="전투 스테이터스" description="생활 스테이터스를 바탕으로 서버에서 계산됩니다.">
        <div class="detail-page__stats">
          <StatBlock value={previewBattleStats?.maxHp ?? '-'} label="체력" note="편집 불가" />
          <StatBlock value={previewBattleStats?.maxAp ?? '-'} label="행동력" note="편집 불가" />
          <StatBlock value={previewBattleStats?.attackPower ?? '-'} label="공격력" note="편집 불가" />
          <StatBlock value={previewBattleStats?.healPower ?? '-'} label="치유력" note="편집 불가" />
        </div>
        <div class="detail-page__actions">
          <button type="button" disabled={previewLoading} onclick={() => void refreshPreview()}>
            {previewLoading ? '계산 중...' : '미리보기 갱신'}
          </button>
        </div>
        {#if previewErrorMessage}
          <p class="detail-page__error">{previewErrorMessage}</p>
        {/if}
      </SectionFrame>

      <SectionFrame title="캐릭터 특성" description="일반 입력에서는 0개에서 2개까지 선택합니다.">
        <p class="detail-page__muted">선택됨 {selectedTraitCount} / 2</p>
        {#if traitLoading}
          <p class="detail-page__muted">특성 목록을 불러오는 중입니다.</p>
        {:else if traitErrorMessage}
          <p class="detail-page__error">{traitErrorMessage}</p>
        {:else if traitOptions.length === 0}
          <p class="detail-page__muted">선택 가능한 특성 목록이 없습니다. 개발자 UI에서 직접 입력할 수 있습니다.</p>
        {:else}
          <div class="detail-page__option-list">
            {#each traitOptions as trait}
              <label class="detail-page__option">
                <input
                  type="checkbox"
                  checked={form.selectedTraitIds.includes(trait.id)}
                  disabled={!form.selectedTraitIds.includes(trait.id) && selectedTraitCount >= 2}
                  onchange={() => toggleTrait(trait.id)}
                />
                <span><strong>{trait.name}</strong><small>{trait.id} · {trait.description}</small></span>
              </label>
            {/each}
          </div>
        {/if}
      </SectionFrame>

      <SectionFrame title="히든 스테이터스" description="캐릭터에 부여할 히든 스테이터스를 선택합니다.">
        {#if optionsLoading}
          <p class="detail-page__muted">히든 스테이터스 목록을 불러오는 중입니다.</p>
        {:else if optionErrorMessage}
          <p class="detail-page__error">{optionErrorMessage}</p>
        {:else}
          <div class="detail-page__option-list">
            {#each hiddenTraitOptions as trait}
              <label class="detail-page__option">
                <input
                  type="checkbox"
                  checked={form.selectedHiddenTraitIds.includes(trait.id)}
                  onchange={() => toggleHiddenTrait(trait.id)}
                />
                <span><strong>{trait.label}</strong><small>{trait.id}</small></span>
              </label>
            {/each}
          </div>
        {/if}
      </SectionFrame>

      <SectionFrame title="카드 설정" description="보유 카드와 현재 스킬 덱, 설정된 EX 카드를 고릅니다.">
        {#if cardLoading}
          <p class="detail-page__muted">카드 목록을 불러오는 중입니다.</p>
        {:else if cardErrorMessage}
          <p class="detail-page__error">{cardErrorMessage}</p>
        {:else}
          <div class="detail-page__columns">
            <div>
              <h4>보유 카드 목록</h4>
              <p class="detail-page__muted">CardType.SKILL 카드만 서버에서 조회합니다. 선택됨 {selectedOwnedCardCount}</p>
              <div class="detail-page__option-list">
                {#each skillCards as card}
                  <label class="detail-page__option">
                    <input
                      type="checkbox"
                      checked={form.ownedSkillCardIds.includes(card.id)}
                      onchange={() => toggleOwnedSkillCard(card.id)}
                    />
                    <span><strong>{card.name}</strong><small>{card.id} · 비용 {card.cost ?? '-'} · {card.description}</small></span>
                  </label>
                {/each}
              </div>
            </div>
            <div>
              <h4>현재 스킬 덱</h4>
              <p class="detail-page__muted">보유 카드로 선택한 SKILL 카드만 덱 후보로 표시합니다.</p>
              <div class="detail-page__option-list">
                {#each currentDeckCandidates as card}
                  <label class="detail-page__option">
                    <input
                      type="checkbox"
                      checked={form.currentSkillDeckOwnedCardIds.includes(card.ownedCardId)}
                      onchange={() => toggleCurrentDeckCard(card.ownedCardId)}
                    />
                    <span><strong>{card.label}</strong><small>{card.ownedCardId}</small></span>
                  </label>
                {/each}
              </div>
            </div>
          </div>
          <div class="detail-page__field">
            <span>설정된 EX 카드</span>
            <select bind:value={form.selectedExCardId}>
              <option value="">선택하지 않음</option>
              {#each exCards as card}
                <option value={card.id}>{card.name} ({card.id}) · 비용 {card.cost ?? '-'}</option>
              {/each}
            </select>
          </div>
        {/if}
      </SectionFrame>

      <SectionFrame title="생성 확인" description="입력 요약을 확인한 뒤 저장합니다.">
        <div class="detail-page__summary">
          <p><strong>이름</strong> {form.name || '-'}</p>
          <p><strong>성별</strong> {getGenderLabel(form.gender)}</p>
          <p><strong>성향</strong> {buildDisposition(form)}</p>
          <p><strong>보유 카드</strong> {form.ownedSkillCardIds.length}장</p>
          <p><strong>현재 스킬 덱</strong> {form.currentSkillDeckOwnedCardIds.length}장</p>
          <p><strong>EX 카드</strong> {form.selectedExCardId || '-'}</p>
        </div>
        <div class="detail-page__actions">
          <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>취소</a>
          <button type="submit" disabled={saving || deleting}>
            {saving ? '저장 중...' : isCreateMode ? '폼 값으로 캐릭터 생성' : '저장'}
          </button>
          {#if !isCreateMode}
            <button type="button" class="detail-page__danger-action" disabled={saving || deleting} onclick={() => void handleDelete()}>
              {deleting ? '삭제 중...' : '삭제'}
            </button>
          {/if}
        </div>
        {#if saveMessage || saveErrorMessage || deleteErrorMessage}
          <div class="detail-page__status" class:detail-page__status--error={Boolean(saveErrorMessage || deleteErrorMessage)}>
            {#if saveMessage}<p>{saveMessage}</p>{/if}
            {#if saveErrorMessage}<p>{saveErrorMessage}</p>{/if}
            {#if deleteErrorMessage}<p>{deleteErrorMessage}</p>{/if}
          </div>
        {/if}
      </SectionFrame>
    </form>

    <SectionFrame title="개발자 UI" description="기존 JSON 직접 편집 기능입니다. 기본 입력과 별도로 유지됩니다.">
      <div class="detail-page__actions">
        <button type="button" onclick={() => (developerUiOpen = !developerUiOpen)}>
          {developerUiOpen ? '개발자 UI 닫기' : '개발자 UI 열기'}
        </button>
      </div>
      {#if developerUiOpen}
        <label class="detail-page__field">
          <span>JSON 직접 편집</span>
          <textarea bind:value={developerJsonText} rows="14" spellcheck="false"></textarea>
        </label>
        <div class="detail-page__actions">
          <button type="button" disabled={saving || loading} onclick={() => void handleDeveloperJsonSave()}>
            JSON으로 캐릭터 생성
          </button>
        </div>
      {/if}
    </SectionFrame>
  </div>
{:else if notFound || missingCharacterId}
  <div class="detail-page">
    <SectionFrame eyebrow="캐릭터 없음" title="캐릭터를 찾을 수 없습니다." description="목록에서 다시 선택해주세요.">
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>목록으로</a>
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterCreate()}>캐릭터 생성</a>
      </div>
    </SectionFrame>
  </div>
{:else if errorMessage}
  <div class="detail-page">
    <SectionFrame eyebrow="오류" title="캐릭터 정보를 불러오지 못했습니다." description={errorMessage}>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>목록으로</a>
        {#if requestedCharacterId}
          <button type="button" onclick={() => void loadCharacterDetail(requestedCharacterId)}>다시 시도</button>
        {/if}
      </div>
    </SectionFrame>
  </div>
{/if}

<style>
  .detail-page,
  .detail-page__grid,
  .detail-page__form-grid,
  .detail-page__option-list {
    display: grid;
    gap: 1.25rem;
  }

  .detail-page__fieldset {
    border: 0;
    margin: 0;
    min-width: 0;
    padding: 0;
  }

  .detail-page__hero,
  .detail-page__actions,
  .detail-page__hero-tags,
  .detail-page__choice-row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .detail-page__hero {
    align-items: flex-start;
    justify-content: space-between;
  }

  .detail-page__hero p,
  .detail-page__hero h3,
  .detail-page__summary p,
  .detail-page__muted,
  .detail-page__error {
    margin: 0;
  }

  .detail-page__hero p,
  .detail-page__muted,
  .detail-page__option small {
    color: var(--color-text-muted);
  }

  .detail-page__hero h3 {
    font-family: var(--font-display);
    font-size: 2rem;
    line-height: 1.15;
    margin: 0.35rem 0 0;
  }

  .detail-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__form-grid,
  .detail-page__stats,
  .detail-page__columns,
  .detail-page__summary {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__field,
  .detail-page__option {
    display: grid;
    gap: 0.55rem;
  }

  .detail-page__field--span-2 {
    grid-column: span 2;
  }

  .detail-page__field > span,
  h4 {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    letter-spacing: 0.08em;
    margin: 0;
    text-transform: uppercase;
  }

  .detail-page__field input,
  .detail-page__field select,
  .detail-page__field textarea {
    background: rgba(12, 11, 10, 0.22);
    border: 1px solid var(--color-border);
    color: var(--color-text);
    font: inherit;
    min-height: 3rem;
    padding: 0.75rem 0.9rem;
    width: 100%;
  }

  .detail-page__field textarea {
    min-height: 7rem;
    resize: vertical;
  }

  .detail-page__choice-row label,
  .detail-page__option {
    align-items: start;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    padding: 0.75rem;
  }

  .detail-page__option {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .detail-page__option span {
    display: grid;
    gap: 0.25rem;
  }

  .detail-page__option small {
    line-height: 1.45;
  }

  .detail-page__actions button,
  .detail-page__link-action {
    align-items: center;
    background: rgba(12, 11, 10, 0.28);
    border: 1px solid var(--color-border);
    color: var(--color-text);
    display: inline-flex;
    justify-content: center;
    min-height: 3rem;
    padding: 0.75rem 1rem;
  }

  .detail-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
  }

  .detail-page__danger-action {
    border-color: rgba(199, 129, 121, 0.42);
  }

  .detail-page__status {
    border: 1px solid var(--color-border);
    display: grid;
    gap: 0.5rem;
    padding: 1rem;
  }

  .detail-page__status p {
    margin: 0;
  }

  .detail-page__status--error,
  .detail-page__error {
    border-color: rgba(199, 129, 121, 0.42);
    color: rgb(224, 161, 151);
  }

  @media (max-width: 960px) {
    .detail-page__grid,
    .detail-page__form-grid,
    .detail-page__stats,
    .detail-page__columns,
    .detail-page__summary {
      grid-template-columns: 1fr;
    }

    .detail-page__field--span-2 {
      grid-column: auto;
    }
  }
</style>
