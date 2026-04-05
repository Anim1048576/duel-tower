package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EquipEquipmentCommandTest {

    @Test
    void buyShopItemAddsEquipRefOrItemRefByOffer() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 200L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedShopReady(state);

        GameEngine engine = new GameEngine();
        EngineContext ctx = equipCtx();

        EngineResult buyEquip = engine.process(state, ctx,
                new BuyShopItemCommand(UUID.randomUUID(), state.version(), playerId, "O-8", 1));
        assertTrue(buyEquip.accepted());
        assertTrue(state.runState().inventory().items().stream()
                .anyMatch(entry -> entry.ref() instanceof EquipRef ref && "E-1".equals(ref.equipId())));

        GameState secondState = new GameState(new Ids.SessionId(UUID.randomUUID()), 2001L);
        secondState.players().put(playerId, new PlayerState(playerId));
        seedShopReady(secondState);

        EngineResult buyBullet = engine.process(secondState, ctx,
                new BuyShopItemCommand(UUID.randomUUID(), secondState.version(), playerId, "O-10", 1));
        assertTrue(buyBullet.accepted());
        assertTrue(secondState.runState().inventory().items().stream()
                .anyMatch(entry -> entry.ref() instanceof ItemRef ref && "I-8".equals(ref.itemId())));
    }

    @Test
    void equipAndUnequipMutateInventoryAndEquippedState() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 201L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedShopReady(state);
        InventoryCommandSupport.addInventoryEntryCount(state, new EquipRef("E-1"), true, 1);

        GameEngine engine = new GameEngine();
        EngineContext ctx = equipCtx();
        String inventoryEquipId = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof EquipRef ref && "E-1".equals(ref.equipId()))
                .map(RunState.InventoryEntry::inventoryEquipId)
                .findFirst()
                .orElseThrow();

        EngineResult equip = engine.process(state, ctx,
                new EquipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, inventoryEquipId));

        assertTrue(equip.accepted());
        assertNull(InventoryCommandSupport.findEquipEntry(state, "E-1"));
        EquippedItem equipped = player.equippedItem(EquipSlot.WEAPON);
        assertNotNull(equipped);
        assertEquals("E-1", equipped.equipId());
        assertEquals(inventoryEquipId, equipped.inventoryEquipId());
        assertTrue(equipped.bound());

        EngineResult unequip = engine.process(state, ctx,
                new UnequipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, inventoryEquipId));

        assertTrue(unequip.accepted());
        assertNull(player.equippedItem(EquipSlot.WEAPON));
        RunState.InventoryEntry returned = InventoryCommandSupport.findEquipEntry(state, "E-1");
        assertNotNull(returned);
        assertEquals(inventoryEquipId, returned.inventoryEquipId());
        assertTrue(returned.bound());
        assertEquals(1, returned.count());
    }

    @Test
    void equipOrUnequipRejectedDuringCombatAndWhenSlotOccupied() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 202L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedShopReady(state);
        InventoryCommandSupport.addInventoryEntryCount(state, new EquipRef("E-1"), false, 1);
        InventoryCommandSupport.addInventoryEntryCount(state, new EquipRef("E-2"), false, 1);

        GameEngine engine = new GameEngine();
        EngineContext ctx = equipCtx();

        String spearInventoryId = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof EquipRef ref && "E-1".equals(ref.equipId()))
                .map(RunState.InventoryEntry::inventoryEquipId)
                .findFirst()
                .orElseThrow();
        String pistolInventoryId = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof EquipRef ref && "E-2".equals(ref.equipId()))
                .map(RunState.InventoryEntry::inventoryEquipId)
                .findFirst()
                .orElseThrow();

        assertTrue(engine.process(state, ctx,
                new EquipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, spearInventoryId)).accepted());

        EngineResult slotConflict = engine.process(state, ctx,
                new EquipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, pistolInventoryId));
        assertFalse(slotConflict.accepted());
        assertTrue(slotConflict.errors().stream().anyMatch(e -> e.contains("slot already occupied")));

        seedCombatMainTurn(state, playerId);

        EngineResult equipInCombat = engine.process(state, ctx,
                new EquipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, pistolInventoryId));
        assertFalse(equipInCombat.accepted());
        assertTrue(equipInCombat.errors().contains("cannot equip equipment during combat"));

        EngineResult unequipInCombat = engine.process(state, ctx,
                new UnequipEquipmentCommand(UUID.randomUUID(), state.version(), playerId, spearInventoryId));
        assertFalse(unequipInCombat.accepted());
        assertTrue(unequipInCombat.errors().contains("cannot unequip equipment during combat"));
    }

    @Test
    void buyingSameEquipTwiceCreatesDistinctInventoryEntries() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 203L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedShopReady(state);

        GameEngine engine = new GameEngine();
        EngineContext ctx = equipCtx();
        assertTrue(engine.process(state, ctx,
                new BuyShopItemCommand(UUID.randomUUID(), state.version(), playerId, "O-9", 2)).accepted());

        var pistols = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof EquipRef ref && "E-2".equals(ref.equipId()))
                .toList();
        assertEquals(2, pistols.size());
        assertNotEquals(pistols.get(0).inventoryEquipId(), pistols.get(1).inventoryEquipId());
        assertEquals(1, pistols.get(0).count());
        assertEquals(1, pistols.get(1).count());
        assertEquals(6, pistols.get(0).loadedAmmo());
        assertEquals(6, pistols.get(1).loadedAmmo());
    }

    private static EngineContext equipCtx() {
        return new EngineContext(
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(
                        "E-1", new EquipDefinition("E-1", "튼튼한 죽창", EquipSlot.WEAPON, "장착 가능한 근접 무기", "장착 가능한 근접 무기", java.util.List.of("장비"), null),
                        "E-2", new EquipDefinition("E-2", "휴대용 권총", EquipSlot.WEAPON, "장착 가능한 원거리 무기", "장착 가능한 원거리 무기", java.util.List.of("장비"), null)
                )
        );
    }

    private static void seedShopReady(GameState state) {
        state.nodeState(NodeState.NON_COMBAT);
        RunState.NodeChoice event = state.runState().availableChoices().stream()
                .filter(c -> c.phase() == RunState.NodePhase.EVENT)
                .findFirst().orElseThrow();
        state.runState().beginNode(event);
    }

    private static void seedCombatMainTurn(GameState state, Ids.PlayerId playerId) {
        CombatState combat = new CombatState();
        combat.phase(CombatPhase.MAIN);
        combat.turnOrder().add(TargetRef.ofPlayer(playerId));
        combat.currentTurnIndex(0);
        state.combat(combat);
        state.nodeState(NodeState.COMBAT);
    }
}
