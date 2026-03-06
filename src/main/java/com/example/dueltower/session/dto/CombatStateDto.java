package com.example.dueltower.session.dto;

import java.util.List;
import java.util.Map;

public record CombatStateDto(
        int round,
        List<String> turnOrder,
        int currentTurnIndex,
        String currentTurnPlayer,
        String phase,
        Map<String, Integer> initiatives,
        List<List<String>> initiativeTieGroups,
        List<SummonDto> summons,
        List<EnemyCombatDto> enemies
) {
    public record SummonDto(
            String summonId,
            String owner,
            int hp,
            int atk,
            int heal,
            boolean actionAvailable
    ) {}

    public record EnemyCombatDto(
            String enemyId,
            int hp,
            int maxHp,
            int ap,
            int attackPower,
            int healPower,
            String exCardId,
            boolean exActivatable,
            boolean exOnCooldown,
            Map<String, Integer> statuses
    ) {}
}
