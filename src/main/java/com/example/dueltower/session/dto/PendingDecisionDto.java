package com.example.dueltower.session.dto;

import java.util.List;

public record PendingDecisionDto(
        String type,
        String reason,
        Integer limit,
        Integer pickCount,
        Integer groupIndex,
        List<String> actorKeys
) {}
