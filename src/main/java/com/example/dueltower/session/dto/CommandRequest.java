package com.example.dueltower.session.dto;

import java.util.List;

public record CommandRequest(
        String type,
        String commandId,
        Long expectedVersion,
        String playerId,
        Integer count,
        List<String> discardIds,
        String cardId,
        List<String> targetPlayerIds, // 추가
        List<String> targetEnemyIds   // 추가
) {}
