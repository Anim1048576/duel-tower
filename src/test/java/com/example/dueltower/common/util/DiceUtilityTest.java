package com.example.dueltower.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiceUtilityTest {

    @Test
    @DisplayName("parseDice: 지원 표기를 명시적으로 파싱한다")
    void parseDiceAcceptsSupportedNotations() {
        DiceUtility.DiceSpec d20 = DiceUtility.parseDice("d20");
        assertEquals(1, d20.count());
        assertEquals(20, d20.sides());
        assertEquals(0, d20.modifier());

        DiceUtility.DiceSpec oneD6 = DiceUtility.parseDice("1d6");
        assertEquals(1, oneD6.count());
        assertEquals(6, oneD6.sides());
        assertEquals(0, oneD6.modifier());

        DiceUtility.DiceSpec twoD8 = DiceUtility.parseDice("2D8");
        assertEquals(2, twoD8.count());
        assertEquals(8, twoD8.sides());
        assertEquals(0, twoD8.modifier());

        DiceUtility.DiceSpec threeD6Plus2 = DiceUtility.parseDice("3d6+2");
        assertEquals(3, threeD6Plus2.count());
        assertEquals(6, threeD6Plus2.sides());
        assertEquals(2, threeD6Plus2.modifier());

        DiceUtility.DiceSpec fourD10Minus1 = DiceUtility.parseDice("4D10 - 1");
        assertEquals(4, fourD10Minus1.count());
        assertEquals(10, fourD10Minus1.sides());
        assertEquals(-1, fourD10Minus1.modifier());
    }

    @Test
    @DisplayName("parseDice: 앞뒤 공백을 제거해 파싱한다")
    void parseDiceTrimsSurroundingWhitespace() {
        DiceUtility.DiceSpec spec = DiceUtility.parseDice("   2d6+3   ");

        assertEquals(2, spec.count());
        assertEquals(6, spec.sides());
        assertEquals(3, spec.modifier());
    }

    @Test
    @DisplayName("parseDice: null 또는 blank 입력을 거부한다")
    void parseDiceRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice(null));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice(""));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("   "));
    }

    @Test
    @DisplayName("parseDice: 잘못된 표기를 거부한다")
    void parseDiceRejectsInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("2x6"));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("d"));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("2d6+"));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("2d-6"));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.parseDice("0d6"));
    }

    @Test
    @DisplayName("minDice: count와 modifier를 반영한 최소값을 반환한다")
    void minDiceReturnsExpectedValues() {
        assertEquals(1, DiceUtility.minDice("d20"));
        assertEquals(5, DiceUtility.minDice("3d6+2"));
        assertEquals(3, DiceUtility.minDice("4d4-1"));
    }

    @Test
    @DisplayName("maxDice: count*sides와 modifier를 반영한 최대값을 반환한다")
    void maxDiceReturnsExpectedValues() {
        assertEquals(20, DiceUtility.maxDice("d20"));
        assertEquals(20, DiceUtility.maxDice("3d6+2"));
        assertEquals(15, DiceUtility.maxDice("4d4-1"));
    }

    @Test
    @DisplayName("expectedDice: 읽기 쉬운 예시에서 Rational 기대값을 반환한다")
    void expectedDiceReturnsExpectedRationalValues() {
        assertEquals(Rational.of(7, 2), DiceUtility.expectedDice("d6"));
        assertEquals(Rational.of(8), DiceUtility.expectedDice("2d6+1"));
        assertEquals(Rational.of(17, 2), DiceUtility.expectedDice("3d4+1"));
    }

    @Test
    @DisplayName("rollDice(count, sides, rng): count/sides 양수 제약을 강제한다")
    void rollDiceWithCountAndSidesEnforcesPositiveInputs() {
        Random rng = new Random(7L);

        assertThrows(IllegalArgumentException.class, () -> DiceUtility.rollDice(0, 6, rng));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.rollDice(2, 0, rng));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.rollDice(-1, 6, rng));
        assertThrows(IllegalArgumentException.class, () -> DiceUtility.rollDice(2, -6, rng));
    }

    @Test
    @DisplayName("rollDice(notation, rng): 같은 시드의 Random이면 결정적 결과를 반환한다")
    void rollDiceNotationIsDeterministicWithSeededRandom() {
        Random first = new Random(12345L);
        Random second = new Random(12345L);

        int firstRoll = DiceUtility.rollDice("3d6+2", first);
        int secondRoll = DiceUtility.rollDice("3d6+2", second);

        assertEquals(firstRoll, secondRoll);
    }

    @Test
    @DisplayName("maxDice: int 범위를 넘는 경우 ArithmeticException을 던진다")
    void maxDiceThrowsWhenIntOverflowWouldOccur() {
        assertThrows(ArithmeticException.class, () -> DiceUtility.maxDice("2147483647d2"));
    }
}
