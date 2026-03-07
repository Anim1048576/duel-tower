package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.TurnFlow;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EnemyEndTurnCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final Ids.EnemyId enemyId;

    public EnemyEndTurnCommand(UUID commandId, long expectedVersion, Ids.EnemyId enemyId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.enemyId = enemyId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        CommandValidation.validateEnemyMainTurn(state, enemyId, errors);
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();
        TurnFlow.endCurrentAndAdvance(state, ctx, events);

        CombatState cs = state.combat();
        events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        return events;
    }
}
