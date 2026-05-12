package com.example.dueltower.lab.dto;

import java.util.List;

public record LabProbeChangesDto(
        EntityChanges actor,
        List<TargetChanges> targets
) {
    public record EntityChanges(
            int hpChange,
            List<StatusChange> statusChanges,
            List<String> addedStatuses,
            List<String> removedStatuses,
            List<String> changedStatuses
    ) {
    }

    public record TargetChanges(
            String kind,
            String id,
            int hpChange,
            List<StatusChange> statusChanges,
            List<String> addedStatuses,
            List<String> removedStatuses,
            List<String> changedStatuses
    ) {
    }

    public record StatusChange(
            String statusId,
            int before,
            int after
    ) {
    }
}
