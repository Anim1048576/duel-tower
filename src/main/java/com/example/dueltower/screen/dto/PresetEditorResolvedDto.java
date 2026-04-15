package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * Server-resolved preview model for PresetEditor.
 * Character / EX / deck / passive labels and metadata are assembled on the backend.
 */
public record PresetEditorResolvedDto(
        String characterLabel,
        String characterSubtitle,
        List<PresetEditorResolvedTagDto> characterTags,
        String exLabel,
        String exSubtitle,
        List<PresetEditorResolvedTagDto> exTags,
        List<PresetEditorResolvedItemDto> deckItems,
        List<PresetEditorResolvedItemDto> passiveItems
) {
}
