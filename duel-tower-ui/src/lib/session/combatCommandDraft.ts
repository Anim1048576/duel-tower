import type {
  SessionStateDto,
  SessionPlayerId,
  TargetRefDto,
} from '../api/sessionTypes'
import type { StoredSessionAccess } from './access'

export type CombatCommandType =
  | 'END_TURN'
  | 'DRAW'
  | 'CLEAR_RECENT_RESULTS'
  | 'PLAY_CARD'
  | 'USE_EX'
  | 'HAND_SWAP'
  | 'DISCARD_TO_HAND_LIMIT'
  | 'SEARCH_PICK'
  | 'RESOLVE_SEARCH_PICK'
  | 'RESOLVE_INITIATIVE_TIE'
  | 'RESOLVE_PENDING'
  | 'GM_REVIEW'

export type CombatCommandDraft = {
  selectedCommandType: CombatCommandType | null
  selectedPlayerId: SessionPlayerId | null
  selectedEnemyId: string | null
  selectedCardId: string | null
  selectedTargets: TargetRefDto[]
  selectedCount: number | null
  selectedDiscardIds: string[]
  selectedIds: string[]
  orderedActorKeys: string[]
  selectedReason: string
}

export type CombatCommandGuards = {
  expectedVersion: number | null
  role: 'gm' | 'player' | 'none'
  runtimePlayerId: string | null
  currentActorPlayerId: string | null
  isCurrentTurnPlayer: boolean
  hasCombatState: boolean
  hasPendingDecision: boolean
  exAvailable: boolean
  hasPlayerToken: boolean
  hasGmToken: boolean
  canIssuePlayerCommand: boolean
  canResolvePendingCommand: boolean
  canIssueGmCommand: boolean
}

function dedupeIdentifiers(values: readonly string[]) {
  const seen = new Set<string>()
  const result: string[] = []

  for (const value of values) {
    const normalized = value.trim()

    if (!normalized || seen.has(normalized)) {
      continue
    }

    seen.add(normalized)
    result.push(normalized)
  }

  return result
}

function normalizeTarget(target: TargetRefDto) {
  return {
    playerId: target.playerId?.trim() || null,
    enemyId: target.enemyId?.trim() || null,
    summonOwnerPlayerId: target.summonOwnerPlayerId?.trim() || null,
    summonInstanceId: target.summonInstanceId?.trim() || null,
  } satisfies TargetRefDto
}

export function createEmptyCombatCommandDraft(): CombatCommandDraft {
  return {
    selectedCommandType: null,
    selectedPlayerId: null,
    selectedEnemyId: null,
    selectedCardId: null,
    selectedTargets: [],
    selectedCount: 1,
    selectedDiscardIds: [],
    selectedIds: [],
    orderedActorKeys: [],
    selectedReason: '',
  }
}

export function toggleCombatIdentifier(values: readonly string[], value: string) {
  const normalized = value.trim()

  if (!normalized) {
    return dedupeIdentifiers(values)
  }

  return values.includes(normalized)
    ? values.filter((entry) => entry !== normalized)
    : [...dedupeIdentifiers(values), normalized]
}

export function normalizeCombatCommandDraft(
  draft: CombatCommandDraft,
  session: SessionStateDto | null,
) {
  const playerIds = new Set(Object.keys(session?.players ?? {}))
  const enemyIds = new Set(session?.combat?.enemies.map((enemy) => enemy.enemyId) ?? [])
  const cardInstanceIds = new Set(Object.keys(session?.cards ?? {}))

  const selectedPlayerId =
    draft.selectedPlayerId && playerIds.has(draft.selectedPlayerId) ? draft.selectedPlayerId : null
  const selectedEnemyId =
    draft.selectedEnemyId && enemyIds.has(draft.selectedEnemyId) ? draft.selectedEnemyId : null
  const selectedCardId =
    draft.selectedCardId && cardInstanceIds.has(draft.selectedCardId) ? draft.selectedCardId : null

  return {
    selectedCommandType: draft.selectedCommandType,
    selectedPlayerId,
    selectedEnemyId,
    selectedCardId,
    selectedTargets: draft.selectedTargets
      .map((target) => normalizeTarget(target))
      .filter(
        (target) =>
          (target.playerId && playerIds.has(target.playerId)) ||
          (target.enemyId && enemyIds.has(target.enemyId)) ||
          (target.summonOwnerPlayerId && target.summonInstanceId),
      ),
    selectedCount:
      typeof draft.selectedCount === 'number' && draft.selectedCount > 0
        ? Math.floor(draft.selectedCount)
        : 1,
    selectedDiscardIds: dedupeIdentifiers(draft.selectedDiscardIds).filter((id) =>
      cardInstanceIds.has(id),
    ),
    selectedIds: dedupeIdentifiers(draft.selectedIds),
    orderedActorKeys: dedupeIdentifiers(draft.orderedActorKeys),
    selectedReason: draft.selectedReason.trim(),
  } satisfies CombatCommandDraft
}

