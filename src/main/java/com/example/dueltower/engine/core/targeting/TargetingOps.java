package com.example.dueltower.engine.core.targeting;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.TargetRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TargetingOps {
    private TargetingOps() {}

    public static List<TargetRef> allyFactionCandidates(GameState state, TargetRef actor) {
        return factionCandidates(state, actor, true);
    }

    public static List<TargetRef> enemyFactionCandidates(GameState state, TargetRef actor) {
        return factionCandidates(state, actor, false);
    }

    private static List<TargetRef> factionCandidates(GameState state, TargetRef actor, boolean allies) {
        boolean actorIsPlayerSide = isPlayerSideActor(state, actor);
        boolean includePlayerSide = allies ? actorIsPlayerSide : !actorIsPlayerSide;

        Set<TargetRef> candidates = new LinkedHashSet<>();
        if (includePlayerSide) {
            state.players().keySet().forEach(pid -> candidates.add(TargetRef.ofPlayer(pid)));
        } else {
            state.enemies().keySet().forEach(eid -> candidates.add(TargetRef.ofEnemy(eid)));
        }

        state.summons().values().forEach(summon -> {
            boolean summonIsPlayerSide = state.players().containsKey(summon.owner());
            if (summonIsPlayerSide == includePlayerSide) {
                candidates.add(TargetRef.ofSummon(summon.owner(), summon.id()));
            }
        });

        return new ArrayList<>(candidates);
    }

    private static boolean isPlayerSideActor(GameState state, TargetRef actor) {
        if (actor instanceof TargetRef.Player) return true;
        if (actor instanceof TargetRef.Enemy) return false;
        if (actor instanceof TargetRef.Summon s) {
            return state.players().containsKey(s.ownerId());
        }
        return true;
    }
}
