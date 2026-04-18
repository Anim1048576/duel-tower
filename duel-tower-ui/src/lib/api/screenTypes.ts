import type { DeckIdentifier, DeckResponse, DeckType, DeckValidationIssue } from './deckTypes'
import type { PresetIdentifier, PresetResponse } from './presetTypes'
import type { SessionCode, SessionStateDto } from './sessionTypes'

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

export type PlayerLobbyToggleReadyPayload = {
  ready?: boolean | null
}

export type PlayerLobbySaveLoadoutPayload = {
  characterId?: number | null
  passiveIds?: string[] | null
  deckOwnedCardIds?: string[] | null
  exCardId?: string | null
}

export type PlayerLobbyApplyPresetPayload = {
  presetId?: number | null
}

export type PlayerLobbyActionPayload =
  | PlayerLobbyToggleReadyPayload
  | PlayerLobbySaveLoadoutPayload
  | PlayerLobbyApplyPresetPayload

export type GmLobbyKickPayload = {
  playerId?: string | null
  reason?: string | null
}

export type GmLobbyResetPayload = {
  keepPlayers?: boolean | null
  keepLoadouts?: boolean | null
  newSeed?: number | null
}

export type GmLobbyStartCombatPayload = {
  expectedVersion?: number | null
  playerId?: string | null
}

export type GmLobbyActionPayload =
  | GmLobbyKickPayload
  | GmLobbyResetPayload
  | GmLobbyStartCombatPayload

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

export type PlayerLobbyActionId =
  | 'playerLobby.toggleReady'
  | 'playerLobby.leave'
  | 'playerLobby.saveLoadout'
  | 'playerLobby.applyPreset'

export type GmLobbyActionId =
  | 'gmLobby.kick'
  | 'gmLobby.reset'
  | 'gmLobby.startCombat'

export type CombatActionId =
  | 'combat.draw'
  | 'combat.endTurn'
  | 'combat.clearRecentResults'
  | 'combat.playCard'
  | 'combat.useEx'
  | 'combat.resolvePending'

export type GmLobbyStartCombatOutcome =
  | 'STARTED'
  | 'ALREADY_ACTIVE'
  | 'BLOCKED'
  | 'GM_ACCESS_REQUIRED'
  | 'FAILED'

export type CombatScreenActionOutcome = 'SUCCEEDED' | 'FAILED' | 'BLOCKED'

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

export type PlayerLobbyTagDto = {
  label: string
  tone: 'accent' | 'muted' | 'success' | 'warning'
}

export type PlayerLobbyParticipantSlotDto = {
  slot: string
  name: string
  state: string
  tone: 'accent' | 'muted' | 'success' | 'warning'
  note: string
}

export type PlayerLobbyLoadoutDto = {
  characterId: number | null
  characterLabel: string
  deckOwnedCardIds: string[]
  exCardId: string
  exLabel: string
  passiveIds: string[]
  deckCount: number
  passiveCount: number
}

export type PlayerLobbyMeSummaryDto = {
  readyLabel: string
  readyTone: 'accent' | 'muted' | 'success' | 'warning'
  loadoutSummary: string
  draftSummary: string
  membershipSummary: string
}

export type PlayerLobbyDraftFlagsDto = {
  dirty: boolean
  deckEditingLocked: boolean
  requiredFieldsMissing: boolean
}

export type PlayerLobbyMeDto = {
  playerId: string
  ready: boolean
  loadout: PlayerLobbyLoadoutDto
  summary: PlayerLobbyMeSummaryDto
  draft: PlayerLobbyLoadoutDto
  draftFlags: PlayerLobbyDraftFlagsDto
}

export type PlayerLobbyOptionDto = {
  id: string
  label: string
  subtitle: string
  tags: PlayerLobbyTagDto[]
}

export type PlayerLobbyOwnedCardOptionDto = {
  ownedCardId: string
  cardId: string
  label: string
  subtitle: string
  tags: PlayerLobbyTagDto[]
}

export type PlayerLobbyReferencesDto = {
  characterOptions: PlayerLobbyOptionDto[]
  exCardOptions: PlayerLobbyOptionDto[]
  passiveOptions: PlayerLobbyOptionDto[]
  ownedCardOptions: PlayerLobbyOwnedCardOptionDto[]
}

export type PlayerLobbyPresetItemDto = {
  presetId: number
  label: string
  subtitle: string
}

export type PlayerLobbyPreviewItemDto = {
  id: string
  label: string
  subtitle: string
  tags: PlayerLobbyTagDto[]
}

export type GmLobbyTagDto = {
  label: string
  tone: 'accent' | 'muted' | 'success' | 'warning'
}

