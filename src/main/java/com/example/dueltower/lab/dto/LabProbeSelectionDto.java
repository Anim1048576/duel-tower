package com.example.dueltower.lab.dto;

import java.util.List;

public record LabProbeSelectionDto(
        List<LabProbeTargetDto> targets,
        List<String> discardIds,
        List<String> selectedIds,
        String choiceId
) {
}
