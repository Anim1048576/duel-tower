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
        assertEquals(4, run.inventory().items().size());
        assertTrue(run.inventory().items().stream().anyMatch(item -> item.ref() instanceof ItemRef ref && ref.itemId().equals("I-1") && item.count() == 3 && !item.bound()));
        assertTrue(run.inventory().items().stream().anyMatch(item -> item.ref() instanceof ItemRef ref && ref.itemId().equals("I-2") && item.count() == 1 && !item.bound()));
        assertTrue(run.inventory().items().stream().anyMatch(item -> item.ref() instanceof ItemRef ref && ref.itemId().equals("I-4") && item.count() == 1 && !item.bound()));
        assertTrue(run.inventory().items().stream().anyMatch(item -> item.ref() instanceof ItemRef ref && ref.itemId().equals("I-6") && item.count() == 1 && !item.bound()));
    }

    @Test
    void shopOffersContainConsumablesEquipmentsAndBulletBundle() {
        RunState run = new RunState();

        assertEquals("I-1", ((ItemRef) run.findShopOffer("O-1").ref()).itemId());
        assertEquals("I-7", ((ItemRef) run.findShopOffer("O-7").ref()).itemId());
        assertEquals("E-1", ((EquipRef) run.findShopOffer("O-8").ref()).equipId());
        assertEquals("E-2", ((EquipRef) run.findShopOffer("O-9").ref()).equipId());
        assertEquals("I-8", ((ItemRef) run.findShopOffer("O-10").ref()).itemId());
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

        run.beginNode(nonCombat);
        run.resolveCurrentNode("reward", "보상 획득", nonCombat.name() + " 결과 확인", "테스트 보상", 200, 0, 0);

        assertNotNull(run.currentNode());
        assertEquals(nonCombat.id(), run.currentNode().id());
        assertEquals(1, run.floor());
        assertTrue(run.resultPending());
        assertFalse(run.recentResults().isEmpty());
        assertTrue(run.inventory().gold() > beforeGold);

        run.completeResultAndPrepareNext(42L);

        assertEquals(2, run.floor());
        assertFalse(run.resultPending());
        assertNull(run.currentNode());
    }

    @Test
    void clearRecentResultsRemovesAll() {
        RunState run = new RunState();
        run.initialize(99L);
        RunState.NodeChoice nonCombat = run.availableChoices().stream()
                .filter(choice -> choice.phase() != RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        run.beginNode(nonCombat);
        run.resolveCurrentNode("reward", "탐색 완료", nonCombat.name() + " 결과 확인", "테스트", 120, 0, 0);
        assertFalse(run.recentResults().isEmpty());

        run.completeResultAndPrepareNext(99L);

        assertTrue(run.recentResults().isEmpty());
        assertEquals(2, run.floor());
    }
}
