package com.example.dueltower.lab.dto;

import java.util.Map;

public record LabProbeEventDto(
        String type,
        String message,
        Map<String, Object> data
) {
}
