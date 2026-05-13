package com.example.dueltower.lab.dto;

import java.util.List;

public record LabDiceResponse(
        String notation,
        String normalizedNotation,
        LabDiceSpecDto spec,
        LabDiceExpressionDto expression,
        int min,
        int max,
        boolean expectedAvailable,
        String expected,
        Long expectedNumerator,
        Long expectedDenominator,
        String expectedNote,
        int rollCount,
        Long seed,
        List<Integer> rolls,
        List<LabDiceHistogramEntryDto> histogram
) {
}
