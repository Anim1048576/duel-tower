package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Public command payload for {@code POST /api/sessions/{code}/command}.
 * <p>
 * Allowed {@code type} values:
 * START_COMBAT, DRAW, END_TURN, HAND_SWAP, PLAY_CARD, USE_EX, USE_SUMMON_ACTION, USE_ITEM, BUY_SHOP_ITEM, OPEN_CHEST, RESOLVE_JUDGEMENT, SURRENDER_COMBAT, DISCARD_TO_HAND_LIMIT, RESOLVE_INITIATIVE_TIE, SEARCH_PICK/RESOLVE_SEARCH_PICK, SELECT_NODE_CHOICE, CLEAR_RECENT_RESULTS, ENEMY_PLAY_CARD, ENEMY_USE_EX, ENEMY_END_TURN.
 * <p>
 * DRAW and HAND_SWAP are product rule commands (not admin/debug commands):
 * they are validated with normal main-turn constraints (phase/actor/pending-decision).
 *
 * Player-auth required types (must pass X-Player-Token -> playerId verification):
 * DRAW, PLAY_CARD, HAND_SWAP, END_TURN, USE_EX, USE_SUMMON_ACTION, USE_ITEM, BUY_SHOP_ITEM, OPEN_CHEST, RESOLVE_JUDGEMENT, SURRENDER_COMBAT, DISCARD_TO_HAND_LIMIT, RESOLVE_INITIATIVE_TIE, SEARCH_PICK/RESOLVE_SEARCH_PICK, SELECT_NODE_CHOICE, CLEAR_RECENT_RESULTS.
 *
 * expectedVersion is required and must be provided by clients for optimistic concurrency.
 *
 * Future extension notes:
 * - BUY_SHOP_ITEM 후보 필드: offerId
 * - OPEN_CHEST 후보 필드: count
 * - RESOLVE_JUDGEMENT 후보 필드: choiceId/selectedIds
 * - CLAIM_RECENT_RESULT 후보 필드: resultId/resultIndex
 * - SELL_INVENTORY_ITEM 후보 필드: itemId/count
 * - RETREAT_COMBAT 후보 필드: reason
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
        String itemId,
        String offerId,
        List<String> targetPlayerIds, // legacy
        List<String> targetEnemyIds,  // legacy
        List<TargetRefDto> targets,
        Integer tieGroupIndex,
        List<String> orderedActorKeys,
        List<String> selectedIds,
        String choiceId,
        String resultId,
        Integer resultIndex,
        String reason
) {
    public String normalizedType() {
        return (type == null) ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public String trimmedPlayerId() {
        return trimToNull(playerId);
    }

    public String trimmedEnemyId() {
        return trimToNull(enemyId);
    }

    public String trimmedCardId() {
        return trimToNull(cardId);
    }

    public String trimmedSummonId() {
        return trimToNull(summonId);
    }

    public String trimmedItemId() {
        return trimToNull(itemId);
    }

    public String trimmedOfferId() {
        return trimToNull(offerId);
    }

    public String trimmedChoiceId() {
        return trimToNull(choiceId);
    }

    public String trimmedResultId() {
        return trimToNull(resultId);
    }

    public String trimmedReason() {
        return trimToNull(reason);
    }

    public int countOrDefault(int fallback) {
        return (count == null) ? fallback : count;
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
