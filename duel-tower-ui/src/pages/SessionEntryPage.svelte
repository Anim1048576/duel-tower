<script lang="ts">
  import EntityListPane from '../lib/components/EntityListPane.svelte'
  import SearchFilterBar from '../lib/components/SearchFilterBar.svelte'
  import SectionFrame from '../lib/components/SectionFrame.svelte'
  import StatBlock from '../lib/components/StatBlock.svelte'
  import TagChip from '../lib/components/TagChip.svelte'
  import { pathBuilders } from '../lib/navigation'
  import { selectionHandoffKeys, setSelectionHandoff } from '../lib/selectionHandoff'

  const sessions = [
    {
      id: 'session-ember-01',
      code: 'TOWER-EMBER-01',
      title: 'Ember Table 01',
      subtitle: 'GM: Archive Keeper',
      meta: '4 / 6 joined · Preparation phase',
      note: 'Open table for standard expedition setup.',
      tags: [
        { label: 'Open', tone: 'success' },
        { label: 'Standard', tone: 'muted' },
      ],
    },
    {
      id: 'session-night-02',
      code: 'TOWER-NIGHT-02',
      title: 'Night Watch 02',
      subtitle: 'GM: Mira',
      meta: '3 / 6 joined · Draft phase',
      note: 'Balanced table with shared deck review before start.',
      tags: [
        { label: 'Open', tone: 'success' },
        { label: 'Draft', tone: 'warning' },
      ],
    },
    {
      id: 'session-sealed-03',
      code: 'TOWER-SEALED-03',
      title: 'Sealed Tower 03',
      subtitle: 'GM: Ashen Knight',
      meta: '6 / 6 joined · Ready to launch',
      note: 'Visible for status only. Entry is currently closed.',
      tags: [
        { label: 'Closed', tone: 'warning' },
        { label: 'Full', tone: 'muted' },
      ],
    },
  ] satisfies Array<{
    id: string
    code: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: { label: string; tone?: 'accent' | 'muted' | 'success' | 'warning' }[]
  }>

  let query = $state('')
  let sessionCode = $state('')
  let selectedId = $state(sessions[0]?.id ?? '')
  let feedbackVisible = $state(false)

  const filteredSessions = $derived.by(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return sessions

    return sessions.filter((item) =>
      [item.title, item.subtitle, item.meta, item.note].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  })

  const selectedSession = $derived.by(
    () => filteredSessions.find((item) => item.id === selectedId) ?? filteredSessions[0] ?? null,
  )

  function persistSelectedSession(session: { id: string; code: string }) {
    setSelectionHandoff(selectionHandoffKeys.sessionId, session.id)
    setSelectionHandoff(selectionHandoffKeys.sessionCode, session.code)
  }

  function handleSelectSession(id: string) {
    selectedId = id

    const nextSession = sessions.find((item) => item.id === id)
    if (!nextSession) return

    persistSelectedSession(nextSession)
  }

  function handleOpenPlayerLobby() {
    if (!selectedSession) return
    persistSelectedSession(selectedSession)
  }

  function handleOpenGmLobby() {
    if (!selectedSession) return
    persistSelectedSession(selectedSession)
  }

  const selectedPlayerLobbyPath = $derived.by(() =>
    pathBuilders.sessionLobbyPlayer(selectedSession?.code),
  )

  const selectedGmLobbyPath = $derived.by(() =>
    pathBuilders.sessionLobbyGm(selectedSession?.code),
  )

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    feedbackVisible = true

    // TODO: Connect session code submission to the session entry API.
  }
</script>

