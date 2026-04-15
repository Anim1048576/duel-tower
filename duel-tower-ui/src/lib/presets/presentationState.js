/** @typedef {import('./editorModel').PresetEditorState} PresetEditorState */
/** @typedef {import('../api/screenTypes').PresetEditorDraftDto} PresetEditorDraftDto */
/** @typedef {import('../api/screenTypes').PresetEditorResolvedDto} PresetEditorResolvedDto */
/** @typedef {import('../components/EntityListPane.svelte').EntityListItem} EntityListItem */
/** @typedef {import('../components/EntityListPane.svelte').EntityListTag} EntityListTag */

/**
 * PresetEditor local presentation helpers stay in the editor-UX layer.
 * They mirror the current local draft for title/dirty/preview display without
 * recreating server-side preset resolution or action-enable rules.
 * Resolved preview metadata remains a server responsibility; local fallbacks only
 * keep the editor visually consistent until the next screen refresh.
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
  return (values ?? [])
    .map((value) => normalizeText(value))
    .filter(Boolean)
}

/**
 * @param {readonly { label: string; tone?: string }[] | null | undefined} tags
 * @returns {EntityListTag[]}
 */
function mapTags(tags) {
  return (tags ?? []).map((tag) => ({
    label: tag.label,
    tone:
      tag.tone === 'accent' || tag.tone === 'muted' || tag.tone === 'success' || tag.tone === 'warning'
        ? tag.tone
        : undefined,
  }))
}

/**
 * @param {readonly { id: string; label: string; subtitle: string; meta: string; tags: readonly { label: string; tone?: string }[] }[]} resolvedItems
 */
function buildResolvedItemQueues(resolvedItems) {
  /** @type {Map<string, Array<{ id: string; label: string; subtitle: string; meta: string; tags: readonly { label: string; tone?: string }[] }>>} */
  const queues = new Map()

  for (const item of resolvedItems) {
    const normalizedId = normalizeText(item.id)

    if (!normalizedId) {
      continue
    }

    const queue = queues.get(normalizedId)

    if (queue) {
      queue.push(item)
      continue
    }

    queues.set(normalizedId, [item])
  }

  return queues
}

/**
 * @param {Map<string, Array<{ id: string; label: string; subtitle: string; meta: string; tags: readonly { label: string; tone?: string }[] }>>} queues
 * @param {string} identifier
 */
function shiftResolvedItem(queues, identifier) {
  const queue = queues.get(identifier)

  if (!queue?.length) {
    return null
  }

  return queue.shift() ?? null
}

/**
 * @param {'deck' | 'passive'} kind
 * @param {string} identifier
 * @param {number} index
 * @returns {EntityListItem}
 */
function buildFallbackPreviewItem(kind, identifier, index) {
  const kindLabel = kind === 'deck' ? 'Deck card' : 'Passive'

  return {
    id: `${kind}:${identifier}:${index}`,
    title: identifier,
    subtitle: `${kindLabel} id in the current local draft`,
    meta: `Entry ${index + 1}`,
    note: 'Resolved metadata refreshes after save, create, or clone.',
    tags: /** @type {EntityListTag[]} */ ([{ label: 'Local Draft', tone: 'warning' }]),
  }
}

/**
 * @param {readonly string[] | null | undefined} identifiers
 * @param {readonly { id: string; label: string; subtitle: string; meta: string; tags: readonly { label: string; tone?: string }[] }[]} resolvedItems
 * @param {'deck' | 'passive'} kind
 * @returns {EntityListItem[]}
 */
function buildPreviewItems(identifiers, resolvedItems, kind) {
  const normalizedIdentifiers = normalizeIdentifierList(identifiers)
  const queues = buildResolvedItemQueues(resolvedItems)

  return normalizedIdentifiers.map((identifier, index) => {
    const resolvedItem = shiftResolvedItem(queues, identifier)

    if (!resolvedItem) {
      return buildFallbackPreviewItem(kind, identifier, index)
    }

    return {
      id: `${kind}:${identifier}:${index}`,
      title: resolvedItem.label,
      subtitle: resolvedItem.subtitle,
      meta: resolvedItem.meta,
      tags: mapTags(resolvedItem.tags),
    }
  })
}

/**
 * @param {'character' | 'ex'} kind
 * @param {number | string | null} localId
 * @param {number | string | null} sourceId
 * @param {string} sourceLabel
 * @param {string} sourceSubtitle
 * @param {readonly { label: string; tone?: string }[]} sourceTags
 * @returns {{ label: string; subtitle: string; tags: EntityListTag[] }}
 */
