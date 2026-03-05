package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SummonState {
    private final SummonInstId id;
    private final PlayerId owner;
    private final CardInstId sourceCardId;

    private int hp;
    private int maxHp;
    private int atk;
    private int heal;
    private int actionCost;
    private boolean actionUsedThisTurn;
    private final Map<String, Integer> statusValues = new LinkedHashMap<>();

    public SummonState(
            SummonInstId id,
            PlayerId owner,
            CardInstId sourceCardId,
            int hp,
            int maxHp,
            int atk,
            int heal,
            int actionCost,
            boolean actionUsedThisTurn
    ) {
        this.id = id;
        this.owner = owner;
        this.sourceCardId = sourceCardId;
        this.maxHp = Math.max(1, maxHp);
        this.hp = clamp(hp, 0, this.maxHp);
        this.atk = Math.max(0, atk);
        this.heal = Math.max(0, heal);
        this.actionCost = Math.max(0, actionCost);
        this.actionUsedThisTurn = actionUsedThisTurn;
    }

    public SummonInstId id() { return id; }
    public PlayerId owner() { return owner; }
    public CardInstId sourceCardId() { return sourceCardId; }

    public int hp() { return hp; }
    public void hp(int v) { this.hp = clamp(v, 0, maxHp); }

    public int maxHp() { return maxHp; }
    public void maxHp(int v) {
        this.maxHp = Math.max(1, v);
        this.hp = clamp(this.hp, 0, this.maxHp);
    }

    public int atk() { return atk; }
    public void atk(int v) { this.atk = Math.max(0, v); }

    public int heal() { return heal; }
    public void heal(int v) { this.heal = Math.max(0, v); }

    public int actionCost() { return actionCost; }
    public void actionCost(int v) { this.actionCost = Math.max(0, v); }

    public boolean actionUsedThisTurn() { return actionUsedThisTurn; }
    public void actionUsedThisTurn(boolean v) { this.actionUsedThisTurn = v; }

    public Map<String, Integer> statusValues() { return statusValues; }

    public void statusSet(String key, int value) {
        if (key == null || key.isBlank()) return;
        if (value <= 0) statusValues.remove(key);
        else statusValues.put(key, value);
    }

    public void statusAdd(String key, int delta) {
        if (delta == 0 || key == null || key.isBlank()) return;
        int next = statusValues.getOrDefault(key, 0) + delta;
        if (next <= 0) statusValues.remove(key);
        else statusValues.put(key, next);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
