package com.example.dueltower.screen.dto;

import java.util.List;

public record PresetEditorDraftDto(
        String name,
        Long characterId,
        List<String> deckCardIds,
        String exCardId,
        List<String> passiveIds
) {
}
