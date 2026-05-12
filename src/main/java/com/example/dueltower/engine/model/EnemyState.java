package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class EnemyState {
    private final EnemyId enemyId;
    private String enemyDefId;
    private String name;
    private int maxHp;
    private int hp;
    private int maxAp;
    private int ap;
    private int attackPower;
    private int healPower;
    private CardInstId exCard;
    private int exCooldownUntilRound;
    private boolean exActivatable;
    private boolean usedExThisTurn;
    private final Map<String, Integer> statusValues = new LinkedHashMap<>();
    private final List<String> passiveIds = new ArrayList<>();

    public EnemyState(EnemyId enemyId, int maxHp) {
        this.enemyId = enemyId;
        this.maxHp = Math.max(1, maxHp);
        this.hp = this.maxHp;
        this.maxAp = 0;
        this.ap = 0;
        this.attackPower = 0;
        this.healPower = 0;
        this.exActivatable = false;
        this.usedExThisTurn = false;
    }

    public EnemyId enemyId() { return enemyId; }

    public String enemyDefId() { return enemyDefId; }
    public void enemyDefId(String enemyDefId) {
        this.enemyDefId = enemyDefId == null ? null : enemyDefId.trim();
    }

    public String name() {
        return name == null || name.isBlank() ? enemyId.value() : name;
    }
    public void name(String name) {
        this.name = name == null ? null : name.trim();
    }

    public int maxHp() { return maxHp; }
    public void maxHp(int v) { this.maxHp = Math.max(1, v); this.hp = clamp(this.hp, 0, this.maxHp); }

    public int hp() { return hp; }
    public void hp(int v) { this.hp = clamp(v, 0, maxHp); }

    public int maxAp() { return maxAp; }
    public void maxAp(int value) {
        this.maxAp = Math.max(0, value);
        if (ap > maxAp) {
            ap = maxAp;
        }
    }

    public int ap() { return ap; }
    public void ap(int v) { this.ap = Math.max(0, v); }

    public int attackPower() { return attackPower; }
    public void attackPower(int v) { this.attackPower = Math.max(0, v); }

    public int healPower() { return healPower; }
    public void healPower(int v) { this.healPower = Math.max(0, v); }

    public CardInstId exCard() { return exCard; }
    public void exCard(CardInstId id) { this.exCard = id; }

    public int exCooldownUntilRound() { return exCooldownUntilRound; }
    public void exCooldownUntilRound(int v) { this.exCooldownUntilRound = Math.max(0, v); }

    public boolean exActivatable() { return exActivatable; }
    public void exActivatable(boolean v) { this.exActivatable = v; }

    public boolean usedExThisTurn() { return usedExThisTurn; }
    public void usedExThisTurn(boolean v) { this.usedExThisTurn = v; }

    public boolean exOnCooldown(int currentRound) {
        return exCooldownUntilRound > 0 && currentRound <= exCooldownUntilRound;
    }

    public Map<String, Integer> statusValues() { return statusValues; }

    public List<String> passiveIds() { return Collections.unmodifiableList(passiveIds); }
    public void passiveIds(Collection<String> value) {
        passiveIds.clear();
        if (value == null) {
            return;
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : value) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("enemy passiveIds contains blank id");
            }
            normalized.add(raw.trim());
        }
        passiveIds.addAll(normalized);
    }

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
