package com.example.dueltower.session.dto;

import java.util.List;

public record RunStateDto(
        int floor,
        String status,
        boolean resultPending,
        CurrentNodeDto currentNode,
        List<NodeChoiceDto> availableChoices,
        List<RecentResultDto> recentResults,
        InventoryDto inventory
) {
    public record CurrentNodeDto(
            String id,
            String name,
            String typeLabel,
            String phase,
            String danger,
            int floor
    ) {}

    public record NodeChoiceDto(
            String id,
            String name,
            String typeLabel,
            String rule,
            String phase,
            String danger,
            boolean disabled,
            String disabledReason
    ) {}

    public record RecentResultDto(
            String id,
            String type,
            String title,
            String summary,
            String detail,
            String source,
            String at
    ) {}

    public record InventoryDto(
            int keys,
            int chests,
            int gold,
            List<InventoryItemDto> items
    ) {}

    public record InventoryItemDto(
            String id,
            String name,
            int count,
            boolean bound,
            boolean battleUsable,
            String summary,
            String description,
            List<String> tags
    ) {}
}
