package com.example.dueltower.session.dto;

public record PendingDecisionDto(
        String type,
        String reason,
        Integer limit,
        Integer pickCount
) {}
