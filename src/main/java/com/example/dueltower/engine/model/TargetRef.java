package com.example.dueltower.engine.model;

public sealed interface TargetRef permits TargetRef.Player, TargetRef.Enemy {
    record Player(Ids.PlayerId id) implements TargetRef {}
    record Enemy(Ids.EnemyId id) implements TargetRef {}

    static TargetRef ofPlayer(Ids.PlayerId id) { return new Player(id); }
    static TargetRef ofEnemy(Ids.EnemyId id) { return new Enemy(id); }

    static Ids.PlayerId requirePlayer(TargetRef ref) {
        if (ref instanceof Player p) return p.id();
        throw new IllegalArgumentException("player target required");
    }

    static Ids.EnemyId requireEnemy(TargetRef ref) {
        if (ref instanceof Enemy e) return e.id();
        throw new IllegalArgumentException("enemy target required");
    }
}