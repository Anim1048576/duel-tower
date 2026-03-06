package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatEntityOpsBattleIncapacitatedTest {

    @Test
    void adjustHp_marksPlayerBattleIncapacitatedAndLogsWhenHpDropsToZero() {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        PlayerId playerId = new PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(5);
        state.players().put(playerId, player);

        CombatState combat = new CombatState();
        combat.turnOrder().add(TargetRef.ofPlayer(playerId));
        combat.currentTurnIndex(0);
        combat.phase(CombatPhase.MAIN);
        state.combat(combat);

        List<GameEvent> out = new ArrayList<>();
        CombatEntityOps.adjustHp(state, new EngineContext(Map.of(), Map.of()), out, TargetRef.ofPlayer(playerId), -5);

        assertThat(player.hp()).isZero();
        assertThat(player.status(CombatStatuses.BATTLE_INCAPACITATED)).isEqualTo(1);
        assertThat(out)
                .filteredOn(e -> e instanceof GameEvent.LogAppended)
                .map(e -> ((GameEvent.LogAppended) e).line())
                .anyMatch(line -> line.contains("becomes [전투 불능]") && line.contains("hp reached 0"));
    }
}
