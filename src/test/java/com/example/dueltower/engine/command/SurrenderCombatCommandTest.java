package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SurrenderCombatCommandTest {

    @Test
    void surrenderInCombatMarksPlayerAndEndsCombatAsDefeat() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 77L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);

        Ids.EnemyId enemyId = new Ids.EnemyId("e1");
        state.enemies().put(enemyId, new EnemyState(enemyId, 20));

        CombatState combat = new CombatState();
        combat.phase(CombatPhase.MAIN);
        combat.turnOrder().add(TargetRef.ofPlayer(playerId));
        combat.turnOrder().add(TargetRef.ofEnemy(enemyId));
        state.combat(combat);
        state.nodeState(NodeState.COMBAT);

        SurrenderCombatCommand command = new SurrenderCombatCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "test"
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(Map.of(), Map.of()), command);

        assertTrue(result.accepted());
        assertNull(state.combat(), "combat should be closed by post-victory check");
        assertTrue(state.runState().resultPending(), "run result should be pending after defeat");
        assertTrue(state.runState().recentResults().stream().anyMatch(r -> r.summary().contains("패배")));
    }

    @Test
    void surrenderOutsideCombatIsRejected() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 78L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        SurrenderCombatCommand command = new SurrenderCombatCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                null
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(Map.of(), Map.of()), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("surrender is only available during combat"));
    }
}
