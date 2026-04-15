import type { DeckIdentifier, DeckType, DeckValidationIssue } from './deckTypes'
import type { SessionCode } from './sessionTypes'

export type ScreenKey = 'PlayerLobby' | 'GmLobby' | 'Combat' | 'DeckEditor'

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

export type DeckEditorDerivedDto = {
  title: string
  deckTypeLabel: string
  totalCards: number
  dirty: boolean
}

export type DeckEditorValidationDto = {
  valid: boolean
  normalizedTotalCards: number
  issues: DeckValidationIssue[]
  isStale: boolean
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

export type DeckEditorScreenAction = ScreenActionDto<DeckEditorActionPayload>

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
  validation: DeckEditorValidationDto
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

export function buildScreenActionPayload<TPayloadTemplate extends ScreenActionPayloadTemplate>(
  action: Pick<ScreenActionDto<TPayloadTemplate>, 'payloadTemplate'>,
  patch: Partial<TPayloadTemplate> | null | undefined = undefined,
) {
  return {
    ...(action.payloadTemplate ?? {}),
    ...(patch ?? {}),
  } as TPayloadTemplate
}
