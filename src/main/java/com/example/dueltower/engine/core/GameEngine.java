package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.combat.VictoryOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public final class GameEngine {

    private static final int MAX_PROCESSED_COMMAND_IDS = 10_000;

    private final Map<UUID, Boolean> processedCommandIds = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > MAX_PROCESSED_COMMAND_IDS;
        }
    };
    public EngineResult process(GameState state, EngineContext ctx, GameCommand cmd) {
        if (processedCommandIds.containsKey(cmd.commandId())) {
            return EngineResult.rejected(List.of("duplicate command"), state);
        }

        if (cmd.expectedVersion() != state.version()) {
            return EngineResult.rejected(List.of("version mismatch"), state);
        }

        List<String> errors = cmd.validate(state, ctx);
        if (!errors.isEmpty()) {
            return EngineResult.rejected(errors, state);
        }

        // Collect events (copy to ensure mutability for post-processing)
        List<GameEvent> events = new ArrayList<>(cmd.handle(state, ctx));

        // Post-processing: victory/defeat check after ANY command
        VictoryOps.postHandleCheck(state, events);

        state.bumpVersion();
        processedCommandIds.put(cmd.commandId(), Boolean.TRUE);

        return EngineResult.accepted(events, state);
    }
}
