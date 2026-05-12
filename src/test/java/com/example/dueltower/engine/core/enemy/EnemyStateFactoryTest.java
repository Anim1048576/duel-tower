package com.example.dueltower.engine.core.enemy;

import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyRole;
import com.example.dueltower.content.enemy.model.EnemyStatsDefinition;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.engine.config.EncounterTableConfig;
import com.example.dueltower.engine.model.EnemyState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnemyStateFactoryTest {

    @Test
    void createsEnemyStateFromDefinitionAndEncounterScaling() {
        EncounterTableConfig.EnemyTemplate template = new EncounterTableConfig.EnemyTemplate(
                "E001_TEST",
                "RUN-ENEMY-1",
                4,
                1,
                2
        );
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test Enemy",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(10, 3, 5, 1),
                List.of(),
                List.of(new EnemyStatusRef("S001_TEST", 2)),
                List.of(new EnemyPassiveRef("P001_TEST"))
        );

        EnemyState enemy = EnemyStateFactory.create(template, definition, 2);

        assertThat(enemy.enemyId().value()).isEqualTo("RUN-ENEMY-1");
        assertThat(enemy.enemyDefId()).isEqualTo("E001_TEST");
        assertThat(enemy.name()).isEqualTo("Test Enemy");
        assertThat(enemy.maxHp()).isEqualTo(18);
        assertThat(enemy.hp()).isEqualTo(18);
        assertThat(enemy.maxAp()).isEqualTo(3);
        assertThat(enemy.ap()).isEqualTo(3);
        assertThat(enemy.attackPower()).isEqualTo(7);
        assertThat(enemy.healPower()).isEqualTo(5);
        assertThat(enemy.status("S001_TEST")).isEqualTo(2);
        assertThat(enemy.passiveIds()).containsExactly("P001_TEST");
    }

    @Test
    void clampsScaledStats() {
        EncounterTableConfig.EnemyTemplate template = new EncounterTableConfig.EnemyTemplate(
                "E001_TEST",
                "RUN-ENEMY-1",
                -20,
                -20,
                -20
        );
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test Enemy",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(10, 0, 5, 1),
                List.of(),
                List.of(),
                List.of()
        );

        EnemyState enemy = EnemyStateFactory.create(template, definition, 1);

        assertThat(enemy.maxHp()).isEqualTo(1);
        assertThat(enemy.attackPower()).isZero();
        assertThat(enemy.healPower()).isZero();
    }
}
