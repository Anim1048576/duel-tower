package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Public command payload for {@code POST /api/sessions/{code}/command}.
 * <p>
 * Allowed {@code type} values:
 * START_COMBAT, DRAW, END_TURN, HAND_SWAP, PLAY_CARD, USE_EX, USE_SUMMON_ACTION, DISCARD_TO_HAND_LIMIT, RESOLVE_INITIATIVE_TIE, ENEMY_PLAY_CARD, ENEMY_USE_EX, ENEMY_END_TURN.
 * <p>
 * DRAW and HAND_SWAP are product rule commands (not admin/debug commands):
 * they are validated with normal main-turn constraints (phase/actor/pending-decision).
 *
 * Player-auth required types (must pass X-Player-Token -> playerId verification):
 * DRAW, PLAY_CARD, HAND_SWAP, END_TURN, USE_EX, USE_SUMMON_ACTION, DISCARD_TO_HAND_LIMIT, RESOLVE_INITIATIVE_TIE.
 *
 * expectedVersion is required and must be provided by clients for optimistic concurrency.
 *
 * Target serialization rules:
 * - Prefer `targets` over legacy targetPlayerIds/targetEnemyIds.
 * - Player target: {"playerId":"P1"}
 * - Enemy target: {"enemyId":"E1"}
 * - Summon target: {"summonOwnerPlayerId":"P1","summonInstanceId":"<uuid>"}
 *
 * playerId is only the in-engine actor identifier for command execution.
 * Authorization must be derived from authenticated principal or server-side token checks.
 */
public record CommandRequest(
        String type,
        String commandId,
        Long expectedVersion,
        String playerId,
        String enemyId,
        Integer count,
        List<String> discardIds,
        String cardId,
        String summonId,
        List<String> targetPlayerIds, // legacy
        List<String> targetEnemyIds,  // legacy
        List<TargetRefDto> targets,
        Integer tieGroupIndex,
        List<String> orderedActorKeys
) {}
