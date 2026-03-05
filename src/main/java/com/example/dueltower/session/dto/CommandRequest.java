package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Public command payload for {@code POST /api/sessions/{code}/command}.
 * <p>
 * Allowed {@code type} values:
 * START_COMBAT, DRAW, END_TURN, HAND_SWAP, PLAY_CARD, USE_EX, USE_SUMMON_ACTION, DISCARD_TO_HAND_LIMIT.
 * <p>
 * DRAW is a product rule command (not an admin/debug command):
 * it is validated with normal main-turn constraints (phase/actor/pending-decision).
 *
 * expectedVersion is required and must be provided by clients for optimistic concurrency.
 *
 * playerId is only the in-engine actor identifier for command execution.
 * Authorization must be derived from authenticated principal or server-side token checks.
 */
public record CommandRequest(
        String type,
        String commandId,
        Long expectedVersion,
        String playerId,
        Integer count,
        List<String> discardIds,
        String cardId,
        String summonId,
        List<String> targetPlayerIds, // legacy
        List<String> targetEnemyIds,  // legacy
        List<TargetRefDto> targets
) {}
