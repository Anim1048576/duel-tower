import type {
  ActionDescriptor,
  CharacterView,
  CombatSnapshot,
  CombatTarget,
  EngineEvent,
  EngineResponse,
  ResolutionLog,
  SessionSnapshot,
  TargetRef,
} from '../model'

type RawApiResponse = {
  accepted?: boolean
  errors?: unknown[]
  events?: EngineEvent[]
  state?: unknown
  resolutionLogs?: unknown[]
  resultSummary?: unknown
  detailedBreakdown?: unknown
}

function asStringArray(input: unknown): string[] {
  return Array.isArray(input) ? input.map(String) : []
}

function targetKey(target: CombatTarget) {
  return target.type === 'player' ? `player:${target.playerId}` : `summon:${target.playerId}:${target.summonId}`
}

function collectValidTargets(state: SessionSnapshot, sourcePlayerId: string): CombatTarget[] {
  const players = Object.values(state.players ?? {})
  const playerTargets: CombatTarget[] = players.map((p) => ({ type: 'player', playerId: p.playerId }))
  const summonTargets: CombatTarget[] = (state.combat?.summons ?? []).map((s) => ({
    type: 'summon',
    playerId: s.owner,
    summonId: s.summonId,
  }))

  const unique = new Map<string, CombatTarget>()
  for (const t of [...playerTargets, ...summonTargets]) unique.set(targetKey(t), t)
  return [...unique.values()].filter((t) => t.playerId !== sourcePlayerId || t.type === 'summon')
}

function buildActionDescriptors(state: SessionSnapshot, player: CharacterView): ActionDescriptor[] {
  const combat = state.combat
  if (!combat) return []

  const isMyTurn = combat.currentTurnPlayer === player.playerId
  const canAct = isMyTurn && !player.pendingDecision
  const validTargets = collectValidTargets(state, player.playerId)
  const disabledReason = canAct ? '' : player.pendingDecision ? '결정 처리 후 행동 가능' : '내 턴에만 행동 가능'

  const actions: ActionDescriptor[] = []

  for (const cardId of player.hand ?? []) {
    actions.push({
      id: `play:${cardId}`,
      kind: 'play',
      label: `카드 사용 · ${cardId}`,
      commandType: 'PLAY_CARD',
      sourcePlayerId: player.playerId,
      cardId,
      requiresTarget: true,
      validTargets,
      disabledReason,
    })
  }

  if (player.exCard) {
    actions.push({
      id: `useEx:${player.exCard}`,
      kind: 'useEx',
      label: `EX 사용 · ${player.exCard}`,
      commandType: 'USE_EX',
      sourcePlayerId: player.playerId,
      cardId: player.exCard,
      requiresTarget: true,
      validTargets,
      disabledReason: player.exOnCooldown ? 'EX 쿨다운 중' : disabledReason,
    })
  }

  for (const summon of combat.summons ?? []) {
    if (summon.owner !== player.playerId) continue
    actions.push({
      id: `summon:${summon.summonId}`,
      kind: 'summon',
      label: `소환 행동 · ${summon.summonId}`,
      commandType: 'USE_SUMMON_ACTION',
      sourcePlayerId: player.playerId,
      summonId: summon.summonId,
      requiresTarget: true,
      validTargets,
      disabledReason: summon.actionAvailable ? disabledReason : '소환 행동 불가',
    })
  }

  return actions
}

export function adaptSessionSnapshot(raw: any): SessionSnapshot {
  const players = Object.fromEntries(
    Object.entries(raw?.players ?? {}).map(([playerId, pRaw]) => {
      const player = { ...(pRaw as CharacterView), playerId } as CharacterView
      return [playerId, player]
    }),
  ) as Record<string, CharacterView>

  const combat = raw?.combat ? ({ ...(raw.combat as CombatSnapshot) } as CombatSnapshot) : null
  const snapshot: SessionSnapshot = {
    sessionCode: String(raw?.sessionCode ?? ''),
    sessionId: String(raw?.sessionId ?? ''),
    version: Number(raw?.version ?? 0),
    seed: Number(raw?.seed ?? 0),
    players,
    combat,
    cards: (raw?.cards ?? {}) as SessionSnapshot['cards'],
  }

  const enrichedPlayers = Object.fromEntries(
    Object.entries(snapshot.players).map(([playerId, p]) => [
      playerId,
      {
        ...p,
        availableActions: buildActionDescriptors(snapshot, p),
      },
    ]),
  ) as Record<string, CharacterView>

  return {
    ...snapshot,
    players: enrichedPlayers,
    combat: snapshot.combat
      ? {
          ...snapshot.combat,
          availableActions: Object.values(enrichedPlayers)
            .flatMap((p) => p.availableActions)
            .filter((a) => a.sourcePlayerId === snapshot.combat?.currentTurnPlayer),
        }
      : null,
  }
}

export function toTargetRef(target: CombatTarget): TargetRef {
  if (target.type === 'player') return { playerId: target.playerId }
  return { summonOwnerPlayerId: target.playerId, summonInstanceId: target.summonId }
}

export function toResolutionLogs(raw: RawApiResponse): ResolutionLog[] {
  if (Array.isArray(raw.resolutionLogs)) {
    return raw.resolutionLogs.map((entry, i) => ({
      id: `resolution-${Date.now()}-${i}`,
      at: new Date().toISOString(),
      level: 'info',
      summary: String((entry as any)?.summary ?? (entry as any)?.resultSummary ?? 'result summary'),
      breakdown: String((entry as any)?.breakdown ?? (entry as any)?.detailedBreakdown ?? JSON.stringify(entry ?? {})),
    }))
  }

  if (raw.resultSummary || raw.detailedBreakdown) {
    return [
      {
        id: `resolution-${Date.now()}`,
        at: new Date().toISOString(),
        level: raw.accepted === false ? 'warn' : 'info',
        summary: String(raw.resultSummary ?? (raw.accepted ? '요청 성공' : '요청 거부')),
        breakdown: String(raw.detailedBreakdown ?? ''),
      },
    ]
  }

  if (raw.events?.length) {
    return [
      {
        id: `resolution-${Date.now()}`,
        at: new Date().toISOString(),
        level: raw.accepted === false ? 'warn' : 'info',
        summary: raw.accepted === false ? '요청 거부' : '요청 처리 완료',
        breakdown: raw.events.map((e) => `${e.type} ${JSON.stringify(e.payload ?? {})}`).join('\n'),
      },
    ]
  }

  return []
}

export function adaptEngineResponse(raw: RawApiResponse): EngineResponse {
  return {
    accepted: Boolean(raw.accepted),
    errors: asStringArray(raw.errors),
    events: Array.isArray(raw.events) ? raw.events : [],
    state: adaptSessionSnapshot(raw.state ?? {}),
    resolutionLogs: toResolutionLogs(raw),
  }
}
