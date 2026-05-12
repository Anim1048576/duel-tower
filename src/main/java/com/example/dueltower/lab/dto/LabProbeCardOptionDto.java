package com.example.dueltower.lab.dto;

import java.util.List;

public record LabProbeCardOptionDto(
        String cardId,
        String name,
        String type,
        int cost,
        String text,
        List<String> tags
) {
}
