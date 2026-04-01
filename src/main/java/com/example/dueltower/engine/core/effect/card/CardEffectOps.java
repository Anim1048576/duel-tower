package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.List;

public final class CardEffectOps {
    private CardEffectOps() {}

    public static int modifiedCost(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            int baseCost,
            List<GameEvent> out,
            String source
    ) {
        if (ci == null || !ctx.hasEffect(ci.defId())) return Math.max(0, baseCost);

        CardEffect effect = ctx.effect(ci.defId());
        CardCostCtx hookCtx = new CardCostCtx(state, actor, cardId, ci, def, source);
        int next = effect.onCost(hookCtx, baseCost);
        return Math.max(0, next);
    }
}