/**
 * Server-curated GM participant card.
 * playerId is the stable action target, while name is display-only.
 * Character / EX / passive / deck summaries and detail tags are already
 * resolved for the GM card UI and should not be recomputed on the frontend.
 */
export type GmLobbyParticipantCardDto = {
  slot: string
  playerId: string
  name: string
  readyLabel: string
  readyTone: 'accent' | 'muted' | 'success' | 'warning'
  characterSummary: string
  exSummary: string
  passiveSummary: string
  deckSummary: string
  detailTags: GmLobbyTagDto[]
}

/**
 * Start-combat option as curated by the server for the GM lobby.
 * playerId is the command target, while label/slot/ready are presentation data.
 */
export type GmLobbySelectableStartPlayerDto = {
  playerId: string
  slot: string
  label: string
  ready: boolean
}

/**
 * Server-owned GM start-combat summary.
 * blockedReason and recommendedStartPlayerId come from the backend so the page
 * does not reinterpret lobby readiness or combat-start rules locally.
 */
export type GmLobbyStartCombatDto = {
  recommendedStartPlayerId: string | null
  blockedReason: DisabledReasonDto | null
  selectableStartPlayers: GmLobbySelectableStartPlayerDto[]
}

export type PlayerLobbyPresetPreviewDto = {
  name: string
  summary: string
  characterLabel: string
  exLabel: string
  deckItems: PlayerLobbyPreviewItemDto[]
  passiveItems: PlayerLobbyPreviewItemDto[]
}

export type PlayerLobbyPresetsDto = {
  items: PlayerLobbyPresetItemDto[]
  selectedId: number | null
  preview: PlayerLobbyPresetPreviewDto | null
}

/**
 * Server-side PlayerLobby me snapshot.
 * loadout/draft/draftFlags come from the backend and should be treated as
 * the latest synced lobby truth, not as frontend-derived UX state.
 */
export type PlayerLobbyServerMe = PlayerLobbyMeDto

/**
 * Server-curated PlayerLobby references.
 * Character/card/passive labels and tags are already resolved on the backend.
 */
export type PlayerLobbyServerReferences = PlayerLobbyReferencesDto

/**
 * Server-side preset archive snapshot for PlayerLobby.
 * preview belongs to the currently selected preset snapshot on the backend.
 */
export type PlayerLobbyServerPresets = PlayerLobbyPresetsDto

/**
 * Frontend-only PlayerLobby presentation state.
 * This keeps dirty/summary/preview-freshness aligned with the current local draft
 * without reintroducing reference resolution or action-enable rules on the client.
 */
