package com.example.dueltower.lab.dto;

import java.util.List;

public record LabEffectProbeRequest(
        String cardId,
        LabProbeActorDto actor,
        LabProbeTargetDto target,
        List<LabProbeTargetDto> targets,
        LabProbeSelectionDto selection,
        List<LabProbeExtraCardDto> extraCards,
        Long seed,
        Boolean validateOnly
) {
    public LabEffectProbeRequest(
            String cardId,
            LabProbeActorDto actor,
            LabProbeTargetDto target,
            LabProbeSelectionDto selection,
            Long seed,
            Boolean validateOnly
    ) {
        this(cardId, actor, target, null, selection, null, seed, validateOnly);
    }
}
