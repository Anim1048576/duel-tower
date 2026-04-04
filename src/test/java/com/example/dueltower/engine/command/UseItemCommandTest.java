package com.example.dueltower.engine.command;

import com.example.dueltower.content.item.idb.I001_SmallPotion;
import com.example.dueltower.content.item.idb.I002_Antidote;
import com.example.dueltower.content.item.idb.I004_EmergencySmokeBomb;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UseItemCommandTest {

    @Test
    void useItemConsumesInventoryAndAppliesHealToSelfByDefault() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(5);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        int beforeCount = findItem(state, "I-1").count();

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-1",
                2,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(beforeCount - 2, findItem(state, "I-1").count());
        assertTrue(player.hp() > 5);
    }

    @Test
    void useItemRejectsWhenAntidoteHasNoTarget() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 124L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.statusSet("S101", 1);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-2",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("player target required"));
    }

    @Test
    void useItemAntidoteRemovesDebuffWhenTargetProvided() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 125L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.statusSet("S101", 1);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-2",
                1,
                new TargetSelection(List.of(TargetRef.ofPlayer(playerId)))
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(0, player.status("S101"));
    }

    @Test
    void useItemSmokeBombAppliesEvasionStatus() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 126L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        int before = player.status("S004");

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-4",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(before + 2, player.status("S004"));
    }

    @Test
    void useItemRejectsWhenBattleUsableIsFalse() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 127L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-3",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("item is not battle usable"));
    }


    @Test
    void useItemRejectsWhenInventoryItemIsUnknown() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 129L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-999",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("item not found"));
    }

    @Test
    void useItemRejectsWhenAnotherNonBattleUsableItemIsUsed() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 130L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-5",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("item is not battle usable"));
    }

    @Test
    void useItemRejectsWhenBattleUsableItemEffectIsMissing() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 128L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-1",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, itemCtxMissingI1Effect(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("item effect not found: I-1"));
    }

    private static void seedCombatMainTurn(GameState state, Ids.PlayerId playerId) {
        CombatState combat = new CombatState();
        combat.phase(CombatPhase.MAIN);
        combat.turnOrder().add(TargetRef.ofPlayer(playerId));
        combat.currentTurnIndex(0);
        state.combat(combat);
        state.enemies().put(new Ids.EnemyId("e1"), new EnemyState(new Ids.EnemyId("e1"), 10));
        state.nodeState(NodeState.COMBAT);
    }

    private static RunState.InventoryItem findItem(GameState state, String itemId) {
        return state.runState().inventory().items().stream()
                .filter(item -> item.itemId().equals(itemId))
                .findFirst()
                .orElseThrow();
    }

    private static EngineContext defaultItemCtx() {
        I001_SmallPotion i1 = new I001_SmallPotion();
        I002_Antidote i2 = new I002_Antidote();
        I004_EmergencySmokeBomb i4 = new I004_EmergencySmokeBomb();

        return new EngineContext(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "I-1", i1.definition(),
                        "I-2", i2.definition(),
                        "I-3", new ItemDefinition("I-3", "단단한 가죽끈", false, "제작 재료", "장비 제작에 사용되는 기본 재료입니다.", List.of("재료")),
                        "I-4", i4.definition(),
                        "I-5", new ItemDefinition("I-5", "강화석 파편", false, "강화 재료", "장비 강화 수치에 따라 다량으로 요구됩니다.", List.of("재료"))
                ),
                Map.of(
                        "I-1", i1,
                        "I-2", i2,
                        "I-4", i4
                )
        );
    }

    private static EngineContext itemCtxMissingI1Effect() {
        I001_SmallPotion i1 = new I001_SmallPotion();
        I002_Antidote i2 = new I002_Antidote();
        I004_EmergencySmokeBomb i4 = new I004_EmergencySmokeBomb();

        return new EngineContext(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "I-1", i1.definition(),
                        "I-2", i2.definition(),
                        "I-3", new ItemDefinition("I-3", "단단한 가죽끈", false, "제작 재료", "장비 제작에 사용되는 기본 재료입니다.", List.of("재료")),
                        "I-4", i4.definition(),
                        "I-5", new ItemDefinition("I-5", "강화석 파편", false, "강화 재료", "장비 강화 수치에 따라 다량으로 요구됩니다.", List.of("재료"))
                ),
                Map.of(
                        "I-2", i2,
                        "I-4", i4
                )
        );
    }
}
