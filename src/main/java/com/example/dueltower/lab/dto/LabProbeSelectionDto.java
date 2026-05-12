package com.example.dueltower.lab.dto;

import java.util.List;

public record LabProbeSelectionDto(
        List<LabProbeTargetDto> targets,
        List<String> discardIds,
        List<String> selectedIds,
        List<String> discardAliases,
        List<String> selectedAliases,
        String choiceId
) {
    public LabProbeSelectionDto(
            List<LabProbeTargetDto> targets,
            List<String> discardIds,
            List<String> selectedIds,
            String choiceId
    ) {
        this(targets, discardIds, selectedIds, null, null, choiceId);
    }
}
