package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
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

        if (state.combat() != null && !state.combat().currentTurnPlayer().equals(playerId)) {
            errors.add("not your turn");
        }
        if (ps.pendingDecision() != null) errors.add("pending decision exists");

        if (!ps.hand().contains(cardId)) errors.add("card not in hand: " + cardId.value());

        CardInstance ci = state.card(cardId);
        if (ci == null) return List.of("card instance missing: " + cardId.value());
        if (!ci.ownerId().equals(playerId)) errors.add("not your card");

        CardDefinition def = ctx.def(ci.defId());
        CardEffect eff = ctx.effect(def.effectId());

        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, List.of());
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        CardInstance ci = state.card(cardId);
        CardDefinition def = ctx.def(ci.defId());

        CardEffect eff = ctx.effect(def.effectId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, events);
        eff.resolve(ec);

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " plays " + def.id().value()));
        return events;
    }
}