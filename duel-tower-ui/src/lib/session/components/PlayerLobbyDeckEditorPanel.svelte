<script lang="ts">
  import ContentStatePanel from '../../components/ContentStatePanel.svelte'
  import SearchFilterBar from '../../components/SearchFilterBar.svelte'
  import TagChip from '../../components/TagChip.svelte'
  import type { PlayerLobbyDeckEditorStateDto } from '../../api/screenTypes'
  import PlayerLobbyCurrentDeckPanel from './PlayerLobbyCurrentDeckPanel.svelte'
  import PlayerLobbyDeckPoolPanel from './PlayerLobbyDeckPoolPanel.svelte'

  type TagTone = 'accent' | 'muted' | 'success' | 'warning'

  type DeckEntryItem = {
    key: string
    ownedCardId: string
    cardId: string
    title: string
    subtitle: string
    tags: { label: string; tone: TagTone }[]
    canRemove: boolean
    reasonCodes: string[]
    lockedInDeck: boolean
    inSavedDeck: boolean
    previewPending: boolean
    unresolved: boolean
  }

  type DeckPoolGroupItem = {
    key: string
    cardId: string
    title: string
    subtitle: string
    tags: { label: string; tone: TagTone }[]
    currentDeckCount: number
    totalOwnedCount: number
    availableOwnedCount: number
    canAdd: boolean
    reasonCodes: string[]
    ownedCards: {
      key: string
      ownedCardId: string
      cardId: string
      title: string
      subtitle: string
      tags: { label: string; tone: TagTone }[]
      inDraftDeck: boolean
      canAdd: boolean
      reasonCodes: string[]
      unresolved: boolean
    }[]
  }

  let {
    deckEditor,
    currentDeckEntries,
    cardPoolGroups,
    controlsDisabled,
    previewPending,
    previewErrorMessage,
    previewStale,
    onRetryPreview,
    onAddOwnedCard,
    onRemoveOwnedCard,
  }: {
    deckEditor: PlayerLobbyDeckEditorStateDto | null
    currentDeckEntries: DeckEntryItem[]
    cardPoolGroups: DeckPoolGroupItem[]
    controlsDisabled: boolean
    previewPending: boolean
    previewErrorMessage: string | null
    previewStale: boolean
    onRetryPreview: (() => void) | null
    onAddOwnedCard: (ownedCardId: string) => void
    onRemoveOwnedCard: (ownedCardId: string) => void
  } = $props()

  let searchQuery = $state('')

  function formatReasonCode(reasonCode: string) {
    return reasonCode
      .split('_')
      .filter(Boolean)
      .map((token) => token.charAt(0) + token.slice(1).toLowerCase())
      .join(' ')
  }

  const filteredCurrentDeckEntries = $derived.by(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) {
      return currentDeckEntries
    }
    return currentDeckEntries.filter((entry) =>
      [entry.title, entry.subtitle, entry.cardId, entry.ownedCardId].some((value) =>
        value.toLowerCase().includes(query),
      ),
    )
  })

  const filteredCardPoolGroups = $derived.by(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) {
      return cardPoolGroups
    }
    return cardPoolGroups.filter((group) =>
      [group.title, group.subtitle, group.cardId].some((value) => value.toLowerCase().includes(query)) ||
      group.ownedCards.some((ownedCard) =>
        [ownedCard.title, ownedCard.subtitle, ownedCard.cardId, ownedCard.ownedCardId].some((value) =>
          value.toLowerCase().includes(query),
        ),
      ),
    )
  })

  const summary = $derived.by(() => {
    if (!deckEditor) {
      return previewPending
        ? '덱 미리보기를 불러오는 중입니다.'
        : '표시할 덱 미리보기가 없습니다.'
    }
    return `Deck ${deckEditor.deck.draftDeckSize}/${deckEditor.deck.requiredDeckSize} cards | Changed ${deckEditor.deck.changedCardCount} | ${deckEditor.deck.saveAllowed ? 'Save allowed' : 'Save blocked'}`
  })
</script>

<div class="player-lobby-deck-editor-panel">
  <SearchFilterBar
    query={searchQuery}
    queryPlaceholder="Search current deck or card pool"
    summary={summary}
    onQueryChange={(value) => {
      searchQuery = value
    }}
  />

  {#if previewErrorMessage}
    <ContentStatePanel
      title={previewStale ? 'Preview refresh failed, showing last server result' : 'Preview refresh failed'}
      message={previewStale
        ? `${previewErrorMessage} 이전 미리보기를 표시합니다.`
        : previewErrorMessage}
      tone="error"
      actionLabel={onRetryPreview ? 'Retry preview' : undefined}
      onAction={onRetryPreview ?? undefined}
    />
  {/if}

  {#if previewPending}
    <ContentStatePanel
      title="Preview refreshing"
      message={previewStale
        ? '미리보기를 새로고침하는 중입니다.'
        : '덱 상태를 불러오는 중입니다.'}
    />
  {/if}

  {#if deckEditor}
    <div class="player-lobby-deck-editor-panel__status">
      <TagChip label={`Deck ${deckEditor.deck.draftDeckSize}/${deckEditor.deck.requiredDeckSize}`} tone={deckEditor.deck.saveAllowed ? 'success' : 'warning'} />
      <TagChip label={`Changed ${deckEditor.deck.changedCardCount}`} tone="accent" />
      <TagChip label={deckEditor.deck.saveAllowed ? 'Save allowed' : 'Save blocked'} tone={deckEditor.deck.saveAllowed ? 'success' : 'warning'} />
      {#each deckEditor.globalReasonCodes as reasonCode}
        <TagChip label={formatReasonCode(reasonCode)} tone="warning" />
      {/each}
    </div>
  {/if}

  <div class="player-lobby-deck-editor-panel__grid">
    <PlayerLobbyCurrentDeckPanel
      entries={filteredCurrentDeckEntries}
      {controlsDisabled}
      onRemove={onRemoveOwnedCard}
    />

    <PlayerLobbyDeckPoolPanel
      groups={filteredCardPoolGroups}
      {controlsDisabled}
      {previewPending}
      onAdd={onAddOwnedCard}
    />
  </div>
</div>

<style>
  .player-lobby-deck-editor-panel {
    display: grid;
    gap: 1rem;
  }

  .player-lobby-deck-editor-panel__status {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .player-lobby-deck-editor-panel__grid {
    display: grid;
    grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
    gap: 1rem;
    align-items: start;
  }

  @media (max-width: 980px) {
    .player-lobby-deck-editor-panel__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