function buildReferencePreview(kind, localId, sourceId, sourceLabel, sourceSubtitle, sourceTags) {
  const localIdentifier =
    kind === 'character' ? normalizeCharacterId(localId) : normalizeText(/** @type {string | null | undefined} */ (localId))
  const sourceIdentifier =
    kind === 'character' ? normalizeCharacterId(sourceId) : normalizeText(/** @type {string | null | undefined} */ (sourceId))

  if (localIdentifier !== null && localIdentifier === sourceIdentifier) {
    return {
      label: sourceLabel,
      subtitle: sourceSubtitle,
      tags: mapTags(sourceTags),
    }
  }

  if (localIdentifier === null || localIdentifier === '') {
    return {
      label: kind === 'character' ? 'No character selected' : 'No EX card selected',
      subtitle: 'Select a value in the local draft to refresh this preview.',
      tags: /** @type {EntityListTag[]} */ ([{ label: 'Local Draft', tone: 'muted' }]),
    }
  }

  const kindLabel = kind === 'character' ? 'Character' : 'EX'

  return {
    label: `${kindLabel} ${localIdentifier}`,
    subtitle: 'Resolved metadata refreshes after save, create, or clone.',
    tags: /** @type {EntityListTag[]} */ ([{ label: 'Local Draft', tone: 'warning' }]),
  }
}

/**
 * @param {PresetEditorState} state
 * @param {PresetEditorDraftDto} draft
 */
export function isPresetEditorPreviewLocallyStale(state, draft) {
  if (normalizeCharacterId(state.characterId) !== normalizeCharacterId(draft.characterId)) {
    return true
  }

  if (normalizeText(state.exCardId) !== normalizeText(draft.exCardId)) {
    return true
  }

  const localDeckIds = normalizeIdentifierList(state.deckCardIds)
  const draftDeckIds = normalizeIdentifierList(draft.deckCardIds)

  if (localDeckIds.length !== draftDeckIds.length) {
    return true
  }

  for (const [index, identifier] of localDeckIds.entries()) {
    if (identifier !== draftDeckIds[index]) {
      return true
    }
  }

  const localPassiveIds = normalizeIdentifierList(state.passiveIds)
  const draftPassiveIds = normalizeIdentifierList(draft.passiveIds)

  if (localPassiveIds.length !== draftPassiveIds.length) {
    return true
  }

  for (const [index, identifier] of localPassiveIds.entries()) {
    if (identifier !== draftPassiveIds[index]) {
      return true
    }
  }

  return false
}

/**
 * @param {PresetEditorState} state
 * @param {PresetEditorDraftDto} draft
 */
export function isPresetEditorLocalDirty(state, draft) {
  if (normalizeText(state.name) !== normalizeText(draft.name)) {
    return true
  }

  return isPresetEditorPreviewLocallyStale(state, draft)
}

/**
 * @param {PresetEditorState} state
 * @param {PresetEditorDraftDto} draft
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

  return mode === 'create' ? 'New preset' : 'Preset detail'
}

/** @param {PresetEditorState} state */
export function buildPresetEditorLocalSummary(state) {
  const characterLabel = normalizeCharacterId(state.characterId)
  const exLabel = normalizeText(state.exCardId)
  const deckCount = normalizeIdentifierList(state.deckCardIds).length
  const passiveCount = normalizeIdentifierList(state.passiveIds).length

  return `${characterLabel ? `Character ${characterLabel}` : 'No character'}, ${deckCount} deck cards, ${passiveCount} passives, EX ${exLabel || 'none'}.`
}

/**
 * @param {PresetEditorState} state
 * @param {PresetEditorDraftDto} draft
 * @param {PresetEditorResolvedDto} resolved
 * @param {'create' | 'edit'} mode
 * @param {boolean} dirty
 */
export function createPresetEditorLocalPresentation(state, draft, resolved, mode, dirty) {
  const deckCardIds = normalizeIdentifierList(state.deckCardIds)
  const passiveIds = normalizeIdentifierList(state.passiveIds)

  return {
    title: getPresetEditorLocalTitle(state, draft, mode),
    summary: buildPresetEditorLocalSummary(state),
    dirty,
    previewNeedsResolveRefresh: isPresetEditorPreviewLocallyStale(state, draft),
    deckCount: deckCardIds.length,
    passiveCount: passiveIds.length,
    character: buildReferencePreview(
      'character',
      state.characterId,
      draft.characterId,
      resolved.characterLabel,
      resolved.characterSubtitle,
      resolved.characterTags,
    ),
    ex: buildReferencePreview(
      'ex',
      state.exCardId,
      draft.exCardId,
      resolved.exLabel,
      resolved.exSubtitle,
      resolved.exTags,
    ),
    deckItems: buildPreviewItems(deckCardIds, resolved.deckItems, 'deck'),
    passiveItems: buildPreviewItems(passiveIds, resolved.passiveItems, 'passive'),
  }
}
