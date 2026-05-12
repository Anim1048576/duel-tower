package com.example.dueltower.content.enemy.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyRole;
import com.example.dueltower.content.enemy.model.EnemyStatsDefinition;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnemyServiceTest {

    @Test
    void listGetExistsAndDefsMapUseLoadedDefinitions() {
        EnemyDefinition dummy = enemy("E001_TRAINING_DUMMY");
        EnemyDefinition rat = enemy("E002_TOWER_RAT");
        EnemyService service = service(List.of(rat, dummy));

        assertThat(service.list()).extracting(EnemyDefinition::id)
                .containsExactly("E001_TRAINING_DUMMY", "E002_TOWER_RAT");
        assertThat(service.get(" E002_TOWER_RAT ")).isSameAs(rat);
        assertThat(service.exists("E001_TRAINING_DUMMY")).isTrue();
        assertThat(service.defsMap()).containsKeys("E001_TRAINING_DUMMY", "E002_TOWER_RAT");
    }

    @Test
    void duplicateEnemyIdFails() {
        assertThatThrownBy(() -> service(List.of(enemy("E001_DUP"), enemy("E001_DUP"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate enemy id")
                .hasMessageContaining("E001_DUP");
    }

    @Test
    void blankEnemyNameFails() {
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                " ",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(),
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> service(List.of(definition)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enemy field must not be blank")
                .hasMessageContaining("E001_TEST")
                .hasMessageContaining("name");
    }

    @Test
    void maxHpMustBePositive() {
        assertThatInvalidStat(enemyWithStats(new EnemyStatsDefinition(0, 0, 0, 0)), "maxHp");
    }

    @Test
    void maxActionPointMustNotBeNegative() {
        assertThatInvalidStat(enemyWithStats(new EnemyStatsDefinition(1, -1, 0, 0)), "maxActionPoint");
    }

    @Test
    void attackPowerMustNotBeNegative() {
        assertThatInvalidStat(enemyWithStats(new EnemyStatsDefinition(1, 0, -1, 0)), "attackPower");
    }

    @Test
    void healPowerMustNotBeNegative() {
        assertThatInvalidStat(enemyWithStats(new EnemyStatsDefinition(1, 0, 0, -1)), "healPower");
    }

    @Test
    void missingDeckCardFails() {
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(new CardDefId("C999_MISSING")),
                List.of(),
                List.of()
        );

        CardService cardService = mock(CardService.class);
        when(cardService.exists(new CardDefId("C999_MISSING"))).thenReturn(false);

        assertThatThrownBy(() -> new EnemyService(loader(List.of(definition)), cardService, mock(StatusService.class), mock(PassiveService.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing card referenced by enemy")
                .hasMessageContaining("C999_MISSING");
    }

    @Test
    void missingStartStatusFails() {
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(),
                List.of(new EnemyStatusRef("S999_MISSING", 1)),
                List.of()
        );
        StatusService statusService = mock(StatusService.class);
        when(statusService.exists("S999_MISSING")).thenReturn(false);

        assertThatThrownBy(() -> new EnemyService(loader(List.of(definition)), mock(CardService.class), statusService, mock(PassiveService.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing status referenced by enemy")
                .hasMessageContaining("S999_MISSING");
    }

    @Test
    void startStatusStacksMustBePositive() {
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(),
                List.of(new EnemyStatusRef("S001_TEST", 0)),
                List.of()
        );

        assertThatThrownBy(() -> service(List.of(definition)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stacks=0");
    }

    @Test
    void missingPassiveFails() {
        EnemyDefinition definition = new EnemyDefinition(
                "E001_TEST",
                "Test",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(),
                List.of(),
                List.of(new EnemyPassiveRef("P999_MISSING"))
        );
        PassiveService passiveService = mock(PassiveService.class);
        when(passiveService.exists("P999_MISSING")).thenReturn(false);

        assertThatThrownBy(() -> new EnemyService(loader(List.of(definition)), mock(CardService.class), mock(StatusService.class), passiveService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing passive referenced by enemy")
                .hasMessageContaining("P999_MISSING");
    }

    private static void assertThatInvalidStat(EnemyDefinition definition, String fieldName) {
        assertThatThrownBy(() -> service(List.of(definition)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid enemy stat")
                .hasMessageContaining(fieldName);
    }

    private static EnemyService service(List<EnemyDefinition> definitions) {
        CardService cardService = mock(CardService.class);
        StatusService statusService = mock(StatusService.class);
        PassiveService passiveService = mock(PassiveService.class);
        return new EnemyService(loader(definitions), cardService, statusService, passiveService);
    }

    private static EnemyContentLoader loader(List<EnemyDefinition> definitions) {
        EnemyContentLoader loader = mock(EnemyContentLoader.class);
        when(loader.loadAll()).thenReturn(definitions);
        return loader;
    }

    private static EnemyDefinition enemy(String id) {
        return new EnemyDefinition(
                id,
                "Test Enemy",
                EnemyRole.NORMAL,
                "",
                new EnemyStatsDefinition(1, 0, 0, 0),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static EnemyDefinition enemyWithStats(EnemyStatsDefinition stats) {
        return new EnemyDefinition(
                "E001_TEST",
                "Test Enemy",
                EnemyRole.NORMAL,
                "",
                stats,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
