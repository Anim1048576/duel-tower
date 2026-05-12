package com.example.dueltower.lab.dto;

import java.util.Map;

public record LabProbeActorDto(
        Integer attackPower,
        Integer healPower,
        Integer hp,
        Integer maxHp,
        Map<String, Integer> statuses
) {
}
