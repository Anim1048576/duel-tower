package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;

import java.util.*;

public final class DiscardToHandLimitCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final List<CardInstId> discardIds;

    public DiscardToHandLimitCommand(UUID commandId, long expectedVersion, PlayerId playerId, List<CardInstId> discardIds) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.discardIds = discardIds == null ? List.of() : List.copyOf(discardIds);
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

        // pendingDecision이 handLimit(6)로 만들어졌더라도,
        // 부동 수만큼은 최소 보장되게 실제 목표 limit을 보정
        int effectiveLimit = Math.max(dt.limit(), HandLimitOps.immovableCountInHand(state, ctx, ps));
        int needDiscard = Math.max(0, ps.hand().size() - effectiveLimit);

        if (discardIds.size() != needDiscard) {
            errors.add("discard count mismatch (need=" + needDiscard + ")");
        }

        // 중복/NULL 체크
        Set<CardInstId> uniq = new HashSet<>();
        for (CardInstId id : discardIds) {
            if (id == null) {
                errors.add("discard id is null");
                continue;
            }
            if (!uniq.add(id)) {
                errors.add("duplicate discard id: " + id.value());
            }
        }

        for (CardInstId id : discardIds) {
            if (id == null) continue;

            if (!ps.hand().contains(id)) {
                errors.add("card not in hand: " + id.value());
                continue;
            }

            if (HandLimitOps.isImmovable(state, ctx, id)) {
                errors.add("cannot discard a '부동' card: " + id.value());
            }
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        // validate 우회 방지
        for (CardInstId id : discardIds) {
            if (HandLimitOps.isImmovable(state, ctx, id)) {
                throw new IllegalStateException("cannot discard a '부동' card: " + id.value());
            }
        }

        for (CardInstId id : discardIds) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, id, Zone.HAND, Zone.GRAVE, events);
        }

        ps.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT"));
        events.add(new GameEvent.LogAppended(ps.playerId().value() + " discards " + discardIds.size()));
        return events;
    }
}