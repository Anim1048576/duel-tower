package com.example.dueltower.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameRulesTest {

    @Test
    void defaultsMatchCurrentBehavior() {
        GameRules rules = GameRules.defaults();

        assertEquals(12, rules.deckSize());
        assertEquals(3, rules.maxDeckCopies());
        assertEquals(2, rules.maxDeckEditChanges());
        assertEquals(2, rules.maxPassives());
        assertEquals(20, rules.maxOwnedCards());
        assertEquals(6, rules.handLimit());
        assertEquals(5, rules.fieldLimit());
        assertEquals(4, rules.combatStartDrawCount());
        assertEquals(1, rules.maxConsumableUsesPerTurn());
        assertEquals(3, rules.maxConsumableUsesPerCombat());
    }

    @Test
    void turnStartDrawCountFollowsThresholdRule() {
        GameRules rules = GameRules.defaults();

        assertEquals(2, rules.turnStartDrawCount(0));
        assertEquals(2, rules.turnStartDrawCount(3));
        assertEquals(1, rules.turnStartDrawCount(4));
        assertEquals(1, rules.turnStartDrawCount(6));
    }
}
