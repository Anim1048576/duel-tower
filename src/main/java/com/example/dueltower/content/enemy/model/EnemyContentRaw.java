package com.example.dueltower.content.enemy.model;

import java.util.List;

public record EnemyContentRaw(
        List<EnemyRaw> enemies
) {
    public record EnemyRaw(
            String id,
            String name,
            String role,
            String description,
            EnemyStatsRaw stats,
            List<String> deck,
            List<EnemyStatusRaw> startStatuses,
            List<EnemyPassiveRaw> passives
    ) {
    }

    public record EnemyStatsRaw(
            Integer maxHp,
            Integer maxActionPoint,
            Integer attackPower,
            Integer healPower
    ) {
    }

    public record EnemyStatusRaw(
            String statusId,
            Integer stacks
    ) {
    }

    public record EnemyPassiveRaw(
            String passiveId
    ) {
    }
}
