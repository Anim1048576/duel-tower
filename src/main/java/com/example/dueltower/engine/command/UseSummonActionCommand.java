package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UseSummonActionCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final SummonInstId summonId;
    private final TargetSelection selection;

    public UseSummonActionCommand(UUID commandId, long expectedVersion, PlayerId playerId, SummonInstId summonId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.summonId = summonId;
        this.selection = (selection == null) ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState ps = CommandValidation.validateMainTurn(state, playerId, errors);
        if (ps == null) return errors;

        SummonState summon = state.summon(summonId);
        if (summon == null) {
            errors.add("summon not found: " + summonId.value());
            return errors;
        }

        if (!playerId.equals(summon.owner())) errors.add("summon is not yours");
        if (summon.hp() <= 0) errors.add("summon is dead");
        if (summon.actionUsedThisTurn()) errors.add("summon action already used this turn");

        int cost = summon.actionCost();
        if (ps.ap() < cost) errors.add("not enough ap (need=" + cost + ", have=" + ps.ap() + ")");

        CardInstId sourceCardId = summon.sourceCardId();
        CardInstance source = state.card(sourceCardId);
        if (source == null) {
            errors.add("summon source card missing: " + sourceCardId.value());
            return errors;
        }

        CardEffect effect = ctx.effect(source.defId());
        List<GameEvent> dummyOut = new ArrayList<>();
        EffectContext ec = new EffectContext(state, ctx, playerId, sourceCardId, selection, dummyOut);
        errors.addAll(effect.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        if (ps == null) throw new IllegalStateException("player not found: " + playerId.value());

        SummonState summon = state.summon(summonId);
        if (summon == null) throw new IllegalStateException("summon not found: " + summonId.value());
        if (!playerId.equals(summon.owner())) throw new IllegalStateException("summon is not yours");
        if (summon.hp() <= 0) throw new IllegalStateException("summon is dead");
        if (summon.actionUsedThisTurn()) throw new IllegalStateException("summon action already used this turn");

        int cost = summon.actionCost();
        if (ps.ap() < cost) {
            throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + ps.ap() + ")");
        }

        List<GameEvent> events = new ArrayList<>();
        if (cost > 0) ps.ap(ps.ap() - cost);

        CardInstId sourceCardId = summon.sourceCardId();
        CardInstance source = state.card(sourceCardId);
        if (source == null) throw new IllegalStateException("summon source card missing: " + sourceCardId.value());

        CardEffect effect = ctx.effect(source.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, sourceCardId, selection, events);
        effect.resolve(ec);

        summon.actionUsedThisTurn(true);

        events.add(new GameEvent.LogAppended(
                playerId.value() + " uses summon action " + summon.id().value() + " (cost=" + cost + ")"
        ));
        return events;
    }
}
