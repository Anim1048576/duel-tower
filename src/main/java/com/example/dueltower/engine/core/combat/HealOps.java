package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.StatusOwnerRef;
import com.example.dueltower.engine.model.TargetRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HealOps {
    private HealOps() {}

    private record HookEntry(StatusOwnerRef owner, String statusId, int priority) {}

    public static void apply(GameState state, EngineContext ctx, List<GameEvent> out, String source, TargetRef target, int amount) {
        apply(state, ctx, out, null, source, target, amount);
    }

    public static void apply(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TargetRef sourceRef,
            String source,
            TargetRef target,
            int amount
    ) {
        if (amount <= 0) return;

        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);
        int remaining = amount;

        // 0) '주는 회복' 변형 순서: passive -> status
        if (sourceRef != null) {
            remaining = PassiveOps.onOutgoingHeal(state, ctx, out, sourceRef, target, remaining, source);
            remaining = applyOutgoing(state, ctx, rt, sourceRef, target, remaining);
        }
        if (remaining <= 0) return;

        // 1) '받는 회복' 변형 순서: passive -> status(대상 상태 + 대상 진영 상태)
        remaining = PassiveOps.onIncomingHeal(state, ctx, out, sourceRef, target, remaining, source);
        remaining = applyIncoming(state, ctx, rt, sourceRef, target, remaining);
        if (remaining <= 0) return;

        // 2) HP 적용
        CombatEntityOps.adjustHp(state, ctx, out, target, remaining);
        out.add(new GameEvent.LogAppended(
                source + " heals " + remaining + " to " + CombatEntityOps.targetLabel(target)
                        + " (hp=" + CombatEntityOps.hpText(state, target) + ")"
        ));
    }

    private static int applyOutgoing(GameState state, EngineContext ctx, StatusRuntime rt, TargetRef source, TargetRef target, int amount) {
        int cur = amount;
        List<HookEntry> entries = collectStatusEntries(state, ctx, rt, source);
        for (HookEntry it : entries) {
            if (cur <= 0) return 0;
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            cur = ctx.statusEffect(k).onOutgoingHeal(rt, it.owner(), source, target, cur);
        }
        return Math.max(cur, 0);
    }

    private static int applyIncoming(GameState state, EngineContext ctx, StatusRuntime rt, TargetRef sourceRef, TargetRef target, int amount) {
        int cur = amount;
        List<HookEntry> entries = collectStatusEntries(state, ctx, rt, target);
        for (HookEntry it : entries) {
            if (cur <= 0) return 0;
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            cur = ctx.statusEffect(k).onIncomingHeal(rt, it.owner(), sourceRef, target, cur);
        }
        return Math.max(cur, 0);
    }

    private static List<HookEntry> collectStatusEntries(GameState state, EngineContext ctx, StatusRuntime rt, TargetRef owner) {
        List<HookEntry> entries = new ArrayList<>();
        var ownerChar = StatusOwnerRef.of(owner);
        for (String k : rt.statusMap(ownerChar).keySet()) {
            entries.add(new HookEntry(ownerChar, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
        }

        CombatState cs = state.combat();
        if (cs != null) {
            var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(owner));
            for (String k : rt.statusMap(ownerFaction).keySet()) {
                entries.add(new HookEntry(ownerFaction, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
            }
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority));
        return entries;
    }
}
