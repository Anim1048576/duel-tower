package com.example.dueltower.engine.model;

public enum NodeState {
    NON_COMBAT,
    COMBAT,
    CURSE,
    CURSE_NODE;

    public boolean deckEditable() {
        return this == NON_COMBAT;
    }

    public boolean curseBlocked() {
        return this == CURSE || this == CURSE_NODE;
    }
}
