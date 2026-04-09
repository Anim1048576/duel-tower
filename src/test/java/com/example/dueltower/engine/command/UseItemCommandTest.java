package com.example.dueltower.engine.command;

import com.example.dueltower.content.item.idb.I001_CHEAP_HEALING_POTION;
import com.example.dueltower.content.item.idb.I002_HEALING_POTION;
import com.example.dueltower.content.item.idb.I003_ADVANCED_HEALING_POTION;
import com.example.dueltower.content.item.idb.I004_CHEAP_BARRIER_GENERATOR;
import com.example.dueltower.content.item.idb.I005_BARRIER_GENERATOR;
import com.example.dueltower.content.item.idb.I006_Antidote;
import com.example.dueltower.content.item.idb.I007_EmergencySmokeBomb;
import com.example.dueltower.content.item.idb.I008_BulletBundle;
import com.example.dueltower.content.status.sdb.S004_Evasion;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.S105_Weak;
import com.example.dueltower.content.status.sdb.S301_Barrier;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UseItemCommandTest {

    @Test
    @DisplayName("아이템 사용은 인벤토리를 소모하고 기본적으로 자신에게 소형 회복을 적용한다")
    void useItemConsumesInventoryAndAppliesCheapHealToSelfByDefault() {
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
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(beforeCount - 1, findItem(state, "I-1").count());
        assertTrue(player.hp() > 5);
        assertEquals(1, player.consumablesUsedThisTurn());
        assertEquals(1, player.consumablesUsedThisCombat());
    }

    @Test
    @DisplayName("아이템 사용은 같은 턴 두 번째 소모품 사용을 거부한다")
    void useItemRejectsSecondConsumableInSameTurn() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1231L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        EngineContext ctx = defaultItemCtx();

        EngineResult first = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-1", 1, TargetSelection.empty())
        );

        assertTrue(first.accepted());
        assertEquals(1, player.consumablesUsedThisTurn());

        EngineResult second = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-4", 1, TargetSelection.empty())
        );

        assertFalse(second.accepted());
        assertTrue(second.errors().contains("consumable use limit reached this turn"));
        assertEquals(1, player.consumablesUsedThisTurn());
    }

    @Test
    @DisplayName("아이템 사용은 전투당 소모품 3회 사용을 허용하고 4번째는 거부한다")
    void useItemAllowsThreeConsumablesPerCombatAndRejectsFourth() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1232L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);
        addInventoryItem(state, "I-3", 1);
        addInventoryItem(state, "I-5", 1);
        addInventoryItem(state, "I-7", 1);

        EngineContext ctx = defaultItemCtx();

        EngineResult first = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-1", 1, TargetSelection.empty())
        );
        assertTrue(first.accepted());

        player.consumablesUsedThisTurn(0);
        EngineResult second = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-3", 1, TargetSelection.empty())
        );
        assertTrue(second.accepted());

        player.consumablesUsedThisTurn(0);
        EngineResult third = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-5", 1, TargetSelection.empty())
        );
        assertTrue(third.accepted());
        assertEquals(3, player.consumablesUsedThisCombat());

        player.consumablesUsedThisTurn(0);
        EngineResult fourth = new GameEngine().process(
                state,
                ctx,
                new UseItemCommand(UUID.randomUUID(), state.version(), playerId, "I-7", 1, TargetSelection.empty())
        );

        assertFalse(fourth.accepted());
        assertTrue(fourth.errors().contains("consumable use limit reached this combat"));
        assertEquals(3, player.consumablesUsedThisCombat());
    }

    @Test
    @DisplayName("아이템 사용은 count가 2인 소모품 사용을 거부한다")
    void useItemRejectsConsumableWhenCountIsTwo() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1233L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-1",
                2,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("consumable count must be 1"));
    }

    @Test
    @DisplayName("아이템 사용은 상급 포션으로 큰 회복을 적용한다")
    void useItemAdvancedPotionAppliesLargeHeal() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 124L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(1);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        addInventoryItem(state, "I-3", 1);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-3",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertTrue(player.hp() > 1);
    }

    @Test
    @DisplayName("아이템 사용은 저가 배리어로 진영 배리어를 적용한다")
    void useItemCheapBarrierAppliesFactionBarrier() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 125L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

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
        assertEquals(8, state.combat().factionStatusValues(CombatState.FactionId.PLAYERS).getOrDefault(S301_Barrier.ID, 0));
    }

    @Test
    @DisplayName("아이템 사용은 배리어 생성기로 큰 진영 배리어를 적용한다")
    void useItemBarrierGeneratorAppliesLargeFactionBarrier() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 126L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        addInventoryItem(state, "I-5", 1);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-5",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(20, state.combat().factionStatusValues(CombatState.FactionId.PLAYERS).getOrDefault(S301_Barrier.ID, 0));
    }

    @Test
    @DisplayName("아이템 사용은 해독제 대상이 없으면 거부한다")
    void useItemRejectsWhenAntidoteHasNoTarget() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 127L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.statusSet(S101_Pain.ID, 2);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-6",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("player target required"));
    }

    @Test
    @DisplayName("아이템 사용은 대상이 주어지면 해독제가 디버프 1개만 제거한다")
    void useItemAntidoteRemovesExactlyOneDebuffWhenTargetProvided() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 128L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.statusSet(S101_Pain.ID, 1);
        player.statusSet(S105_Weak.ID, 1);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-6",
                1,
                new TargetSelection(List.of(TargetRef.ofPlayer(playerId)))
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        int remaining = (player.status(S101_Pain.ID) > 0 ? 1 : 0) + (player.status(S105_Weak.ID) > 0 ? 1 : 0);
        assertTrue(result.accepted());
        assertEquals(1, remaining);
    }

    @Test
    @DisplayName("아이템 사용은 연막탄으로 회피 1을 적용한다")
    void useItemSmokeBombAppliesEvasionOne() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 129L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        int before = player.status(S004_Evasion.ID);

        addInventoryItem(state, "I-7", 1);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-7",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertTrue(result.accepted());
        assertEquals(before + 1, player.status(S004_Evasion.ID));
    }

    @Test
    @DisplayName("아이템 사용은 알 수 없는 인벤토리 아이템이면 거부한다")
    void useItemRejectsWhenInventoryItemIsUnknown() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 130L);
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
    @DisplayName("아이템 사용은 전투 사용 아이템 effect가 없으면 거부한다")
    void useItemRejectsWhenBattleUsableItemEffectIsMissing() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 131L);
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

    @Test
    @DisplayName("아이템 사용은 아이템이 전투 사용 가능하지 않으면 거부한다")
    void useItemRejectsWhenItemIsNotBattleUsable() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 132L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        addInventoryItem(state, "I-8", 1);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-8",
                1,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, defaultItemCtx(), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("item is not battle usable"));
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

    private static RunState.InventoryEntry findItem(GameState state, String itemId) {
        return state.runState().inventory().items().stream()
                .filter(item -> item.ref() instanceof ItemRef ref && ref.itemId().equals(itemId))
                .findFirst()
                .orElseThrow();
    }

    private static void addInventoryItem(GameState state, String itemId, int count) {
        var next = new java.util.ArrayList<>(state.runState().inventory().items());
        next.add(RunState.InventoryEntry.item(new ItemRef(itemId), count, false));
        state.runState().inventory().replaceItems(next);
    }

    private static EngineContext defaultItemCtx() {
        I001_CHEAP_HEALING_POTION i1 = new I001_CHEAP_HEALING_POTION();
        I002_HEALING_POTION i2 = new I002_HEALING_POTION();
        I003_ADVANCED_HEALING_POTION i3 = new I003_ADVANCED_HEALING_POTION();
        I004_CHEAP_BARRIER_GENERATOR i4 = new I004_CHEAP_BARRIER_GENERATOR();
        I005_BARRIER_GENERATOR i5 = new I005_BARRIER_GENERATOR();
        I006_Antidote i6 = new I006_Antidote();
        I007_EmergencySmokeBomb i7 = new I007_EmergencySmokeBomb();
        I008_BulletBundle i8 = new I008_BulletBundle();

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
                        "I-3", i3.definition(),
                        "I-4", i4.definition(),
                        "I-5", i5.definition(),
                        "I-6", i6.definition(),
                        "I-7", i7.definition(),
                        "I-8", i8.definition()
                ),
                Map.of(
                        "I-1", i1,
                        "I-2", i2,
                        "I-3", i3,
                        "I-4", i4,
                        "I-5", i5,
                        "I-6", i6,
                        "I-7", i7
                )
        );
    }

    private static EngineContext itemCtxMissingI1Effect() {
        I001_CHEAP_HEALING_POTION i1 = new I001_CHEAP_HEALING_POTION();
        I002_HEALING_POTION i2 = new I002_HEALING_POTION();
        I003_ADVANCED_HEALING_POTION i3 = new I003_ADVANCED_HEALING_POTION();
        I004_CHEAP_BARRIER_GENERATOR i4 = new I004_CHEAP_BARRIER_GENERATOR();
        I005_BARRIER_GENERATOR i5 = new I005_BARRIER_GENERATOR();
        I006_Antidote i6 = new I006_Antidote();
        I007_EmergencySmokeBomb i7 = new I007_EmergencySmokeBomb();
        I008_BulletBundle i8 = new I008_BulletBundle();

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
                        "I-3", i3.definition(),
                        "I-4", i4.definition(),
                        "I-5", i5.definition(),
                        "I-6", i6.definition(),
                        "I-7", i7.definition(),
                        "I-8", i8.definition()
                ),
                Map.of(
                        "I-2", i2,
                        "I-3", i3,
                        "I-4", i4,
                        "I-5", i5,
                        "I-6", i6,
                        "I-7", i7
                )
        );
    }
}
