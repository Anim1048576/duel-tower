package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DrawCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final int count;

    public DrawCommand(UUID commandId, long expectedVersion, PlayerId playerId, int count) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.count = count;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState ps = state.player(playerId);
        if (ps == null) errors.add("player not found");
        if (count <= 0) errors.add("count must be positive");
        if (ps != null && ps.pendingDecision() != null) errors.add("pending decision exists");
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        ZoneOps.drawWithRefill(state, ctx, ps, count, events);

        HandLimitOps.ensureHandLimitOrPending(state, ctx, ps, events, "hand limit exceeded");

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " draws " + count));
        return events;
    }
}