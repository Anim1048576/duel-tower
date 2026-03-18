package com.example.dueltower.session.dto;

import com.example.dueltower.common.api.ApiErrorResponse;

import java.util.List;

public record EngineResponseDto(
        boolean accepted,
        List<String> errors,
        List<ApiErrorResponse> errorDetails,
        List<EventDto> events,
        SessionStateDto state
) {}
