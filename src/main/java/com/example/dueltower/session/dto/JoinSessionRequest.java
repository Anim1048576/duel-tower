package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Join payload contract.
 * - playerId: required
 * - passiveIds: optional, 0..2 items
 * - each passive id must be non-blank and match server passive id format (e.g. P001)
 * - duplicate ids are not allowed
 */
public record JoinSessionRequest(
        String playerId,
        List<String> passiveIds
) {}
