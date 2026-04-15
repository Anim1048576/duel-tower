<script lang="ts">
  import ContentStatePanel from '../../components/ContentStatePanel.svelte'
  import SectionFrame from '../../components/SectionFrame.svelte'
  import StatBlock from '../../components/StatBlock.svelte'
  import TagChip from '../../components/TagChip.svelte'
  import type {
    DeckEditorActionId,
    DeckEditorScreenAction,
    DeckEditorScreenResponse,
    DeckEditorValidationDto,
  } from '../../api/screenTypes'
  import { pathBuilders } from '../../navigation'

  type LocalPresentation = {
    title: string
    dirty: boolean
  }

  let {
    screen,
    validationState,
    localPresentation,
    pendingActionId,
    deleteConfirmOpen,
    actionErrorMessage,
    actionSuccessMessage,
    validateAction,
    saveAction,
    createAction,
    deleteAction,
    onRunAction,
    onOpenDeleteConfirmation,
    onCancelDeleteConfirmation,
  }: {
    screen: DeckEditorScreenResponse
    validationState: DeckEditorValidationDto | null
    localPresentation: LocalPresentation | null
    pendingActionId: DeckEditorActionId | null
    deleteConfirmOpen: boolean
    actionErrorMessage: string | null
    actionSuccessMessage: string | null
    validateAction: DeckEditorScreenAction | null
    saveAction: DeckEditorScreenAction | null
    createAction: DeckEditorScreenAction | null
    deleteAction: DeckEditorScreenAction | null
    onRunAction: (actionId: DeckEditorActionId) => void
    onOpenDeleteConfirmation: () => void
    onCancelDeleteConfirmation: () => void
  } = $props()

  function getPendingActionLabel(actionId: DeckEditorActionId) {
    switch (actionId) {
      case 'deckEditor.validate':
        return 'Validating deck...'
      case 'deckEditor.save':
        return 'Saving deck...'
      case 'deckEditor.create':
        return 'Creating deck...'
      case 'deckEditor.delete':
        return 'Deleting deck...'
    }
  }

  function getActionLabel(action: DeckEditorScreenAction | null) {
    if (!action) {
      return ''
    }

    return pendingActionId === action.id ? getPendingActionLabel(action.id) : action.label
  }

  function getDisabledReason(action: DeckEditorScreenAction | null) {
    return action?.disabledReason?.userMessage ?? null
  }
</script>

<SectionFrame
  title="Editor actions"
  description={screen.mode === 'create'
    ? 'Create a new deck record from the current local input state.'
    : 'Validate, save, or delete by invoking the screen-declared actions.'}
