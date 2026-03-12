package com.example.dueltower.engine.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunStateTest {

    @Test
    void initializeSeedsChoicesAndInventory() {
        RunState run = new RunState();

        run.initialize(1234L);

        assertEquals(1, run.floor());
        assertEquals(3, run.availableChoices().size());
        assertNotNull(run.inventory());
        assertEquals(2, run.inventory().keys());
        assertEquals(1, run.inventory().chests());
        assertEquals(12450, run.inventory().gold());
        assertFalse(run.inventory().items().isEmpty());
    }

    @Test
    void selectNonCombatNodeAddsRecentResultAndAdvancesFloor() {
        RunState run = new RunState();
        run.initialize(42L);

        RunState.NodeChoice nonCombat = run.availableChoices().stream()
                .filter(choice -> choice.phase() != RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        int beforeGold = run.inventory().gold();

        run.select(nonCombat, 42L);

        assertNotNull(run.currentNode());
        assertEquals(nonCombat.id(), run.currentNode().id());
        assertEquals(2, run.floor());
        assertFalse(run.recentResults().isEmpty());
        assertTrue(run.inventory().gold() > beforeGold);
    }

    @Test
    void clearRecentResultsRemovesAll() {
        RunState run = new RunState();
        run.initialize(99L);
        RunState.NodeChoice nonCombat = run.availableChoices().stream()
                .filter(choice -> choice.phase() != RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        run.select(nonCombat, 99L);
        assertFalse(run.recentResults().isEmpty());

        run.clearRecentResults();

        assertTrue(run.recentResults().isEmpty());
    }
}
