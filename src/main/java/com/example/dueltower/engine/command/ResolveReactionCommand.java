package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.card.ReactionEffectContext;
import com.example.dueltower.engine.core.effect.card.ReactiveCardEffect;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.core.reaction.ReactionOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResolveReactionCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final CardInstId cardId;

    public ResolveReactionCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId cardId) {
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
        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        if (ps.pendingDecision() == null) {
            errors.add("no pending decision");
            return errors;
        }
        if (!(ps.pendingDecision() instanceof PendingDecision.ReactionCard decision)) {
            errors.add("pending decision mismatch");
            return errors;
        }
        if (!playerId.equals(decision.context().ownerPlayerId())) {
            errors.add("reaction owner mismatch");
            return errors;
        }

        if (cardId == null) {
            if (!decision.skippable()) {
                errors.add("reaction skip is not allowed");
            }
            return errors;
        }

        if (!decision.candidateIds().contains(cardId)) {
            errors.add("card id not in reaction candidates: " + cardId.value());
            return errors;
        }
        if (!ps.hand().contains(cardId)) {
            errors.add("card not in hand: " + cardId.value());
            return errors;
        }

        CardInstance ci = state.card(cardId);
        if (ci == null) {
            errors.add("card instance missing: " + cardId.value());
            return errors;
        }
        if (!playerId.equals(ci.ownerId())) {
            errors.add("not your card");
        }
        if (ci.zone() != Zone.HAND) {
            errors.add("card not in hand: " + cardId.value());
        }

        try {
            if (!(ctx.effect(ci.defId()) instanceof ReactiveCardEffect reactive)) {
                errors.add("card is not reactive: " + ci.defId().value());
            } else if (!reactive.canReact(new ReactionEffectContext(state, ctx, playerId, cardId, decision.context(), List.of()))) {
                errors.add("card cannot react to trigger: " + decision.context().trigger().name());
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        PendingDecision.ReactionCard decision = (PendingDecision.ReactionCard) ps.pendingDecision();
        List<GameEvent> events = new ArrayList<>();

        if (cardId == null) {
            ps.pendingDecision(null);
            events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), ReactionOps.DECISION_TYPE));
            events.add(new GameEvent.LogAppended(ps.playerId().value() + " skipped reaction"));
            return events;
        }

        CardInstance ci = state.card(cardId);
        CardDefinition def = ctx.def(ci.defId());
        ReactiveCardEffect effect = (ReactiveCardEffect) ctx.effect(ci.defId());
        EngineContext reactionCtx = ctx.withResolvingReaction(true);

        ps.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), ReactionOps.DECISION_TYPE));
        events.add(new GameEvent.CombatLogAppended(
                "combat.reaction",
                "PLAYER",
                ps.playerId().value() + "이 [" + def.name() + "]으로 반응했다.",
                ps.playerId().value(),
                ps.playerId().value(),
                targetLabel(decision.context().source()),
                targetLabel(decision.context().source()),
                def.id().value(),
                def.name(),
                List.of(
                        "트리거: " + decision.context().trigger().name(),
                        "피해량: " + decision.context().damageAmount(),
                        "카드 ID: " + def.id().value(),
                        "인스턴스: " + cardId.value()
                ),
                reactionLogData(def, decision)
        ));

        effect.resolveReaction(new ReactionEffectContext(state, reactionCtx, playerId, cardId, decision.context(), events));

        Zone to = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();
        if (ps.hand().contains(cardId) && state.card(cardId) != null) {
            ZoneOps.moveToZoneOrVanishIfToken(state, reactionCtx, ps, cardId, to, events, MoveReason.PLAY);
        }
        events.add(new GameEvent.LogAppended(ps.playerId().value() + " resolved reaction " + cardId.value()));
        return events;
    }

    private Map<String, Object> reactionLogData(CardDefinition def, PendingDecision.ReactionCard decision) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorId", playerId.value());
        data.put("cardInstanceId", cardId.value().toString());
        data.put("cardDefId", def.id().value());
        data.put("cardName", def.name());
        data.put("reaction", true);
        data.put("trigger", decision.context().trigger().name());
        data.put("source", targetLabel(decision.context().source()));
        data.put("subject", targetLabel(decision.context().subject()));
        data.put("damageAmount", decision.context().damageAmount());
        data.put("sourceAction", decision.context().sourceAction());
        return data;
    }

    private static String targetLabel(com.example.dueltower.engine.model.TargetRef target) {
        if (target instanceof com.example.dueltower.engine.model.TargetRef.Player p) return "PLAYER:" + p.id().value();
        if (target instanceof com.example.dueltower.engine.model.TargetRef.Enemy e) return "ENEMY:" + e.id().value();
        if (target instanceof com.example.dueltower.engine.model.TargetRef.Summon s) return "SUMMON:" + s.ownerId().value() + ":" + s.summonId().value();
        return String.valueOf(target);
    }
}
