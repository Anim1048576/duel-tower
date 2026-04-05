package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterTableConfigTest {

    @Test
    void defaultsSelectCombatEncounterAndScaleEnemyByFloor() {
        RunState runState = new RunState();
        runState.initialize(123L);

        List<EnemyState> floorOneEnemies = EncounterTableConfig.defaults().instantiateEncounterEnemies(runState);
        EnemyState floorOneEnemy = floorOneEnemies.get(0);

        assertEquals("RUN-ENEMY-1", floorOneEnemy.enemyId().value());
        assertEquals(22, floorOneEnemy.hp());
        assertEquals(5, floorOneEnemy.attackPower());
        assertEquals(0, floorOneEnemy.healPower());

        RunState.NodeChoice choice = runState.availableChoices().stream().findFirst().orElseThrow();
        runState.beginNode(choice);
        runState.resolveCurrentNode("전투", "테스트", "요약", "상세", 0, 0, 0);
        runState.completeResultAndPrepareNext(456L);

        List<EnemyState> floorTwoEnemies = EncounterTableConfig.defaults().instantiateEncounterEnemies(runState);
        EnemyState floorTwoEnemy = floorTwoEnemies.get(0);

        assertEquals(26, floorTwoEnemy.hp());
        assertEquals(6, floorTwoEnemy.attackPower());
        assertEquals(1, floorTwoEnemy.healPower());
    }

    @Test
    void throwsWhenNoEncounterMatchesAndFallbackMissing() {
        EncounterTableConfig config = new EncounterTableConfig(
                List.of(
                        new EncounterTableConfig.EncounterTemplate(
                                "HIGH-FLOOR",
                                10,
                                null,
                                RunState.NodePhase.COMBAT,
                                List.of(new EncounterTableConfig.EnemyTemplate("E-1", 20, 0, 3, 0, 0, 0))
                        )
                ),
                "MISSING-FALLBACK"
        );

        RunState runState = new RunState();
        runState.initialize(99L);

        assertThrows(IllegalStateException.class, () -> config.instantiateEncounterEnemies(runState));
    }
}
