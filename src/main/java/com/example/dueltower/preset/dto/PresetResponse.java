package com.example.dueltower.preset.dto;

import java.sql.Timestamp;
import java.util.List;

public record PresetResponse(
        Long id,
        String ownerUsername,
        String name,
        Long characterId,
        List<String> deckCardIds,
        String exCardId,
        List<String> passiveIds,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
