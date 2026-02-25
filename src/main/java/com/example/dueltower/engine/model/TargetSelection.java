package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;

public record TargetSelection(List<TargetRef> targets) {
    public static TargetSelection empty() { return new TargetSelection(List.of()); }

    public TargetRef requireOne() {
        if (targets == null || targets.size() != 1) throw new IllegalArgumentException("exactly one target required");
        return targets.get(0);
    }

    public PlayerId requireOnePlayer() { return TargetRef.requirePlayer(requireOne()); }
    public EnemyId requireOneEnemy() { return TargetRef.requireEnemy(requireOne()); }

    public List<PlayerId> allPlayersOnly() {
        List<PlayerId> r = new ArrayList<>();
        if (targets == null) return r;
        for (TargetRef t : targets) if (t instanceof TargetRef.Player p) r.add(p.id());
        return r;
    }

    public List<EnemyId> allEnemiesOnly() {
        List<EnemyId> r = new ArrayList<>();
        if (targets == null) return r;
        for (TargetRef t : targets) if (t instanceof TargetRef.Enemy e) r.add(e.id());
        return r;
    }
}