package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
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
    private final TargetSelection selection;

    public PlayCardCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId cardId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.cardId = cardId;
        this.selection = selection == null ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        CombatState cs = state.combat();
        if (cs != null) {
            TargetRef cur = cs.currentTurnActor();
            if (!(cur instanceof TargetRef.Player p) || !p.id().equals(playerId)) {
                errors.add("not your turn");
            }
        }
        if (ps.pendingDecision() != null) errors.add("pending decision exists");

        if (!ps.hand().contains(cardId)) errors.add("card not in hand: " + cardId.value());

        CardInstance ci = state.card(cardId);
        if (ci == null) return List.of("card instance missing: " + cardId.value());
        if (!ci.ownerId().equals(playerId)) errors.add("not your card");

        CardDefinition def = ctx.def(ci.defId());
        Zone to = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();

        // 코스트/AP 체크
        int need = def.cost();
        int have = ps.ap();
        if (have < need) errors.add("not enough ap (need=" + need + ", have=" + have + ")");

        // 필드 제한 체크 (resolveTo가 FIELD일 때)
        if (to == Zone.FIELD && ps.field().size() >= ps.fieldLimit()) {
            errors.add("field is full (limit=" + ps.fieldLimit() + ")");
        }

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, List.of());
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        CardInstance ci = state.card(cardId);
        if (ci == null) {
            events.add(new GameEvent.LogAppended("missing card instance: " + cardId.value()));
            return events;
        }

        CardDefinition def = ctx.def(ci.defId());
        Zone to = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();

        // 코스트 지불
        int cost = def.cost();
        if (ps.ap() < cost) {
            throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + ps.ap() + ")");
        }
        if (cost > 0) ps.ap(ps.ap() - cost);

        // 효과 해결
        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, events);
        eff.resolve(ec);

        // 카드 이동 (HAND -> resolveTo)
        if (ps.hand().contains(cardId) && state.card(cardId) != null) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, cardId, Zone.HAND, to, events);
        }

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " plays " + def.id().value()));
        return events;
    }
}