package com.example.dueltower.engine.command;

import java.util.*;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
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
        events.add(new GameEvent.LogAppended(playerId.value() + " ends turn"));

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
        PlayerState nextPs = state.player(cs.currentTurnPlayer());
        if (nextPs != null) {
            // 턴 시작 시: 패 교환(1턴 1회) 플래그 리셋
            nextPs.swappedThisTurn(false);

            // 턴 시작 드로우: 손패 4장 미만이면 2장, 아니면 1장
            int draw = (nextPs.hand().size() < 4) ? 2 : 1;
            ZoneOps.drawWithRefill(state, ctx, nextPs, draw, events);

            if (nextPs.hand().size() > nextPs.handLimit()) {
                nextPs.pendingDecision(new PendingDecision.DiscardToHandLimit("hand limit exceeded", nextPs.handLimit()));
                events.add(new GameEvent.PendingDecisionSet(nextPs.playerId().value(), "DISCARD_TO_HAND_LIMIT", "hand limit exceeded"));
            }

            events.add(new GameEvent.LogAppended(nextPs.playerId().value() + " draws " + draw + " (turn start)"));
        }

        events.add(new GameEvent.TurnAdvanced(cs.currentTurnPlayer().value(), cs.round()));
        return events;
    }
}