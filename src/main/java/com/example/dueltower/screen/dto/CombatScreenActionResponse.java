package com.example.dueltower.screen.dto;

import java.util.List;
import java.util.Map;

/**
 * Result envelope for combat screen actions.
 *
 * <p>The frontend consumes a single response shape for combat write flows:
 * action outcome, structured failure reason, and the latest combat screen when
 * available. This keeps command/pending/recent-result procedures in the BFF
 * layer instead of repeating engine-response handling in the combat page.</p>
 */
public record CombatScreenActionResponse(
        boolean success,
        String outcome,
        String message,
        DisabledReasonDto disabledReason,
        Long latestVersion,
        List<String> serverNotices,
        Map<String, Object> resultSummary,
        CombatScreenResponse latestScreen
) {
}
