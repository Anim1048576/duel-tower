package com.example.dueltower.engine.command;

import java.util.*;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Ids.*;

public final class DiscardToHandLimitCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final List<CardInstId> discardIds;

    public DiscardToHandLimitCommand(UUID commandId, long expectedVersion, PlayerId playerId, List<CardInstId> discardIds) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.discardIds = List.copyOf(discardIds);
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        if (!(ps.pendingDecision() instanceof PendingDecision.DiscardToHandLimit dt)) {
            errors.add("no discard-to-limit pending decision");
            return errors;
        }

        int needDiscard = Math.max(0, ps.hand().size() - dt.limit());
        if (discardIds.size() != needDiscard) errors.add("discard count mismatch");

        for (CardInstId id : discardIds) {
            if (!ps.hand().contains(id)) errors.add("card not in hand: " + id.value());
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        for (CardInstId id : discardIds) {
            ZoneOps.moveHandToGrave(state, ps, id, events);
        }

        ps.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT"));
        events.add(new GameEvent.LogAppended(ps.playerId().value() + " discards " + discardIds.size()));
        return events;
    }
}