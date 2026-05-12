package com.example.dueltower.lab.dto;

public record LabDiceRequest(
        String notation,
        Integer rollCount,
        Long seed
) {
}
