<script lang="ts">
  import ContentStatePanel from '../lib/components/ContentStatePanel.svelte'
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { onMount } from 'svelte'
  import { createSession, joinSession, startDebugSoloCombat } from '../lib/api/sessions'
  import { getApiErrorMessage } from '../lib/api/types'
  import { authState } from '../lib/auth/authState.svelte'
  import { pathBuilders } from '../lib/navigation'
  import {
    readSessionPageFeedback,
    sessionEntryStateCopy,
    type SessionPageFeedback,
    setSessionPageFeedback,
  } from '../lib/session/pageState'
  import {
    normalizeSessionCode,
    setStoredSessionAccess,
  } from '../lib/session/access'
  import {
    removeSelectionHandoff,
    selectionHandoffKeys,
    setSelectionHandoff,
  } from '../lib/selectionHandoff'

  type EntryActionId = 'join-by-code' | 'create-gm-session' | 'route-handoff'

  type EntryActionItem = {
    id: EntryActionId
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }

  const entryActions = [
    {
      id: 'join-by-code',
      title: 'Join by session code',
      subtitle: 'Player entry',
      meta: 'POST /api/sessions/{code}/join',
      note: '현재 계정으로 플레이어 로비에 입장합니다.',
      tags: [
        { label: 'Player', tone: 'accent' },
        { label: 'Login session', tone: 'success' },
      ],
    },
    {
      id: 'create-gm-session',
      title: 'Create GM session',
      subtitle: 'GM control entry',
      meta: 'POST /api/sessions',
      note: '현재 계정으로 GM 세션을 만듭니다.',
      tags: [
        { label: 'GM', tone: 'warning' },
        { label: 'Live API', tone: 'success' },
      ],
    },
    {
      id: 'route-handoff',
      title: 'Route and runtime handoff',
      subtitle: 'Session access storage',
      meta: '다음 로비에 code와 token 저장',
      note: '세션 코드와 권한 토큰을 저장합니다.',
      tags: [
        { label: 'Code-first', tone: 'accent' },
        { label: 'Session storage', tone: 'muted' },
      ],
    },
  ] satisfies EntryActionItem[]

  let query = $state('')
  let sessionCode = $state('')
  let selectedId = $state<EntryActionId>(entryActions[0].id)
  let pendingAction = $state<'create' | 'join' | 'debug' | null>(null)
  let formErrorMessage = $state<string | null>(null)
  let apiErrorMessage = $state<string | null>(null)
  let feedback = $state<SessionPageFeedback | null>(null)

  const filteredActions = $derived.by(() => {
    const normalized = query.trim().toLowerCase()

    if (!normalized) {
      return entryActions
    }

    return entryActions.filter((item) =>
      [item.title, item.subtitle, item.meta, item.note].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  })

  const selectedAction = $derived.by(
    () => filteredActions.find((item) => item.id === selectedId) ?? filteredActions[0] ?? null,
  )

  const joinPending = $derived.by(() => pendingAction === 'join')
  const createPending = $derived.by(() => pendingAction === 'create')
  const debugPending = $derived.by(() => pendingAction === 'debug')
  const actionPending = $derived.by(() => pendingAction !== null)
  const currentUsername = $derived.by(() => authState.user?.username?.trim() ?? '')
  const filteredActionSummary = $derived.by(() =>
    `${filteredActions.length}개 작업 표시 중`,
  )
  const selectedActionPlayerPath = $derived.by(() =>
    pathBuilders.sessionLobbyPlayer(sessionCode.trim() ? normalizeSessionCode(sessionCode) : undefined),
  )

  function clearErrors() {
    formErrorMessage = null
    apiErrorMessage = null
  }

  function navigateTo(path: string, replace = false) {
    if (typeof window === 'undefined') {
      return
    }

    window.history[replace ? 'replaceState' : 'pushState']({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function persistSessionCode(sessionCode: string) {
    setSelectionHandoff(selectionHandoffKeys.sessionCode, sessionCode)
    removeSelectionHandoff(selectionHandoffKeys.sessionId)
  }

  function handleSelectAction(id: string) {
    if (id === 'join-by-code' || id === 'create-gm-session' || id === 'route-handoff') {
      selectedId = id
    }
  }

  function handleUseSelectedCode() {
    if (!selectedAction || selectedAction.id !== 'join-by-code') {
      return
    }

    const normalizedCode = sessionCode.trim()

    if (!normalizedCode) {
      return
    }

    sessionCode = normalizeSessionCode(normalizedCode)
  }

  async function handleCreateSession() {
    if (actionPending || authState.loading) {
      return
    }

    const username = currentUsername

    clearErrors()

    if (!username) {
      formErrorMessage = 'Sign in again before creating a GM session.'
      return
    }

    pendingAction = 'create'

    try {
      const response = await createSession({
        gmId: username,
      })
      const nextCode = normalizeSessionCode(response.code)

      setStoredSessionAccess({
        code: nextCode,
        role: 'gm',
        gmToken: response.gmToken,
      })
      persistSessionCode(nextCode)
      setSessionPageFeedback(sessionEntryStateCopy.createdFeedback)
      navigateTo(pathBuilders.sessionLobbyGm(nextCode), true)
    } catch (error) {
      apiErrorMessage = getApiErrorMessage(error, 'Unable to create a new session.')
    } finally {
      pendingAction = null
    }
  }

  async function handleJoinSession() {
    if (actionPending || authState.loading) {
      return
    }

    const username = currentUsername
    const normalizedCode = normalizeSessionCode(sessionCode)

    clearErrors()

    if (!username) {
      formErrorMessage = '다시 로그인해 주세요.'
      return
    }

    if (!normalizedCode) {
      formErrorMessage = 'Enter a session code before joining.'
      return
    }

    pendingAction = 'join'
    sessionCode = normalizedCode

    try {
      const response = await joinSession(normalizedCode, {
        playerId: username,
        characterId: null,
        passiveIds: [],
      })
      const nextCode = normalizeSessionCode(response.state.sessionCode || normalizedCode)

      setStoredSessionAccess({
        code: nextCode,
        role: 'player',
        playerToken: response.playerToken,
        playerId: username,
      })
      persistSessionCode(nextCode)
      setSessionPageFeedback(sessionEntryStateCopy.joinedFeedback)
      navigateTo(pathBuilders.sessionLobbyPlayer(nextCode), true)
    } catch (error) {
      apiErrorMessage = getApiErrorMessage(error, 'Unable to join the requested session.')
    } finally {
      pendingAction = null
    }
  }

  async function handleDebugSoloCombat() {
    if (actionPending) {
      return
    }

    clearErrors()
    pendingAction = 'debug'

    try {
      const response = await startDebugSoloCombat()
      const nextCode = normalizeSessionCode(response.sessionCode)

      setStoredSessionAccess({
        code: nextCode,
        role: 'player',
        playerToken: response.playerToken,
        playerId: response.gmPlayerId,
      })
      persistSessionCode(nextCode)
      navigateTo(response.redirectUrl || pathBuilders.combat(nextCode), true)
    } catch (error) {
      apiErrorMessage = getApiErrorMessage(error, '디버그 전투를 시작하지 못했습니다.')
    } finally {
      pendingAction = null
    }
  }

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    void handleJoinSession()
  }

  onMount(() => {
    feedback = readSessionPageFeedback()
  })
</script>

<div class="session-entry-page">
  <SectionFrame
    eyebrow="Session Overview"
    title="Session Entry"
    description="세션을 만들거나 코드로 참가합니다."
  >
    <div class="session-entry-page__stats">
      <StatBlock
        value={authState.isAuthenticated ? 'Live' : 'Locked'}
        label="Entry mode"
        note="Uses the authenticated account"
      />
      <StatBlock
        value="Code"
        label="Primary route key"
        note="Session code drives the next lobby URL"
      />
      <StatBlock
        value="Token"
        label="Runtime access"
        note="GM or player token is stored after success"
      />
    </div>

    {#if feedback}
      <ContentStatePanel
        title={feedback.title}
        message={feedback.message}
      />
    {/if}

    {#if formErrorMessage || apiErrorMessage}
      <ContentStatePanel
        title="Session entry is unavailable"
        message={apiErrorMessage ?? formErrorMessage ?? 'Unable to continue.'}
        tone="error"
      />
    {/if}
  </SectionFrame>

  <div class="session-entry-page__top">
    <SectionFrame
      title="Join by code"
      description="세션 코드를 입력해 참가합니다."
    >
      <form class="session-entry-page__form" onsubmit={handleSubmit} aria-busy={joinPending}>
        <fieldset class="session-entry-page__fieldset" disabled={actionPending || authState.loading}>
          <label class="session-entry-page__field">
            <span>Session Code</span>
            <input
              bind:value={sessionCode}
              name="sessionCode"
              placeholder="TOWER-EMBER-01"
            />
          </label>

          <div class="session-entry-page__actions">
            <button type="submit">{joinPending ? 'Joining...' : 'Join session'}</button>
            <TagChip label="Live join API" tone="success" />
          </div>
        </fieldset>
      </form>

      <p class="session-entry-page__feedback">
        참가 후 플레이어 로비로 이동합니다.
      </p>
    </SectionFrame>

    <SectionFrame
      title="Create GM session"
      description="새 세션을 만들고 GM 로비로 이동합니다."
    >
      <div class="session-entry-page__guide">
        <p>Signed-in account: {currentUsername || 'Unavailable'}</p>
        <p>생성 후 세션 코드와 GM 토큰을 저장합니다.</p>
        <p>세션 코드를 기준으로 이동합니다.</p>
      </div>

      <div class="session-entry-page__actions">
        <button
          type="button"
          onclick={() => void handleCreateSession()}
          disabled={actionPending || authState.loading}
        >
          {createPending ? 'Creating...' : 'Create GM session'}
        </button>
        <TagChip label="Live create API" tone="warning" />
      </div>
    </SectionFrame>

    {#if import.meta.env.DEV}
      <SectionFrame
        title="디버그 전투"
        description="GM 플레이어와 GM 조종 NPC를 포함한 테스트 전투를 바로 시작합니다."
      >
        <div class="session-entry-page__guide">
          <p>개발 환경에서만 표시됩니다.</p>
          <p>기존 세션 생성, 로드아웃, 전투 시작 흐름을 사용합니다.</p>
        </div>

        <div class="session-entry-page__actions">
          <button
            type="button"
            onclick={() => void handleDebugSoloCombat()}
            disabled={actionPending}
          >
            {debugPending ? '디버그 전투 시작 중...' : '디버그 전투 바로 시작'}
          </button>
          <TagChip label="Debug API" tone="warning" />
        </div>
      </SectionFrame>
    {/if}
  </div>

  <SectionFrame
    title="Session entry flow"
    description="세션 작업을 확인합니다."
  >
    <SearchFilterBar
      query={query}
      queryPlaceholder="Search entry actions"
      summary={filteredActionSummary}
      onQueryChange={(value) => (query = value)}
    >
      {#snippet filters()}
        <TagChip label="All" tone="accent" />
        <TagChip label="Player" tone="accent" />
        <TagChip label="GM" tone="warning" />
      {/snippet}

      {#snippet sort()}
        <TagChip label="API" tone="muted" />
        <TagChip label="Runtime" tone="muted" />
      {/snippet}
    </SearchFilterBar>

    <div class="session-entry-page__bottom">
      <EntityListPane
        items={filteredActions}
        selectedId={selectedId}
        onSelect={handleSelectAction}
        emptyMessage="검색 결과가 없습니다."
      />

      <SectionFrame
        title="Selected action"
        description="선택한 작업을 실행합니다."
      >
        {#if selectedAction}
          <div class="session-entry-page__detail">
            <div>
              <h3>{selectedAction.title}</h3>
              <p>{selectedAction.subtitle}</p>
            </div>

            <div class="session-entry-page__detail-tags">
              {#each selectedAction.tags ?? [] as tag}
                <TagChip label={tag.label} tone={tag.tone} />
              {/each}
            </div>

            <p>{selectedAction.meta}</p>
            <p>{selectedAction.note}</p>

            {#if selectedAction.id === 'join-by-code'}
              <div class="session-entry-page__actions">
                <button
                  type="button"
                  onclick={() => void handleJoinSession()}
                  disabled={actionPending || authState.loading}
                >
                  {joinPending ? 'Joining...' : 'Join with current code'}
                </button>
                <a
                  class="session-entry-page__link-action session-entry-page__link-action--muted"
                  data-nav
                  href={selectedActionPlayerPath}
                  onclick={(event) => {
                    event.preventDefault()
                    handleUseSelectedCode()
                  }}
                >
                  Normalize current code
                </a>
              </div>
            {:else if selectedAction.id === 'create-gm-session'}
              <div class="session-entry-page__actions">
                <button
                  type="button"
                  onclick={() => void handleCreateSession()}
                  disabled={actionPending || authState.loading}
                >
                  {createPending ? 'Creating...' : 'Create and open GM lobby'}
                </button>
              </div>
            {:else}
              <div class="session-entry-page__todo">
                <p>저장 값은 `code`, `role`, 역할별 token입니다.</p>
                <p>`sessionCode`만 유지합니다.</p>
              </div>
            {/if}
          </div>
        {:else}
          <p class="session-entry-page__empty">선택한 작업이 없습니다.</p>
        {/if}
      </SectionFrame>
    </div>
  </SectionFrame>
</div>

<style>
  .session-entry-page,
  .session-entry-page__top,
  .session-entry-page__bottom,
  .session-entry-page__guide,
  .session-entry-page__detail,
  .session-entry-page__todo,
  .session-entry-page__form {
    display: grid;
    gap: 1.5rem;
  }

  .session-entry-page__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .session-entry-page__top {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .session-entry-page__bottom {
    grid-template-columns: minmax(0, 1.2fr) minmax(19rem, 0.8fr);
    align-items: start;
  }

  .session-entry-page__fieldset {
    border: 0;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 1.5rem;
  }

  .session-entry-page__field {
    display: grid;
    gap: 0.45rem;
  }

  .session-entry-page__field span {
    color: var(--color-text-muted);
    font-size: 0.78rem;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .session-entry-page__field input {
    min-height: 3rem;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.42);
    padding: 0.85rem 0.95rem;
    outline: none;
  }

  .session-entry-page__field input:focus {
    border-color: rgba(255, 179, 175, 0.4);
  }

  .session-entry-page__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    align-items: center;
  }

  .session-entry-page__actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    color: var(--color-text);
  }

  .session-entry-page__feedback,
  .session-entry-page__guide p,
  .session-entry-page__detail p,
  .session-entry-page__todo p,
  .session-entry-page__empty {
    margin: 0;
    color: var(--color-text-soft);
    line-height: 1.7;
  }

  .session-entry-page__detail h3 {
    margin: 0;
    font-family: var(--font-display);
    font-size: 1.5rem;
  }

  .session-entry-page__detail > div:first-child p {
    color: var(--color-text-soft);
  }

  .session-entry-page__detail-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .session-entry-page__link-action {
    min-height: 3rem;
    width: fit-content;
    padding: 0.75rem 1rem;
    border: 1px solid rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
    display: inline-flex;
    align-items: center;
    color: var(--color-text);
  }

  .session-entry-page__link-action--muted {
    border-color: var(--color-border);
    background: rgba(12, 11, 10, 0.28);
  }

  .session-entry-page__todo {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  @media (max-width: 960px) {
    .session-entry-page__stats,
    .session-entry-page__top,
    .session-entry-page__bottom {
      grid-template-columns: 1fr;
    }
  }
</style>
