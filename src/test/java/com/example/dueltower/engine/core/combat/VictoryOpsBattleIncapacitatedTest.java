package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VictoryOpsBattleIncapacitatedTest {

    @Test
    void check_treatsBattleIncapacitatedPlayerAsDefeated() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1L);

        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 1);
        state.players().put(player.playerId(), player);

        EnemyState enemy = new EnemyState(new Ids.EnemyId("e1"), 10, 1, 0, 1);
        state.enemies().put(enemy.enemyId(), enemy);

        assertThat(VictoryOps.check(state)).isEqualTo(VictoryOps.Outcome.PLAYERS_LOSE);
    }
}
