package com.example.dueltower.content.enemy.model;

import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.List;
import java.util.Objects;

public record EnemyDefinition(
        String id,
        String name,
        EnemyRole role,
        String description,
        EnemyStatsDefinition stats,
        List<CardDefId> deck,
        List<EnemyStatusRef> startStatuses,
        List<EnemyPassiveRef> passives
) {
    public EnemyDefinition {
        deck = List.copyOf(Objects.requireNonNull(deck, "deck"));
        startStatuses = List.copyOf(Objects.requireNonNull(startStatuses, "startStatuses"));
        passives = List.copyOf(Objects.requireNonNull(passives, "passives"));
    }
}
