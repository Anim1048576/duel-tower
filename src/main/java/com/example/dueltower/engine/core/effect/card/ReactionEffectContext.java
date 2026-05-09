package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.TargetRef;

import java.util.List;

public record ReactionEffectContext(
        GameState state,
        EngineContext ctx,
        PlayerId actor,
        CardInstId cardId,
        PendingDecision.ReactionContext reaction,
        List<GameEvent> out
) {
    public TargetRef actorRef() {
        return TargetRef.ofPlayer(actor);
    }

    public String sourceLabel() {
        return actor.value();
    }
}
