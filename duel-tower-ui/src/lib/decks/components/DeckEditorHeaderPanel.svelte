<script lang="ts">
  import SectionFrame from '../../components/SectionFrame.svelte'
  import StatBlock from '../../components/StatBlock.svelte'
  import TagChip from '../../components/TagChip.svelte'
  import type { DeckType } from '../../api/deckTypes'
  import type { DeckEditorScreenResponse } from '../../api/screenTypes'
  import { getDeckTypeTone } from '../deckEditorView'

  let {
    screen,
    title,
    deckType,
    deckTypeLabel,
    summary,
    totalCards,
    draftEntries,
    editorName,
    editorType,
    deckTypeOptions,
    controlsDisabled,
    onNameInput,
    onTypeChange,
  }: {
    screen: DeckEditorScreenResponse
    title: string
    deckType: DeckType | ''
    deckTypeLabel: string
    summary: string
    totalCards: number
    draftEntries: number
    editorName: string
    editorType: DeckType | ''
    deckTypeOptions: DeckType[]
    controlsDisabled: boolean
    onNameInput: (value: string) => void
    onTypeChange: (value: DeckType) => void
  } = $props()
</script>

<SectionFrame
  eyebrow={screen.mode === 'create' ? 'New Deck' : 'Selected Deck'}
  {title}
  description={screen.mode === 'create'
    ? '새 덱을 생성합니다.'
    : '덱을 수정하고 저장합니다.'}
>
  <div class="editor-header__hero">
    <div class="editor-header__hero-copy">
      <p>{deckTypeLabel}</p>
      <h3>{summary}</h3>
    </div>

    <div class="editor-header__hero-tags">
      <TagChip label={deckTypeLabel} tone={getDeckTypeTone(deckType)} />
      <TagChip label={`${draftEntries} Entries`} tone="accent" />
      <TagChip label={draftEntries ? 'Editing Draft' : 'Empty Draft'} tone={draftEntries ? 'warning' : 'muted'} />
    </div>
  </div>

  <div class="editor-header__stats">
    <StatBlock
      value={totalCards}
      label="Cards"
      note="Total cards from the current local draft"
    />
    <StatBlock
      value={draftEntries}
      label="Draft entries"
      note="현재 카드 수"
    />
    <StatBlock
      value={deckTypeLabel}
      label="Deck type"
      note="현재 덱 타입"
    />
  </div>

  <fieldset class="editor-header__fieldset">
    <legend>Deck metadata</legend>

    <div class="editor-header__form-grid">
      <label class="editor-header__field editor-header__field--span-2">
        <span>Deck name</span>
        <input
          type="text"
          value={editorName}
          placeholder="Enter deck name"
          disabled={controlsDisabled}
          oninput={(event) => onNameInput((event.currentTarget as HTMLInputElement).value)}
        />
      </label>

      <label class="editor-header__field">
        <span>Deck type</span>
        <select
          value={editorType}
          disabled={controlsDisabled}
          onchange={(event) => onTypeChange((event.currentTarget as HTMLSelectElement).value as DeckType)}
        >
          {#each deckTypeOptions as option}
            <option value={option}>{option}</option>
          {/each}
        </select>
      </label>

      <div class="editor-header__field">
        <span>{screen.mode === 'create' ? 'Deck id' : 'Source deck id'}</span>
        <p class="editor-header__readonly">{screen.deckId == null ? 'Assigned after create' : screen.deckId}</p>
      </div>
    </div>
  </fieldset>

  {#if screen.uiNotices.length}
    <div class="editor-header__note">
      {#each screen.uiNotices as notice}
        <p>{notice}</p>
      {/each}
    </div>
  {/if}
</SectionFrame>

<style>
  .editor-header__hero {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .editor-header__hero-copy {
    display: grid;
    gap: 0.5rem;
    max-width: 42rem;
  }

  .editor-header__hero-copy p,
  .editor-header__hero-copy h3,
  .editor-header__note p {
    margin: 0;
  }

  .editor-header__hero-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-size: 0.78rem;
  }

  .editor-header__hero-copy h3 {
    font-family: var(--font-display);
    font-size: clamp(1.8rem, 2.6vw, 2.4rem);
    line-height: 1.1;
  }

  .editor-header__hero-tags,
  .editor-header__stats {
    display: grid;
    gap: 1rem;
  }

  .editor-header__hero-tags {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .editor-header__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .editor-header__fieldset {
    display: grid;
    gap: 1rem;
    border: 1px solid var(--color-border);
    padding: 1rem;
    margin: 0;
  }

  .editor-header__fieldset legend {
    padding: 0 0.5rem;
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .editor-header__form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1rem;
  }

  .editor-header__field {
    display: grid;
    gap: 0.5rem;
  }

  .editor-header__field--span-2 {
    grid-column: span 2;
  }

  .editor-header__field span {
    color: var(--color-text-muted);
    font-size: 0.82rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .editor-header__field input,
  .editor-header__field select {
    min-height: 3rem;
    width: 100%;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.3);
    color: var(--color-text);
    padding: 0.75rem 0.9rem;
    font: inherit;
  }

  .editor-header__readonly {
    min-height: 3rem;
    display: flex;
    align-items: center;
    padding: 0.75rem 0.9rem;
    margin: 0;
    border: 1px solid var(--color-border);
    background: rgba(12, 11, 10, 0.18);
    color: var(--color-text-soft);
  }

  .editor-header__note {
    display: grid;
    gap: 1rem;
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  .editor-header__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .editor-header__stats,
    .editor-header__form-grid {
      grid-template-columns: 1fr;
    }

    .editor-header__field--span-2 {
      grid-column: span 1;
    }
  }
</style>