<div class="session-entry-page">
  <SectionFrame
    eyebrow="Session Overview"
    title="세션 입장 준비"
    description="코드 입력, 입장 가능한 세션 목록, 그리고 안내 영역을 함께 수용하는 진입 구조를 먼저 만듭니다."
  >
    <div class="session-entry-page__stats">
      <StatBlock value="2" label="Open tables" note="Available to join now" />
      <StatBlock value="1" label="Closed table" note="Visible but locked" />
      <StatBlock value="6" label="Party cap" note="Designed for six-player expansion" />
    </div>
  </SectionFrame>

  <div class="session-entry-page__top">
    <SectionFrame
      title="코드로 입장"
      description="직접 전달받은 세션 코드를 입력하는 흐름입니다."
    >
      <form class="session-entry-page__form" onsubmit={handleSubmit}>
        <label class="session-entry-page__field">
          <span>Session Code</span>
          <input
            bind:value={sessionCode}
            name="sessionCode"
            placeholder="TOWER-EMBER-01"
          />
        </label>

        <div class="session-entry-page__actions">
          <button type="submit">입장 시도</button>
          <TagChip label="TODO API" tone="warning" />
        </div>
      </form>

      {#if feedbackVisible}
        <p class="session-entry-page__feedback">
          실제 세션 입장 요청은 아직 연결되지 않았습니다. TODO 위치에 입장 API를 연결하면
          됩니다.
        </p>
      {/if}
    </SectionFrame>

    <SectionFrame
      title="입장 안내"
      description="세션 입장 전 사용자에게 보여줄 고정 가이드를 위한 자리입니다."
    >
      <div class="session-entry-page__guide">
        <p>1. 세션 코드를 직접 입력하거나 아래 목록에서 공개 세션을 선택합니다.</p>
        <p>2. 실제 인증/권한 검사는 이후 API 계약에 맞춰 연결합니다.</p>
        <p>3. 다음 배치에서 플레이어 로비와 GM 로비로 흐름을 분기합니다.</p>
      </div>
    </SectionFrame>
  </div>

  <SectionFrame
    title="입장 가능한 세션"
    description="Session Entry Stitch 화면 기준으로, 공개 세션을 코드 입력과 함께 검토할 수 있는 목록 영역입니다."
  >
    <SearchFilterBar
      query={query}
      queryPlaceholder="Search sessions"
      summary={`${filteredSessions.length}개의 세션이 현재 목록에 표시됩니다.`}
      onQueryChange={(value) => (query = value)}
    >
      {#snippet filters()}
        <TagChip label="All" tone="accent" />
        <TagChip label="Open" tone="success" />
        <TagChip label="Closed" tone="warning" />
      {/snippet}

      {#snippet sort()}
        <TagChip label="Players" tone="muted" />
        <TagChip label="Phase" tone="muted" />
      {/snippet}
    </SearchFilterBar>

    <div class="session-entry-page__bottom">
      <EntityListPane
        items={filteredSessions}
        selectedId={selectedId}
        onSelect={handleSelectSession}
        emptyMessage="입장 가능한 세션이 없습니다."
      />

      <SectionFrame
        title="선택된 세션"
        description="선택한 세션의 요약과 다음 단계 연결 위치를 표시합니다."
      >
        {#if selectedSession}
          <div class="session-entry-page__detail">
            <div>
              <h3>{selectedSession.title}</h3>
              <p>{selectedSession.subtitle}</p>
            </div>

            <div class="session-entry-page__detail-tags">
              {#each selectedSession.tags ?? [] as tag}
                <TagChip label={tag.label} tone={tag.tone} />
              {/each}
            </div>

            <p>{selectedSession.meta}</p>
            <p>{selectedSession.note}</p>

            <a
              class="session-entry-page__link-action"
              data-nav
              href={selectedPlayerLobbyPath}
              onclick={handleOpenPlayerLobby}
            >
              Open player lobby for {selectedSession.title}
            </a>

            <a
              class="session-entry-page__link-action session-entry-page__link-action--muted"
              data-nav
              href={selectedGmLobbyPath}
              onclick={handleOpenGmLobby}
            >
              Open GM lobby for {selectedSession.title}
            </a>

            <div class="session-entry-page__todo">
              <p>
                TODO: Remove the legacy fixed lobby route fallback after /sessions/:code/player and
                /sessions/:code/gm are the only entry paths.
              </p>
              <p>TODO: Connect session entry and lobby selection to the session API contract.</p>
              <p>TODO: Remove mock session availability data after live lobby state is available.</p>
            </div>
          </div>
        {:else}
          <p class="session-entry-page__empty">표시할 세션이 없습니다.</p>
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
