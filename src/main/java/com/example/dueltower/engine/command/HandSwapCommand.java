package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 패 교환: 내 턴 1회, 손패 1장 버리고 1장 드로우.
 * 단, 토큰을 버린 경우 드로우 없음.
 * 단, 부동 키워드 카드는 (강제 상황이 아니므로) 버릴 수 없음.
 */
public final class HandSwapCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final CardInstId discardId;

    public HandSwapCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId discardId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.discardId = discardId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) {
            errors.add("player not found");
            return errors;
        }

        CombatState cs = state.combat();
        if (cs != null) {
            TargetRef cur = cs.currentTurnActor();
            if (!(cur instanceof TargetRef.Player p) || !p.id().equals(playerId)) {
                errors.add("not your turn");
            }
        }

        if (ps.pendingDecision() != null) errors.add("pending decision exists");
        if (ps.swappedThisTurn()) errors.add("hand swap already used this turn");

        if (discardId == null) {
            errors.add("discardId is required");
            return errors;
        }

        if (!ps.hand().contains(discardId)) {
            errors.add("card not in hand: " + discardId.value());
            return errors;
        }

        CardInstance ci = state.card(discardId);
        if (ci == null) {
            errors.add("card instance missing: " + discardId.value());
            return errors;
        }

        CardDefinition def = ctx.def(ci.defId());
        if (def.keywords() != null && def.keywords().contains(Keyword.부동)) {
            errors.add("cannot discard a '부동' card");
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        CardInstance ci = state.card(discardId);
        CardDefinition def = ctx.def(ci.defId());
        boolean isToken = def.token();

        // 1) 손패 1장 버리기(토큰이면 소멸)
        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, discardId, Zone.HAND, Zone.GRAVE, events);

        // 2) 토큰이 아니면 1장 드로우
        if (!isToken) {
            ZoneOps.drawWithRefill(state, ctx, ps, 1, events);
        }

        ps.swappedThisTurn(true);

        if (ps.hand().size() > ps.handLimit()) {
            ps.pendingDecision(new PendingDecision.DiscardToHandLimit("hand limit exceeded", ps.handLimit()));
            events.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT", "hand limit exceeded"));
        }

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " hand swaps (discard 1" + (isToken ? ", token" : ", draw 1") + ")"));
        return events;
    }
}
