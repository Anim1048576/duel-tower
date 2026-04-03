package com.example.dueltower.session.dto;

import java.util.List;

public record SessionEventPageResponse(
        String code,
        long fromVersion,
        long toVersion,
        List<SessionEventItemDto> items,
        boolean hasMore
) {}
