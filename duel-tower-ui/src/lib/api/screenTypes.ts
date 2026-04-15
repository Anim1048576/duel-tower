import type { DeckIdentifier, DeckResponse, DeckType, DeckValidationIssue } from './deckTypes'
import type { PresetIdentifier, PresetResponse } from './presetTypes'
import type { SessionCode } from './sessionTypes'

export type ScreenKey = 'PlayerLobby' | 'GmLobby' | 'Combat' | 'DeckEditor' | 'PresetEditor'

export type ScreenActionAuth =
  | 'public'
  | 'sessionReadable'
  | 'playerToken'
  | 'gmToken'
  | 'loginCookie'

export type ScreenActionMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type ScreenRequestParamsByKey = {
  PlayerLobby: {
    code: SessionCode
  }
  GmLobby: {
    code: SessionCode
  }
  Combat: {
    code: SessionCode
  }
  DeckEditor: {
    deckId?: DeckIdentifier | null
  }
  PresetEditor: {
    presetId?: PresetIdentifier | null
  }
}

export type DisabledReasonDto = {
  code: string | null
  category: string | null
  userMessage: string | null
  debugMessage: string | null
  details: unknown | null
  status: number | null
  path: string | null
}

export type ScreenActionPayloadTemplate = Record<string, unknown>

export type DeckEditorActionPayload = {
  name?: string | null
  type?: DeckType | null
  cards?: {
    cardId: string
    count?: number | null
  }[] | null
}

export type PresetEditorActionPayload = {
  name?: string | null
  characterId?: number | null
  deckCardIds?: string[] | null
  exCardId?: string | null
  passiveIds?: string[] | null
}

export type DeckEditorActionId =
  | 'deckEditor.validate'
  | 'deckEditor.save'
  | 'deckEditor.create'
  | 'deckEditor.delete'

export type PresetEditorActionId =
  | 'presetEditor.save'
  | 'presetEditor.create'
  | 'presetEditor.clone'
  | 'presetEditor.delete'

export type DeckEditorDraftCardDto = {
  key: string
  cardId: string
  count: number
  position: number
}

export type DeckEditorDraftDto = {
  name: string
  type: DeckType
  cards: DeckEditorDraftCardDto[]
}

export type PresetEditorDraftDto = {
  name: string
  characterId: number | null
  deckCardIds: string[]
  exCardId: string
  passiveIds: string[]
}

export type DeckEditorDerivedDto = {
  title: string
  deckTypeLabel: string
  totalCards: number
  dirty: boolean
}

export type PresetEditorResolvedTagDto = {
  label: string
  tone: 'accent' | 'muted' | 'success' | 'warning' | string
}

export type PresetEditorResolvedItemDto = {
  id: string
  label: string
  subtitle: string
  meta: string
  tags: PresetEditorResolvedTagDto[]
}

export type PresetEditorResolvedDto = {
  characterLabel: string
  characterSubtitle: string
  characterTags: PresetEditorResolvedTagDto[]
  exLabel: string
  exSubtitle: string
  exTags: PresetEditorResolvedTagDto[]
  deckItems: PresetEditorResolvedItemDto[]
  passiveItems: PresetEditorResolvedItemDto[]
}

export type PresetEditorDerivedDto = {
  dirty: boolean
  createdAtLabel: string
  updatedAtLabel: string
}

/**
 * Server-side PresetEditor draft snapshot.
 * The frontend copies this into local editor state and does not reinterpret preset semantics.
 */
export type PresetEditorServerDraft = PresetEditorDraftDto

/**
 * Server-side resolved preview snapshot.
 * Character / EX / deck / passive labels and tags are resolved on the backend.
 */
export type PresetEditorServerResolved = PresetEditorResolvedDto

/**
 * Frontend-only presentation state for PresetEditor.
 * This mirrors the current local draft for dirty/title/preview display without
 * re-implementing backend reference resolution or action-enable rules.
 */
export type PresetEditorLocalPresentationState = {
  title: string
  summary: string
  dirty: boolean
  previewNeedsResolveRefresh: boolean
  deckCount: number
  passiveCount: number
  character: {
    label: string
    subtitle: string
    tags: {
      label: string
      tone?: 'accent' | 'muted' | 'success' | 'warning'
    }[]
  }
  ex: {
    label: string
    subtitle: string
    tags: {
      label: string
      tone?: 'accent' | 'muted' | 'success' | 'warning'
    }[]
  }
  deckItems: {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: {
      label: string
      tone?: 'accent' | 'muted' | 'success' | 'warning'
    }[]
  }[]
  passiveItems: {
    id: string
    title: string
    subtitle?: string
    meta?: string
    note?: string
    tags?: {
      label: string
      tone?: 'accent' | 'muted' | 'success' | 'warning'
    }[]
  }[]
}

