/** @typedef {import('../api/screenTypes').PlayerLobbyScreenResponse} PlayerLobbyScreenResponse */
/** @typedef {import('../api/screenTypes').PlayerLobbyOptionDto} PlayerLobbyOptionDto */
/** @typedef {import('../api/screenTypes').PlayerLobbyPreviewItemDto} PlayerLobbyPreviewItemDto */
/** @typedef {import('../api/screenTypes').PlayerLobbyTagDto} PlayerLobbyTagDto */
/** @typedef {import('./loadoutEditor').SessionLoadoutDraft} SessionLoadoutDraft */
/** @typedef {import('../components/EntityListPane.svelte').EntityListItem} EntityListItem */
/** @typedef {import('../components/EntityListPane.svelte').EntityListTag} EntityListTag */

/**
 * PlayerLobby local presentation helpers stay in the editor-UX layer.
 * They keep the current local draft visually aligned with the form while reusing
 * server-curated reference options and preset preview snapshots.
 * This is not game-rule resolution; it is editor-style presentation state.
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
 * @param {readonly string[]} source
 * @param {readonly string[]} draft
 */
function areStringListsEqual(source, draft) {
  if (source.length !== draft.length) {
    return false
  }

  for (const [index, sourceValue] of source.entries()) {
    if (sourceValue !== draft[index]) {
      return false
    }
  }

  return true
}

/**
 * Local dirty for PlayerLobby is editor presentation state.
 * It only answers whether the current local loadout input matches the last
 * server-synced draft snapshot; it does not resolve references or validate rules.
 *
 * @param {Pick<SessionLoadoutDraft, 'characterId' | 'deckOwnedCardIds' | 'exCardId' | 'passiveIds'>} source
 * @param {Pick<SessionLoadoutDraft, 'characterId' | 'deckOwnedCardIds' | 'exCardId' | 'passiveIds'>} draft
 */
