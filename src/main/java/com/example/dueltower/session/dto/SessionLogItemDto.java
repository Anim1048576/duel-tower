package com.example.dueltower.session.dto;

import java.time.Instant;

public record SessionLogItemDto(
        long cursor,
        long version,
        String type,
        String message,
        Instant timestamp
) {}
