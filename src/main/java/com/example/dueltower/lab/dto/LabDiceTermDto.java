package com.example.dueltower.lab.dto;

public record LabDiceTermDto(
        int sign,
        String kind,
        Integer value,
        Integer count,
        Integer sides,
        String selector,
        Integer selectorAmount,
        String display
) {
}
