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

public final class PlayCardCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final CardInstId cardId;

    public PlayCardCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId cardId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.cardId = cardId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        if (state.combat() != null && !state.combat().currentTurnPlayer().equals(playerId)) {
            errors.add("not your turn");
        }
        if (ps.pendingDecision() != null) errors.add("pending decision exists");

        if (!ps.hand().contains(cardId)) errors.add("card not in hand: " + cardId.value());

        CardInstance ci = state.card(cardId);
        if (ci == null) return List.of("card instance missing: " + cardId.value());
        if (!ci.ownerId().equals(playerId)) errors.add("not your card");

        CardDefinition def = ctx.def(ci.defId());
        if (def.keywords() != null && def.keywords().contains(Keyword.부동)) {
            errors.add("cannot play a '부동' card");
        }

        if (!ctx.hasEffect(def.effectId())) errors.add("missing effect: " + def.effectId());

        // 비용/자원은 나중에 PlayerState에 붙이면 여기서 체크
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        CardInstance ci = state.card(cardId);
        CardDefinition def = ctx.def(ci.defId());

        // 1) 공통: 사용 처리(이동)
        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, cardId, Zone.HAND, def.resolveTo(), events);

        // 2) 카드 전용 효과
        ctx.effect(def.effectId()).resolve(state, ctx, playerId, cardId, events);

        // 3) 공통: 손패 제한 체크
        if (ps.hand().size() > ps.handLimit()) {
            ps.pendingDecision(new PendingDecision.DiscardToHandLimit("hand limit exceeded", ps.handLimit()));
            events.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT", "hand limit exceeded"));
        }

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " plays " + def.id().value()));
        return events;
    }
}