<script lang="ts">
  import { onMount } from 'svelte'
  import { validateDeckDraft } from '../lib/api/decks'
  import type { DeckType, DeckValidationResponse } from '../lib/api/deckTypes'
  import { getScreen } from '../lib/api/screens'
  import { invokeEditorEntityActionAndRefresh, invokeEditorScreenAction } from '../lib/api/editorScreenActions'
  import { listCards } from '../lib/api/content'
  import type { CardDefinition } from '../lib/api/contentTypes'
  import { listCharacters } from '../lib/api/characters'
  import type { CharacterProfileResponse } from '../lib/api/characterTypes'
  import {
    findDeckEditorAction,
    type DeckEditorActionId,
    type DeckEditorActionPayload,
    type DeckEditorActionResponseById,
    type DeckEditorLocalValidationState,
    type DeckEditorScreenAction,
    type DeckEditorScreenResponse,
    type DeckEditorServerValidationDto,
  } from '../lib/api/screenTypes'
  import { ApiError, getApiErrorMessage } from '../lib/api/types'
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { getCardTypeLabel, getCardTypeTone } from '../lib/content/display'
  import {
    buildDeckEditorActionPatch,
    createDeckEditorState,
    createEmptyDeckEditorState,
    type DeckEditorState,
  } from '../lib/decks/editorModel'
  import { getDeckEditorLocalTotalCards, isDeckEditorLocalDirty } from '../lib/decks/presentationState.js'
  import { createDeckEditorDraftSignature, isDeckEditorValidationLocallyStale } from '../lib/decks/validationFreshness.js'
  import { deckEditorStateCopy, deckListStateCopy, setDeckPageFeedback } from '../lib/decks/pageState'
  import { pathBuilders, resolveRouteMatch, routePaths } from '../lib/navigation'
  import {
    readSelectionHandoff,
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type CandidateCard = {
    card: CardDefinition
    ownedCount: number | null
    deckCount: number
    disabled: boolean
  }

  const deckTypeOptions: DeckType[] = ['PLAYER', 'ENEMY']
  const CANDIDATE_CARDS_PER_PAGE = 8

  let loading = $state(true)
  let screen = $state<DeckEditorScreenResponse | null>(null)
  let editorState = $state<DeckEditorState>(createEmptyDeckEditorState())
  let errorMessage = $state<string | null>(null)
  let notFoundId = $state<string | null>(null)
  let requestedDeckId = $state<string | null>(null)
  let pendingActionId = $state<DeckEditorActionId | 'deckEditor.validateDraft' | null>(null)
  let deleteConfirmOpen = $state(false)
  let actionErrorMessage = $state<string | null>(null)
  let actionSuccessMessage = $state<string | null>(null)
  let debugOpen = $state(false)
  let selectedCharacterId = $state('')
  let candidateCardPage = $state(1)
  let cardsLoading = $state(false)
  let cardsErrorMessage = $state<string | null>(null)
  let charactersLoading = $state(false)
  let charactersErrorMessage = $state<string | null>(null)
  let allCards = $state<CardDefinition[]>([])
  let skillCards = $state<CardDefinition[]>([])
  let characters = $state<CharacterProfileResponse[]>([])
  let localCardKeySequence = 0
  let requestSequence = 0

  function getDeckIdFromRoute() {
    if (typeof window === 'undefined') return null
    const match = resolveRouteMatch(window.location.pathname)
    if (match?.page.key !== 'deck-editor') return null
    const deckId = match.params.id?.trim()
    return deckId ? deckId : null
  }

  function isCreateDeckRoute() {
    if (typeof window === 'undefined') return false
    const match = resolveRouteMatch(window.location.pathname)
    return match?.page.key === 'deck-editor' && !match.params.id && match.page.path === routePaths.deckEditor
  }

  function getDeckTypeDisplayLabel(type: DeckType | '') {
    if (type === 'PLAYER') return '캐릭터'
    if (type === 'ENEMY') return '에너미'
    return '미선택'
  }

  function summarizeDescription(description: string | null | undefined, maxLength = 92) {
    const normalized = String(description ?? '').replace(/\s+/g, ' ').trim()
    if (!normalized) return '설명이 없습니다.'
    return normalized.length > maxLength ? `${normalized.slice(0, maxLength).trimEnd()}...` : normalized
  }

  function buildCardMeta(card: CardDefinition) {
    const parts = [getCardTypeLabel(card.type), `비용 ${card.cost ?? '-'}`]
    if (card.resolveTo) parts.push(card.resolveTo)
    return parts.join(' · ')
  }

  function clearActionFeedback() {
    actionErrorMessage = null
    actionSuccessMessage = null
  }

  function applyScreen(nextScreen: DeckEditorScreenResponse) {
    screen = nextScreen
    editorState = createDeckEditorState(nextScreen.draft)
    deleteConfirmOpen = false
    clearActionFeedback()

    if (nextScreen.deckId != null) {
      setSelectionHandoff(selectionHandoffKeys.deckId, String(nextScreen.deckId))
    }
  }

  function resetTransientState() {
    pendingActionId = null
    deleteConfirmOpen = false
    clearActionFeedback()
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') return
    if (replace) {
      window.history.replaceState({}, '', path)
    } else {
      window.history.pushState({}, '', path)
    }
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function replaceWithDeckEditor(id: string) {
    navigateTo(pathBuilders.deckEditor(id), true)
  }

  async function loadReferenceData() {
    cardsLoading = true
    charactersLoading = true
    cardsErrorMessage = null
    charactersErrorMessage = null

    try {
      const [nextAllCards, nextSkillCards] = await Promise.all([listCards(), listCards({ type: 'SKILL' })])
      allCards = nextAllCards
      skillCards = nextSkillCards
    } catch (error) {
      allCards = []
      skillCards = []
      cardsErrorMessage = getApiErrorMessage(error, '카드 목록을 불러오지 못했습니다.')
    } finally {
      cardsLoading = false
    }

    try {
      characters = await listCharacters()
    } catch (error) {
      characters = []
      selectedCharacterId = ''
      charactersErrorMessage = getApiErrorMessage(error, '캐릭터 목록을 불러오지 못했습니다.')
    } finally {
      charactersLoading = false
    }
  }

  async function loadDeckEditorScreen(deckId?: string | null) {
    const requestId = ++requestSequence
    loading = true
    screen = null
    editorState = createEmptyDeckEditorState()
    requestedDeckId = deckId ?? null
    errorMessage = null
    notFoundId = null
    resetTransientState()

    try {
      const response = deckId
        ? await getScreen<DeckEditorScreenResponse>('DeckEditor', { deckId })
        : await getScreen<DeckEditorScreenResponse>('DeckEditor')

      if (requestId !== requestSequence) return
      applyScreen(response)
    } catch (error) {
      if (requestId !== requestSequence) return

      if (error instanceof ApiError && (error.status === 404 || error.code === 'not_found')) {
        notFoundId = deckId ?? null
        return
      }

      errorMessage = getApiErrorMessage(error, '덱 편집 화면을 불러오지 못했습니다.')
    } finally {
      if (requestId === requestSequence) loading = false
    }
  }

  function retryLoad() {
    const routeDeckId = getDeckIdFromRoute()
    if (routeDeckId) {
      void loadDeckEditorScreen(routeDeckId)
      return
    }
    if (isCreateDeckRoute()) {
      void loadDeckEditorScreen(null)
      return
    }
    if (requestedDeckId) void loadDeckEditorScreen(requestedDeckId)
  }

  function updateName(value: string) {
    editorState = { ...editorState, name: value }
  }

  function updateType(value: DeckType | '') {
    editorState = { ...editorState, type: value }
    candidateCardPage = 1
  }

  function createLocalDeckCardKey(cardId: string) {
    localCardKeySequence += 1
    return `deck-card-local-${cardId}-${localCardKeySequence}`
  }

  function addDeckCardCopy(cardId: string) {
    const normalizedCardId = cardId.trim()
    if (!normalizedCardId) return

    const existing = editorState.cards.find((entry) => entry.cardId === normalizedCardId)
    editorState = {
      ...editorState,
      cards: existing
        ? editorState.cards.map((entry) =>
            entry.cardId === normalizedCardId ? { ...entry, count: Math.max(1, entry.count) + 1 } : entry,
          )
        : [
            ...editorState.cards,
            {
              key: createLocalDeckCardKey(normalizedCardId),
              cardId: normalizedCardId,
              count: 1,
            },
          ],
    }
  }

  function removeDeckCardCopy(cardId: string) {
    const normalizedCardId = cardId.trim()
    if (!normalizedCardId) return

    editorState = {
      ...editorState,
      cards: editorState.cards.flatMap((entry) => {
        if (entry.cardId !== normalizedCardId) return [entry]
        const nextCount = Math.max(0, Math.floor(entry.count) - 1)
        return nextCount > 0 ? [{ ...entry, count: nextCount }] : []
      }),
    }
  }

  function toDeckEditorServerValidation(response: DeckValidationResponse): DeckEditorServerValidationDto {
    return {
      valid: response.valid,
      normalizedTotalCards: response.normalizedTotalCards,
      issues: response.issues,
      validatedDraftSignature: createDeckEditorDraftSignature(editorState),
      validatedAt: new Date().toISOString(),
    }
  }

  function getActionSuccessMessage(actionId: DeckEditorActionId | 'deckEditor.validateDraft') {
    switch (actionId) {
      case 'deckEditor.validate':
      case 'deckEditor.validateDraft':
        return '덱 검증 결과를 갱신했습니다.'
      case 'deckEditor.save':
        return '덱 변경사항을 저장했습니다.'
      case 'deckEditor.create':
        return '덱을 생성했습니다.'
      case 'deckEditor.delete':
        return null
    }
  }

  async function runValidateDraft() {
    if (!screen || pendingActionId) return
    pendingActionId = 'deckEditor.validateDraft'
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      const payload = buildDeckEditorActionPatch('deckEditor.validate', editorState)
      const response = await validateDeckDraft({
        type: payload.type ?? null,
        cards: payload.cards ?? [],
      })
      screen = {
        ...screen,
        validation: toDeckEditorServerValidation(response),
      }
      actionSuccessMessage = getActionSuccessMessage('deckEditor.validateDraft')
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, '덱 검증에 실패했습니다.')
    } finally {
      pendingActionId = null
    }
  }

  async function runAction(actionId: DeckEditorActionId) {
    if (!screen || pendingActionId) return
    const action = findDeckEditorAction(screen, actionId)
    if (!action?.enabled) return

    pendingActionId = actionId
    actionErrorMessage = null
    actionSuccessMessage = null

    try {
      if (actionId === 'deckEditor.delete') {
        const result = await invokeEditorScreenAction<
          DeckEditorScreenResponse,
          DeckEditorScreenAction,
          DeckEditorActionId,
          DeckEditorState,
          DeckEditorActionPayload,
          DeckEditorActionResponseById['deckEditor.delete']
        >({
          screen,
          actionId,
          editorState,
          findAction: findDeckEditorAction,
          buildPatch: buildDeckEditorActionPatch,
        })

        if (!result) return
        removeSelectionHandoff(selectionHandoffKeys.deckId)
        setDeckPageFeedback(deckListStateCopy.deletedFeedback)
        navigateTo(pathBuilders.deckList(), true)
        return
      }

      const result = await invokeEditorEntityActionAndRefresh<
        DeckEditorScreenResponse,
        DeckEditorScreenAction,
        DeckEditorActionId,
        DeckEditorState,
        DeckEditorActionPayload,
        DeckEditorActionResponseById['deckEditor.save' | 'deckEditor.create'],
        string
      >({
        screen,
        actionId,
        editorState,
        findAction: findDeckEditorAction,
        buildPatch: buildDeckEditorActionPatch,
        getResourceId: (response) => String(response.id),
        refreshScreen: async (nextDeckId) => {
          await loadDeckEditorScreen(nextDeckId)
        },
      })

      if (!result) return
      actionSuccessMessage = getActionSuccessMessage(actionId)
      if (actionId === 'deckEditor.create') replaceWithDeckEditor(result.resourceId)
    } catch (error) {
      actionErrorMessage = getApiErrorMessage(error, '덱 작업에 실패했습니다.')
    } finally {
      pendingActionId = null
    }
  }

  function openDeleteConfirmation() {
    if (!screen || !findDeckEditorAction(screen, 'deckEditor.delete') || pendingActionId) return
    clearActionFeedback()
    deleteConfirmOpen = true
  }

  function cancelDeleteConfirmation() {
    if (pendingActionId === 'deckEditor.delete') return
    deleteConfirmOpen = false
  }

  onMount(() => {
    void loadReferenceData()

    const routeDeckId = getDeckIdFromRoute()
    if (routeDeckId) {
      void loadDeckEditorScreen(routeDeckId)
      return
    }

    if (isCreateDeckRoute()) {
      void loadDeckEditorScreen(null)
      return
    }

    const handoffDeckId = readSelectionHandoff(selectionHandoffKeys.deckId)?.trim()
    if (handoffDeckId) {
      replaceWithDeckEditor(handoffDeckId)
      return
    }

    loading = false
  })

  const cardDefinitionMap = $derived.by(() => new Map(allCards.map((card) => [card.id, card])))
  const skillCardDefinitionMap = $derived.by(() => new Map(skillCards.map((card) => [card.id, card])))
  const deckCountById = $derived.by(() => {
    const counts = new Map<string, number>()
    for (const entry of editorState.cards) {
      counts.set(entry.cardId, (counts.get(entry.cardId) ?? 0) + Math.max(1, Math.floor(entry.count)))
    }
    return counts
  })
  const totalCards = $derived.by(() => getDeckEditorLocalTotalCards(editorState))
  const selectedCharacter = $derived.by(() =>
    characters.find((character) => String(character.id) === selectedCharacterId) ?? null,
  )
  const selectedCharacterOwnedCounts = $derived.by(() => {
    const counts = new Map<string, number>()
    for (const owned of selectedCharacter?.ownedCardList ?? []) {
      counts.set(owned.cardId, (counts.get(owned.cardId) ?? 0) + 1)
    }
    return counts
  })
  const candidateCards = $derived.by<CandidateCard[]>(() => {
    if (editorState.type === 'ENEMY') {
      return allCards.map((card) => ({
        card,
        ownedCount: null,
        deckCount: deckCountById.get(card.id) ?? 0,
        disabled: false,
      }))
    }

    if (editorState.type !== 'PLAYER' || !selectedCharacter) return []

    return [...selectedCharacterOwnedCounts.entries()]
      .map((entry): CandidateCard | null => {
        const [cardId, ownedCount] = entry
        const card = skillCardDefinitionMap.get(cardId)
        if (!card) return null
        const deckCount = deckCountById.get(cardId) ?? 0
        return {
          card,
          ownedCount,
          deckCount,
          disabled: deckCount >= ownedCount,
        }
      })
      .filter((entry): entry is CandidateCard => entry !== null)
  })
  const candidatePageCount = $derived.by(() =>
    Math.max(1, Math.ceil(candidateCards.length / CANDIDATE_CARDS_PER_PAGE)),
  )
  const pagedCandidateCards = $derived.by(() => {
    const start = (candidateCardPage - 1) * CANDIDATE_CARDS_PER_PAGE
    return candidateCards.slice(start, start + CANDIDATE_CARDS_PER_PAGE)
  })
  const expandedDeckCards = $derived.by(() =>
    editorState.cards.flatMap((entry) =>
      Array.from({ length: Math.max(1, Math.floor(entry.count)) }, (_, index) => ({
        key: `${entry.key}-${index}`,
        cardId: entry.cardId,
        card: cardDefinitionMap.get(entry.cardId) ?? skillCardDefinitionMap.get(entry.cardId) ?? null,
        copyIndex: index,
      })),
    ),
  )
  const serverValidation = $derived.by(() => screen?.validation ?? null)
  const localValidationState = $derived.by((): DeckEditorLocalValidationState | null => {
    if (!screen) return null
    return {
      ...screen.validation,
      isLocallyStale: isDeckEditorValidationLocallyStale(editorState, screen.validation),
    }
  })
  const saveAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.save') : null))
  const createAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.create') : null))
  const deleteAction = $derived.by(() => (screen ? findDeckEditorAction(screen, 'deckEditor.delete') : null))
  const primaryAction = $derived.by(() => (screen?.mode === 'create' ? createAction : saveAction))
  const primaryActionId = $derived.by(() => (screen?.mode === 'create' ? 'deckEditor.create' : 'deckEditor.save'))
  const title = $derived.by(() => {
    const name = editorState.name.trim()
    if (name) return name
    return screen?.mode === 'create' ? '새 덱' : '선택한 덱'
  })
  const dirty = $derived.by(() => (screen ? isDeckEditorLocalDirty(editorState, screen.draft) : false))
  const debugPayload = $derived.by(() => JSON.stringify(buildDeckEditorActionPatch('deckEditor.save', editorState), null, 2))

  $effect(() => {
    if (candidateCardPage > candidatePageCount) candidateCardPage = candidatePageCount
    if (candidateCardPage < 1) candidateCardPage = 1
  })

  $effect(() => {
    if (editorState.type !== 'PLAYER') selectedCharacterId = ''
  })
