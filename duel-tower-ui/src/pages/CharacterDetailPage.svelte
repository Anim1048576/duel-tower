<script lang="ts">
  import { onMount } from 'svelte'
  import { createCharacter, deleteCharacter, getCharacter, updateCharacter } from '../lib/api/characters'
  import type {
    CharacterGender,
    CharacterProfileRequest,
    CharacterProfileResponse,
    OwnedCardModifierRequest,
    OwnedCardRequest,
  } from '../lib/api/characterTypes'
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

  type DetailTag = {
    label: string
    tone?: 'accent' | 'muted' | 'success' | 'warning'
  }

  type CharacterDetailMode = 'create' | 'edit'

  type CharacterFormState = {
    name: string
    gender: CharacterGender | ''
    age: string
    wish: string
    disposition: string
    oneLiner: string
    story: string
    physical: string
    technique: string
    sense: string
    willpower: string
    trait1: string
    trait2: string
    ownedCards: string
    exCard: string
  }

  type CharacterNavigationState = {
    characterFeedback?: string
  }

  function createEmptyFormState(): CharacterFormState {
    return {
      name: '',
      gender: '',
      age: '',
      wish: '',
      disposition: '',
      oneLiner: '',
      story: '',
      physical: '',
      technique: '',
      sense: '',
      willpower: '',
      trait1: '',
      trait2: '',
      ownedCards: '',
      exCard: '',
    }
  }

  function createFormStateFromResponse(character: CharacterProfileResponse): CharacterFormState {
    return {
      name: character.name,
      gender: character.gender,
      age: character.age === null ? '' : String(character.age),
      wish: character.wish,
      disposition: character.disposition,
      oneLiner: character.oneLiner,
      story: character.story,
      physical: String(character.physical),
      technique: String(character.technique),
      sense: String(character.sense),
      willpower: String(character.willpower),
      trait1: character.trait1 ?? '',
      trait2: character.trait2 ?? '',
      ownedCards: character.ownedCards,
      exCard: character.exCard,
    }
  }

  function getCharacterRouteState() {
    if (typeof window === 'undefined') {
      return {
        mode: 'edit' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    const match = resolveRouteMatch(window.location.pathname)

    if (match?.page.key === 'character-create') {
      return {
        mode: 'create' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    if (match?.page.key !== 'character-detail') {
      return {
        mode: 'edit' as CharacterDetailMode,
        routeId: null as string | null,
      }
    }

    return {
      mode: 'edit' as CharacterDetailMode,
      routeId: match.params.id ?? null,
    }
  }

  function isCharacterApiId(value: string | null | undefined): value is string {
    return typeof value === 'string' && /^\d+$/.test(value.trim())
  }

  function getGenderLabel(gender: CharacterGender | '') {
    switch (gender) {
      case 'MALE':
        return 'Male'
      case 'FEMALE':
        return 'Female'
      case 'OTHER':
        return 'Other'
      default:
        return 'Unspecified'
    }
  }

  function formatNullableNumber(value: number | string | null | undefined) {
    if (value === null || value === undefined) return '-'
    const normalized = String(value).trim()
    return normalized || '-'
  }

  function parseNullableNumber(value: string) {
    const normalized = value.trim()

    if (!normalized) {
      return null
    }

    const parsed = Number(normalized)
    return Number.isFinite(parsed) ? parsed : null
  }

  function normalizeOptionalText(value: string) {
    const normalized = value.trim()
    return normalized ? normalized : null
  }

  function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
  }

  function normalizeOptionalString(value: unknown) {
    return typeof value === 'string' && value.trim() ? value.trim() : null
  }

  function normalizeBoolean(value: unknown, fallback: boolean) {
    return typeof value === 'boolean' ? value : fallback
  }

  function parseOwnedCardModifierInput(value: unknown, index: number, modifierIndex: number): OwnedCardModifierRequest {
    if (!isRecord(value)) {
      throw new Error(`Owned cards entry[${index}].modifiers[${modifierIndex}] must be an object.`)
    }

    const modifierId = normalizeOptionalString(value.modifierId)
    if (!modifierId) {
      throw new Error(`Owned cards entry[${index}].modifiers[${modifierIndex}].modifierId is required.`)
    }

    const rawValue = value.value
    if (rawValue !== null && rawValue !== undefined && typeof rawValue !== 'number') {
      throw new Error(`Owned cards entry[${index}].modifiers[${modifierIndex}].value must be a number or null.`)
    }

    return {
      modifierId,
      value: rawValue ?? null,
    }
  }

  function parseOwnedCardsInput(raw: string): OwnedCardRequest[] {
    const normalized = raw.trim()
    if (!normalized) return []

    let parsed: unknown
    try {
      parsed = JSON.parse(normalized)
    } catch {
      throw new Error('Owned cards must be a JSON array.')
    }

    if (!Array.isArray(parsed)) {
      throw new Error('Owned cards must be a JSON array.')
    }

    return parsed
      .map((entry, index): OwnedCardRequest | null => {
        if (typeof entry === 'string') {
          const cardId = entry.trim()
          if (!cardId) return null
          return {
            ownedCardId: null,
            cardId,
            modifiers: [],
            strengthened: false,
            weakened: false,
            lockedInDeck: false,
            forgettable: true,
            notForgettableReason: null,
          }
        }

        if (!isRecord(entry)) {
          throw new Error(`Owned cards entry[${index}] must be a card id string or an object.`)
        }

        const cardId = normalizeOptionalString(entry.cardId)
        if (!cardId) {
          throw new Error(`Owned cards entry[${index}].cardId is required.`)
        }

        const rawModifiers = entry.modifiers
        if (rawModifiers !== undefined && rawModifiers !== null && !Array.isArray(rawModifiers)) {
          throw new Error(`Owned cards entry[${index}].modifiers must be an array.`)
        }

        return {
          ownedCardId: normalizeOptionalString(entry.ownedCardId),
          cardId,
          modifiers: Array.isArray(rawModifiers)
            ? rawModifiers.map((modifier, modifierIndex) =>
                parseOwnedCardModifierInput(modifier, index, modifierIndex),
              )
            : [],
          strengthened: normalizeBoolean(entry.strengthened, false),
          weakened: normalizeBoolean(entry.weakened, false),
          lockedInDeck: normalizeBoolean(entry.lockedInDeck, false),
          forgettable: normalizeBoolean(entry.forgettable, true),
          notForgettableReason: normalizeOptionalString(entry.notForgettableReason),
        }
      })
      .filter((entry): entry is OwnedCardRequest => entry !== null)
  }

  function parseExCardInput(raw: string): string | null {
    const normalized = raw.trim()
    if (!normalized) return null

    let parsed: unknown
    try {
      parsed = JSON.parse(normalized)
    } catch {
      if (normalized.startsWith('{') || normalized.startsWith('[') || normalized.startsWith('"')) {
        throw new Error('EX card must be {}, a JSON string, a JSON object with id, or a plain card id.')
      }
      return normalized
    }

    if (parsed === null) return null
    if (typeof parsed === 'string') {
      const value = parsed.trim()
      return value || null
    }
    if (isRecord(parsed)) {
      if (Object.keys(parsed).length === 0) return null
      const id = normalizeOptionalString(parsed.id)
      if (id) return id
    }
    throw new Error('EX card must be {}, a JSON string, a JSON object with id, or a plain card id.')
  }

  function buildCharacterTags(form: CharacterFormState): DetailTag[] {
    const tags: DetailTag[] = [{ label: getGenderLabel(form.gender), tone: 'muted' }]
    const currentDeckCount = character?.currentSkillDeckPreviewCardIds?.length ?? 0

    tags.push(
      currentDeckCount ? { label: 'Deck Applied', tone: 'success' } : { label: 'No Applied Deck', tone: 'muted' },
    )

    if (form.trait1.trim() || form.trait2.trim()) {
      tags.push({ label: 'Traits', tone: 'accent' })
    }

    return tags
  }

  function buildCharacterPayload(form: CharacterFormState): CharacterProfileRequest {
    return {
      name: form.name.trim(),
      gender: form.gender || null,
      age: parseNullableNumber(form.age),
      wish: form.wish.trim(),
      disposition: form.disposition.trim(),
      oneLiner: form.oneLiner.trim(),
      story: form.story.trim(),
      physical: parseNullableNumber(form.physical),
      technique: parseNullableNumber(form.technique),
      sense: parseNullableNumber(form.sense),
      willpower: parseNullableNumber(form.willpower),
      trait1: normalizeOptionalText(form.trait1),
      trait2: normalizeOptionalText(form.trait2),
      hiddenTraitIds: [],
      ownedCardList: parseOwnedCardsInput(form.ownedCards),
      exCardId: parseExCardInput(form.exCard) ?? '',
    }
  }

  function isSameFormState(left: CharacterFormState, right: CharacterFormState) {
    return (
      left.name === right.name &&
      left.gender === right.gender &&
      left.age === right.age &&
      left.wish === right.wish &&
      left.disposition === right.disposition &&
      left.oneLiner === right.oneLiner &&
      left.story === right.story &&
      left.physical === right.physical &&
      left.technique === right.technique &&
      left.sense === right.sense &&
      left.willpower === right.willpower &&
      left.trait1 === right.trait1 &&
      left.trait2 === right.trait2 &&
      left.ownedCards === right.ownedCards &&
      left.exCard === right.exCard
    )
  }

  function getNavigationFeedback() {
    if (typeof window === 'undefined') return null
    const state = history.state as CharacterNavigationState | null
    const feedback = state?.characterFeedback ?? null

    if (feedback) {
      history.replaceState({}, '', window.location.pathname)
    }

    return feedback
  }

  function navigateTo(path: string, mode: 'push' | 'replace' = 'push', state: CharacterNavigationState = {}) {
    if (typeof window === 'undefined') return

    history[mode === 'replace' ? 'replaceState' : 'pushState'](state, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function handleApplySavedDeck() {
    if (isCreateMode || !requestedCharacterId || saving || deleting || loading) {
      saveErrorMessage = '덱 적용 전 캐릭터를 저장해 주세요.'
      saveMessage = null
      return
    }

    if (formDirty) {
      saveErrorMessage = '덱 적용 전 변경사항을 저장하거나 취소해 주세요.'
      saveMessage = null
      return
    }

    saveErrorMessage = null
    saveMessage = null
    setSelectionHandoff(selectionHandoffKeys.deckApplyCharacterId, requestedCharacterId)
    setSelectionHandoff(selectionHandoffKeys.characterId, requestedCharacterId)
    navigateTo(pathBuilders.deckList())
  }

  const routeState = getCharacterRouteState()
  const isCreateMode = routeState.mode === 'create'
  const characterSelection = !isCreateMode
    ? resolveRouteFirstSelection({
        routeValue: routeState.routeId,
        handoffValue: readSelectionHandoff(selectionHandoffKeys.characterId),
        isValid: isCharacterApiId,
      })
    : {
        value: null,
        source: 'none' as const,
        missingRouteValue: null,
      }

  const requestedCharacterId = isCreateMode ? null : characterSelection.value
  const missingCharacterId = isCreateMode ? null : characterSelection.missingRouteValue

  let loading = $state(!isCreateMode && requestedCharacterId !== null)
  let saving = $state(false)
  let deleting = $state(false)
  let notFound = $state(false)
  let errorMessage = $state<string | null>(null)
  let saveErrorMessage = $state<string | null>(null)
  let deleteErrorMessage = $state<string | null>(null)
  let saveMessage = $state<string | null>(null)
  let character = $state<CharacterProfileResponse | null>(null)
  let form = $state<CharacterFormState>(createEmptyFormState())

  const characterTags = $derived.by(() => buildCharacterTags(form))
  const characterTraits = $derived.by(() =>
    [form.trait1.trim(), form.trait2.trim()].filter((trait): trait is string => Boolean(trait)),
  )
  const currentDeckPreviewCardIds = $derived.by(() => character?.currentSkillDeckPreviewCardIds ?? [])
  const formDirty = $derived.by(() =>
    character === null ? false : !isSameFormState(form, createFormStateFromResponse(character)),
  )
  const applySavedDeckBlocked = $derived.by(() =>
    isCreateMode || !requestedCharacterId || saving || deleting || loading || formDirty,
  )
  const selectionUnavailable = $derived.by(
    () => !isCreateMode && requestedCharacterId === null && missingCharacterId === null && !loading && !errorMessage,
  )
  const displayedTitle = $derived.by(() =>
    isCreateMode
      ? form.name.trim() || 'New Character Record'
      : character?.name || form.name.trim() || 'Character Detail',
  )
  const displayedEyebrow = $derived.by(() =>
    form.disposition.trim() || (isCreateMode ? 'New archive record' : 'Character archive record'),
  )
  const displayedSummary = $derived.by(() =>
    form.oneLiner.trim() ||
    (isCreateMode
      ? '새 캐릭터 정보를 입력합니다.'
      : 'No one-line summary has been recorded yet.'),
  )

  function syncCharacterState(response: CharacterProfileResponse) {
    character = response
    form = createFormStateFromResponse(response)
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
        errorMessage = getApiErrorMessage(error, '캐릭터를 불러오지 못했습니다.')
      }
    } finally {
      loading = false
    }
  }

  async function handleSave(event?: SubmitEvent) {
    event?.preventDefault()

    if (saving || loading) {
      return
    }

    if (!form.name.trim()) {
      saveErrorMessage = 'Character name is required before saving.'
      saveMessage = null
      return
    }

    saveErrorMessage = null
    deleteErrorMessage = null
    saveMessage = null
    saving = true

    try {
      const payload = buildCharacterPayload(form)
      const response = isCreateMode
        ? await createCharacter(payload)
        : await updateCharacter(requestedCharacterId ?? '', payload)

      syncCharacterState(response)
      notFound = false
      errorMessage = null
      saveMessage = isCreateMode ? '캐릭터를 생성했습니다.' : '캐릭터를 저장했습니다.'

      if (isCreateMode) {
        navigateTo(pathBuilders.characterDetail(String(response.id)), 'replace')
      }
    } catch (error) {
      saveErrorMessage = getApiErrorMessage(
        error,
        isCreateMode ? '캐릭터를 생성하지 못했습니다.' : '캐릭터를 저장하지 못했습니다.',
      )
    } finally {
      saving = false
    }
  }

  async function handleDelete() {
    if (isCreateMode || !requestedCharacterId || saving || deleting || loading) {
      return
    }

    const confirmed = window.confirm(
      `${character?.name || '이 캐릭터'}를 삭제할까요? 되돌릴 수 없습니다.`,
    )

    if (!confirmed) {
      return
    }

    deleteErrorMessage = null
    saveErrorMessage = null
    saveMessage = null
    deleting = true

    try {
      await deleteCharacter(requestedCharacterId)
      removeSelectionHandoff(selectionHandoffKeys.characterId)
      navigateTo(pathBuilders.characterList(), 'replace', {
        characterFeedback: '캐릭터를 삭제했습니다.',
      })
    } catch (error) {
      deleteErrorMessage = getApiErrorMessage(error, '캐릭터를 삭제하지 못했습니다.')
    } finally {
      deleting = false
    }
  }

  onMount(() => {
    saveMessage = getNavigationFeedback()

    if (isCreateMode) {
      loading = false
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
    <SectionFrame
      eyebrow="Record Loading"
      title="Loading character detail"
      description="캐릭터를 불러오는 중입니다."
    >
      <div class="detail-page__todo">
        <p>캐릭터를 불러오는 중입니다.</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
      </div>
    </SectionFrame>
  </div>
{:else if isCreateMode || character}
  <form class="detail-page" onsubmit={handleSave}>
    <SectionFrame
      eyebrow={isCreateMode ? 'Record Draft' : 'Selected Record'}
      title={displayedTitle}
      description={
        isCreateMode
          ? '새 캐릭터를 생성합니다.'
          : '캐릭터 정보를 확인하고 수정합니다.'
      }
    >
      <div class="detail-page__hero">
        <div class="detail-page__hero-copy">
          <p>{displayedEyebrow}</p>
          <h3>{displayedSummary}</h3>
        </div>

        <div class="detail-page__hero-tags">
          {#each characterTags as tag}
            <TagChip label={tag.label} tone={tag.tone} />
          {/each}
        </div>
      </div>

      <div class="detail-page__stats">
        <StatBlock value={formatNullableNumber(form.age)} label="Age" note="Recorded profile age" />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.maxHp)}
          label="Max HP"
          note={character ? 'Combat profile' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.maxAp)}
          label="Max AP"
          note={character ? 'Action capacity' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.attackPower)}
          label="Attack"
          note={character ? 'Combat power' : 'Calculated after the first save'}
        />
        <StatBlock
          value={formatNullableNumber(character?.combatStats.healPower)}
          label="Heal"
          note={character ? 'Recovery power' : 'Calculated after the first save'}
        />
      </div>
    </SectionFrame>

    <div class="detail-page__grid">
      <SectionFrame
        title="Combat profile"
        description="기본 정보와 능력치를 수정합니다."
      >
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field detail-page__field--span-2">
              <span>Name</span>
              <input bind:value={form.name} name="name" type="text" required />
            </label>

            <label class="detail-page__field">
              <span>Gender</span>
              <select bind:value={form.gender} name="gender">
                <option value="">Unspecified</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </label>

            <label class="detail-page__field">
              <span>Age</span>
              <input bind:value={form.age} name="age" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Disposition</span>
              <input bind:value={form.disposition} name="disposition" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>One-line summary</span>
              <input bind:value={form.oneLiner} name="oneLiner" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Story</span>
              <textarea bind:value={form.story} name="story" rows="6"></textarea>
            </label>

            <label class="detail-page__field">
              <span>Physical</span>
              <input bind:value={form.physical} name="physical" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Technique</span>
              <input bind:value={form.technique} name="technique" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Sense</span>
              <input bind:value={form.sense} name="sense" type="number" min="0" step="1" />
            </label>

            <label class="detail-page__field">
              <span>Willpower</span>
              <input bind:value={form.willpower} name="willpower" type="number" min="0" step="1" />
            </label>
          </div>
        </fieldset>
      </SectionFrame>

      <SectionFrame
        title="Loadout and notes"
        description="프로필 메모와 적용 덱을 확인합니다."
      >
        <fieldset class="detail-page__fieldset" disabled={saving || deleting}>
          <div class="detail-page__form-grid">
            <label class="detail-page__field">
              <span>Trait 1</span>
              <input bind:value={form.trait1} name="trait1" type="text" />
            </label>

            <label class="detail-page__field">
              <span>Trait 2</span>
              <input bind:value={form.trait2} name="trait2" type="text" />
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Wish</span>
              <textarea bind:value={form.wish} name="wish" rows="4"></textarea>
            </label>

            <div class="detail-page__field detail-page__field--span-2">
              <span>Current skill deck</span>
              <div class="detail-page__readonly-list" aria-live="polite">
                {#if currentDeckPreviewCardIds.length}
                  {#each currentDeckPreviewCardIds as card, index}
                    <span>{index + 1}. {card}</span>
                  {/each}
                {:else}
                  <span>적용된 덱이 없습니다.</span>
                {/if}
              </div>
            </div>

            <label class="detail-page__field detail-page__field--span-2">
              <span>Owned cards JSON</span>
              <textarea bind:value={form.ownedCards} name="ownedCards" rows="5"></textarea>
            </label>

            <label class="detail-page__field detail-page__field--span-2">
              <span>EX card id / JSON</span>
              <textarea bind:value={form.exCard} name="exCard" rows="4"></textarea>
            </label>
          </div>
        </fieldset>

        <div class="detail-page__list-block">
          <div>
            <strong>Trait preview</strong>
            {#if characterTraits.length}
              <ul>
                {#each characterTraits as trait}
                  <li>{trait}</li>
                {/each}
              </ul>
            {:else}
              <p>No traits have been recorded yet.</p>
            {/if}
          </div>
          <div>
            <strong>Current skill deck preview</strong>
            {#if currentDeckPreviewCardIds.length}
              <ul>
                {#each currentDeckPreviewCardIds as card}
                  <li>{card}</li>
                {/each}
              </ul>
            {:else}
              <p>적용된 덱이 없습니다.</p>
            {/if}
          </div>
        </div>
      </SectionFrame>
    </div>

    <SectionFrame
      title={isCreateMode ? 'Create queue' : 'Edit queue'}
      description="변경사항을 저장하거나 덱을 적용합니다."
    >
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <button type="submit" disabled={saving || deleting}>
          {saving ? 'Saving...' : isCreateMode ? 'Create character' : 'Save changes'}
        </button>
        {#if !isCreateMode}
          <button
            type="button"
            class="detail-page__danger-action"
            disabled={saving || deleting}
            onclick={() => void handleDelete()}
          >
            {deleting ? 'Deleting...' : 'Delete record'}
          </button>
        {/if}
        <button
          type="button"
          disabled={applySavedDeckBlocked}
          onclick={handleApplySavedDeck}
        >
          Apply saved deck
        </button>
      </div>

      {#if saveMessage || saveErrorMessage || deleteErrorMessage}
        <div
          class="detail-page__status"
          class:detail-page__status--error={Boolean(saveErrorMessage || deleteErrorMessage)}
        >
          {#if saveMessage}
            <p>{saveMessage}</p>
          {/if}
          {#if saveErrorMessage}
            <p>{saveErrorMessage}</p>
          {/if}
          {#if deleteErrorMessage}
            <p>{deleteErrorMessage}</p>
          {/if}
        </div>
      {/if}

      <div class="detail-page__todo">
        {#if isCreateMode}
          <p>덱 적용 전 캐릭터를 저장해 주세요.</p>
        {:else if formDirty}
          <p>덱 적용 전 변경사항을 저장하거나 취소해 주세요.</p>
        {:else}
          <p>저장된 덱을 캐릭터에 적용합니다.</p>
        {/if}
      </div>
    </SectionFrame>
  </form>
{:else if notFound || missingCharacterId}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Missing"
      title="Character not found"
      description="요청한 캐릭터를 찾을 수 없습니다."
    >
      <div class="detail-page__todo">
        <p>Requested id: {missingCharacterId ?? requestedCharacterId ?? 'Unknown'}</p>
        <p>캐릭터 목록에서 다시 열어 주세요.</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterCreate()}>
          Create new character
        </a>
      </div>
    </SectionFrame>
  </div>
{:else if errorMessage}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Error"
      title="Unable to load character detail"
      description="캐릭터를 불러오지 못했습니다."
    >
      <div class="detail-page__todo">
        <p>{errorMessage}</p>
        {#if requestedCharacterId}
          <p>Requested id: {requestedCharacterId}</p>
        {/if}
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        {#if requestedCharacterId}
          <button type="button" onclick={() => void loadCharacterDetail(requestedCharacterId)}>
            Retry load
          </button>
        {/if}
      </div>
    </SectionFrame>
  </div>
{:else if selectionUnavailable}
  <div class="detail-page">
    <SectionFrame
      eyebrow="Record Missing"
      title="Character selection unavailable"
      description="캐릭터 목록에서 캐릭터를 열어 주세요."
    >
      <div class="detail-page__todo">
        <p>현재 경로에 캐릭터 id가 없습니다.</p>
        <p>캐릭터 목록에서 캐릭터를 열어 주세요.</p>
      </div>
      <div class="detail-page__actions">
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterList()}>
          Back to roster list
        </a>
        <a class="detail-page__link-action" data-nav href={pathBuilders.characterCreate()}>
          Create new character
        </a>
      </div>
    </SectionFrame>
  </div>
{/if}

<style>
  .detail-page,
  .detail-page__grid,
  .detail-page__list-block,
  .detail-page__todo,
  .detail-page__form-grid {
    display: grid;
    gap: 1.5rem;
  }

  .detail-page__fieldset {
    border: 0;
    margin: 0;
    padding: 0;
    min-width: 0;
  }

  .detail-page__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .detail-page__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .detail-page__hero-copy p,
  .detail-page__hero-copy h3,
  .detail-page__todo p {
    margin: 0;
  }

  .detail-page__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .detail-page__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .detail-page__hero-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .detail-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .detail-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-page__field {
    display: grid;
    gap: 0.55rem;
  }

  .detail-page__field--span-2 {
    grid-column: span 2;
  }

  .detail-page__field span {
    font-size: 0.8rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .detail-page__field input,
  .detail-page__field select,
  .detail-page__field textarea {
    min-height: 3rem;
    width: 100%;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
    color: var(--color-text);
    font: inherit;
  }

  .detail-page__field textarea {
    min-height: 7rem;
    resize: vertical;
  }

  .detail-page__readonly-list {
    min-height: 7rem;
    width: 100%;
    padding: 0.75rem 0.9rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.16);
    color: var(--color-text-soft);
    display: grid;
    align-content: start;
    gap: 0.35rem;
    line-height: 1.5;
  }

  .detail-page__readonly-list span {
    color: inherit;
    font-size: 0.92rem;
    letter-spacing: 0;
    text-transform: none;
  }

  .detail-page__list-block > div {
    display: grid;
    gap: 0.65rem;
  }

  .detail-page__list-block strong {
    font-size: 0.92rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
  }

  .detail-page__list-block p,
  .detail-page__list-block ul {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .detail-page__list-block ul {
    padding-left: 1.1rem;
  }

  .detail-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .detail-page__status {
    display: grid;
    gap: 0.6rem;
    padding: 1rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.22);
  }

  .detail-page__status--error {
    border-color: rgba(199, 129, 121, 0.38);
  }

  .detail-page__status p {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.6;
  }

  .detail-page__link-action,
  .detail-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .detail-page__danger-action {
    border-color: rgba(199, 129, 121, 0.42);
    background: linear-gradient(180deg, rgba(199, 129, 121, 0.18), rgba(199, 129, 121, 0.08));
  }

  .detail-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .detail-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .detail-page__todo p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .detail-page__stats,
    .detail-page__grid,
    .detail-page__form-grid {
      grid-template-columns: 1fr;
    }

    .detail-page__field--span-2 {
      grid-column: auto;
    }
  }
</style>
