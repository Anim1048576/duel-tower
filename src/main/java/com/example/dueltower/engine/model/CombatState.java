package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;

public final class CombatState {
    private int round = 1;
    private int currentTurnIndex = 0;
    private final List<PlayerId> turnOrder = new ArrayList<>();

    public int round() { return round; }
    public void round(int r) { this.round = r; }

    public int currentTurnIndex() { return currentTurnIndex; }
    public void currentTurnIndex(int idx) { this.currentTurnIndex = idx; }

    public List<PlayerId> turnOrder() { return turnOrder; }

    public PlayerId currentTurnPlayer() {
        return turnOrder.get(currentTurnIndex);
    }
}