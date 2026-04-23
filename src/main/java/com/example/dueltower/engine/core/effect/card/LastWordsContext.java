package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;

import java.util.List;
import java.util.Objects;

public record LastWordsContext(
        GameState state,
        EngineContext ctx,
        Ids.PlayerId actor,
        Ids.CardInstId sourceCardId,
        List<GameEvent> out
) {
    public LastWordsContext {
        Objects.requireNonNull(state);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(sourceCardId);
        Objects.requireNonNull(out);
    }
}