/**
 * Server-side validation snapshot for the last validated deck draft.
 * This does not encode whether the current local editor state is stale.
 * validatedDraftSignature is the server's identifier for the exact draft that was validated.
 */
export type DeckEditorServerValidationDto = {
  valid: boolean
  normalizedTotalCards: number
  issues: DeckValidationIssue[]
  validatedDraftSignature: string
  validatedAt: string
}

/**
 * Frontend-only validation view state.
 * Local freshness is computed from the current editor state vs validatedDraftSignature.
 * This is editor UX state, not a game-rule calculation.
 */
export type DeckEditorLocalValidationState = DeckEditorServerValidationDto & {
  isLocallyStale: boolean
}

export type ScreenActionDto<TPayloadTemplate = ScreenActionPayloadTemplate> = {
  id: string
  label: string
  method: ScreenActionMethod | string
  href: string
  auth: ScreenActionAuth
  enabled: boolean
  disabledReason: DisabledReasonDto | null
  payloadTemplate: TPayloadTemplate | null
}

export type DeckEditorScreenAction = ScreenActionDto<DeckEditorActionPayload> & {
  id: DeckEditorActionId
}

export type PresetEditorScreenAction = ScreenActionDto<PresetEditorActionPayload> & {
  id: PresetEditorActionId
}

export type ScreenResponseBase<TAction extends ScreenActionDto = ScreenActionDto> = {
  screenKey: string
  generatedAt: string
  uiNotices: string[]
  possibleActions: TAction[]
}

export type DeckEditorScreenResponse = ScreenResponseBase<DeckEditorScreenAction> & {
  deckId: number | null
  mode: 'create' | 'edit'
  routeTemplate: string
  policyGroup: string
  auth: string
  draft: DeckEditorDraftDto
  derived: DeckEditorDerivedDto
  validation: DeckEditorServerValidationDto
}

/**
 * PresetEditor screen contract.
 * The backend owns draft serialization, resolved preview metadata, derived labels,
 * and mode-specific actions. The frontend adds only local presentation state on top.
 */
export type PresetEditorScreenResponse = ScreenResponseBase<PresetEditorScreenAction> & {
  presetId: number | null
  mode: 'create' | 'edit'
  routeTemplate: string
  policyGroup: string
  auth: string
  draft: PresetEditorServerDraft
  resolved: PresetEditorServerResolved
  derived: PresetEditorDerivedDto
}

export type DeckEditorActionResponseById = {
  'deckEditor.validate': DeckValidationResponseLike
  'deckEditor.save': DeckResponse
  'deckEditor.create': DeckResponse
  'deckEditor.delete': void
}

export type PresetEditorActionResponseById = {
  'presetEditor.save': PresetResponse
  'presetEditor.create': PresetResponse
  'presetEditor.clone': PresetResponse
  'presetEditor.delete': void
}

type DeckValidationResponseLike = {
  valid: boolean
  normalizedTotalCards: number
  issues: DeckValidationIssue[]
}

export type ScreenActionResponse<TScreen extends ScreenResponseBase = ScreenResponseBase> = Record<
  string,
  unknown
> & {
  screen?: TScreen | null
  nextScreen?: TScreen | null
  redirectHref?: string | null
}

export class ScreenActionDisabledError extends Error {
  actionId: string
  disabledReason: DisabledReasonDto | null

  constructor(action: Pick<ScreenActionDto, 'id' | 'label' | 'disabledReason'>) {
    super(
      action.disabledReason?.userMessage ??
        `${action.label} is currently disabled and cannot be invoked.`,
    )

    this.name = 'ScreenActionDisabledError'
    this.actionId = action.id
    this.disabledReason = action.disabledReason
  }
}

export function isScreenActionDisabled(action: Pick<ScreenActionDto, 'enabled'>) {
  return action.enabled === false
}

export function findScreenAction<TAction extends ScreenActionDto>(
  screen: Pick<ScreenResponseBase<TAction>, 'possibleActions'>,
  actionId: string,
) {
  return screen.possibleActions.find((action) => action.id === actionId) ?? null
}

export function findDeckEditorAction(
  screen: Pick<DeckEditorScreenResponse, 'possibleActions'>,
  actionId: DeckEditorActionId,
) {
  return screen.possibleActions.find((action) => action.id === actionId) ?? null
}

export function findPresetEditorAction(
  screen: Pick<PresetEditorScreenResponse, 'possibleActions'>,
  actionId: PresetEditorActionId,
) {
  return screen.possibleActions.find((action) => action.id === actionId) ?? null
}

export function buildScreenActionPayload<TPayloadTemplate extends ScreenActionPayloadTemplate>(
  action: Pick<ScreenActionDto<TPayloadTemplate>, 'payloadTemplate'>,
  patch: Partial<TPayloadTemplate> | null | undefined = undefined,
) {
  return {
    ...(action.payloadTemplate ?? {}),
    ...(patch ?? {}),
  } as TPayloadTemplate
}
