package com.example.dueltower.lab.dto;

import java.util.List;

public record LabEffectProbeResponse(
        String cardId,
        String cardName,
        boolean valid,
        List<String> validationErrors,
        boolean resolved,
        LabProbeSnapshotDto before,
        LabProbeSnapshotDto after,
        LabProbeChangesDto changes,
        List<LabProbeEventDto> events,
        List<String> notes
) {
}
