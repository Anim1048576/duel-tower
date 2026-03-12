package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClearRecentResultsCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId
) implements GameCommand {

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (playerId == null) {
            errors.add("playerId is required");
            return errors;
        }
        if (!state.players().containsKey(playerId)) {
            errors.add("player not found");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        state.runState().clearRecentResults();
        return List.of(new GameEvent.LogAppended("recent results cleared"));
    }
}
