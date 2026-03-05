package com.example.dueltower.engine.model;

public sealed interface TargetRef permits TargetRef.Player, TargetRef.Enemy, TargetRef.Summon {
    record Player(Ids.PlayerId id) implements TargetRef {}
    record Enemy(Ids.EnemyId id) implements TargetRef {}
    record Summon(Ids.PlayerId ownerId, Ids.SummonInstId summonId) implements TargetRef {}

    static TargetRef ofPlayer(Ids.PlayerId id) { return new Player(id); }
    static TargetRef ofEnemy(Ids.EnemyId id) { return new Enemy(id); }
    static TargetRef ofSummon(Ids.PlayerId ownerId, Ids.SummonInstId summonId) { return new Summon(ownerId, summonId); }

    static Ids.PlayerId requirePlayer(TargetRef ref) {
        if (ref instanceof Player p) return p.id();
        throw new IllegalArgumentException("player target required");
    }

    static Ids.EnemyId requireEnemy(TargetRef ref) {
        if (ref instanceof Enemy e) return e.id();
        throw new IllegalArgumentException("enemy target required");
    }

    static Summon requireSummon(TargetRef ref) {
        if (ref instanceof Summon s) return s;
        throw new IllegalArgumentException("summon target required");
    }
}
