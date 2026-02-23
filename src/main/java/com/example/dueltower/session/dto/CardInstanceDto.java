package com.example.dueltower.session.dto;

import java.util.Map;

public record CardInstanceDto(
        String instanceId,
        String defId,
        String ownerId,
        String zone,
        Map<String, Integer> counters
) {}
