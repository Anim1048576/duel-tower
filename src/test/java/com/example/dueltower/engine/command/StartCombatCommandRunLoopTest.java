package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StartCombatCommandRunLoopTest {

    @Test
    void startCombatSeedsPlaceholderEncounterForCombatNode() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        RunState.NodeChoice combatChoice = state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .filter(choice -> choice.phase() == RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        state.runState().beginNode(combatChoice);
        state.nodeState(NodeState.COMBAT);

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new StartCombatCommand(UUID.randomUUID(), state.version(), playerId)
        );

        assertTrue(result.accepted());
        assertNotNull(state.combat());
        assertFalse(state.enemies().isEmpty(), "combat node should provide a placeholder encounter");
        assertFalse(state.runState().resultPending(), "combat should not skip directly to result pending");
    }
}
