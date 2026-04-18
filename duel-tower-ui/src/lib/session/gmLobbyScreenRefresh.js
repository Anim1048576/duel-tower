/**
 * GmLobby refresh policy keeps screen-model updates and lightweight local
 * selections readable.
 *
 * The page declares why it is refreshing, and this helper decides whether the
 * refresh should show loading UI and whether the current start-player selection
 * should be preserved or reset back to the latest server recommendation.
 */

/**
 * @typedef {'initial-load' | 'retry-load' | 'route-change' | 'polling' | 'action-kick' | 'action-reset' | 'action-start-combat-success' | 'action-start-combat-failed'} GmLobbyScreenRefreshReason
 */

/**
 * @param {GmLobbyScreenRefreshReason} reason
 */
export function resolveGmLobbyScreenRefreshPlan(reason) {
  switch (reason) {
    case 'initial-load':
    case 'retry-load':
    case 'route-change':
      return {
        showLoading: true,
        preserveStartPlayerSelection: false,
      }
    case 'polling':
    case 'action-kick':
    case 'action-reset':
    case 'action-start-combat-failed':
      return {
        showLoading: false,
        preserveStartPlayerSelection: true,
      }
    case 'action-start-combat-success':
      return {
        showLoading: false,
        preserveStartPlayerSelection: false,
      }
  }
}

/**
 * @param {{
 *   participantCards: { playerId: string }[]
 *   startCombat: {
 *     recommendedStartPlayerId: string | null
 *     selectableStartPlayers: { playerId: string }[]
 *   }
 * }} screen
 * @param {{
 *   selectedKickPlayerId: string
 *   selectedStartPlayerId: string
 *   preserveStartPlayerSelection: boolean
 * }} current
 */
export function resolveGmLobbySelections(screen, current) {
  const participantIds = screen.participantCards.map((participant) => participant.playerId)
  const selectableStartPlayerIds = screen.startCombat.selectableStartPlayers.map((player) => player.playerId)
  const recommendedStartPlayerId = screen.startCombat.recommendedStartPlayerId ?? ''

  const nextKickPlayerId = participantIds.includes(current.selectedKickPlayerId)
    ? current.selectedKickPlayerId
    : participantIds[0] ?? ''

  const canKeepSelectedStartPlayer =
    current.preserveStartPlayerSelection &&
    selectableStartPlayerIds.includes(current.selectedStartPlayerId)

  const nextStartPlayerId = canKeepSelectedStartPlayer
    ? current.selectedStartPlayerId
    : recommendedStartPlayerId || selectableStartPlayerIds[0] || ''

  return {
    selectedKickPlayerId: nextKickPlayerId,
    selectedStartPlayerId: nextStartPlayerId,
  }
}
