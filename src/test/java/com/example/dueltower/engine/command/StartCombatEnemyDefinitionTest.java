package com.example.dueltower.engine.command;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyRole;
import com.example.dueltower.content.enemy.model.EnemyStatsDefinition;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.engine.config.EncounterTableConfig;
import com.example.dueltower.engine.config.RunConfigs;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartCombatEnemyDefinitionTest {

    @Test
    void startCombatCreatesEnemyStateFromEnemyDefinition() {
        EnemyDefinition definition = enemyDefinition("E001_TEST", "Test Enemy", 20, 4, 7, 2);
        EncounterTableConfig.EnemyTemplate enemyTemplate = new EncounterTableConfig.EnemyTemplate(
                definition.id(),
                "RUN-ENEMY-1",
                3,
                2,
                1
        );
        EncounterTableConfig encounterTable = encounterTable(List.of(enemyTemplate));
        GameState state = stateWithPlayer("p1");

        EngineResult result = new GameEngine().process(
                state,
                context(encounterTable, Map.of(definition.id(), definition)),
                new StartCombatCommand(UUID.randomUUID(), state.version(), new Ids.PlayerId("p1"))
        );

        EnemyState enemy = state.enemy(new Ids.EnemyId("RUN-ENEMY-1"));

        assertThat(result.accepted()).isTrue();
        assertThat(enemy).isNotNull();
        assertThat(enemy.enemyDefId()).isEqualTo("E001_TEST");
        assertThat(enemy.name()).isEqualTo("Test Enemy");
        assertThat(enemy.maxHp()).isEqualTo(20);
        assertThat(enemy.hp()).isEqualTo(20);
        assertThat(enemy.maxAp()).isEqualTo(4);
        assertThat(enemy.ap()).isEqualTo(4);
        assertThat(enemy.attackPower()).isEqualTo(7);
        assertThat(enemy.healPower()).isEqualTo(2);
        assertThat(enemy.status("S001_TEST")).isEqualTo(2);
        assertThat(enemy.passiveIds()).containsExactly("P001_TEST");
        assertThat(result.events())
                .filteredOn(GameEvent.LogAppended.class::isInstance)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anySatisfy(line -> assertThat(line).contains("RUN-ENEMY-1").contains("E001_TEST"));
    }

    @Test
    void missingEnemyDefinitionFailsClearly() {
        EncounterTableConfig encounterTable = encounterTable(List.of(
                new EncounterTableConfig.EnemyTemplate("E999_MISSING", "RUN-ENEMY-1", 0, 0, 0)
        ));
        GameState state = stateWithPlayer("p1");

        assertThatThrownBy(() -> new GameEngine().process(
                state,
                context(encounterTable, Map.of()),
                new StartCombatCommand(UUID.randomUUID(), state.version(), new Ids.PlayerId("p1"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing EnemyDefinition")
                .hasMessageContaining("E999_MISSING");
    }

    @Test
    void duplicateEncounterInstanceIdFailsBeforeCombatStart() {
        assertThatThrownBy(() -> encounterTable(List.of(
                new EncounterTableConfig.EnemyTemplate("E001_TEST", "RUN-ENEMY-1", 0, 0, 0),
                new EncounterTableConfig.EnemyTemplate("E002_TEST", "RUN-ENEMY-1", 0, 0, 0)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate enemy instance id")
                .hasMessageContaining("RUN-ENEMY-1");
    }

    private static GameState stateWithPlayer(String playerId) {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId id = new Ids.PlayerId(playerId);
        state.players().put(id, new PlayerState(id));
        return state;
    }

    private static EncounterTableConfig encounterTable(List<EncounterTableConfig.EnemyTemplate> enemies) {
        return new EncounterTableConfig(
                List.of(new EncounterTableConfig.EncounterTemplate(
                        "RUN-DEFAULT-COMBAT",
                        1,
                        null,
                        null,
                        enemies
                )),
                "RUN-DEFAULT-COMBAT"
        );
    }

    private static EnemyDefinition enemyDefinition(
            String id,
            String name,
            int maxHp,
            int maxActionPoint,
            int attackPower,
            int healPower
    ) {
        return new EnemyDefinition(
                id,
                name,
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(maxHp, maxActionPoint, attackPower, healPower),
                List.of(),
                List.of(new EnemyStatusRef("S001_TEST", 2)),
                List.of(new EnemyPassiveRef("P001_TEST"))
        );
    }

    private static EngineContext context(
            EncounterTableConfig encounterTable,
            Map<String, EnemyDefinition> enemyDefs
    ) {
        return new EngineContext(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                GameRules.defaults(),
                RewardTableConfig.defaults(),
                encounterTable,
                RunConfigs.defaultConfig(),
                enemyDefs
        );
    }
}
