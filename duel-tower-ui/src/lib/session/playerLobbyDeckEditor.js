/** @typedef {import('./loadoutEditor').SessionLoadoutDraft} SessionLoadoutDraft */
/** @typedef {import('../api/screenTypes').PlayerLobbyDeckEditorStateDto} PlayerLobbyDeckEditorStateDto */
/** @typedef {import('../api/screenTypes').PlayerLobbyOwnedCardOptionDto} PlayerLobbyOwnedCardOptionDto */
/** @typedef {import('../api/sessionTypes').PreviewSessionLoadoutResponse} PreviewSessionLoadoutResponse */

/** @param {string | null | undefined} value */
function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

/** @param {readonly string[] | null | undefined} values */
function normalizeIdentifierList(values) {
  return (values ?? []).map((value) => normalizeText(value)).filter(Boolean)
}

/**
 * @param {SessionLoadoutDraft} draft
 */
export function buildPlayerLobbyPreviewDraftSignature(draft) {
  const characterToken =
    typeof draft?.characterId === 'number' && Number.isFinite(draft.characterId) && draft.characterId > 0
      ? String(draft.characterId)
      : ''
  const passiveToken = normalizeIdentifierList(draft?.passiveIds).join('|')
  const deckToken = normalizeIdentifierList(draft?.deckOwnedCardIds).join('|')
  const exToken = normalizeText(draft?.exCardId)

  return `characterId=${characterToken};passiveIds=${passiveToken};deckOwnedCardIds=${deckToken};exCardId=${exToken}`
}

/**
 * @param {SessionLoadoutDraft} draft
 * @param {string} ownedCardId
 * @returns {SessionLoadoutDraft}
 */
export function addOwnedCardIdToLoadoutDraft(draft, ownedCardId) {
  const normalizedOwnedCardId = normalizeText(ownedCardId)
  if (!normalizedOwnedCardId || normalizeIdentifierList(draft.deckOwnedCardIds).includes(normalizedOwnedCardId)) {
    return {
      ...draft,
      deckOwnedCardIds: normalizeIdentifierList(draft.deckOwnedCardIds),
    }
  }

  return {
    ...draft,
    deckOwnedCardIds: [...normalizeIdentifierList(draft.deckOwnedCardIds), normalizedOwnedCardId],
  }
}

/**
 * @param {SessionLoadoutDraft} draft
 * @param {string} ownedCardId
 * @returns {SessionLoadoutDraft}
 */
export function removeOwnedCardIdFromLoadoutDraft(draft, ownedCardId) {
  const normalizedOwnedCardId = normalizeText(ownedCardId)
  return {
    ...draft,
    deckOwnedCardIds: normalizeIdentifierList(draft.deckOwnedCardIds).filter(
      (entry) => entry !== normalizedOwnedCardId,
    ),
  }
}

/**
 * @param {PreviewSessionLoadoutResponse | null | undefined} previewResponse
 * @param {string} draftSignature
 */
export function isPlayerLobbyPreviewResponseCurrent(previewResponse, draftSignature) {
  return normalizeText(previewResponse?.draftSignature) !== '' &&
    normalizeText(previewResponse?.draftSignature) === normalizeText(draftSignature)
}

/**
 * @param {{
 *   requestId: number
 *   latestRequestId: number
 *   draftSignature: string
 *   response: PreviewSessionLoadoutResponse | null | undefined
 * }} params
 */
export function shouldAcceptPlayerLobbyPreviewResponse(params) {
  return params.requestId === params.latestRequestId &&
    isPlayerLobbyPreviewResponseCurrent(params.response, params.draftSignature)
}

/**
 * @param {readonly PlayerLobbyOwnedCardOptionDto[] | null | undefined} options
 * @param {string} ownedCardId
 */
function findOwnedCardOption(options, ownedCardId) {
  const normalizedOwnedCardId = normalizeText(ownedCardId)
  if (!normalizedOwnedCardId) {
    return null
  }
  return (options ?? []).find((option) => normalizeText(option.ownedCardId) === normalizedOwnedCardId) ?? null
}

/**
 * @param {readonly PlayerLobbyOwnedCardOptionDto[] | null | undefined} options
 * @param {string} cardId
 */
function findOwnedCardOptionByCardId(options, cardId) {
  const normalizedCardId = normalizeText(cardId)
  if (!normalizedCardId) {
    return null
  }
  return (options ?? []).find((option) => normalizeText(option.cardId) === normalizedCardId) ?? null
}

/**
 * @param {{
 *   screenDeckEditor: PlayerLobbyDeckEditorStateDto | null | undefined
 *   previewResponse: PreviewSessionLoadoutResponse | null | undefined
 *   draftSignature: string
 *   draftDirty: boolean
 * }} params
 * @returns {PlayerLobbyDeckEditorStateDto | null}
 */
