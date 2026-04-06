package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.List;

public record EffectContext(
        GameState state,
        EngineContext ctx,
        PlayerId actor,
        CardInstId cardId,
        TargetSelection selection,
        List<GameEvent> out,
        SummonInstId statSourceSummonId
) {
    public EffectContext(
            GameState state,
            EngineContext ctx,
            PlayerId actor,
            CardInstId cardId,
            TargetSelection selection,
            List<GameEvent> out
    ) {
        this(state, ctx, actor, cardId, selection, out, null);
    }
}
