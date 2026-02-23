package com.example.dueltower.session.dto;

import java.util.List;

public record EngineResponseDto(
        boolean accepted,
        List<String> errors,
        List<EventDto> events,
        SessionStateDto state
) {}
