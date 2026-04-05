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

class ReloadEquipmentCommandTest {

    @Test
    void reloadConsumesBulletBundleAndRestoresAmmoToMax() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 401L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-1", "E-2", false, 1, 6));
        InventoryCommandSupport.addInventoryEntryCount(state, new ItemRef("I-8"), false, 2);

        EngineResult result = new GameEngine().process(state, equipCtx(),
                new ReloadEquipmentCommand(UUID.randomUUID(), state.version(), playerId, "eq-1"));

        assertTrue(result.accepted());
        assertEquals(6, player.equippedItem(EquipSlot.WEAPON).loadedAmmo());
        RunState.InventoryEntry ammo = InventoryCommandSupport.findItemEntry(state, "I-8");
        assertNotNull(ammo);
        assertEquals(1, ammo.count());
    }

    @Test
    void reloadRejectedWhenAmmoAlreadyFull() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 402L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-1", "E-2", false, 6, 6));
        InventoryCommandSupport.addInventoryEntryCount(state, new ItemRef("I-8"), false, 1);

        EngineResult result = new GameEngine().process(state, equipCtx(),
                new ReloadEquipmentCommand(UUID.randomUUID(), state.version(), playerId, "eq-1"));

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("loaded ammo is already full"));
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
                        "E-2", new EquipDefinition("E-2", "휴대용 권총", EquipSlot.WEAPON, "s", "d", List.of("장비"),
                                new EquipActionDefinition("E-2-FIRE", "사격", "s", "d", Target.ENEMY_ONE, 0, true, 1))
                )
        );
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