function isPlayerLobbyDraftLocallyDirty(source, draft) {
  if (source.characterId !== draft.characterId) {
    return true
  }

  if (normalizeText(source.exCardId) !== normalizeText(draft.exCardId)) {
    return true
  }

  if (
    !areStringListsEqual(
      normalizeIdentifierList(source.deckOwnedCardIds),
      normalizeIdentifierList(draft.deckOwnedCardIds),
    )
  ) {
    return true
  }

  if (
    !areStringListsEqual(
      normalizeIdentifierList(source.passiveIds),
      normalizeIdentifierList(draft.passiveIds),
    )
  ) {
    return true
  }

  return false
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
 * @param {readonly PlayerLobbyOptionDto[]} options
 * @param {number | null} characterId
 */
function findCharacterOption(options, characterId) {
  if (characterId === null) {
    return null
  }

  return options.find((option) => normalizeText(option.id) === String(characterId)) ?? null
}

/**
 * @param {readonly PlayerLobbyOptionDto[]} options
 * @param {string} value
 */
function findOption(options, value) {
  const normalized = normalizeText(value)
  if (!normalized) {
    return null
  }

  return options.find((option) => normalizeText(option.id) === normalized) ?? null
}

/**
 * @param {PlayerLobbyOptionDto | null} option
 * @param {string} emptyLabel
 * @param {string} missingKind
 */
function buildReferencePreview(option, emptyLabel, missingKind) {
  if (option) {
    return {
      label: option.label,
      subtitle: option.subtitle,
      tags: mapTags(option.tags),
    }
  }

  if (emptyLabel) {
    return {
      label: emptyLabel,
      subtitle: `Select a ${missingKind} in the local draft.`,
      tags: /** @type {EntityListTag[]} */ ([{ label: 'Draft', tone: 'muted' }]),
    }
  }

  return {
    label: `${missingKind} (unresolved)`,
    subtitle: 'The current local draft refers to a value that is not available in the latest server references.',
    tags: /** @type {EntityListTag[]} */ ([{ label: 'Unresolved', tone: 'warning' }]),
  }
}

/**
 * @param {readonly string[]} identifiers
 * @param {readonly PlayerLobbyOptionDto[]} options
 * @returns {EntityListItem[]}
 */
function buildPassiveItems(identifiers, options) {
  return identifiers.map((passiveId, index) => {
    const option = findOption(options, passiveId)

    if (!option) {
      return {
        id: `passive:${passiveId}:${index}`,
        title: passiveId,
        subtitle: 'Passive id in the current local draft',
        meta: `Entry ${index + 1}`,
        note: 'This passive id is not available in the latest server reference options.',
        tags: /** @type {EntityListTag[]} */ ([{ label: 'Unresolved', tone: 'warning' }]),
      }
    }

    return {
      id: `passive:${option.id}:${index}`,
      title: option.label,
      subtitle: option.subtitle,
      meta: `Entry ${index + 1}`,
      tags: mapTags(option.tags),
    }
  })
}

/**
 * @param {readonly PlayerLobbyPreviewItemDto[]} items
 * @param {string} kind
 * @returns {EntityListItem[]}
 */
function mapPresetPreviewItems(items, kind) {
  return (items ?? []).map((item, index) => ({
    id: `${kind}:${item.id}:${index}`,
    title: item.label,
    subtitle: item.subtitle,
    meta: `Entry ${index + 1}`,
    tags: mapTags(item.tags),
  }))
}

/**
 * @param {PlayerLobbyScreenResponse} screen
 * @param {SessionLoadoutDraft} loadoutDraft
 * @param {string} selectedPresetId
 */
export function createPlayerLobbyLocalPresentation(screen, loadoutDraft, selectedPresetId) {
  const characterId = normalizeCharacterId(loadoutDraft.characterId)
  const deckOwnedCardIds = normalizeIdentifierList(loadoutDraft.deckOwnedCardIds)
  const exCardId = normalizeText(loadoutDraft.exCardId)
  const passiveIds = normalizeIdentifierList(loadoutDraft.passiveIds)
  const localDirty = isPlayerLobbyDraftLocallyDirty(screen.me.draft, {
    characterId,
    deckOwnedCardIds,
    exCardId,
    passiveIds,
  })

  const characterOption = findCharacterOption(screen.references.characterOptions, characterId)
  const exCardOption = findOption(screen.references.exCardOptions, exCardId)
  const selectedPreset =
    screen.presets.items.find((item) => String(item.presetId) === normalizeText(selectedPresetId)) ?? null
  const serverSelectedPresetId =
    screen.presets.selectedId == null ? '' : String(screen.presets.selectedId)
  const characterChangePending = characterId !== normalizeCharacterId(screen.me.draft.characterId)
  const requiredFieldsMissing = characterId === null || exCardId === ''
  const deckEditingLocked = screen.me.draftFlags.deckEditingLocked || characterChangePending
  const presetPreviewSynced =
    normalizeText(selectedPresetId) !== '' &&
    normalizeText(selectedPresetId) === normalizeText(serverSelectedPresetId) &&
    screen.presets.preview !== null
  const presetPreviewStale = normalizeText(selectedPresetId) !== '' && !presetPreviewSynced
  const localSummary = `Deck ${deckOwnedCardIds.length} cards | ${passiveIds.length} passives | EX ${exCardId || 'none'}`
  const syncedSummary = normalizeText(screen.me.summary.loadoutSummary) || screen.me.summary.draftSummary

  return {
    dirty: localDirty,
    characterChangePending,
    deckEditingLocked,
    deckEditingLockReason: deckEditingLocked
      ? characterChangePending
        ? 'Save the new character first to refresh owned card options and unlock deck editing.'
        : 'Deck editing is locked in the latest server snapshot.'
      : '',
    requiredFieldsMissing,
    summary: localSummary,
    syncedSummary,
    deckCount: deckOwnedCardIds.length,
    passiveCount: passiveIds.length,
    character: buildReferencePreview(
      characterOption,
      characterId === null ? 'No character selected' : '',
      'character',
    ),
    ex: buildReferencePreview(
      exCardOption,
      exCardId === '' ? 'No EX card selected' : '',
      'EX card',
    ),
    passiveItems: buildPassiveItems(passiveIds, screen.references.passiveOptions),
    preset: {
      selectedId: normalizeText(selectedPresetId),
      label: selectedPreset?.label ?? 'No preset selected',
      subtitle: selectedPreset?.subtitle ?? 'Choose a saved preset from the current server-provided list.',
      previewSynced: presetPreviewSynced,
      previewStale: presetPreviewStale,
      name: presetPreviewSynced ? screen.presets.preview?.name ?? '' : selectedPreset?.label ?? '',
      summary: presetPreviewSynced
        ? screen.presets.preview?.summary ?? ''
        : selectedPreset
          ? 'Resolved preview is available only for the latest server-selected preset snapshot.'
          : 'Choose a saved preset before applying it to the current session.',
      characterLabel: presetPreviewSynced ? screen.presets.preview?.characterLabel ?? '' : '',
      exLabel: presetPreviewSynced ? screen.presets.preview?.exLabel ?? '' : '',
      deckItems: presetPreviewSynced
        ? mapPresetPreviewItems(screen.presets.preview?.deckItems ?? [], 'deck')
        : [],
      passiveItems: presetPreviewSynced
        ? mapPresetPreviewItems(screen.presets.preview?.passiveItems ?? [], 'passive')
        : [],
    },
  }
}
