package com.example.dueltower.engine.core.effect.passive;

import com.example.dueltower.common.util.Rational;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PassiveOps {
    private PassiveOps() {}

    private record HookEntry(String passiveId, int priority) {}

    private static List<HookEntry> collectEntries(GameState state, EngineContext ctx, TargetRef actor) {
        if (!(actor instanceof TargetRef.Player p)) return List.of();

        PlayerState ps = state.player(p.id());
        if (ps == null) return List.of();

        List<HookEntry> entries = new ArrayList<>();
        for (String id : ps.passiveIds()) {
            entries.add(new HookEntry(id, ctx.hasPassiveDef(id) ? ctx.passiveDef(id).priority() : Integer.MAX_VALUE));
        }
        entries.sort(Comparator.comparingInt(HookEntry::priority).thenComparing(HookEntry::passiveId));
        return entries;
    }

    public static int modifiedCost(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstance ci,
            CardDefinition def,
            int baseCost,
            List<GameEvent> out,
            String source
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, source);
        int cur = baseCost;
        for (HookEntry it : collectEntries(state, ctx, actor)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onCost(rt, actor, ci, def, cur);
        }
        return Math.max(0, cur);
    }

    public static void validatePlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstance ci,
            CardDefinition def,
            List<String> errors
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, new ArrayList<>(), "VALIDATE");
        for (HookEntry it : collectEntries(state, ctx, actor)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            ctx.passiveEffect(it.passiveId()).validatePlayCard(rt, actor, ci, def, errors);
        }
    }

    public static void afterPlayCard(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, source);
        for (HookEntry it : collectEntries(state, ctx, actor)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            ctx.passiveEffect(it.passiveId()).onAfterPlayCard(rt, actor, ci, def);
        }
    }

    public static int onOutgoingDamage(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            int amount,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = amount;
        for (HookEntry it : collectEntries(state, ctx, source)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onOutgoingDamage(rt, source, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(cur, 0);
    }

    public static int onIncomingDamage(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            int amount,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = amount;
        for (HookEntry it : collectEntries(state, ctx, target)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onIncomingDamage(rt, source, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(cur, 0);
    }


    public static int onOutgoingHeal(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            int amount,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = amount;
        for (HookEntry it : collectEntries(state, ctx, source)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onOutgoingHeal(rt, source, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(cur, 0);
    }


    public static int criticalChancePercent(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            String kind,
            int baseChance,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = baseChance;
        for (HookEntry it : collectEntries(state, ctx, source)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onCriticalChancePercent(rt, source, target, kind, cur);
        }
        return Math.max(0, Math.min(100, cur));
    }

    public static Rational criticalAmountMultiplier(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            String kind,
            Rational baseMultiplier,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        Rational cur = Rational.max(Rational.ONE, baseMultiplier);
        for (HookEntry it : collectEntries(state, ctx, source)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onCriticalAmountMultiplier(rt, source, target, kind, cur);
        }
        return Rational.max(Rational.ONE, cur);
    }

    public static int incomingCriticalChancePercent(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            String kind,
            int baseChance,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = baseChance;
        for (HookEntry it : collectEntries(state, ctx, target)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onIncomingCriticalChancePercent(rt, source, target, kind, cur);
        }
        return Math.max(0, Math.min(100, cur));
    }

    public static Rational incomingCriticalAmountMultiplier(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            String kind,
            Rational baseMultiplier,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        Rational cur = Rational.max(Rational.ONE, baseMultiplier);
        for (HookEntry it : collectEntries(state, ctx, target)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onIncomingCriticalAmountMultiplier(rt, source, target, kind, cur);
        }
        return Rational.max(Rational.ONE, cur);
    }

    public static int onIncomingHeal(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef source,
            TargetRef target,
            int amount,
            String hookSource
    ) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, hookSource);
        int cur = amount;
        for (HookEntry it : collectEntries(state, ctx, target)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            cur = ctx.passiveEffect(it.passiveId()).onIncomingHeal(rt, source, target, cur);
            if (cur <= 0) return 0;
        }
        return Math.max(cur, 0);
    }

    public static void turnStart(GameState state, EngineContext ctx, TargetRef owner, List<GameEvent> out, String source) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, source);
        for (HookEntry it : collectEntries(state, ctx, owner)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            ctx.passiveEffect(it.passiveId()).onTurnStart(rt, owner);
        }
    }

    public static void turnEnd(GameState state, EngineContext ctx, TargetRef owner, List<GameEvent> out, String source) {
        PassiveRuntime rt = new PassiveRuntime(state, ctx, out, source);
        for (HookEntry it : collectEntries(state, ctx, owner)) {
            if (!ctx.hasPassiveEffect(it.passiveId())) continue;
            ctx.passiveEffect(it.passiveId()).onTurnEnd(rt, owner);
        }
    }
}
