package com.example.dueltower.engine.core.effect.cardmodifier;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.LastWordsBatchCollector;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CardModifierOps {
    private CardModifierOps() {}

    private record HookEntry(String modifierId, int value, int priority, int index) {}

    private static List<HookEntry> collectEntries(EngineContext ctx, CardInstance ci) {
        if (ci == null || ci.modifiers().isEmpty()) return List.of();

        List<HookEntry> entries = new ArrayList<>();
        for (int i = 0; i < ci.modifiers().size(); i++) {
            OwnedCardModifier modifier = ci.modifiers().get(i);
            if (modifier == null || !ctx.hasCardModifierDef(modifier.modifierId())) continue;
            entries.add(new HookEntry(
                    modifier.modifierId(),
                    modifier.value(),
                    ctx.cardModifierDef(modifier.modifierId()).priority(),
                    i
            ));
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority)
                .thenComparing(HookEntry::modifierId)
                .thenComparingInt(HookEntry::index));
        return entries;
    }

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
        int cur = baseCost;
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            ModifyCostCtx hookCtx = new ModifyCostCtx(state, ctx, out, actor, cardId, ci, def);
            cur = ctx.cardModifierEffect(it.modifierId()).modifyCost(rt, hookCtx, cur);
        }
        return Math.max(0, cur);
    }

    public static void validatePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<String> errors
    ) {
        List<GameEvent> dummyOut = new ArrayList<>();
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, dummyOut, "VALIDATE", it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, dummyOut, actor, actorState, cardId, ci, def, null);
            ctx.cardModifierEffect(it.modifierId()).validatePlayCard(rt, hookCtx, errors);
        }
    }

    public static void beforeResolvePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        beforeResolvePlayCard(state, ctx, actor, actorState, cardId, ci, def, out, null, source);
    }

    public static void beforeResolvePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            LastWordsBatchCollector lastWordsBatchCollector,
            String source
    ) {
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, out, actor, actorState, cardId, ci, def, lastWordsBatchCollector);
            ctx.cardModifierEffect(it.modifierId()).beforeResolvePlayCard(rt, hookCtx);
        }
    }

    public static void afterResolvePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        afterResolvePlayCard(state, ctx, actor, actorState, cardId, ci, def, out, null, source);
    }

    public static void afterResolvePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            LastWordsBatchCollector lastWordsBatchCollector,
            String source
    ) {
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, out, actor, actorState, cardId, ci, def, lastWordsBatchCollector);
            ctx.cardModifierEffect(it.modifierId()).afterResolvePlayCard(rt, hookCtx);
        }
    }


    public static void validateUseEx(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<String> errors
    ) {
        List<GameEvent> dummyOut = new ArrayList<>();
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, dummyOut, "VALIDATE", it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, dummyOut, actor, actorState, cardId, ci, def, null);
            ctx.cardModifierEffect(it.modifierId()).validateUseEx(rt, hookCtx, errors);
        }
    }

    public static void beforeResolveUseEx(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, out, actor, actorState, cardId, ci, def, null);
            ctx.cardModifierEffect(it.modifierId()).beforeResolveUseEx(rt, hookCtx);
        }
    }

    public static void afterResolveUseEx(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            PlayerState actorState,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            PlayCardModifierCtx hookCtx = new PlayCardModifierCtx(state, ctx, out, actor, actorState, cardId, ci, def, null);
            ctx.cardModifierEffect(it.modifierId()).afterResolveUseEx(rt, hookCtx);
        }
    }

    public static TargetRef resolveEnemyOneTarget(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            TargetRef chosenEnemy,
            List<TargetRef> candidates,
            List<GameEvent> out,
            String source
    ) {
        if (chosenEnemy == null) return null;

        TargetRef cur = chosenEnemy;
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            EnemyOneModifierTargetCtx hookCtx = new EnemyOneModifierTargetCtx(state, ctx, out, actor, cardId, ci, def);
            TargetRef next = ctx.cardModifierEffect(it.modifierId()).resolveEnemyOneTarget(rt, hookCtx, cur, candidates);
            if (next != null) cur = next;
        }
        return cur;
    }

    public static int onOutgoingDamage(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef actor,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            TargetRef target,
            int amount,
            String source
    ) {
        int cur = amount;
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            OutgoingCardValueCtx hookCtx = new OutgoingCardValueCtx(state, ctx, out, actor, cardId, ci, def, source);
            cur = ctx.cardModifierEffect(it.modifierId()).onOutgoingDamage(rt, hookCtx, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(0, cur);
    }

    public static int onOutgoingHeal(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef actor,
            CardInstId cardId,
            CardInstance ci,
            CardDefinition def,
            TargetRef target,
            int amount,
            String source
    ) {
        int cur = amount;
        for (HookEntry it : collectEntries(ctx, ci)) {
            if (!ctx.hasCardModifierEffect(it.modifierId())) continue;
            CardModifierRuntime rt = new CardModifierRuntime(state, ctx, out, source, it.value());
            OutgoingCardValueCtx hookCtx = new OutgoingCardValueCtx(state, ctx, out, actor, cardId, ci, def, source);
            cur = ctx.cardModifierEffect(it.modifierId()).onOutgoingHeal(rt, hookCtx, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(0, cur);
    }
}
