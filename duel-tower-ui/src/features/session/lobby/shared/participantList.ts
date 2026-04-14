import type { PlayerStateDto } from '../../../../lib/api/sessionTypes'

export type LobbyParticipantTone = 'accent' | 'muted' | 'success' | 'warning'

export function buildPlayerLobbyParticipantStateLabel(player: PlayerStateDto, currentPlayerId: string | null) {
  if (player.playerId === currentPlayerId) {
    return player.ready ? 'You 쨌 Ready' : 'You 쨌 Joined'
  }

  return player.ready ? 'Ready' : 'Joined'
}

export function buildPlayerLobbyParticipantTone(
  player: PlayerStateDto,
  currentPlayerId: string | null,
): LobbyParticipantTone {
  if (player.playerId === currentPlayerId) {
    return player.ready ? 'success' : 'accent'
  }

  return player.ready ? 'success' : 'muted'
}

export function sortPlayersByCurrentPlayer(
  players: readonly PlayerStateDto[],
  currentPlayerId: string | null,
) {
  return [...players].sort((left, right) => {
    if (left.playerId === currentPlayerId) return -1
    if (right.playerId === currentPlayerId) return 1
    return left.playerId.localeCompare(right.playerId)
  })
}

export function buildPlayerLobbyParticipantItems<TNote>({
  players,
  currentPlayerId,
  buildNote,
}: {
  players: readonly PlayerStateDto[]
  currentPlayerId: string | null
  buildNote: (player: PlayerStateDto) => TNote
}) {
  return sortPlayersByCurrentPlayer(players, currentPlayerId).map((player, index) => ({
    id: player.playerId,
    slot: `P${index + 1}`,
    name: player.playerId === currentPlayerId ? `${player.playerId} (You)` : player.playerId,
    state: buildPlayerLobbyParticipantStateLabel(player, currentPlayerId),
    tone: buildPlayerLobbyParticipantTone(player, currentPlayerId),
    note: buildNote(player),
  }))
}

export function buildReadyParticipantStateLabel(player: PlayerStateDto) {
  return player.ready ? 'Ready' : 'Not ready'
}

export function buildReadyParticipantTone(player: PlayerStateDto): LobbyParticipantTone {
  return player.ready ? 'success' : 'muted'
}

export function sortPlayersByReady(players: readonly PlayerStateDto[]) {
  return [...players].sort((left, right) => {
    if (left.ready !== right.ready) {
      return left.ready ? -1 : 1
    }

    return left.playerId.localeCompare(right.playerId)
  })
}

export function getPreferredReadyPlayerId(players: readonly PlayerStateDto[]) {
  const sortedPlayers = sortPlayersByReady(players)
  return sortedPlayers.find((player) => player.ready)?.playerId ?? sortedPlayers[0]?.playerId ?? ''
}

export function buildGmLobbyParticipantItems<TDetails>({
  players,
  buildDetails,
}: {
  players: readonly PlayerStateDto[]
  buildDetails: (player: PlayerStateDto) => TDetails
}) {
  return sortPlayersByReady(players).map((player, index) => ({
    id: player.playerId,
    slot: `P${index + 1}`,
    name: player.playerId,
    readyLabel: buildReadyParticipantStateLabel(player),
    readyTone: buildReadyParticipantTone(player),
    ...buildDetails(player),
  }))
}
