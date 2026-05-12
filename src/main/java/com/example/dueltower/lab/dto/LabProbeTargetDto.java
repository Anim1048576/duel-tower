package com.example.dueltower.lab.dto;

import java.util.Map;

public record LabProbeTargetDto(
        String kind,
        String id,
        Integer hp,
        Integer maxHp,
        Map<String, Integer> statuses
) {
}
