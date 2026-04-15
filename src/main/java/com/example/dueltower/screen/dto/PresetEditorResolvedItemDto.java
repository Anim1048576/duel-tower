package com.example.dueltower.screen.dto;

import java.util.List;

public record PresetEditorResolvedItemDto(
        String id,
        String label,
        String subtitle,
        String meta,
        List<PresetEditorResolvedTagDto> tags
) {
}
