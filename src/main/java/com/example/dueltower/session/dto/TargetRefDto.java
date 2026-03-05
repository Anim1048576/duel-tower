package com.example.dueltower.session.dto;

/**
 * Generic target reference used by command payloads.
 * Exactly one of playerId/enemyId/summonInstanceId should be set.
 */
public record TargetRefDto(
        String playerId,
        String enemyId,
        String summonOwnerPlayerId,
        String summonInstanceId
) {}
