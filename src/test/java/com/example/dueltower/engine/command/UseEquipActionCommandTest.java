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

class UseEquipActionCommandTest {

    @Test
    void pistolActionDealsFixedDamageAndConsumesLoadedAmmo() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 301L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        Ids.EnemyId enemyId = new Ids.EnemyId("e1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.enemies().put(enemyId, new EnemyState(enemyId, 50));
        seedCombatMainTurn(state, playerId);

        String inventoryEquipId = "eq-1";
        player.equipItem(EquipSlot.WEAPON, new EquippedItem(inventoryEquipId, "E-2", false, 6, 6));

        GameEngine engine = new GameEngine();
        EngineContext ctx = equipCtx();

        int beforeHp = state.enemy(enemyId).hp();
        EngineResult result = engine.process(state, ctx,
                new UseEquipActionCommand(
                        UUID.randomUUID(),
                        state.version(),
                        playerId,
                        inventoryEquipId,
                        new TargetSelection(List.of(TargetRef.ofEnemy(enemyId)))
                ));

        assertTrue(result.accepted());
        assertEquals(beforeHp - 12, state.enemy(enemyId).hp());
        assertEquals(5, player.equippedItem(EquipSlot.WEAPON).loadedAmmo());
    }

    @Test
    void pistolActionRejectedWhenNoLoadedAmmo() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 302L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        Ids.EnemyId enemyId = new Ids.EnemyId("e1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.enemies().put(enemyId, new EnemyState(enemyId, 30));
        seedCombatMainTurn(state, playerId);

        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-1", "E-2", false, 0, 6));
        EngineResult result = new GameEngine().process(state, equipCtx(),
                new UseEquipActionCommand(
                        UUID.randomUUID(),
                        state.version(),
                        playerId,
                        "eq-1",
                        new TargetSelection(List.of(TargetRef.ofEnemy(enemyId)))
                ));
        assertFalse(result.accepted());
        assertTrue(result.errors().contains("not enough loaded ammo"));
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
                        "E-1", new EquipDefinition("E-1", "튼튼한 죽창", EquipSlot.WEAPON, "s", "d", List.of("장비"), null, null, null, null),
                        "E-2", new EquipDefinition("E-2", "휴대용 권총", EquipSlot.WEAPON, "s", "d", List.of("장비"),
                                new EquipAmmoPolicy(6, 6),
                                new EquipReloadPolicy("I-8", 6),
                                new EquipActionDefinition("E-2-FIRE", "사격", "s", "d", Target.ENEMY_ONE, 0, true, 1, 12),
                                null)
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
