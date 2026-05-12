package com.example.dueltower.content.enemy.service;

import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyRole;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnemyContentLoaderTest {

    @Test
    void defaultEnemiesJsonLoads() {
        EnemyContentLoader loader = new EnemyContentLoader(new ClassPathResource("balance/enemies.json"));

        List<EnemyDefinition> definitions = loader.loadAll();

        assertThat(definitions).extracting(EnemyDefinition::id)
                .contains("E001_TRAINING_DUMMY", "E002_TOWER_RAT");
        EnemyDefinition rat = definitions.stream()
                .filter(definition -> definition.id().equals("E002_TOWER_RAT"))
                .findFirst()
                .orElseThrow();
        assertThat(rat.role()).isEqualTo(EnemyRole.NORMAL);
        assertThat(rat.stats().maxHp()).isEqualTo(18);
        assertThat(rat.deck()).isEmpty();
        assertThat(rat.startStatuses()).isEmpty();
        assertThat(rat.passives()).isEmpty();
    }

    @Test
    void emptyEnemyContentFails() {
        EnemyContentLoader loader = new EnemyContentLoader(jsonResource("""
                {
                  "enemies": []
                }
                """));

        assertThatThrownBy(loader::loadAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enemy content must not be empty");
    }

    @Test
    void invalidRoleMessageContainsEnemyId() {
        EnemyContentLoader loader = new EnemyContentLoader(jsonResource("""
                {
                  "enemies": [
                    {
                      "id": "E999_BAD",
                      "name": "Bad",
                      "role": "UNKNOWN",
                      "stats": {
                        "maxHp": 1,
                        "maxActionPoint": 0,
                        "attackPower": 0,
                        "healPower": 0
                      }
                    }
                  ]
                }
                """));

        assertThatThrownBy(loader::loadAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid enemy role")
                .hasMessageContaining("E999_BAD");
    }

    private static Resource jsonResource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "test enemy json";
            }
        };
    }
}
