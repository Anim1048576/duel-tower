package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Card/command level status hooks (cost, validations, after-use hooks).
 * Keeps the orchestration in one place while letting each StatusEffect own its logic.
 */
public final class StatusOps {
    private StatusOps() {}

    private record HookEntry(StatusOwnerRef owner, String statusId, int priority) {}

    private static List<HookEntry> collectActorAndFactionEntries(StatusRuntime rt, GameState state, EngineContext ctx, TargetRef actor) {
        List<HookEntry> entries = new ArrayList<>();

        var ownerChar = StatusOwnerRef.of(actor);
        for (String k : rt.statusMap(ownerChar).keySet()) {
            entries.add(new HookEntry(ownerChar, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
        }

        CombatState cs = state.combat();
        if (cs != null) {
            var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(actor));
            for (String k : rt.statusMap(ownerFaction).keySet()) {
                entries.add(new HookEntry(ownerFaction, k, ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));
            }
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority));
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
        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);
        int cur = baseCost;

        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            cur = ctx.statusEffect(k).onCost(rt, actor, ci, def, cur);
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
        StatusRuntime rt = new StatusRuntime(state, ctx, List.of(), "VALIDATE");

        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            ctx.statusEffect(k).validatePlayCard(rt, actor, ci, def, errors);
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
        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            ctx.statusEffect(k).onAfterPlayCard(rt, actor, ci, def);
        }
    }

    public static void validateUseEx(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstance ci,
            CardDefinition def,
            List<String> errors
    ) {
        StatusRuntime rt = new StatusRuntime(state, ctx, List.of(), "VALIDATE");

        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            ctx.statusEffect(k).validateUseEx(rt, actor, ci, def, errors);
        }
    }

    public static void afterUseEx(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            CardInstance ci,
            CardDefinition def,
            List<GameEvent> out,
            String source
    ) {
        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;
            ctx.statusEffect(k).onAfterUseEx(rt, actor, ci, def);
        }
    }

    /**
     * Validate 'ENEMY_ONE' target choice against global rules such as TAUNT.
     * This is called from EffectOps.validateTarget so individual card effects don't need to care.
     */
    public static void validateEnemyOneTarget(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy,
            List<String> errors
    ) {
        if (!(chosenEnemy instanceof TargetRef.Enemy)) return;

        StatusRuntime rt = new StatusRuntime(state, ctx, List.of(), "VALIDATE");

        // CONFUSION ignores TAUNT (rule: 혼란은 도발 무시)
        if (ctx.hasStatusEffect("CONFUSION") && rt.stacks(actor, "CONFUSION") > 0) {
            return;
        }

        List<TargetRef> enemyCandidates;
        if (actor instanceof TargetRef.Player) {
            enemyCandidates = state.enemies().keySet().stream().map(TargetRef::ofEnemy).toList();
        } else if (actor instanceof TargetRef.Enemy) {
            enemyCandidates = state.players().keySet().stream().map(TargetRef::ofPlayer).toList();
        } else {
            enemyCandidates = List.of();
        }

        // Only call hooks for status effects that opt into this rule.
        for (String statusId : ctxStatusIds(ctx)) {
            if (!ctx.hasStatusEffect(statusId)) continue;
            ctx.statusEffect(statusId).validateEnemyOneTarget(rt, actor, cardId, chosenEnemy, enemyCandidates, errors);
        }
    }

    /**
     * Resolve the final target for an ENEMY_ONE intent, applying actor-side overrides first (e.g., CONFUSION),
     * then global constraints (e.g., TAUNT).
     */
    public static TargetRef resolveEnemyOneTarget(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy,
            List<GameEvent> out,
            String source
    ) {
        if (!(chosenEnemy instanceof TargetRef.Enemy)) return chosenEnemy;

        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        // Candidate pool for actor-side "may hit allies" effects (CONFUSION etc.)
        List<TargetRef> allCandidates = new ArrayList<>();
        state.players().keySet().forEach(pid -> allCandidates.add(TargetRef.ofPlayer(pid)));
        state.enemies().keySet().forEach(eid -> allCandidates.add(TargetRef.ofEnemy(eid)));

        TargetRef cur = chosenEnemy;

        // 1) actor-side overrides (scan only actor/faction statuses)
        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String k = it.statusId();
            if (!ctx.hasStatusEffect(k)) continue;
            int stacks = rt.stacks(it.owner(), k);
            if (stacks <= 0) continue;

            TargetRef next = ctx.statusEffect(k).onResolveEnemyOneTarget(rt, actor, cardId, cur, allCandidates);
            if (next != null) cur = next;
        }

        // If actor-side override already diverted to non-enemy (e.g., CONFUSION), do not apply taunt.
        if (!(cur instanceof TargetRef.Enemy)) return cur;

        // 2) global constraints (TAUNT etc.) - give them only the opponent candidates
        List<TargetRef> enemyCandidates;
        if (actor instanceof TargetRef.Player) {
            enemyCandidates = state.enemies().keySet().stream().map(TargetRef::ofEnemy).toList();
        } else if (actor instanceof TargetRef.Enemy) {
            enemyCandidates = state.players().keySet().stream().map(TargetRef::ofPlayer).toList();
        } else {
            enemyCandidates = List.of();
        }

        for (String statusId : ctxStatusIds(ctx)) {
            if (!ctx.hasStatusEffect(statusId)) continue;
            TargetRef next = ctx.statusEffect(statusId).onResolveEnemyOneTarget(rt, actor, cardId, cur, enemyCandidates);
            if (next != null) cur = next;
        }

        return cur;
    }

    /**
     * We currently don't have a registry of "targeting rule status IDs".
     * Keep this list centralized so we don't sprinkle string literals across the engine.
     */
    private static List<String> ctxStatusIds(EngineContext ctx) {
        // If not registered, hasStatusEffect will filter it out.
        return List.of("TAUNT");
    }
}
