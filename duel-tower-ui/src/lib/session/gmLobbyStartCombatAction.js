/**
 * GmLobby start-combat action follow-up policy.
 *
 * The backend owns combat-start procedure, blocked reasons, retry semantics,
 * and latest-screen snapshots. The frontend only decides how to reflect the
 * action result: navigate, apply latest screen, or refresh the current screen.
 */

/**
 * @typedef {'action-start-combat-success' | 'action-start-combat-failed'} GmLobbyStartCombatRefreshReason
 */

/**
 * @param {{
 *   success: boolean
 *   outcome: string
 *   message: string | null
 *   disabledReason: { userMessage: string | null } | null
 *   nextRoute: string | null
 *   latestScreen: unknown | null
 * }} response
 */
export function resolveGmLobbyStartCombatFollowUp(response) {
  const isSuccess = response.success === true
  const hasLatestScreen = response.latestScreen != null
  const shouldNavigate = isSuccess && typeof response.nextRoute === 'string' && response.nextRoute.length > 0

  return {
    shouldNavigate,
    nextRoute: shouldNavigate ? response.nextRoute : null,
    shouldApplyLatestScreen: hasLatestScreen,
    preserveStartPlayerSelection: !isSuccess,
    refreshReason: hasLatestScreen
      ? null
      : /** @type {GmLobbyStartCombatRefreshReason} */ (
          isSuccess ? 'action-start-combat-success' : 'action-start-combat-failed'
        ),
    successMessage: isSuccess ? response.message ?? 'Combat start completed.' : null,
    errorTitle: isSuccess
      ? null
      : response.outcome === 'BLOCKED'
        ? 'Combat start unavailable'
        : response.outcome === 'GM_ACCESS_REQUIRED'
          ? 'GM access restore failed'
          : 'Combat start failed',
    errorMessage: isSuccess
      ? null
      : response.disabledReason?.userMessage ??
        response.message ??
        '전투를 시작하지 못했습니다.',
  }
}
