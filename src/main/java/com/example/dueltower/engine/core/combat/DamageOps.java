package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.*;

public final class DamageOps {
    private DamageOps() {}

    public static void apply(GameState state, EngineContext ctx, List<GameEvent> out, String source, TargetRef target, int amount) {
        apply(state, ctx, out, null, source, target, amount, DamageFlags.NONE);
    }

    public static void apply(GameState state, EngineContext ctx, List<GameEvent> out, TargetRef sourceRef, String source, TargetRef target, int amount) {
        apply(state, ctx, out, sourceRef, source, target, amount, DamageFlags.NONE);
    }

    public static void apply(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef sourceRef,
            String source,
            TargetRef target,
            int amount,
            DamageFlags flags
    ) {
        if (amount <= 0) return;

        DamageFlags f = (flags == null) ? DamageFlags.NONE : flags;

        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);
        int remaining = amount;

        // 0) '주는 피해' 변형(공격자 상태)
        if (sourceRef != null) {
            remaining = applyOutgoing(state, ctx, rt, sourceRef, target, remaining);
        }
        if (remaining <= 0) return;

        // 1) '받는 피해' 변형(대상 상태 + 대상 진영 상태)
        remaining = applyIncoming(state, ctx, rt, sourceRef, target, remaining, f);
        if (remaining <= 0) return;

        // 2) HP 적용
        CombatEntityOps.adjustHp(state, ctx, out, target, -remaining);
        out.add(new GameEvent.LogAppended(
                source + " deals " + remaining + " to " + CombatEntityOps.targetLabel(target)
                        + " (hp=" + CombatEntityOps.hpText(state, target) + ")"
        ));
    }

    private record HookEntry(StatusOwnerRef owner, String statusId, int priority) {}

    private static int applyOutgoing(GameState state, EngineContext ctx, StatusRuntime rt, TargetRef source, TargetRef target, int amount) {
        int cur = amount;
        List<HookEntry> entries = new ArrayList<>();

        var ownerChar = StatusOwnerRef.of(source);
        for (String k : rt.statusMap(ownerChar).keySet()) {
            entries.add(new HookEntry(ownerChar, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
        }

        CombatState cs = state.combat();
        if (cs != null) {
            var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(source));
            for (String k : rt.statusMap(ownerFaction).keySet()) {
                entries.add(new HookEntry(ownerFaction, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
            }
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority));

        for (HookEntry it : entries) {
            if (cur <= 0) { cur = 0; break; }
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            cur = ctx.statusEffect(k).onOutgoingDamage(rt, it.owner(), source, target, cur);
        }
        return Math.max(cur, 0);
    }

    private static int applyIncoming(
            GameState state,
            EngineContext ctx,
            StatusRuntime rt,
            TargetRef sourceRef,
            TargetRef target,
            int amount,
            DamageFlags flags
    ) {
        int cur = amount;
        List<HookEntry> entries = new ArrayList<>();

        var ownerChar = StatusOwnerRef.of(target);
        for (String k : rt.statusMap(ownerChar).keySet()) {
            entries.add(new HookEntry(ownerChar, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
        }

        CombatState cs = state.combat();
        if (cs != null) {
            var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(target));
            for (String k : rt.statusMap(ownerFaction).keySet()) {
                entries.add(new HookEntry(ownerFaction, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
            }
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority));

        for (HookEntry it : entries) {
            if (cur <= 0) { cur = 0; break; }
            String k = it.statusId();

            // Keyword-driven ignores (필중/관통)
            if (flags != null && ctx.hasStatusDef(k)) {
                StatusDefinition def = ctx.statusDef(k);
                if (flags.ignoreEvasion() && def.hasTag(StatusTag.EVASION)) continue;
                if (flags.ignoreShield() && def.hasTag(StatusTag.SHIELD)) continue;
                if (flags.ignoreBarrier() && def.hasTag(StatusTag.BARRIER)) continue;
            }

            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            cur = ctx.statusEffect(k).onIncomingDamage(rt, it.owner(), sourceRef, target, cur);
        }
        return Math.max(cur, 0);
    }
}
