package com.example.dueltower.engine.command;

import java.util.*;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

public final class EndTurnCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final Ids.PlayerId playerId;

    public EndTurnCommand(UUID commandId, long expectedVersion, Ids.PlayerId playerId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");
        if (state.player(playerId) == null) errors.add("player not found");
        if (state.combat() != null && !state.combat().currentTurnPlayer().equals(playerId)) errors.add("not your turn");

        PlayerState ps = state.player(playerId);
        if (ps != null && ps.pendingDecision() != null) errors.add("pending decision exists");
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        CombatState cs = state.combat();
        int nextIndex = cs.currentTurnIndex() + 1;

        if (nextIndex >= cs.turnOrder().size()) {
            nextIndex = 0;
            cs.round(cs.round() + 1);
        }

        cs.currentTurnIndex(nextIndex);
        events.add(new GameEvent.TurnAdvanced(cs.currentTurnPlayer().value(), cs.round()));
        events.add(new GameEvent.LogAppended(playerId.value() + " ends turn"));
        return events;
    }
}