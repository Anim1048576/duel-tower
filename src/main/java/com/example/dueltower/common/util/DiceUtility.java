package com.example.dueltower.common.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 주사위 표기 유틸 */
public class DiceUtility {

    /**
     * 지원 표기 예시
     * - "1D6", "2d8", "d20" (개수 생략 시 1로 처리)
     * - "3d6+2", "4D10 - 1" (보정치 +/- 지원)
     */
    private static final Pattern DICE_PATTERN =
            Pattern.compile("^\\s*(\\d*)\\s*[dD]\\s*(\\d+)\\s*([+-]\\s*\\d+)?\\s*$");

    public record DiceSpec(int count, int sides, int modifier) {
        public DiceSpec {
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
            if (sides <= 0) throw new IllegalArgumentException("sides must be positive");
        }
    }

    /**
     * "2d6+1" 같은 표기를 파싱해 DiceSpec으로 반환.
     */
    public static DiceSpec parseDice(String notation) {
        if (notation == null || notation.isBlank()) {
            throw new IllegalArgumentException("notation is blank");
        }

        Matcher m = DICE_PATTERN.matcher(notation);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid dice notation: " + notation);
        }

        String cRaw = m.group(1);
        String sRaw = m.group(2);
        String modRaw = m.group(3);

        int count = (cRaw == null || cRaw.isBlank()) ? 1 : Integer.parseInt(cRaw);
        int sides = Integer.parseInt(sRaw);

        int modifier = 0;
        if (modRaw != null && !modRaw.isBlank()) {
            modifier = Integer.parseInt(modRaw.replace(" ", ""));
        }

        return new DiceSpec(count, sides, modifier);
    }

    /**
     * count개의 sides면체 주사위를 굴려 합계를 반환.
     */
    public static int rollDice(int count, int sides, Random rng) {
        if (rng == null) throw new IllegalArgumentException("rng is null");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        if (sides <= 0) throw new IllegalArgumentException("sides must be positive");

        long sum = 0L;
        for (int i = 0; i < count; i++) {
            // 1..sides
            sum += (long) (rng.nextInt(sides) + 1);
        }
        return safeToInt(sum);
    }

    /**
     * "2d6+1" 같은 표기를 실제로 굴려 결과를 반환.
     */
    public static int rollDice(String notation, Random rng) {
        DiceSpec spec = parseDice(notation);
        long total = (long) rollDice(spec.count(), spec.sides(), rng) + (long) spec.modifier();
        return safeToInt(total);
    }

    /**
     * ThreadLocalRandom을 사용해서 굴림.
     */
    public static int rollDice(String notation) {
        return rollDice(notation, ThreadLocalRandom.current());
    }

    /**
     * 기대값(평균). 예: 1d6 -> 3.5, 2d6+1 -> 8
     */
    public static Rational expectedDice(String notation) {
        DiceSpec spec = parseDice(notation);

        // E[1..s] = (s+1)/2
        // E[n d s] = n*(s+1)/2
        Rational base = new Rational((long) spec.count() * ((long) spec.sides() + 1L), 2L);
        return base.add(spec.modifier());
    }

    public static int minDice(String notation) {
        DiceSpec spec = parseDice(notation);
        long min = (long) spec.count() + (long) spec.modifier();
        return safeToInt(min);
    }

    public static int maxDice(String notation) {
        DiceSpec spec = parseDice(notation);
        long max = (long) spec.count() * (long) spec.sides() + (long) spec.modifier();
        return safeToInt(max);
    }

    private static int safeToInt(long v) {
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new ArithmeticException("overflow: " + v);
        }
        return (int) v;
    }
}
