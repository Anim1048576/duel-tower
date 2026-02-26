package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods to run keyword hooks for a card instance.
 */
public final class KeywordOps {
    private KeywordOps() {}

    public static boolean blocksDiscard(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason) {
        if (id == null) return false;
        CardInstance ci = state.card(id);
        if (ci == null) return false;
        CardDefinition def = ctx.def(ci.defId());
        List<String> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return false;

        DiscardCtx dc = new DiscardCtx(ps, id, reason);

        for (String raw : kws) {
            KeywordRuntime rt = KeywordRuntime.parse(raw);
            if (rt.id().isEmpty()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            if (ctx.keywordEffect(rt.id()).blocksDiscard(rt, dc)) return true;
        }
        return false;
    }

    public static List<String> validateDiscard(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason) {
        List<String> errors = new ArrayList<>();
        validateDiscard(state, ctx, ps, id, reason, errors);
        return errors;
    }

    public static void validateDiscard(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason, List<String> errors) {
        if (id == null) {
            errors.add("discard id is null");
            return;
        }
        CardInstance ci = state.card(id);
        if (ci == null) {
            errors.add("card instance missing: " + id.value());
            return;
        }
        CardDefinition def = ctx.def(ci.defId());
        List<String> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return;

        DiscardCtx dc = new DiscardCtx(ps, id, reason);

        for (String raw : kws) {
            KeywordRuntime rt = KeywordRuntime.parse(raw);
            if (rt.id().isEmpty()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            ctx.keywordEffect(rt.id()).validateDiscard(rt, dc, errors);
        }
    }
}
