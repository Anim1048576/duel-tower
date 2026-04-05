package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
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
    void throwsWhenFallbackEncounterIdDoesNotExist() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new EncounterTableConfig(
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
        ));

        assertEquals("fallback encounterId is missing in encounters: MISSING-FALLBACK", ex.getMessage());
    }

    @Test
    void loadFromResourceParsesAndValidatesEncounterJson() {
        String raw = """
                {
                  "fallbackEncounterId": "T-1",
                  "encounters": [
                    {
                      "encounterId": "T-1",
                      "minFloor": 1,
                      "requiredNodePhase": "COMBAT",
                      "enemies": [
                        {
                          "enemyId": "TE-1",
                          "maxHp": 30,
                          "hpPerFloor": 2,
                          "attackPower": 6,
                          "attackPowerPerFloor": 1,
                          "healingPower": 0,
                          "healingPowerPerFloor": 0
                        }
                      ]
                    }
                  ]
                }
                """;

        EncounterTableConfig loaded = EncounterTables.load(new ByteArrayResource(raw.getBytes(StandardCharsets.UTF_8)));

        RunState runState = new RunState();
        runState.initialize(1L);
        List<EnemyState> enemies = loaded.instantiateEncounterEnemies(runState);

        assertEquals(1, enemies.size());
        assertEquals("TE-1", enemies.get(0).enemyId().value());
        assertEquals(30, enemies.get(0).hp());
    }
}
