export function toPlayerActorKey(playerId: string): string {
  return `P:${playerId}`
}

export function toEnemyActorKey(enemyId: string): string {
  return `E:${enemyId}`
}

export function isPlayerActorTurn(actorKey: string | null | undefined, playerId: string): boolean {
  if (!actorKey || !playerId) return false
  return actorKey === playerId || actorKey === toPlayerActorKey(playerId)
}

export function isEnemyActorTurn(actorKey: string | null | undefined): actorKey is string {
  return Boolean(actorKey?.startsWith('E:'))
}

export function enemyIdFromActorKey(actorKey: string): string {
  return actorKey.replace(/^E:/, '')
}
