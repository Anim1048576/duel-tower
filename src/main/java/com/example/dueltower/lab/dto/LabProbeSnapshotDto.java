package com.example.dueltower.lab.dto;

import java.util.List;
import java.util.Map;

public record LabProbeSnapshotDto(
        Actor actor,
        List<Target> targets
) {
    public record Actor(
            String id,
            int hp,
            int maxHp,
            int ap,
            Map<String, Integer> statuses
    ) {
    }

    public record Target(
            String kind,
            String id,
            int hp,
            int maxHp,
            Integer ap,
            Map<String, Integer> statuses
    ) {
    }
}
