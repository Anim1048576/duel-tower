/**
 * PresetEditor local presentation helpers stay in the editor-UX layer.
 * They mirror the current local draft for labels, summaries, and dirty markers
 * without recreating server-side preset validation.
 */

/** @param {string | null | undefined} value */
function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/** @param {unknown} value */
function normalizeCharacterId(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null
}

/** @param {readonly string[] | null | undefined} values */
function normalizeIdentifierList(values) {
  return (values ?? []).map((value) => normalizeText(value)).filter(Boolean)
}

/**
 * @param {readonly string[]} left
 * @param {readonly string[]} right
 */
function areStringListsEqual(left, right) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

/**
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} state
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} draft
 */
export function isPresetEditorLocalDirty(state, draft) {
  return (
    normalizeText(state.name) !== normalizeText(draft.name) ||
    normalizeCharacterId(state.characterId) !== normalizeCharacterId(draft.characterId) ||
    normalizeText(state.exCardId) !== normalizeText(draft.exCardId) ||
    !areStringListsEqual(normalizeIdentifierList(state.deckCardIds), normalizeIdentifierList(draft.deckCardIds)) ||
    !areStringListsEqual(normalizeIdentifierList(state.passiveIds), normalizeIdentifierList(draft.passiveIds))
  )
}

/**
 * @param {{ name: string }} state
 * @param {{ name: string }} draft
 * @param {'create' | 'edit'} mode
 */
export function getPresetEditorLocalTitle(state, draft, mode) {
  const localName = normalizeText(state.name)
  if (localName) {
    return localName
  }

  const draftName = normalizeText(draft.name)
  if (draftName) {
    return draftName
  }

  return mode === 'create' ? 'New preset' : 'Untitled preset'
}

/** @param {{ characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} state */
export function buildPresetEditorLocalSummary(state) {
  const characterLabel = normalizeCharacterId(state.characterId) ?? 'none'
  const deckCount = normalizeIdentifierList(state.deckCardIds).length
  const passiveCount = normalizeIdentifierList(state.passiveIds).length
  const exLabel = normalizeText(state.exCardId) || 'none'
  return `Character ${characterLabel}, ${deckCount} deck cards, ${passiveCount} passives, EX ${exLabel}.`
}

/**
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} state
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} draft
 */
export function isPresetEditorPreviewLocallyStale(state, draft) {
  return isPresetEditorLocalDirty(
    { ...state, name: normalizeText(draft.name) },
    { ...draft, name: normalizeText(draft.name) },
  )
}

/**
 * @param {{ id: string, label: string, subtitle?: string, meta?: string, tags?: readonly { label: string, tone?: string }[] }} item
 */
function toListItem(item) {
  return {
    id: item.id,
    title: item.label,
    subtitle: item.subtitle ?? '',
    meta: item.meta ?? '',
    tags: [...(item.tags ?? [])],
  }
}

/**
 * @param {readonly { id: string, label: string, subtitle?: string, meta?: string, tags?: readonly { label: string, tone?: string }[] }[]} resolvedItems
 * @param {string} id
 * @param {number} index
 * @param {string} prefix
 */
function resolveListItem(resolvedItems, id, index, prefix) {
  const resolved = resolvedItems.find((item) => normalizeText(item.id) === id)
  if (resolved) {
    return toListItem(resolved)
  }

  return {
    id: `${prefix}:${id}:${index}`,
    title: id,
    subtitle: 'Local draft id',
    meta: `Entry ${index + 1}`,
    note: '저장 후 갱신됩니다.',
    tags: [{ label: 'Unresolved', tone: 'warning' }],
  }
}

/**
 * @param {{ label: string, subtitle?: string, tags?: readonly { label: string, tone?: string }[] }} input
 */
function buildResolvedPreview(input) {
  return {
    label: input.label,
    subtitle: input.subtitle ?? '',
    tags: [...(input.tags ?? [])],
  }
}

/**
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} state
 * @param {{ name: string, characterId: number | null, deckCardIds: string[], exCardId: string, passiveIds: string[] }} draft
 * @param {{
 *   characterLabel: string,
 *   characterSubtitle?: string,
 *   characterTags?: readonly { label: string, tone?: string }[],
 *   exLabel: string,
 *   exSubtitle?: string,
 *   exTags?: readonly { label: string, tone?: string }[],
 *   deckItems: readonly { id: string, label: string, subtitle?: string, meta?: string, tags?: readonly { label: string, tone?: string }[] }[],
 *   passiveItems: readonly { id: string, label: string, subtitle?: string, meta?: string, tags?: readonly { label: string, tone?: string }[] }[],
 * }} resolved
 * @param {'create' | 'edit'} mode
 * @param {boolean} previewResolved
 */
export function createPresetEditorLocalPresentation(state, draft, resolved, mode, previewResolved) {
  const previewNeedsResolveRefresh = Boolean(previewResolved) && isPresetEditorPreviewLocallyStale(state, draft)
  const characterId = normalizeCharacterId(state.characterId)
  const exCardId = normalizeText(state.exCardId)

  return {
    dirty: isPresetEditorLocalDirty(state, draft),
    title: getPresetEditorLocalTitle(state, draft, mode),
    summary: buildPresetEditorLocalSummary(state),
    previewNeedsResolveRefresh,
    character:
      characterId === normalizeCharacterId(draft.characterId)
        ? buildResolvedPreview({
            label: resolved.characterLabel,
            subtitle: resolved.characterSubtitle,
            tags: resolved.characterTags,
          })
        : {
            label: characterId === null ? 'No character selected' : `Character ${characterId}`,
            subtitle: 'Local draft character id',
            tags: [{ label: 'Draft', tone: 'muted' }],
          },
    ex:
      exCardId === normalizeText(draft.exCardId)
        ? buildResolvedPreview({
            label: resolved.exLabel,
            subtitle: resolved.exSubtitle,
            tags: resolved.exTags,
          })
        : {
            label: exCardId ? `EX ${exCardId}` : 'No EX selected',
            subtitle: 'Local draft EX id',
            tags: [{ label: 'Draft', tone: 'muted' }],
          },
    deckItems: normalizeIdentifierList(state.deckCardIds).map((id, index) =>
      resolveListItem(resolved.deckItems, id, index, 'deck'),
    ),
    passiveItems: normalizeIdentifierList(state.passiveIds).map((id, index) =>
      resolveListItem(resolved.passiveItems, id, index, 'passive'),
    ),
  }
}