</script>

<div class="editor-page">
  {#if loading}
    <SectionFrame eyebrow="덱 편집" title={deckEditorStateCopy.loadingTitle} description="덱 편집 화면을 불러오는 중입니다.">
      <ContentStatePanel title={deckEditorStateCopy.loadingTitle} message={deckEditorStateCopy.loadingMessage} />
    </SectionFrame>
  {:else if notFoundId}
    <SectionFrame eyebrow="덱 없음" title={deckEditorStateCopy.notFoundTitle} description="요청한 덱을 찾을 수 없습니다.">
      <div class="editor-page__note">
        <p>요청 ID: {notFoundId}</p>
        <p>{deckEditorStateCopy.notFoundMessage}</p>
      </div>
      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>덱 목록으로</a>
      </div>
    </SectionFrame>
  {:else if errorMessage}
    <SectionFrame eyebrow="덱 오류" title={deckEditorStateCopy.loadErrorTitle} description="덱 편집 화면을 불러오지 못했습니다.">
      <ContentStatePanel title={deckEditorStateCopy.loadErrorMessageTitle} message={errorMessage} tone="error" actionLabel="다시 불러오기" onAction={retryLoad} />
      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>덱 목록으로</a>
      </div>
    </SectionFrame>
  {:else if screen}
    <div class="editor-page__layout">
      <SectionFrame title={screen.mode === 'create' ? '덱 생성' : '덱 편집'} description="카드를 선택해 덱을 구성합니다. 캐릭터 덱은 정확히 12장의 SKILL 카드로 저장됩니다.">
        <div class="editor-page__intro-row">
          <div class="editor-page__intro-copy">
            <p>{title}</p>
            <h3>{screen.mode === 'create' ? '새 덱' : '선택한 덱'}</h3>
            <span>{dirty ? '저장되지 않은 변경사항이 있습니다.' : '현재 화면의 draft가 서버 상태와 같습니다.'}</span>
          </div>
          <label class="editor-page__field">
            <span>덱 이름</span>
            <input value={editorState.name} type="text" placeholder="덱 이름을 입력하세요" disabled={Boolean(pendingActionId)} oninput={(event) => updateName(event.currentTarget.value)} />
          </label>
        </div>
      </SectionFrame>

      <SectionFrame title="덱 대상" description="덱 유형과 보유 카드 후보를 정합니다. 대상 캐릭터는 저장 JSON에 포함되지 않습니다.">
        <div class="editor-page__target-row">
          <label class="editor-page__field">
            <span>덱 유형</span>
            <select value={editorState.type} disabled={Boolean(pendingActionId)} onchange={(event) => updateType(event.currentTarget.value as DeckType | '')}>
              {#each deckTypeOptions as option}
                <option value={option}>{getDeckTypeDisplayLabel(option)}</option>
              {/each}
            </select>
          </label>

          {#if editorState.type === 'PLAYER'}
            <label class="editor-page__field">
              <span>대상 캐릭터</span>
              <select bind:value={selectedCharacterId} disabled={charactersLoading || Boolean(charactersErrorMessage)}>
                <option value="">캐릭터 선택</option>
                {#each characters as character}
                  <option value={String(character.id)}>{character.name} · 보유 {character.ownedCardList.length}장</option>
                {/each}
              </select>
            </label>
          {/if}
        </div>
        {#if charactersErrorMessage && editorState.type === 'PLAYER'}
          <p class="editor-page__error">{charactersErrorMessage}</p>
        {/if}
      </SectionFrame>

      <SectionFrame title="현재 스킬 덱" description="카드를 클릭하면 현재 덱에서 1장 제거합니다.">
        <div class="editor-page__deck-summary">
          <TagChip label={`${totalCards}장`} tone={editorState.type === 'PLAYER' && totalCards === 12 ? 'success' : 'muted'} />
          <TagChip label={getDeckTypeDisplayLabel(editorState.type)} tone="accent" />
        </div>

        {#if expandedDeckCards.length === 0}
          <ContentStatePanel title="현재 덱이 비어 있습니다" message="아래 카드 후보에서 카드를 클릭해 추가하세요." />
        {:else}
          <div class="editor-page__deck-rail" aria-label="현재 스킬 덱 카드 목록">
            {#each expandedDeckCards as item (item.key)}
              <button type="button" class="editor-page__deck-card" aria-label={`${item.card?.name ?? item.cardId} 1장 제거`} onclick={() => removeDeckCardCopy(item.cardId)} disabled={Boolean(pendingActionId)}>
                <div class="editor-page__card-chrome">
                  <strong>{item.card?.cost ?? '-'}</strong>
                  <span>{item.card ? getCardTypeLabel(item.card.type) : 'UNKNOWN'}</span>
                </div>
                <div class="editor-page__card-art"><p>{item.cardId}</p></div>
                <div class="editor-page__card-copy">
                  <h4>{item.card?.name ?? item.cardId}</h4>
                  <p>현재 덱 {deckCountById.get(item.cardId) ?? 0}장</p>
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </SectionFrame>

      <SectionFrame title="보유 카드 목록" description={editorState.type === 'PLAYER' ? '선택한 캐릭터가 보유한 SKILL 카드만 표시합니다.' : '에너미 덱은 모든 카드가 후보입니다.'}>
        {#if cardsLoading}
          <ContentStatePanel title="카드 목록 로딩 중" message="카드 후보를 불러오는 중입니다." />
        {:else if cardsErrorMessage}
          <ContentStatePanel title="카드 목록을 불러오지 못했습니다" message={cardsErrorMessage} tone="error" actionLabel="다시 불러오기" onAction={() => void loadReferenceData()} />
        {:else if editorState.type === 'PLAYER' && !selectedCharacter}
          <ContentStatePanel title="대상 캐릭터를 선택하세요" message="캐릭터 덱은 선택한 캐릭터의 보유 카드만 후보로 표시합니다." />
        {:else if candidateCards.length === 0}
          <ContentStatePanel title="표시할 카드가 없습니다" message="현재 조건에 맞는 카드 후보가 없습니다." />
        {:else}
          <div class="editor-page__candidate-toolbar">
            <p>{candidateCards.length}종 중 {candidateCardPage} / {candidatePageCount} 페이지</p>
            <div class="editor-page__pager">
              <button type="button" onclick={() => (candidateCardPage = Math.max(1, candidateCardPage - 1))} disabled={candidateCardPage <= 1}>이전</button>
              <button type="button" onclick={() => (candidateCardPage = Math.min(candidatePageCount, candidateCardPage + 1))} disabled={candidateCardPage >= candidatePageCount}>다음</button>
            </div>
          </div>

          <div class="editor-page__candidate-grid">
            {#each pagedCandidateCards as candidate (candidate.card.id)}
              <button type="button" class="editor-page__candidate-card" class:editor-page__candidate-card--disabled={candidate.disabled} aria-label={`${candidate.card.name} 1장 추가`} onclick={() => addDeckCardCopy(candidate.card.id)} disabled={Boolean(pendingActionId) || candidate.disabled}>
                <div class="editor-page__card-chrome">
                  <strong>{candidate.card.cost ?? '-'}</strong>
                  <span>{getCardTypeLabel(candidate.card.type)}</span>
                </div>
                <div class="editor-page__card-art"><p>{candidate.card.id}</p></div>
                <div class="editor-page__card-copy">
                  <h4>{candidate.card.name}</h4>
                  <p>{buildCardMeta(candidate.card)}</p>
                  <p>{summarizeDescription(candidate.card.description)}</p>
                </div>
                <div class="editor-page__card-tags">
                  <TagChip label={getCardTypeLabel(candidate.card.type)} tone={getCardTypeTone(candidate.card.type)} />
                  <TagChip label={`덱 ${candidate.deckCount}장`} tone="muted" />
                  {#if candidate.ownedCount !== null}
                    <TagChip label={`보유 ${candidate.ownedCount}장`} tone={candidate.disabled ? 'warning' : 'success'} />
                  {/if}
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </SectionFrame>

      <SectionFrame title="검증 결과" description="최종 규칙 판정은 서버 검증 결과를 사용합니다.">
        {#if localValidationState}
          <div class="editor-page__validation">
            <div class="editor-page__validation-summary">
              <TagChip label={localValidationState.valid ? '유효' : '오류 있음'} tone={localValidationState.valid ? 'success' : 'warning'} />
              <TagChip label={`총 ${localValidationState.normalizedTotalCards}장`} tone="muted" />
              <TagChip label={`${localValidationState.issues.length}개 문제`} tone={localValidationState.issues.length ? 'warning' : 'success'} />
              {#if localValidationState.isLocallyStale}
                <TagChip label="검증 이후 변경됨" tone="warning" />
              {/if}
            </div>
            {#if localValidationState.issues.length > 0}
              <ul>
                {#each localValidationState.issues as issue}
                  <li><strong>{issue.code}</strong> {issue.message}</li>
                {/each}
              </ul>
            {:else}
              <p>검증 문제가 없습니다.</p>
            {/if}
          </div>
        {:else}
          <p class="editor-page__muted">아직 검증 결과가 없습니다.</p>
        {/if}
      </SectionFrame>

      <SectionFrame title="덱 작업" description="덱을 검증하거나 저장합니다.">
        <div class="editor-page__actions">
          <button type="button" onclick={() => void runValidateDraft()} disabled={Boolean(pendingActionId)}>
            {pendingActionId === 'deckEditor.validateDraft' ? '덱 검증 중...' : '덱 검증'}
          </button>
          <button type="button" class="editor-page__primary-action" onclick={() => void runAction(primaryActionId)} disabled={Boolean(pendingActionId) || !primaryAction?.enabled}>
            {pendingActionId === primaryActionId ? (screen.mode === 'create' ? '덱 생성 중...' : '덱 저장 중...') : (screen.mode === 'create' ? '덱 생성' : '덱 저장')}
          </button>
          <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>덱 목록으로</a>
          {#if deleteAction}
            {#if deleteConfirmOpen}
              <button type="button" class="editor-page__danger-action" onclick={() => void runAction('deckEditor.delete')} disabled={Boolean(pendingActionId)}>삭제 확인</button>
              <button type="button" onclick={cancelDeleteConfirmation} disabled={Boolean(pendingActionId)}>취소</button>
            {:else}
              <button type="button" class="editor-page__danger-action" onclick={openDeleteConfirmation} disabled={Boolean(pendingActionId) || !deleteAction.enabled}>덱 삭제</button>
            {/if}
          {/if}
        </div>
        {#if actionErrorMessage}
          <p class="editor-page__error">{actionErrorMessage}</p>
        {/if}
        {#if actionSuccessMessage}
          <p class="editor-page__success">{actionSuccessMessage}</p>
        {/if}
      </SectionFrame>

      <SectionFrame title="디버그 UI" description="실제 저장/검증 요청에 사용할 JSON을 확인합니다.">
        <button type="button" class="editor-page__debug-toggle" onclick={() => (debugOpen = !debugOpen)}>
          {debugOpen ? '디버그 UI 닫기' : '디버그 UI 열기'}
        </button>
        {#if debugOpen}
          <textarea class="editor-page__debug-json" readonly value={debugPayload}></textarea>
        {/if}
      </SectionFrame>
    </div>
  {:else}
    <SectionFrame eyebrow="덱 선택" title={deckEditorStateCopy.selectionTitle} description={deckEditorStateCopy.selectionDescription}>
      <div class="editor-page__note">
        <p>{deckEditorStateCopy.selectionRouteMessage}</p>
        <p>{deckEditorStateCopy.selectionActionMessage}</p>
      </div>
      <div class="editor-page__actions">
        <a class="editor-page__link-action" data-nav href={pathBuilders.deckList()}>덱 목록으로</a>
      </div>
    </SectionFrame>
  {/if}
</div>

<style>
  .editor-page,
  .editor-page__layout,
  .editor-page__validation,
  .editor-page__card-copy {
    display: grid;
    gap: 1.25rem;
  }

  .editor-page__intro-row,
  .editor-page__target-row,
  .editor-page__candidate-toolbar,
  .editor-page__actions,
  .editor-page__deck-summary,
  .editor-page__validation-summary,
  .editor-page__card-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    align-items: center;
  }

  .editor-page__intro-row,
  .editor-page__target-row {
    justify-content: space-between;
  }

  .editor-page__intro-copy {
    display: grid;
    gap: 0.35rem;
    min-width: min(100%, 20rem);
  }

  .editor-page__intro-copy p,
  .editor-page__intro-copy h3,
  .editor-page__intro-copy span,
  .editor-page__card-copy h4,
  .editor-page__card-copy p,
  .editor-page__candidate-toolbar p,
  .editor-page__validation p,
  .editor-page__note p,
  .editor-page__error,
  .editor-page__success,
  .editor-page__muted {
    margin: 0;
  }

  .editor-page__intro-copy p {
    color: var(--color-text-muted);
    font-size: 0.76rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .editor-page__intro-copy h3 {
    font-family: var(--font-display);
    font-size: 1.6rem;
  }

  .editor-page__field {
    display: grid;
    gap: 0.4rem;
    min-width: min(100%, 18rem);
  }

  .editor-page__field span {
    color: var(--color-text-muted);
    font-size: 0.76rem;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  .editor-page__field input,
  .editor-page__field select,
  .editor-page__debug-json {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    font: inherit;
    padding: 0.7rem 0.8rem;
  }

  .editor-page__deck-rail {
    display: flex;
    gap: 0.9rem;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    padding-bottom: 0.25rem;
  }

  .editor-page__deck-card,
  .editor-page__candidate-card {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.24);
    color: var(--color-text);
    cursor: pointer;
    display: grid;
    gap: 0.75rem;
    padding: 0.85rem;
    text-align: left;
  }

  .editor-page__deck-card {
    flex: 0 0 13.5rem;
  }

  .editor-page__candidate-card:hover,
  .editor-page__candidate-card:focus-visible,
  .editor-page__deck-card:hover,
  .editor-page__deck-card:focus-visible {
    border-color: rgba(226, 193, 155, 0.52);
    outline: none;
  }

  .editor-page__candidate-card--disabled,
  .editor-page__candidate-card:disabled,
  .editor-page__deck-card:disabled {
    cursor: not-allowed;
    opacity: 0.62;
  }

  .editor-page__candidate-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    grid-auto-rows: minmax(18rem, auto);
    gap: 0.9rem;
    min-height: 37rem;
  }

  .editor-page__card-chrome {
    align-items: center;
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
  }

  .editor-page__card-chrome strong,
  .editor-page__card-chrome span {
    border: 1px solid var(--color-border);
    background: rgba(8, 7, 6, 0.42);
    display: inline-flex;
    min-height: 1.8rem;
    align-items: center;
    padding: 0.15rem 0.5rem;
    font-size: 0.72rem;
  }

  .editor-page__card-art {
    align-items: flex-end;
    aspect-ratio: 4 / 3;
    background: rgba(226, 193, 155, 0.08);
    border: 1px solid rgba(226, 193, 155, 0.14);
    display: flex;
    padding: 0.75rem;
  }

  .editor-page__card-art p {
    color: var(--color-text-muted);
    font-size: 0.72rem;
    margin: 0;
    word-break: break-all;
  }

  .editor-page__card-copy {
    gap: 0.35rem;
  }

  .editor-page__card-copy p,
  .editor-page__intro-copy span,
  .editor-page__candidate-toolbar p,
  .editor-page__validation p,
  .editor-page__note p,
  .editor-page__muted {
    color: var(--color-text-muted);
    line-height: 1.55;
  }

  .editor-page__pager,
  .editor-page__actions {
    margin-left: auto;
  }

  .editor-page__pager {
    display: flex;
    gap: 0.5rem;
  }

  .editor-page__actions button,
  .editor-page__link-action,
  .editor-page__debug-toggle,
  .editor-page__pager button {
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
    min-height: 2.75rem;
    padding: 0.65rem 0.95rem;
  }

  .editor-page__primary-action,
  .editor-page__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .editor-page__danger-action {
    border-color: rgba(224, 161, 151, 0.46) !important;
    color: rgb(224, 161, 151) !important;
  }

  .editor-page__validation ul {
    margin: 0;
    padding-left: 1.15rem;
  }

  .editor-page__debug-json {
    min-height: 14rem;
    max-height: 24rem;
    resize: vertical;
  }

  .editor-page__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .editor-page__error {
    color: rgb(224, 161, 151);
  }

  .editor-page__success {
    color: var(--color-accent);
  }

  @media (max-width: 1080px) {
    .editor-page__candidate-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 720px) {
    .editor-page__candidate-grid {
      grid-template-columns: 1fr;
      min-height: auto;
    }

    .editor-page__pager,
    .editor-page__actions {
      margin-left: 0;
    }
  }
</style>
