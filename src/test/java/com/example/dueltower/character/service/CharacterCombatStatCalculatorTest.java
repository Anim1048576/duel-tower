package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterCombatStatCalculatorTest {

    private final CharacterCombatStatCalculator calculator = new CharacterCombatStatCalculator();

    @Test
    @DisplayName("calculateMaxHp: 낮은 능력치에서도 최소 20을 보장한다")
    void calculateMaxHpHasFloorOfTwenty() {
        int maxHp = CharacterCombatStatCalculator.calculateMaxHp(0, 0);

        assertEquals(20, maxHp);
        assertTrue(maxHp >= 20);
    }

    @Test
    @DisplayName("calculateMaxHp: score 40 경계 전후에서 소프트캡 규칙을 따른다")
    void calculateMaxHpSoftCapBoundaryAroundForty() {
        assertEquals(39, CharacterCombatStatCalculator.calculateMaxHp(6, 2));
        assertEquals(40, CharacterCombatStatCalculator.calculateMaxHp(5, 4));
        assertEquals(41, CharacterCombatStatCalculator.calculateMaxHp(6, 3));
        assertEquals(42, CharacterCombatStatCalculator.calculateMaxHp(7, 2));
    }

    @Test
    @DisplayName("calculateMaxAp: 의지 값별 공식과 내림 동작을 따른다")
    void calculateMaxApFormulaAcrossWillpowerValues() {
        assertEquals(3, CharacterCombatStatCalculator.calculateMaxAp(0));
        assertEquals(3, CharacterCombatStatCalculator.calculateMaxAp(5));
        assertEquals(4, CharacterCombatStatCalculator.calculateMaxAp(6));
        assertEquals(4, CharacterCombatStatCalculator.calculateMaxAp(11));
        assertEquals(5, CharacterCombatStatCalculator.calculateMaxAp(12));
    }

    @Test
    @DisplayName("calculateAttackPower: score 10 경계 전후에서 소프트캡 규칙을 따른다")
    void calculateAttackPowerSoftCapBoundaryAroundTen() {
        assertEquals(9, CharacterCombatStatCalculator.calculateAttackPower(4, 4, 3));
        assertEquals(10, CharacterCombatStatCalculator.calculateAttackPower(4, 4, 4));
        assertEquals(10, CharacterCombatStatCalculator.calculateAttackPower(4, 4, 6));
        assertEquals(11, CharacterCombatStatCalculator.calculateAttackPower(4, 4, 8));
    }

    @Test
    @DisplayName("calculateHealPower: score 10 경계 전후에서 소프트캡 규칙을 따른다")
    void calculateHealPowerSoftCapBoundaryAroundTen() {
        assertEquals(9, CharacterCombatStatCalculator.calculateHealPower(4, 3));
        assertEquals(10, CharacterCombatStatCalculator.calculateHealPower(4, 4));
        assertEquals(10, CharacterCombatStatCalculator.calculateHealPower(4, 6));
        assertEquals(11, CharacterCombatStatCalculator.calculateHealPower(5, 4));
    }

    @Test
    @DisplayName("calculate: CombatStats 필드가 개별 공식 결과와 일치한다")
    void calculateReturnsRecordMatchingFormulaOutputs() {
        CharacterProfile profile = sampleProfile(7, 2, 5, 11);

        CharacterCombatStatCalculator.CombatStats stats = calculator.calculate(profile);

        assertEquals(42, stats.maxHp());
        assertEquals(4, stats.maxAp());
        assertEquals(10, stats.attackPower());
        assertEquals(10, stats.healPower());
    }

    @Test
    @DisplayName("회귀 예시: physical=6, technique=3, sense=4, willpower=12 인 경우 고정 결과를 반환한다")
    void regressionExampleReadableConcreteNumbers() {
        CharacterProfile profile = sampleProfile(6, 3, 4, 12);

        CharacterCombatStatCalculator.CombatStats stats = calculator.calculate(profile);

        assertEquals(41, stats.maxHp());
        assertEquals(5, stats.maxAp());
        assertEquals(10, stats.attackPower());
        assertEquals(9, stats.healPower());
    }

    private static CharacterProfile sampleProfile(int physical, int technique, int sense, int willpower) {
        return CharacterProfile.builder()
                .name("test")
                .gender(CharacterGender.MALE)
                .age(20)
                .wish("wish")
                .disposition("calm")
                .oneLiner("line")
                .story("story")
                .physical(physical)
                .technique(technique)
                .sense(sense)
                .willpower(willpower)
                .build();
    }
}
