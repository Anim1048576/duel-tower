package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineProcessedCommandIdsTest {

    @Test
    void rejectsVeryOldDuplicateCommandIdsEvenAfterManyOtherCommands() {
        GameEngine engine = new GameEngine();
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        EngineContext ctx = new EngineContext(Map.of(), Map.of());

        UUID firstCommandId = UUID.randomUUID();
        EngineResult first = engine.process(state, ctx, new NoOpCommand(firstCommandId, state.version()));
        assertTrue(first.accepted());

        for (int i = 0; i < 12_000; i++) {
            EngineResult result = engine.process(state, ctx, new NoOpCommand(UUID.randomUUID(), state.version()));
            assertTrue(result.accepted());
        }

        EngineResult duplicate = engine.process(state, ctx, new NoOpCommand(firstCommandId, state.version()));

        assertFalse(duplicate.accepted());
        assertTrue(duplicate.errors().contains("duplicate command"));
    }

    private record NoOpCommand(UUID commandId, long expectedVersion) implements GameCommand {
        @Override
        public List<String> validate(GameState state, EngineContext ctx) {
            return List.of();
        }

        @Override
        public List<GameEvent> handle(GameState state, EngineContext ctx) {
            return List.of();
        }
    }
}
