package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.EnemyId;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EnemyState {
    private final EnemyId enemyId;
    private int maxHp;
    private int hp;
    private final Map<String, Integer> statusValues = new LinkedHashMap<>();

    public EnemyState(EnemyId enemyId, int maxHp) {
        this.enemyId = enemyId;
        this.maxHp = Math.max(1, maxHp);
        this.hp = this.maxHp;
    }

    public EnemyId enemyId() { return enemyId; }

    public int maxHp() { return maxHp; }
    public void maxHp(int v) { this.maxHp = Math.max(1, v); this.hp = clamp(this.hp, 0, this.maxHp); }

    public int hp() { return hp; }
    public void hp(int v) { this.hp = clamp(v, 0, maxHp); }

    public Map<String, Integer> statusValues() { return statusValues; }

    public int status(String key) { return statusValues.getOrDefault(key, 0); }
    public void statusSet(String key, int value) {
        if (value == 0) statusValues.remove(key);
        else statusValues.put(key, value);
    }
    public void statusAdd(String key, int delta) { statusSet(key, status(key) + delta); }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}