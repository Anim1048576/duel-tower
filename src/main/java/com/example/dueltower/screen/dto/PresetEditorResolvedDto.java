package com.example.dueltower.screen.dto;

import java.util.List;

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
