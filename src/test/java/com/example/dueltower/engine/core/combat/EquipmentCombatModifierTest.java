package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentCombatModifierTest {

    @Test
    void sturdySpearAddsAttackPowerToGeneralDamageCalculation() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 601L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        Ids.EnemyId enemyId = new Ids.EnemyId("e1");
        PlayerState player = new PlayerState(playerId);
        player.body(2);
        state.players().put(playerId, player);
        state.enemies().put(enemyId, new EnemyState(enemyId, 100));

        EffectOps withoutEquip = new EffectOps(new EffectContext(
                state, new EngineContext(Map.of(), Map.of()), playerId, null,
                new TargetSelection(List.of(TargetRef.ofEnemy(enemyId))), new ArrayList<>()
        ));
        int before = state.enemy(enemyId).hp();
        withoutEquip.damageWithActorAttack(Target.ENEMY_ONE);
        int dealtWithoutEquip = before - state.enemy(enemyId).hp();

        state.enemy(enemyId).hp(100);
        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-1", "E-1", false, null, null));
        EffectOps withEquip = new EffectOps(new EffectContext(
                state, new EngineContext(Map.of(), Map.of()), playerId, null,
                new TargetSelection(List.of(TargetRef.ofEnemy(enemyId))), new ArrayList<>()
        ));
        int beforeWithEquip = state.enemy(enemyId).hp();
        withEquip.damageWithActorAttack(Target.ENEMY_ONE);
        int dealtWithEquip = beforeWithEquip - state.enemy(enemyId).hp();

        assertEquals(dealtWithoutEquip + 2, dealtWithEquip);
    }

    @Test
    void sturdySpearIncreasesIncomingDamageByOne() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 602L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(20);
        state.players().put(playerId, player);

        List<com.example.dueltower.engine.event.GameEvent> events = new ArrayList<>();
        DamageOps.apply(state, new EngineContext(Map.of(), Map.of()), events,
                "src", TargetRef.ofPlayer(playerId), 5);
        int hpWithoutEquip = player.hp();

        player.hp(20);
        player.equipItem(EquipSlot.WEAPON, new EquippedItem("eq-2", "E-1", false, null, null));
        DamageOps.apply(state, new EngineContext(Map.of(), Map.of()), events,
                "src", TargetRef.ofPlayer(playerId), 5);
        int hpWithEquip = player.hp();

        assertEquals(hpWithoutEquip - 1, hpWithEquip);
    }
}
