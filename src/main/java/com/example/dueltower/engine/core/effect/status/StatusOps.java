package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        StatusRuntime rt = new StatusRuntime(state, ctx, new ArrayList<>(), "VALIDATE");

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
        StatusRuntime rt = new StatusRuntime(state, ctx, new ArrayList<>(), "VALIDATE");

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
        if (!isEnemyOneTarget(chosenEnemy)) return;

        StatusRuntime rt = new StatusRuntime(state, ctx, new ArrayList<>(), "VALIDATE");

        // CONFUSION-tagged statuses ignore TAUNT (rule: 혼란은 도발 무시)
        if (hasActorOrFactionTag(rt, state, ctx, actor, StatusTag.CONFUSION)) {
            return;
        }

        // Keyword may ignore TAUNT (rule: 명경은 도발 무시)
        if (KeywordOps.ignoresTaunt(state, ctx, actor, cardId, chosenEnemy)) {
            return;
        }

        List<TargetRef> enemyCandidates = enemyOneCandidates(state, ctx, actor, chosenEnemy);

        // Run global targeting constraints for statuses present on opponents that have TAUNT tag.
        for (String statusId : statusIdsWithTagOnTargets(rt, ctx, enemyCandidates, StatusTag.TAUNT)) {
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
        if (!isEnemyOneTarget(chosenEnemy)) return chosenEnemy;

        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        // Candidate pool for actor-side "may hit allies" effects (CONFUSION etc.)
        List<TargetRef> allCandidates = new ArrayList<>();
        state.players().keySet().forEach(pid -> allCandidates.add(TargetRef.ofPlayer(pid)));
        state.enemies().keySet().forEach(eid -> allCandidates.add(TargetRef.ofEnemy(eid)));
        allCandidates.addAll(allSummonTargets(state, ctx));

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
        if (!isEnemyOneTarget(cur)) return cur;

        // CONFUSION-tagged statuses ignore TAUNT even if they didn't reroute this time.
        if (hasActorOrFactionTag(rt, state, ctx, actor, StatusTag.CONFUSION)) {
            return cur;
        }

        // Keyword may ignore TAUNT (rule: 명경은 도발 무시)
        if (KeywordOps.ignoresTaunt(state, ctx, actor, cardId, cur)) {
            return cur;
        }

        // 2) global constraints (TAUNT etc.) - give them only the opponent candidates
        List<TargetRef> enemyCandidates = enemyOneCandidates(state, ctx, actor, cur);

        for (String statusId : statusIdsWithTagOnTargets(rt, ctx, enemyCandidates, StatusTag.TAUNT)) {
            if (!ctx.hasStatusEffect(statusId)) continue;
            TargetRef next = ctx.statusEffect(statusId).onResolveEnemyOneTarget(rt, actor, cardId, cur, enemyCandidates);
            if (next != null) cur = next;
        }

        return cur;
    }

    private static boolean hasActorOrFactionTag(
            StatusRuntime rt,
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            StatusTag tag
    ) {
        for (HookEntry it : collectActorAndFactionEntries(rt, state, ctx, actor)) {
            String id = it.statusId();
            if (!ctx.hasStatusDef(id)) continue;
            if (!ctx.statusDef(id).hasTag(tag)) continue;
            if (rt.stacks(it.owner(), id) > 0) return true;
        }
        return false;
    }

    private static List<String> statusIdsWithTagOnTargets(
            StatusRuntime rt,
            EngineContext ctx,
            List<TargetRef> targets,
            StatusTag tag
    ) {
        Set<String> ids = new HashSet<>();
        if (targets != null) {
            for (TargetRef t : targets) {
                StatusOwnerRef owner = StatusOwnerRef.of(t);
                for (String statusId : rt.statusMap(owner).keySet()) {
                    if (!ctx.hasStatusDef(statusId)) continue;
                    if (!ctx.statusDef(statusId).hasTag(tag)) continue;
                    if (rt.stacks(owner, statusId) <= 0) continue;
                    ids.add(statusId);
                }
            }
        }

        List<String> r = new ArrayList<>(ids);
        r.sort(Comparator
                .comparingInt((String id) -> ctx.hasStatusDef(id) ? ctx.statusDef(id).priority() : Integer.MAX_VALUE)
                .thenComparing(String::toString));
        return r;
    }

    private static boolean isEnemyOneTarget(TargetRef ref) {
        return (ref instanceof TargetRef.Enemy) || (ref instanceof TargetRef.Summon);
    }

    private static List<TargetRef> enemyOneCandidates(GameState state, EngineContext ctx, TargetRef actor, TargetRef chosenEnemy) {
        List<TargetRef> candidates = new ArrayList<>();
        if (actor instanceof TargetRef.Player p) {
            state.enemies().keySet().forEach(eid -> candidates.add(TargetRef.ofEnemy(eid)));
            for (TargetRef summon : allSummonTargets(state, ctx)) {
                if (summon instanceof TargetRef.Summon s && !s.ownerId().equals(p.id())) candidates.add(summon);
            }
        } else if (actor instanceof TargetRef.Enemy) {
            state.players().keySet().forEach(pid -> candidates.add(TargetRef.ofPlayer(pid)));
            candidates.addAll(allSummonTargets(state, ctx));
        }

        if (chosenEnemy != null && candidates.stream().noneMatch(chosenEnemy::equals)) {
            candidates.add(chosenEnemy);
        }
        return candidates;
    }

    private static List<TargetRef> allSummonTargets(GameState state, EngineContext ctx) {
        List<TargetRef> summons = new ArrayList<>();
        for (CardInstance ci : state.cardInstances().values()) {
            if (ci.zone() != Zone.FIELD) continue;
            if (!KeywordOps.hasKeyword(state, ctx, ci.instanceId(), "소환")) continue;
            summons.add(TargetRef.ofSummon(ci.ownerId(), new Ids.SummonInstId(ci.instanceId().value())));
        }
        return summons;
    }

}