export type PlayerLobbyLocalPresentationState = {
  dirty: boolean
  characterChangePending: boolean
  deckEditingLocked: boolean
  deckEditingLockReason: string
  requiredFieldsMissing: boolean
  summary: string
  syncedSummary: string
  deckCount: number
  passiveCount: number
  previewNeedsResolveRefresh: boolean
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
  preset: {
    selectedId: string
    label: string
    subtitle: string
    previewSynced: boolean
    previewStale: boolean
    name: string
    summary: string
    characterLabel: string
    exLabel: string
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

export type CombatTagDto = {
  label: string
  tone: string | null
}

export type CombatMetricDto = {
  label: string
  value: string | number | boolean | null
  note: string | null
}

export type CombatCardDto = {
  instanceId: string
  defId: string | null
  title: string
  subtitle: string
  unresolved: boolean
  tags: CombatTagDto[]
  meta: string | null
}

export type CombatActorSummaryDto = {
  raw: string | null
  kind: string
  id: string | null
  label: string
  note: string
  tone: string
}

export type CombatRequirementTargetRuleDto = {
  target: string
  requiredSelection: boolean
}

export type CombatDiscardRequirementDto = {
  count: number
  excludeSourceCard: boolean
  filter: string
}

export type CombatSelectedIdsRequirementDto = {
  minSelections: number
  maxSelections: number
  scope: string
  filter: string
  excludeSourceCard: boolean
}

export type CombatChoiceOptionDto = {
  id: string
  label: string
  description: string
}

export type CombatPendingChoiceSchemaDto = {
  id: string
  label: string
  minSelections: number
  maxSelections: number
  options: CombatChoiceOptionDto[]
}

export type CombatRequirementViewDto = {
  sourceLabel: string
  targetSummary: string
  discardSummary: string
  selectedIdsSummary: string
  choiceSummary: string
  targetRule: CombatRequirementTargetRuleDto | null
  discardRequirement: CombatDiscardRequirementDto | null
  selectedIdsRequirement: CombatSelectedIdsRequirementDto | null
  pendingChoiceSchema: CombatPendingChoiceSchemaDto | null
  unsupportedReason: string | null
}

export type CombatPlayCardSourceOptionDto = {
  instanceId: string
  title: string
  sourceCard: CombatCardDto | null
  requirementView: CombatRequirementViewDto | null
  supported: boolean
  unsupportedReason: string | null
}

export type CombatPendingDecisionSchemaDto = {
  type: string
  reason?: string | null
  discardCount?: number | null
  pickCount?: number | null
  candidateIds?: string[] | null
  destination?: string | null
  shuffleAfterPick?: boolean | null
  groupIndex?: number | null
  actorKeys?: string[] | null
  selectedIdsField?: string | null
}

export type CombatSimpleActionMetadataDto = {
  kind: 'simple' | 'utility'
  note: string
}

export type CombatPlayCardActionMetadataDto = {
  kind: 'playCard'
  note: string
  localSelection: {
    requiresSelectedCard: boolean
    sourceType: string
  }
  sourceOptions: CombatPlayCardSourceOptionDto[]
}

export type CombatUseExActionMetadataDto = {
  kind: 'useEx'
  note: string
  sourceCard: CombatCardDto | null
  requirementView: CombatRequirementViewDto | null
  supported: boolean
  unsupportedReason: string | null
}

export type CombatPendingActionMetadataDto = {
  kind: 'pendingDecision'
  note: string
  supported: boolean
  unsupportedReason: string | null
  pendingDecisionType: string | null
  schema: CombatPendingDecisionSchemaDto | null
  blocked: boolean
}

export type CombatActionMetadataDto =
  | CombatSimpleActionMetadataDto
  | CombatPlayCardActionMetadataDto
  | CombatUseExActionMetadataDto
  | CombatPendingActionMetadataDto

export type CombatGuardSummaryDto = {
  canIssuePlayerCommand: boolean
  canResolvePendingCommand: boolean
  canClearRecentResultsCommand: boolean
  canIssueGmCommand: boolean
  exAvailable: boolean
  hasPendingDecision: boolean
  isCurrentTurnPlayer: boolean
  hasCombatState: boolean
}

export type CombatAccessDto = {
  role: string
  runtimePlayerId: string | null
  expectedVersion: number
  guards: CombatGuardSummaryDto
}

export type CombatStatusDto = {
  round: number | null
  phase: string | null
  currentActor: CombatActorSummaryDto | null
  turnOrderSummary: string
  battlefieldSummary: string
  runSummary: string
  tieGroupSummary: string | null
}

export type CombatPlayerDto = {
  playerId: string
  ready: boolean
  stateLabel: string
  stateTone: string
  metrics: CombatMetricDto[]
  summaryLines: string[]
  statusTags: CombatTagDto[]
  passives: string[]
  handCards: CombatCardDto[]
  fieldCards: CombatCardDto[]
  graveCards: CombatCardDto[]
  excludedCards: CombatCardDto[]
  exCard: CombatCardDto | null
}

export type CombatEnemyDto = {
  enemyId: string
  stateLabel: string
  stateTone: string
  metrics: CombatMetricDto[]
  summaryLines: string[]
  statusEntries: string[]
}

export type CombatSummonDto = {
  summonId: string
  owner: string
  stateLabel: string
  stateTone: string
  metrics: CombatMetricDto[]
  summaryLines: string[]
}

export type CombatActorsDto = {
  players: CombatPlayerDto[]
  enemies: CombatEnemyDto[]
  summons: CombatSummonDto[]
}

export type CombatZonesDto = {
  visiblePlayerId: string | null
  hand: CombatCardDto[]
  field: CombatCardDto[]
  grave: CombatCardDto[]
  excluded: CombatCardDto[]
  ex: CombatCardDto | null
}

export type CombatFeedEntryDto = {
  title: string
  lines: string[]
}

export type CombatRecentResultEntryDto = {
  title: string
  summary: string
  meta: string
}

export type CombatSidebarDto = {
  events: CombatFeedEntryDto[]
  logs: CombatFeedEntryDto[]
  recentResults: CombatRecentResultEntryDto[]
}

export type ScreenActionDto<
  TPayloadTemplate = ScreenActionPayloadTemplate,
  TMetadata = Record<string, unknown>,
> = {
  id: string
  label: string
  method: ScreenActionMethod | string
  href: string
  auth: ScreenActionAuth
  enabled: boolean
  disabledReason: DisabledReasonDto | null
  payloadTemplate: TPayloadTemplate | null
  metadata?: TMetadata | null
}

export type DeckEditorScreenAction = ScreenActionDto<DeckEditorActionPayload> & {
  id: DeckEditorActionId
}

export type PresetEditorScreenAction = ScreenActionDto<PresetEditorActionPayload> & {
  id: PresetEditorActionId
}

export type PlayerLobbyScreenAction = ScreenActionDto<PlayerLobbyActionPayload> & {
  id: PlayerLobbyActionId
}

export type GmLobbyKickAction = ScreenActionDto<GmLobbyKickPayload> & {
  id: 'gmLobby.kick'
  auth: 'gmToken'
}

export type GmLobbyResetAction = ScreenActionDto<GmLobbyResetPayload> & {
  id: 'gmLobby.reset'
  auth: 'gmToken'
}

export type GmLobbyStartCombatAction = ScreenActionDto<GmLobbyStartCombatPayload> & {
  id: 'gmLobby.startCombat'
  auth: 'gmToken' | 'loginCookie'
}

export type GmLobbyScreenAction =
  | GmLobbyKickAction
  | GmLobbyResetAction
  | GmLobbyStartCombatAction

export type CombatScreenAction = ScreenActionDto<
  Record<string, unknown>,
  CombatActionMetadataDto
> & {
  id: CombatActionId
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

/**
 * PlayerLobby screen contract.
 * The backend owns participant slot summary, reference option curation,
 * preset preview resolution, and action metadata. The frontend keeps only
 * local loadout input state and editor-style presentation helpers.
 */
export type PlayerLobbyScreenResponse = ScreenResponseBase<PlayerLobbyScreenAction> & {
  sessionCode: string
  version: number
  routeTemplate: string
  policyGroup: string
  auth: string
  participantSlots: PlayerLobbyParticipantSlotDto[]
  me: PlayerLobbyServerMe
  references: PlayerLobbyServerReferences
  presets: PlayerLobbyServerPresets
}

/**
 * GmLobby screen contract.
 * The backend owns participant-card curation, start-combat blocked state,
 * recommended start-player selection, and action enablement. The frontend keeps
 * only lightweight selection inputs and refresh/action feedback around it.
 */
export type GmLobbyScreenResponse = ScreenResponseBase<GmLobbyScreenAction> & {
  sessionCode: string
  version: number
  routeTemplate: string
  policyGroup: string
  auth: string
  participantCards: GmLobbyParticipantCardDto[]
  startCombat: GmLobbyStartCombatDto
}

export type CombatScreenResponse = ScreenResponseBase<CombatScreenAction> & {
  sessionCode: string
  version: number
  changed: boolean
  status: CombatStatusDto
  access: CombatAccessDto
  actors: CombatActorsDto
  zones: CombatZonesDto
  sidebar: CombatSidebarDto
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

export type PlayerLobbyActionResponseById = {
  'playerLobby.toggleReady': SessionStateDto
  'playerLobby.leave': SessionStateDto
  'playerLobby.saveLoadout': SessionStateDto
  'playerLobby.applyPreset': SessionStateDto
}

export type GmLobbyStartCombatActionResponse = {
  success: boolean
  outcome: GmLobbyStartCombatOutcome
  message: string | null
  disabledReason: DisabledReasonDto | null
  nextRoute: string | null
  combatEntryHint: string | null
  gmAccessRestored: boolean
  restoredGmToken: string | null
  retryUsed: boolean
  latestScreen: GmLobbyScreenResponse | null
}

export type GmLobbyActionResponseById = {
  'gmLobby.kick': SessionStateDto
  'gmLobby.reset': SessionStateDto
  'gmLobby.startCombat': GmLobbyStartCombatActionResponse
}

export type CombatScreenActionResponse = {
  success: boolean
  outcome: CombatScreenActionOutcome
  message: string | null
  disabledReason: DisabledReasonDto | null
  latestVersion: number | null
  serverNotices: string[]
  resultSummary: Record<string, unknown> | null
  latestScreen: CombatScreenResponse | null
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

export function findPlayerLobbyAction(
  screen: Pick<PlayerLobbyScreenResponse, 'possibleActions'>,
  actionId: PlayerLobbyActionId,
) {
  return screen.possibleActions.find((action) => action.id === actionId) ?? null
}

export function findGmLobbyAction<TActionId extends GmLobbyActionId>(
  screen: Pick<GmLobbyScreenResponse, 'possibleActions'>,
  actionId: TActionId,
) {
  return (
    screen.possibleActions.find((action) => action.id === actionId) as Extract<
      GmLobbyScreenAction,
      { id: TActionId }
    > | null
  ) ?? null
}

export function findCombatAction<TActionId extends CombatActionId>(
  screen: Pick<CombatScreenResponse, 'possibleActions'>,
  actionId: TActionId,
) {
  return (
    screen.possibleActions.find((action) => action.id === actionId) as Extract<
      CombatScreenAction,
      { id: TActionId }
    > | null
  ) ?? null
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
