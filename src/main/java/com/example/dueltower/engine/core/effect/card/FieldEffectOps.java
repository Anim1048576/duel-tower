package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.keyword.kdb.K004_Summon;
import com.example.dueltower.content.status.sdb.S901_InstalledFieldBuff;
import com.example.dueltower.content.status.sdb.S902_SummonFieldAura;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class FieldEffectOps {
    private FieldEffectOps() {}

    public static void onEnterField(GameState state, EngineContext ctx, PlayerState owner, CardInstId cardId, List<GameEvent> out, String source) {
        withActiveTransition(state, ctx, owner, cardId, out, source, true, (ec, effect, sourceCardId) -> {
            applyKeywordAura(ec, sourceCardId, +1);
            effect.onEnterField(ec, sourceCardId);
        });
    }

    public static void onLeaveField(GameState state, EngineContext ctx, PlayerState owner, CardInstId cardId, List<GameEvent> out, String source) {
        withActiveTransition(state, ctx, owner, cardId, out, source, false, (ec, effect, sourceCardId) -> {
            effect.onLeaveField(ec, sourceCardId);
            applyKeywordAura(ec, sourceCardId, -1);
        });
    }

    public static void onTurnStart(GameState state, EngineContext ctx, PlayerState owner, List<GameEvent> out, String source) {
        for (CardInstId sourceCardId : orderedActiveCards(state, owner)) {
            CardInstance ci = state.card(sourceCardId);
            if (ci == null || !ci.fieldEffectActive()) continue;
            EffectContext ec = new EffectContext(state, ctx, owner.playerId(), sourceCardId, TargetSelection.empty(), out);
            ctx.effect(ci.defId()).onTurnStart(ec, sourceCardId);
        }
    }

    public static void onTurnEnd(GameState state, EngineContext ctx, PlayerState owner, List<GameEvent> out, String source) {
        for (CardInstId sourceCardId : orderedActiveCards(state, owner)) {
            CardInstance ci = state.card(sourceCardId);
            if (ci == null || !ci.fieldEffectActive()) continue;
            EffectContext ec = new EffectContext(state, ctx, owner.playerId(), sourceCardId, TargetSelection.empty(), out);
            ctx.effect(ci.defId()).onTurnEnd(ec, sourceCardId);
        }
    }

    private static void applyKeywordAura(EffectContext ec, CardInstId sourceCardId, int delta) {
        if (delta == 0) return;
        GameState state = ec.state();
        EngineContext ctx = ec.ctx();

        StatusRuntime rt = new StatusRuntime(state, ctx, ec.out(), "FIELD_EFFECT");
        if (KeywordOps.hasKeyword(state, ctx, sourceCardId, K003_Installed.ID)) {
            rt.stacksAdd(TargetRef.ofPlayer(ec.actor()), S901_InstalledFieldBuff.ID, delta);
        }

        if (KeywordOps.hasKeyword(state, ctx, sourceCardId, K004_Summon.ID)) {
            if (state.combat() == null) return;
            rt.stacksAdd(StatusOwnerRef.of(CombatState.FactionId.PLAYERS), S902_SummonFieldAura.ID, delta);
        }
    }

    private interface FieldHookAction {
        void run(EffectContext ec, CardEffect effect, CardInstId sourceCardId);
    }

    private static void withActiveTransition(
            GameState state,
            EngineContext ctx,
            PlayerState owner,
            CardInstId cardId,
            List<GameEvent> out,
            String source,
            boolean entering,
            FieldHookAction action
    ) {
        if (state == null || ctx == null || owner == null || cardId == null) return;
        CardInstance ci = state.card(cardId);
        if (ci == null || ci.fieldEffectTransitioning()) return;

        if (entering && ci.fieldEffectActive()) return;
        if (!entering && !ci.fieldEffectActive()) return;

        ci.fieldEffectTransitioning(true);
        try {
            EffectContext ec = new EffectContext(state, ctx, owner.playerId(), cardId, TargetSelection.empty(), out);
            CardEffect effect = ctx.effect(ci.defId());
            action.run(ec, effect, cardId);
            ci.fieldEffectActive(entering);
        } finally {
            ci.fieldEffectTransitioning(false);
        }
    }

    private static List<CardInstId> orderedActiveCards(GameState state, PlayerState owner) {
        LinkedHashSet<CardInstId> ids = new LinkedHashSet<>(owner.field());
        ids.addAll(owner.summonByCard().keySet());

        List<CardInstId> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator
                .comparing((CardInstId id) -> {
                    CardInstance ci = state.card(id);
                    return (ci == null) ? "~" : ci.defId().value();
                })
                .thenComparing(id -> id.value().toString()));
        return ordered;
    }
}
