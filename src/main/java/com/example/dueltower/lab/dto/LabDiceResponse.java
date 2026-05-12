package com.example.dueltower.lab.dto;

import java.util.List;

public record LabDiceResponse(
        String notation,
        LabDiceSpecDto spec,
        int min,
        int max,
        String expected,
        long expectedNumerator,
        long expectedDenominator,
        int rollCount,
        Long seed,
        List<Integer> rolls,
        List<LabDiceHistogramEntryDto> histogram
) {
}
