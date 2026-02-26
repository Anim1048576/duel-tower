package com.example.dueltower.engine.model;

import java.util.*;

public final class CombatState {
    private int round = 1;
    private int currentTurnIndex = 0;
    private final List<TargetRef> turnOrder = new ArrayList<>();
    private final Map<FactionId, Map<String,Integer>> factionStatusValues = new EnumMap<>(FactionId.class);

    // 전투 시작 시 굴린 이니셔티브 (actorKey -> 1..100)
    private final Map<String, Integer> initiatives = new LinkedHashMap<>();

    // 플레이어끼리 이니셔티브가 동률인 그룹(협의 필요 표시용)
    private final List<List<String>> initiativeTieGroups = new ArrayList<>();

    public CombatState() {
        factionStatusValues.put(FactionId.PLAYERS, new LinkedHashMap<>());
        factionStatusValues.put(FactionId.ENEMIES, new LinkedHashMap<>());
    }

    public int round() { return round; }
    public void round(int r) { this.round = r; }

    public int currentTurnIndex() { return currentTurnIndex; }
    public void currentTurnIndex(int idx) { this.currentTurnIndex = idx; }

    public List<TargetRef> turnOrder() { return turnOrder; }

    public TargetRef currentTurnActor() { return turnOrder.get(currentTurnIndex); }

    @Deprecated
    public Ids.PlayerId currentTurnPlayer() {
        return TargetRef.requirePlayer(currentTurnActor());
    }

    public Map<String,Integer> factionStatusValues(FactionId faction) {
        return factionStatusValues.get(faction);
    }

    public Map<String, Integer> initiatives() { return initiatives; }
    public List<List<String>> initiativeTieGroups() { return initiativeTieGroups; }

    public static FactionId factionOf(TargetRef ref) {
        if (ref instanceof TargetRef.Player) return FactionId.PLAYERS;
        if (ref instanceof TargetRef.Enemy)  return FactionId.ENEMIES;
        throw new IllegalArgumentException("unknown TargetRef: " + ref);
    }

    public static String actorKey(TargetRef ref) {
        if (ref instanceof TargetRef.Player p) return "P:" + p.id().value();
        if (ref instanceof TargetRef.Enemy e)  return "E:" + e.id().value();
        throw new IllegalArgumentException("unknown TargetRef: " + ref);
    }

    public enum FactionId { PLAYERS, ENEMIES }
}