export function syncCombatCommandDraft(
  draft: CombatCommandDraft,
  session: SessionStateDto | null,
  runtimeAccess: StoredSessionAccess | null,
) {
  const normalized = normalizeCombatCommandDraft(draft, session)
  const runtimePlayerId =
    runtimeAccess?.role === 'player' && runtimeAccess.playerId ? runtimeAccess.playerId : null
  const fallbackPlayerId = runtimePlayerId ?? Object.keys(session?.players ?? {})[0] ?? null
  const fallbackEnemyId = session?.combat?.enemies[0]?.enemyId ?? null

  return {
    ...normalized,
    selectedPlayerId: normalized.selectedPlayerId ?? fallbackPlayerId,
    selectedEnemyId: normalized.selectedEnemyId ?? fallbackEnemyId,
    selectedTargets: normalized.selectedTargets,
  } satisfies CombatCommandDraft
}

export function buildCombatCommandGuards(
  session: SessionStateDto | null,
  runtimeAccess: StoredSessionAccess | null,
) {
  const runtimePlayerId =
    runtimeAccess?.role === 'player' && runtimeAccess.playerId ? runtimeAccess.playerId : null
  const currentActorPlayerId = session?.combat?.currentTurnPlayer ?? null
  const runtimePlayer = runtimePlayerId && session ? session.players[runtimePlayerId] ?? null : null

  return {
    expectedVersion: session?.version ?? null,
    role: runtimeAccess?.role ?? 'none',
    runtimePlayerId,
    currentActorPlayerId,
    isCurrentTurnPlayer:
      !!runtimePlayerId && !!currentActorPlayerId && runtimePlayerId === currentActorPlayerId,
    hasCombatState: session?.combat !== null && session?.combat !== undefined,
    hasPendingDecision: !!runtimePlayer?.pendingDecision,
    exAvailable: !!runtimePlayer?.exCard && !runtimePlayer.exOnCooldown,
    hasPlayerToken:
      runtimeAccess?.role === 'player' &&
      typeof runtimeAccess.playerToken === 'string' &&
      runtimeAccess.playerToken.length > 0,
    hasGmToken:
      runtimeAccess?.role === 'gm' &&
      typeof runtimeAccess.gmToken === 'string' &&
      runtimeAccess.gmToken.length > 0,
    canIssuePlayerCommand:
      runtimeAccess?.role === 'player' &&
      typeof runtimeAccess.playerToken === 'string' &&
      runtimeAccess.playerToken.length > 0 &&
      !!runtimePlayerId &&
      !!currentActorPlayerId &&
      runtimePlayerId === currentActorPlayerId,
    canResolvePendingCommand:
      runtimeAccess?.role === 'player' &&
      typeof runtimeAccess.playerToken === 'string' &&
      runtimeAccess.playerToken.length > 0 &&
      !!runtimePlayerId &&
      !!runtimePlayer?.pendingDecision,
    canIssueGmCommand:
      runtimeAccess?.role === 'gm' &&
      typeof runtimeAccess.gmToken === 'string' &&
      runtimeAccess.gmToken.length > 0,
  } satisfies CombatCommandGuards
}
