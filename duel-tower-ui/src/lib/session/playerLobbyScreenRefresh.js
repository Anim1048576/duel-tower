/**
 * PlayerLobby refresh policy keeps screen-model updates readable.
 * The page decides why it is refreshing, and this helper decides whether that
 * refresh should show loading UI or resync the local draft from the latest
 * server screen snapshot.
 */

/**
 * @typedef {'initial-load' | 'retry-load' | 'route-change' | 'polling' | 'action-toggle-ready' | 'action-save-loadout'} PlayerLobbyScreenRefreshReason
 */

/**
 * @param {PlayerLobbyScreenRefreshReason} reason
 */
export function resolvePlayerLobbyScreenRefreshPlan(reason) {
  switch (reason) {
    case 'initial-load':
    case 'retry-load':
    case 'route-change':
      return {
        showLoading: true,
        forceDraftSync: true,
      }
    case 'polling':
    case 'action-toggle-ready':
      return {
        showLoading: false,
        forceDraftSync: false,
      }
    case 'action-save-loadout':
      return {
        showLoading: false,
        forceDraftSync: true,
      }
  }
}
