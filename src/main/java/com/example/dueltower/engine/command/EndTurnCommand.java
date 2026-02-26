package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.status.StatusPhases;
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

        CombatState cs = state.combat();
        TargetRef current = cs.currentTurnActor();
        events.add(new GameEvent.LogAppended(CombatState.actorKey(current) + " ends turn"));
        StatusPhases.turnEnd(state, ctx, current, events, "TURN_END");

        int nextIndex = cs.currentTurnIndex() + 1;
        int nextRound = cs.round();

        if (nextIndex >= cs.turnOrder().size()) {
            nextIndex = 0;
            nextRound = cs.round() + 1;
            cs.round(nextRound);

            // 라운드가 넘어가는 시점에 EX 쿨다운 만료 정리
            for (PlayerState ps : state.players().values()) {
                if (ps.exCooldownUntilRound() > 0 && nextRound > ps.exCooldownUntilRound()) {
                    ps.exCooldownUntilRound(0);
                }
            }
        }

        cs.currentTurnIndex(nextIndex);
        TargetRef nextActor = cs.currentTurnActor();
        if (nextActor instanceof TargetRef.Player np) {
            PlayerState nextPs = state.player(np.id());
            if (nextPs != null) {
                nextPs.swappedThisTurn(false);

                int draw = (nextPs.hand().size() < 4) ? 2 : 1;
                ZoneOps.drawWithRefill(state, ctx, nextPs, draw, events);

                HandLimitOps.ensureHandLimitOrPending(state, ctx, nextPs, events, "hand limit exceeded");

                events.add(new GameEvent.LogAppended(nextPs.playerId().value() + " draws " + draw + " (turn start)"));
            }
        }

        events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        return events;
    }
}