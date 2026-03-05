package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.combat.VictoryOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

@Slf4j
public final class GameEngine {

    private static final String DUPLICATE_COMMAND = "duplicate command";
    private static final String VERSION_MISMATCH = "version mismatch";

    /**
     * commandId dedupe는 기본적으로 세션 생존 동안 유지한다.
     * 메모리 제한이 필요한 경우 TTL을 켜서 오래된 id를 정리할 수 있다.
     */
    private static final long DEDUPE_TTL_DISABLED = -1L;

    private final Map<UUID, Long> processedCommandIdsFirstSeenAt = new HashMap<>();
    private final long processedCommandIdTtlMs;
    private final LongSupplier nowMsSupplier;

    public GameEngine() {
        this(DEDUPE_TTL_DISABLED, System::currentTimeMillis);
    }

    GameEngine(long processedCommandIdTtlMs, LongSupplier nowMsSupplier) {
        this.processedCommandIdTtlMs = processedCommandIdTtlMs;
        this.nowMsSupplier = nowMsSupplier;
    }

    public EngineResult process(GameState state, EngineContext ctx, GameCommand cmd) {
        long startNs = System.nanoTime();
        long beforeVersion = state.version();
        String cmdType = (cmd == null) ? "null" : cmd.getClass().getSimpleName();
        long nowMs = nowMsSupplier.getAsLong();

        cleanupExpiredProcessedCommandIds(nowMs);

        if (processedCommandIdsFirstSeenAt.containsKey(cmd.commandId())) {
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

        // Debug safety net: detect zone/list inconsistencies as early as possible.
        if (log.isDebugEnabled()) {
            ZoneOps.assertInvariants(state);
        }

        state.bumpVersion();
        processedCommandIdsFirstSeenAt.put(cmd.commandId(), nowMs);

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

    private void cleanupExpiredProcessedCommandIds(long nowMs) {
        if (processedCommandIdTtlMs <= 0) {
            return;
        }

        long expireBefore = nowMs - processedCommandIdTtlMs;
        processedCommandIdsFirstSeenAt.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
    }
}
