package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterCombatStatCalculatorTest {

    private final CharacterCombatStatCalculator calculator = new CharacterCombatStatCalculator();

    @Test
    void calculate_appliesFormulaAndSoftCaps() {
        CharacterProfile profile = CharacterProfile.builder()
                .name("테스터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("소원")
                .disposition("성향")
                .oneLiner("한마디")
                .story("스토리")
                .physical(8)
                .technique(9)
                .sense(7)
                .willpower(10)
                .trait1("특성1")
                .trait2("특성2")
                .ownedCards("[]")
                .currentSkillDeck(List.of())
                .exCard("{}")
                .build();

        CharacterCombatStatCalculator.CombatStats stats = calculator.calculate(profile);

        assertThat(stats.maxHp()).isEqualTo(35);
        assertThat(stats.maxAp()).isEqualTo(4);
        assertThat(stats.attackPower()).isEqualTo(11);
        assertThat(stats.healPower()).isEqualTo(12);
    }

    @Test
    void calculate_respectsMaxHpFloor() {
        CharacterProfile profile = CharacterProfile.builder()
                .name("초보")
                .gender(CharacterGender.OTHER)
                .age(18)
                .wish("소원")
                .disposition("성향")
                .oneLiner("한마디")
                .story("스토리")
                .physical(0)
                .technique(0)
                .sense(0)
                .willpower(0)
                .trait1("특성1")
                .trait2("특성2")
                .ownedCards("[]")
                .currentSkillDeck(List.of())
                .exCard("{}")
                .build();

        CharacterCombatStatCalculator.CombatStats stats = calculator.calculate(profile);

        assertThat(stats.maxHp()).isEqualTo(20);
        assertThat(stats.maxAp()).isEqualTo(3);
        assertThat(stats.attackPower()).isEqualTo(0);
        assertThat(stats.healPower()).isEqualTo(0);
    }
}
