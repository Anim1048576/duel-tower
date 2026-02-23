package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GameEngine {

    private final Set<UUID> processedCommandIds = new HashSet<>();

    public EngineResult process(GameState state, EngineContext ctx, GameCommand cmd) {
        if (processedCommandIds.contains(cmd.commandId())) {
            return EngineResult.rejected(List.of("duplicate command"), state);
        }

        if (cmd.expectedVersion() != state.version()) {
            return EngineResult.rejected(List.of("version mismatch"), state);
        }

        List<String> errors = cmd.validate(state, ctx);
        if (!errors.isEmpty()) {
            return EngineResult.rejected(errors, state);
        }

        List<GameEvent> events = cmd.handle(state, ctx);

        state.bumpVersion();
        processedCommandIds.add(cmd.commandId());

        return EngineResult.accepted(events, state);
    }
}