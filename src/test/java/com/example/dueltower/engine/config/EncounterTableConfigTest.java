package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterTableConfigTest {

    @Test
    void defaultsSelectCombatEncounterAndExposeScalingByFloor() {
        RunState runState = new RunState();
        runState.initialize(123L);

        EncounterTableConfig config = EncounterTableConfig.defaults();
        EncounterTableConfig.EncounterTemplate floorOneEncounter = config.selectEncounter(runState);
        EncounterTableConfig.EnemyTemplate floorOneEnemy = floorOneEncounter.enemies().get(0);

        assertEquals("RUN-DEFAULT-COMBAT", floorOneEncounter.encounterId());
        assertEquals("E002_TOWER_RAT", floorOneEnemy.enemyDefId());
        assertEquals("RUN-ENEMY-1", floorOneEnemy.instanceId());
        assertEquals(4, floorOneEnemy.hpPerFloor());
        assertEquals(1, floorOneEnemy.attackPowerPerFloor());
        assertEquals(0, floorOneEnemy.healingPowerPerFloor());
        assertEquals(1, config.resolveFloor(runState));
        assertEquals(0, config.resolveFloorDelta(runState, floorOneEncounter));

        RunState.NodeChoice choice = runState.availableChoices().stream().findFirst().orElseThrow();
        runState.beginNode(choice);
        runState.resolveCurrentNode("전투", "테스트", "요약", "상세", 0, 0, 0);
        runState.markCurrentFloorClearedByBoss();
        runState.completeResultAndPrepareNext(456L);

        EncounterTableConfig.EncounterTemplate floorTwoEncounter = config.selectEncounter(runState);

        assertEquals(2, config.resolveFloor(runState));
        assertEquals(1, config.resolveFloorDelta(runState, floorTwoEncounter));
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
                                List.of(new EncounterTableConfig.EnemyTemplate("E001_TEST", "E-1", 0, 0, 0))
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
                          "enemyDefId": "E001_TEST",
                          "instanceId": "TE-1",
                          "hpPerFloor": 2,
                          "attackPowerPerFloor": 1,
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
        EncounterTableConfig.EncounterTemplate encounter = loaded.selectEncounter(runState);
        EncounterTableConfig.EnemyTemplate enemy = encounter.enemies().get(0);

        assertEquals("T-1", encounter.encounterId());
        assertEquals("E001_TEST", enemy.enemyDefId());
        assertEquals("TE-1", enemy.instanceId());
        assertEquals(2, enemy.hpPerFloor());
    }

    @Test
    void throwsWhenEncounterContainsDuplicateInstanceId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new EncounterTableConfig(
                List.of(
                        new EncounterTableConfig.EncounterTemplate(
                                "T-1",
                                1,
                                null,
                                RunState.NodePhase.COMBAT,
                                List.of(
                                        new EncounterTableConfig.EnemyTemplate("E001_TEST", "TE-1", 0, 0, 0),
                                        new EncounterTableConfig.EnemyTemplate("E002_TEST", "TE-1", 0, 0, 0)
                                )
                        )
                ),
                "T-1"
        ));

        assertEquals("duplicate enemy instance id in encounter: encounterId=T-1, instanceId=TE-1", ex.getMessage());
    }
}
