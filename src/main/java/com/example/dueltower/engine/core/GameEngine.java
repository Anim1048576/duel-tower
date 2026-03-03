package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.combat.VictoryOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class GameEngine {

    private static final String DUPLICATE_COMMAND = "duplicate command";
    private static final String VERSION_MISMATCH = "version mismatch";

    private static final int MAX_PROCESSED_COMMAND_IDS = 10_000;

    private final Map<UUID, Boolean> processedCommandIds = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > MAX_PROCESSED_COMMAND_IDS;
        }
    };
    public EngineResult process(GameState state, EngineContext ctx, GameCommand cmd) {
        long startNs = System.nanoTime();
        long beforeVersion = state.version();
        String cmdType = (cmd == null) ? "null" : cmd.getClass().getSimpleName();

        if (processedCommandIds.containsKey(cmd.commandId())) {
            log.debug("engine reject {} cmdId={} type={} stateVersion={}",
                    DUPLICATE_COMMAND, cmd.commandId(), cmdType, beforeVersion);
            return EngineResult.rejected(List.of("duplicate command"), state);
        }

        if (cmd.expectedVersion() != state.version()) {
            log.debug("engine reject {} cmdId={} type={} expectedVersion={} stateVersion={}",
                    VERSION_MISMATCH, cmd.commandId(), cmdType, cmd.expectedVersion(), beforeVersion);
            return EngineResult.rejected(List.of("version mismatch"), state);
        }

        List<String> errors = cmd.validate(state, ctx);
        if (!errors.isEmpty()) {
            log.debug("engine reject validation cmdId={} type={} errors={} stateVersion={}",
                    cmd.commandId(), cmdType, errors, beforeVersion);
            return EngineResult.rejected(errors, state);
        }

        // Collect events (copy to ensure mutability for post-processing)
        List<GameEvent> events = new ArrayList<>(cmd.handle(state, ctx));

        // Post-processing: victory/defeat check after ANY command
        VictoryOps.postHandleCheck(state, events);

        state.bumpVersion();
        processedCommandIds.put(cmd.commandId(), Boolean.TRUE);

        // Human-friendly log lines are useful even at DEBUG.
        if (log.isDebugEnabled()) {
            for (GameEvent e : events) {
                if (e instanceof GameEvent.LogAppended la) {
                    log.debug("game log cmdId={} type={} :: {}", cmd.commandId(), cmdType, la.line());
                }
            }
        }

        if (log.isTraceEnabled()) {
            for (GameEvent e : events) {
                log.trace("engine event cmdId={} type={} -> {}", cmd.commandId(), cmdType, e);
            }
        }

        long tookMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        log.debug("engine accepted cmdId={} type={} version {}->{} events={} ({}ms)",
                cmd.commandId(), cmdType, beforeVersion, state.version(), events.size(), tookMs);

        return EngineResult.accepted(events, state);
    }
}
