package com.example.dueltower.engine.model;

public enum NodeState {
    NON_COMBAT,
    COMBAT;

    public boolean deckEditable() {
        return this == NON_COMBAT;
    }
}
