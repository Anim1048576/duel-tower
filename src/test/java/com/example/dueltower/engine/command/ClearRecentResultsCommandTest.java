package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClearRecentResultsCommandTest {

    @Test
    void clearsRunRecentResultsWhenAccepted() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        RunState.NodeChoice eventChoice = state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .filter(choice -> choice.phase() != RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        state.runState().beginNode(eventChoice);
        state.runState().resolveCurrentNode("reward", "탐색 완료", eventChoice.name() + " 결과 확인", "테스트", 120, 0, 0);
        assertFalse(state.runState().recentResults().isEmpty());

        ClearRecentResultsCommand command = new ClearRecentResultsCommand(UUID.randomUUID(), state.version(), playerId);
        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertTrue(result.accepted());
        assertTrue(state.runState().recentResults().isEmpty());
        assertEquals(2, state.runState().floor());
        assertNull(state.runState().currentNode());
    }

    @Test
    void rejectsWhenPlayerDoesNotExist() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 456L);

        ClearRecentResultsCommand command = new ClearRecentResultsCommand(
                UUID.randomUUID(),
                state.version(),
                new Ids.PlayerId("ghost")
        );

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("player not found"));
    }
}
