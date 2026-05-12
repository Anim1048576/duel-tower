package com.example.dueltower.lab.dto;

public record LabEffectProbeRequest(
        String cardId,
        LabProbeActorDto actor,
        LabProbeTargetDto target,
        LabProbeSelectionDto selection,
        Long seed,
        Boolean validateOnly
) {
}
