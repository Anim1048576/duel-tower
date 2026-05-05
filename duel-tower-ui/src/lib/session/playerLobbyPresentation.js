/** @typedef {import('../api/screenTypes').PlayerLobbyScreenResponse} PlayerLobbyScreenResponse */
/** @typedef {import('../api/screenTypes').PlayerLobbyOptionDto} PlayerLobbyOptionDto */
/** @typedef {import('../api/screenTypes').PlayerLobbyTagDto} PlayerLobbyTagDto */
/** @typedef {import('./loadoutEditor').SessionLoadoutDraft} SessionLoadoutDraft */
/** @typedef {import('../components/EntityListPane.svelte').EntityListItem} EntityListItem */
/** @typedef {import('../components/EntityListPane.svelte').EntityListTag} EntityListTag */

/**
 * PlayerLobby local presentation helpers stay in the editor-UX layer.
 * They keep the current local draft visually aligned with the form while reusing
 * server-curated reference options.
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
    subtitle: '?꾩옱 ?좏깮媛믪쓣 ?ъ슜?????놁뒿?덈떎.',
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
        note: '???⑥떆釉?id瑜??ъ슜?????놁뒿?덈떎.',
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
 * @param {PlayerLobbyScreenResponse} screen
 * @param {SessionLoadoutDraft} loadoutDraft
 */
export function createPlayerLobbyLocalPresentation(screen, loadoutDraft) {
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
  const characterChangePending = characterId !== normalizeCharacterId(screen.me.draft.characterId)
  const requiredFieldsMissing = characterId === null || exCardId === ''
  const deckEditingLocked = screen.me.draftFlags.deckEditingLocked || characterChangePending
  const localSummary = `Deck ${deckOwnedCardIds.length} cards | ${passiveIds.length} passives | EX ${exCardId || 'none'}`
  const syncedSummary = normalizeText(screen.me.summary.loadoutSummary) || screen.me.summary.draftSummary

  return {
    dirty: localDirty,
    characterChangePending,
    deckEditingLocked,
    deckEditingLockReason: deckEditingLocked
      ? characterChangePending
        ? '캐릭터 선택을 먼저 저장해 주세요.'
        : '덱 편집이 잠겨 있습니다.'
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
  }
}