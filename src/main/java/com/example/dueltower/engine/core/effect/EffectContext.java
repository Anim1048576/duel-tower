package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.List;

public record EffectContext(
        GameState state,
        EngineContext ctx,
        PlayerId actor,
        CardInstId cardId,
        TargetSelection selection,
        List<CardInstId> discardIds,
        List<GameEvent> out,
        SummonInstId statSourceSummonId,
        SummonInstId actorSourceSummonId
) {
    public EffectContext {
        discardIds = (discardIds == null) ? List.of() : List.copyOf(discardIds);
    }

    public EffectContext(
            GameState state,
            EngineContext ctx,
            PlayerId actor,
            CardInstId cardId,
            TargetSelection selection,
            List<GameEvent> out
    ) {
        this(state, ctx, actor, cardId, selection, List.of(), out, null, null);
    }

    public EffectContext(
            GameState state,
            EngineContext ctx,
            PlayerId actor,
            CardInstId cardId,
            TargetSelection selection,
            List<GameEvent> out,
            SummonInstId statSourceSummonId
    ) {
        this(state, ctx, actor, cardId, selection, List.of(), out, statSourceSummonId, null);
    }

    public EffectContext(
            GameState state,
            EngineContext ctx,
            PlayerId actor,
            CardInstId cardId,
            TargetSelection selection,
            List<GameEvent> out,
            SummonInstId statSourceSummonId,
            SummonInstId actorSourceSummonId
    ) {
        this(state, ctx, actor, cardId, selection, List.of(), out, statSourceSummonId, actorSourceSummonId);
    }

    public EffectContext(
            GameState state,
            EngineContext ctx,
            PlayerId actor,
            CardInstId cardId,
            TargetSelection selection,
            List<CardInstId> discardIds,
            List<GameEvent> out
    ) {
        this(state, ctx, actor, cardId, selection, discardIds, out, null, null);
    }

    public TargetRef actorRef() {
        if (actorSourceSummonId != null) {
            return TargetRef.ofSummon(actor, actorSourceSummonId);
        }
        if (actor != null && state.enemy(new EnemyId(actor.value())) != null) {
            return TargetRef.ofEnemy(new EnemyId(actor.value()));
        }
        return TargetRef.ofPlayer(actor);
    }

    public String sourceLabel() {
        if (actorSourceSummonId != null) {
            return actorSourceSummonId.value().toString();
        }
        return actor.value();
    }
}
