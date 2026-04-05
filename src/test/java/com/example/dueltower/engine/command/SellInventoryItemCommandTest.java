package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SellInventoryItemCommandTest {

    @Test
    void sellEquipGrantsGoldAndRemovesExactEntry() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 501L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        state.nodeState(NodeState.NON_COMBAT);
        int beforeGold = state.runState().inventory().gold();

        InventoryCommandSupport.addInventoryEntryCount(state, new EquipRef("E-1"), false, 1);
        String inventoryEquipId = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof EquipRef ref && "E-1".equals(ref.equipId()))
                .findFirst().orElseThrow().inventoryEquipId();

        EngineResult result = new GameEngine().process(state, equipCtx(),
                new SellInventoryItemCommand(UUID.randomUUID(), state.version(), playerId, null, inventoryEquipId, 1));

        assertTrue(result.accepted());
        assertEquals(beforeGold + 100, state.runState().inventory().gold());
        assertNull(InventoryCommandSupport.findEquipEntryByInventoryId(state, inventoryEquipId));
    }

    @Test
    void cannotSellEquippedEquipEntry() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 502L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);

        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-locked", "E-1", false, null, null));
        InventoryCommandSupport.addEquipInventoryEntry(state, "eq-locked", "E-1", false, null, null);

        EngineResult result = new GameEngine().process(state, equipCtx(),
                new SellInventoryItemCommand(UUID.randomUUID(), state.version(), playerId, null, "eq-locked", 1));

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("equipped equipment cannot be sold"));
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
                        "E-1", new EquipDefinition("E-1", "튼튼한 죽창", EquipSlot.WEAPON, "s", "d", List.of("장비"), null),
                        "E-2", new EquipDefinition("E-2", "휴대용 권총", EquipSlot.WEAPON, "s", "d", List.of("장비"), null)
                )
        );
    }
}