>
  <div class="controls-panel__actions">
    <a class="controls-panel__link-action" data-nav href={pathBuilders.deckList()}>
      Back to deck list
    </a>

    {#if validateAction}
      <button
        type="button"
        disabled={Boolean(pendingActionId) || deleteConfirmOpen || !validateAction.enabled}
        onclick={() => onRunAction(validateAction.id)}
      >
        {getActionLabel(validateAction)}
      </button>
    {/if}

    {#if screen.mode === 'create'}
      {#if createAction}
        <button
          type="button"
          disabled={Boolean(pendingActionId) || !createAction.enabled}
          onclick={() => onRunAction(createAction.id)}
        >
          {getActionLabel(createAction)}
        </button>
      {/if}
    {:else}
      {#if saveAction}
        <button
          type="button"
          disabled={Boolean(pendingActionId) || deleteConfirmOpen || !saveAction.enabled}
          onclick={() => onRunAction(saveAction.id)}
        >
          {getActionLabel(saveAction)}
        </button>
      {/if}
      {#if deleteAction}
        <button
          type="button"
          disabled={Boolean(pendingActionId) || !deleteAction.enabled}
          onclick={onOpenDeleteConfirmation}
        >
          {pendingActionId === 'deckEditor.delete'
            ? getPendingActionLabel('deckEditor.delete')
            : deleteConfirmOpen
              ? 'Delete pending'
              : deleteAction.label}
        </button>
      {/if}
    {/if}
  </div>

  <div class="controls-panel__status">
    <p>Current draft title: {localPresentation?.title ?? screen.derived.title}</p>
    <p>Current local dirty flag: {localPresentation?.dirty ? 'Changed' : 'Synced'}</p>
    <p>Current validation state: {validationState?.valid ? 'Valid' : 'Invalid'}</p>
    <p>Validation issues and action metadata remain sourced from the latest server response.</p>
    {#if getDisabledReason(validateAction)}
      <p>{getDisabledReason(validateAction)}</p>
    {/if}
    {#if getDisabledReason(saveAction)}
      <p>{getDisabledReason(saveAction)}</p>
    {/if}
    {#if getDisabledReason(createAction)}
      <p>{getDisabledReason(createAction)}</p>
    {/if}
    {#if getDisabledReason(deleteAction)}
      <p>{getDisabledReason(deleteAction)}</p>
    {/if}
  </div>

  {#if actionErrorMessage}
    <ContentStatePanel
      title="Deck action failed"
      message={actionErrorMessage}
      tone="error"
    />
  {:else if actionSuccessMessage}
    <ContentStatePanel
      title="Deck action complete"
      message={actionSuccessMessage}
    />
  {/if}

  {#if deleteConfirmOpen}
    <ContentStatePanel
      title="Delete this deck?"
      message="This deck will be removed from the archive. This action cannot be undone."
      tone="error"
    >
      <div class="controls-panel__confirm-actions">
        <button type="button" onclick={() => onRunAction('deckEditor.delete')}>Confirm delete</button>
        <button type="button" onclick={onCancelDeleteConfirmation}>Cancel</button>
      </div>
    </ContentStatePanel>
  {/if}
</SectionFrame>

<SectionFrame
  title="Validation"
  description="The panel renders the validation block carried by the latest screen model."
>
  <div class="controls-panel__validation">
    <div class="controls-panel__validation-header">
      <div class="controls-panel__validation-copy">
        <p>Validation result</p>
        <h3>{validationState?.valid ? 'Deck draft is valid' : 'Deck draft has validation issues'}</h3>
      </div>

      <div class="controls-panel__hero-tags">
        <TagChip
          label={validationState?.valid ? 'Valid' : 'Invalid'}
          tone={validationState?.valid ? 'success' : 'warning'}
        />
        <TagChip label={`Normalized ${validationState?.normalizedTotalCards ?? 0}`} tone="accent" />
        {#if validationState?.isStale}
          <TagChip label="Stale" tone="muted" />
        {/if}
      </div>
    </div>

    <div class="controls-panel__stats controls-panel__stats--compact">
      <StatBlock
        value={validationState?.valid ? 'Valid' : 'Invalid'}
        label="Draft state"
        note={validationState?.isStale
          ? 'The current local draft differs from the last validated draft'
          : 'Validation matches the current local draft'}
      />
      <StatBlock
        value={validationState?.normalizedTotalCards ?? 0}
        label="Normalized cards"
        note="Total cards reported by server-side validation"
      />
      <StatBlock
        value={validationState?.issues.length ?? 0}
        label="Issues"
        note={(validationState?.issues.length ?? 0)
          ? 'Server-reported validation issues'
          : 'No validation issues were returned'}
      />
    </div>

    {#if (validationState?.issues.length ?? 0)}
      <ul class="controls-panel__validation-list">
        {#each validationState?.issues ?? [] as issue}
          <li>
            <p>{issue.message}</p>
            <span>{issue.field ? `${issue.field} | ${issue.code}` : issue.code}</span>
          </li>
        {/each}
      </ul>
    {:else}
      <div class="controls-panel__note">
        <p>The validation endpoint did not return any issues for the current deck card draft.</p>
      </div>
    {/if}
  </div>
</SectionFrame>

<style>
  .controls-panel__actions,
  .controls-panel__confirm-actions,
  .controls-panel__hero-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }

  .controls-panel__link-action,
  .controls-panel__actions button,
  .controls-panel__confirm-actions button {
    min-height: 3rem;
    padding: 0.75rem 1rem;
    border: 1px solid var(--color-border);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(12, 11, 10, 0.28);
    color: var(--color-text);
  }

  .controls-panel__link-action {
    border-color: rgba(226, 193, 155, 0.42);
    background: linear-gradient(180deg, rgba(226, 193, 155, 0.18), rgba(226, 193, 155, 0.08));
  }

  .controls-panel__status,
  .controls-panel__validation,
  .controls-panel__validation-copy,
  .controls-panel__note {
    display: grid;
    gap: 0.5rem;
  }

  .controls-panel__status {
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid var(--color-border);
  }

  .controls-panel__status p,
  .controls-panel__validation-copy p,
  .controls-panel__validation-copy h3,
  .controls-panel__validation-list p,
  .controls-panel__validation-list span,
  .controls-panel__note p {
    margin: 0;
  }

  .controls-panel__status p,
  .controls-panel__note p {
    color: var(--color-text-muted);
    line-height: 1.6;
  }

  .controls-panel__validation-header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .controls-panel__validation-copy {
    gap: 1rem;
  }

  .controls-panel__validation-copy p {
    color: var(--color-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-size: 0.76rem;
  }

  .controls-panel__validation-copy h3 {
    font-family: var(--font-display);
    font-size: 1.4rem;
    line-height: 1.15;
  }

  .controls-panel__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .controls-panel__stats--compact {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .controls-panel__validation-list {
    margin: 0;
    padding-left: 1.25rem;
    display: grid;
    gap: 0.85rem;
  }

  .controls-panel__validation-list li {
    display: grid;
    gap: 0.3rem;
  }

  .controls-panel__validation-list p {
    color: var(--color-text);
    line-height: 1.55;
  }

  .controls-panel__validation-list span {
    color: var(--color-text-muted);
    font-size: 0.88rem;
  }

  .controls-panel__note {
    border-top: 1px solid var(--color-border);
    padding-top: 1rem;
  }

  @media (max-width: 960px) {
    .controls-panel__stats,
    .controls-panel__stats--compact {
      grid-template-columns: 1fr;
    }
  }
</style>
