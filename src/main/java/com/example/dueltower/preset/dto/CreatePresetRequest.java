package com.example.dueltower.preset.dto;

import java.util.List;

public record CreatePresetRequest(
        String name,
        Long characterId,
        List<String> deckCardIds,
        String exCardId,
        List<String> passiveIds
) {
}
