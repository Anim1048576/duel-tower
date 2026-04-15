package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * Editable preset draft snapshot.
 * This is the server-provided baseline that the frontend copies into local editor state.
 */
public record PresetEditorDraftDto(
        String name,
        Long characterId,
        List<String> deckCardIds,
        String exCardId,
        List<String> passiveIds
) {
}
