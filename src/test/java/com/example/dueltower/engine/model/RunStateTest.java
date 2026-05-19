package com.example.dueltower.engine.model;

import com.example.dueltower.engine.config.RunConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        run.initialize(1234L);
        RunState.NodeChoice event = run.availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.EVENT)
                .findFirst()
                .orElseThrow();

        run.beginNode(event);

        assertTrue(run.shopState().open());
        assertEquals("I-1", ((ItemRef) run.findShopOffer("O-1").ref()).itemId());
        assertEquals("I-7", ((ItemRef) run.findShopOffer("O-7").ref()).itemId());
        assertEquals("E-1", ((EquipRef) run.findShopOffer("O-8").ref()).equipId());
        assertEquals("E-2", ((EquipRef) run.findShopOffer("O-9").ref()).equipId());
        assertEquals("I-8", ((ItemRef) run.findShopOffer("O-10").ref()).itemId());
        assertEquals(5, run.findShopOffer("O-1").stockRemaining());
    }

    @Test
    void selectNonCombatNodeAddsRecentResultButDoesNotAdvanceFloorWithoutBossClear() {
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

        assertEquals(1, run.floor());
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
        assertEquals(1, run.floor());
    }

    @Test
    void bossClearAllowsFloorAdvanceAndResetsClearStateOnNextFloor() {
        RunState run = new RunState();
        run.initialize(100L);
        RunState.NodeChoice choice = run.availableChoices().get(0);
        run.beginNode(choice);
        run.resolveCurrentNode("combat", "보스 전투 결과", "보스 전투 승리", "테스트", 0, 0, 0);

        assertFalse(run.currentFloorCleared());
        assertFalse(run.canAdvanceToNextFloor());

        assertTrue(run.markCurrentFloorClearedByBoss());
        assertTrue(run.currentFloorCleared());
        assertTrue(run.currentFloorSafeZone());
        assertTrue(run.canAdvanceToNextFloor());

        run.completeResultAndPrepareNext(100L);

        assertEquals(2, run.floor());
        assertFalse(run.currentFloorCleared());
        assertFalse(run.canAdvanceToNextFloor());
    }

    @Test
    void requiredKeyRuleUsesNodeDefinitionInsteadOfSpecificNodeId() {
        RunConfig config = new RunConfig(
                0,
                0,
                0,
                List.of(),
                List.of(
                        new RunConfig.RunNodeDefinition("CUSTOM-1", "맞춤 전투", "전투", "열쇠 필요", RunState.NodePhase.COMBAT, RunState.Danger.MID, true, "열쇠 필요"),
                        new RunConfig.RunNodeDefinition("CUSTOM-2", "맞춤 판정", "판정", "일반 판정", RunState.NodePhase.JUDGEMENT, RunState.Danger.LOW, false, null),
                        new RunConfig.RunNodeDefinition("CUSTOM-3", "맞춤 이벤트", "이벤트", "일반 이벤트", RunState.NodePhase.EVENT, RunState.Danger.LOW, false, null)
                ),
                List.of(new RunState.ShopOffer("TEST-OFFER", new ItemRef("I-1"), 10, 1, false))
        );
        RunState run = new RunState(config);

        run.initialize(7L);

        RunState.NodeChoice customCombat = run.availableChoices().stream()
                .filter(choice -> "CUSTOM-1".equals(choice.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(customCombat.disabled());
        assertEquals("열쇠 필요", customCombat.disabledReason());
    }
}
