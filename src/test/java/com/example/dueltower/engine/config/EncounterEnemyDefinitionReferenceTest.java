package com.example.dueltower.engine.config;

import com.example.dueltower.content.enemy.service.EnemyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EncounterEnemyDefinitionReferenceTest {

    @Autowired
    private EncounterTables encounterTables;

    @Autowired
    private EnemyService enemyService;

    @Test
    void defaultEncountersLoadAndReferenceExistingEnemyDefinitions() {
        EncounterTableConfig config = encounterTables.encounterTableConfig();

        assertThat(config.encounters()).isNotEmpty();
        assertThat(config.encounters())
                .extracting(EncounterTableConfig.EncounterTemplate::encounterId)
                .contains(config.fallbackEncounterId());

        for (EncounterTableConfig.EncounterTemplate encounter : config.encounters()) {
            assertThat(encounter.enemies()).isNotEmpty();
            Set<String> instanceIds = new HashSet<>();
            for (EncounterTableConfig.EnemyTemplate enemy : encounter.enemies()) {
                assertThat(enemyService.exists(enemy.enemyDefId()))
                        .as("enemyDefId exists: encounterId=%s, enemyDefId=%s", encounter.encounterId(), enemy.enemyDefId())
                        .isTrue();
                assertThat(instanceIds.add(enemy.instanceId()))
                        .as("instanceId is unique: encounterId=%s, instanceId=%s", encounter.encounterId(), enemy.instanceId())
                        .isTrue();
            }
        }
    }
}