export function resolvePlayerLobbyActiveDeckEditor(params) {
  if (!params.draftDirty) {
    return params.screenDeckEditor ?? null
  }
  if (isPlayerLobbyPreviewResponseCurrent(params.previewResponse, params.draftSignature)) {
    return params.previewResponse?.deckEditor ?? null
  }
  return null
}

/**
 * @param {{
 *   draftDeckOwnedCardIds: readonly string[]
 *   screenDeckEditor: PlayerLobbyDeckEditorStateDto | null | undefined
 *   previewResponse: PreviewSessionLoadoutResponse | null | undefined
 *   draftSignature: string
 *   draftDirty: boolean
 *   ownedCardOptions: readonly PlayerLobbyOwnedCardOptionDto[] | null | undefined
 * }} params
 */
export function buildPlayerLobbyCurrentDeckEntries(params) {
  const activeDeckEditor = resolvePlayerLobbyActiveDeckEditor(params)
  if (activeDeckEditor) {
    return activeDeckEditor.draftEntries.map((entry, index) => {
      const option = findOwnedCardOption(params.ownedCardOptions, entry.ownedCardId)
      return {
        key: `deck-entry:${entry.ownedCardId}:${index}`,
        ownedCardId: entry.ownedCardId,
        cardId: entry.cardId,
        title: option?.label ?? (normalizeText(entry.cardId) || normalizeText(entry.ownedCardId) || 'Unknown card'),
        subtitle: option?.subtitle ?? `Owned card ${normalizeText(entry.ownedCardId) || 'unresolved'}`,
        tags: option?.tags ?? [],
        canRemove: Boolean(entry.canRemove),
        reasonCodes: entry.reasonCodes ?? [],
        lockedInDeck: Boolean(entry.lockedInDeck),
        inSavedDeck: Boolean(entry.inSavedDeck),
        previewPending: false,
        unresolved: !option,
      }
    })
  }

  return normalizeIdentifierList(params.draftDeckOwnedCardIds).map((ownedCardId, index) => {
    const option = findOwnedCardOption(params.ownedCardOptions, ownedCardId)
    return {
      key: `draft-entry:${ownedCardId}:${index}`,
      ownedCardId,
      cardId: normalizeText(option?.cardId),
      title: option?.label ?? ownedCardId,
      subtitle: option?.subtitle ?? 'Owned card id in the current local draft',
      tags: option?.tags ?? [],
      canRemove: false,
      reasonCodes: [],
      lockedInDeck: false,
      inSavedDeck: false,
      previewPending: params.draftDirty,
      unresolved: !option,
    }
  })
}

/**
 * @param {{
 *   screenDeckEditor: PlayerLobbyDeckEditorStateDto | null | undefined
 *   previewResponse: PreviewSessionLoadoutResponse | null | undefined
 *   draftSignature: string
 *   draftDirty: boolean
 *   ownedCardOptions: readonly PlayerLobbyOwnedCardOptionDto[] | null | undefined
 * }} params
 */
export function buildPlayerLobbyDeckPoolGroups(params) {
  const activeDeckEditor = resolvePlayerLobbyActiveDeckEditor(params)
  if (!activeDeckEditor) {
    return []
  }

  return activeDeckEditor.cardPoolGroups.map((group, index) => {
    const option = findOwnedCardOptionByCardId(params.ownedCardOptions, group.cardId)

    return {
      key: `card-pool:${group.cardId}:${index}`,
      cardId: group.cardId,
      title: option?.label ?? (normalizeText(group.cardId) || 'Unknown card'),
      subtitle: option?.subtitle ?? 'Owned card group from the current server preview',
      tags: option?.tags ?? [],
      currentDeckCount: group.currentDeckCount,
      totalOwnedCount: group.totalOwnedCount,
      availableOwnedCount: group.availableOwnedCount,
      canAdd: Boolean(group.canAdd),
      nextOwnedCardId: group.nextOwnedCardId,
      reasonCodes: group.reasonCodes ?? [],
      ownedCards: (group.ownedCards ?? []).map((ownedCard, ownedIndex) => {
        const ownedOption = findOwnedCardOption(params.ownedCardOptions, ownedCard.ownedCardId)
        return {
          key: `group-owned:${ownedCard.ownedCardId}:${ownedIndex}`,
          ownedCardId: ownedCard.ownedCardId,
          cardId: ownedCard.cardId,
          title:
            ownedOption?.label ??
            (normalizeText(ownedCard.cardId) || normalizeText(ownedCard.ownedCardId) || 'Unknown owned card'),
          subtitle: ownedOption?.subtitle ?? `Owned card ${normalizeText(ownedCard.ownedCardId) || 'unresolved'}`,
          tags: ownedOption?.tags ?? [],
          inDraftDeck: Boolean(ownedCard.inDraftDeck),
          canAdd: Boolean(ownedCard.canAdd),
          reasonCodes: ownedCard.reasonCodes ?? [],
          unresolved: !ownedOption,
        }
      }),
    }
  })
}

