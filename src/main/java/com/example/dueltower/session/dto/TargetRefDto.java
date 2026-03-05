package com.example.dueltower.session.dto;

/**
 * Generic target reference used by command payloads.
 * Exactly one target kind should be set:
 * - player target: playerId
 * - enemy target: enemyId
 * - summon target: summonOwnerPlayerId + summonInstanceId (both required)
 */
public record TargetRefDto(
        String playerId,
        String enemyId,
        String summonOwnerPlayerId,
        String summonInstanceId
) {}
