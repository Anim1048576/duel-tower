package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.TurnFlow;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        if (state.combat() != null) {
            if (state.combat().phase() != CombatPhase.MAIN) {
                errors.add("invalid phase: " + state.combat().phase());
            }
            TargetRef cur = state.combat().currentTurnActor();
            if (!(cur instanceof TargetRef.Player p) || !p.id().equals(playerId)) {
                errors.add("not your turn");
            }
        }

        PlayerState ps = state.player(playerId);
        if (ps != null && ps.pendingDecision() != null) errors.add("pending decision exists");
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        TurnFlow.endCurrentAndAdvanceToNextPlayer(state, ctx, events);

        CombatState cs = state.combat();
        events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        return events;
    }
}