package com.example.dueltower.session.dto;

import java.util.List;

public record SessionLogPageResponse(
        String code,
        List<SessionLogItemDto> items,
        Long nextBefore
) {}
