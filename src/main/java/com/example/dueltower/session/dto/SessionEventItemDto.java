package com.example.dueltower.session.dto;

import java.time.Instant;
import java.util.Map;

public record SessionEventItemDto(
        long cursor,
        long version,
        String type,
        Map<String, Object> payload,
        Instant timestamp
) {}
