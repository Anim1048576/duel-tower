package com.example.dueltower.screen.dto;

/**
 * Server-derived editor metadata.
 * These fields describe the current screen snapshot and are not local preview calculations.
 */
public record PresetEditorDerivedDto(
        boolean dirty,
        String createdAtLabel,
        String updatedAtLabel
) {
}
