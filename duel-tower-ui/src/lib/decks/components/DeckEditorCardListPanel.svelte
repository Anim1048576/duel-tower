<script lang="ts">
  import EntityListPane from '../../components/EntityListPane.svelte'
  import SectionFrame from '../../components/SectionFrame.svelte'
  import type { DeckCardItem } from '../deckEditorView'

  let {
    items,
    selectedCardKey,
    controlsDisabled,
    onSelect,
  }: {
    items: DeckCardItem[]
    selectedCardKey: string
    controlsDisabled: boolean
    onSelect: (id: string) => void
  } = $props()
</script>

<SectionFrame
  title="Deck cards"
  description="현재 덱 카드 목록입니다."
>
  <EntityListPane
    {items}
    selectedId={selectedCardKey}
    onSelect={(id) => {
      if (!controlsDisabled) {
        onSelect(id)
      }
    }}
    emptyMessage="No cards are assigned to this deck yet."
  />
</SectionFrame>
