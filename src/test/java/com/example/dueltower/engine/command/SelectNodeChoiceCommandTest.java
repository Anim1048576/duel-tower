package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SelectNodeChoiceCommandTest {

    @Test
    void validChoiceUpdatesRunStateAndReturnsAccepted() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        String choiceId = state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .findFirst()
                .orElseThrow()
                .id();

        SelectNodeChoiceCommand command = new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId);

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertTrue(result.accepted());
        assertNotNull(state.runState().currentNode());
        assertEquals(choiceId, state.runState().currentNode().id());
        assertEquals(2, state.runState().floor());
        assertTrue(state.nodeState() == NodeState.NON_COMBAT || state.nodeState() == NodeState.COMBAT);
    }

    @Test
    void invalidChoiceIsRejected() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 456L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        SelectNodeChoiceCommand command = new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, "NOT-EXISTS");

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("choice not found"));
        assertNull(state.runState().currentNode());
    }
}
