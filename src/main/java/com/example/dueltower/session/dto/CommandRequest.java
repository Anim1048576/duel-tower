package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Public command payload for {@code POST /api/sessions/{code}/command}.
 * <p>
 * Allowed {@code type} values:
 * START_COMBAT, DRAW, END_TURN, HAND_SWAP, PLAY_CARD, USE_EX, DISCARD_TO_HAND_LIMIT.
 * <p>
 * DRAW is a product rule command (not an admin/debug command):
 * it is validated with normal main-turn constraints (phase/actor/pending-decision).
 */
public record CommandRequest(
        String type,
        String commandId,
        Long expectedVersion,
        String playerId,
        Integer count,
        List<String> discardIds,
        String cardId,
        List<String> targetPlayerIds, // 추가
        List<String> targetEnemyIds   // 추가
) {}
