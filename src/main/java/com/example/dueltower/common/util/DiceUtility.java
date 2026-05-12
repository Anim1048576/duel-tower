package com.example.dueltower.common.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 주사위 표기 유틸 */
public class DiceUtility {

    /**
     * 기존 단일 주사위 표기.
     * - "1D6", "2d8", "d20" (개수 생략 시 1로 처리)
     * - "3d6+2", "4D10 - 1" (보정치 +/- 지원)
     */
    private static final Pattern DICE_PATTERN =
            Pattern.compile("^\\s*(\\d*)\\s*[dD]\\s*(\\d+)\\s*([+-]\\s*\\d+)?\\s*$");

    /** 기존 단일 주사위식의 파싱 결과. */
    public record DiceSpec(int count, int sides, int modifier) {
        public DiceSpec {
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
            if (sides <= 0) throw new IllegalArgumentException("sides must be positive");
        }
    }

    /** 여러 항으로 구성된 주사위 표현식. */
    public record DiceExpression(List<SignedTerm> terms) {
        public DiceExpression {
            if (terms == null || terms.isEmpty()) {
                throw new IllegalArgumentException("terms must not be empty");
            }
            terms = List.copyOf(terms);
        }
    }

    /** 표현식 안의 부호가 붙은 항. sign은 1 또는 -1이다. */
    public record SignedTerm(int sign, DiceTerm term) {
        public SignedTerm {
            if (sign != 1 && sign != -1) {
                throw new IllegalArgumentException("sign must be 1 or -1");
            }
            if (term == null) {
                throw new IllegalArgumentException("term is null");
            }
        }
    }

    /** 표현식 항의 공통 타입. 주사위 항과 상수 항이 구현한다. */
    public interface DiceTerm {}

    /** count개의 sides면체 주사위와 선택 규칙을 나타내는 항. */
    public record DiceRollTerm(int count, int sides, DiceSelector selector) implements DiceTerm {
        public DiceRollTerm {
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
            if (sides <= 0) throw new IllegalArgumentException("sides must be positive");
            if (selector != null) {
                selector.validateFor(count);
            }
        }
    }

    /** 정수 상수 항. */
    public record ConstantTerm(int value) implements DiceTerm {
    }

    /** 주사위 굴림값 중 일부를 유지하거나 제거하는 선택 규칙. */
    public record DiceSelector(DiceSelectorKind kind, int amount) {
        public DiceSelector {
            if (kind == null) {
                throw new IllegalArgumentException("selector kind is required");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("selector amount must be positive");
            }
        }

        /**
         * selector amount가 주사위 개수에 대해 유효한지 검증한다.
         *
         * @param count 선택 규칙이 적용될 주사위 개수
         */
        private void validateFor(int count) {
            switch (kind) {
                case KH, KL -> {
                    if (amount > count) {
                        throw new IllegalArgumentException(kind + " amount must be <= count");
                    }
                }
                case DH, DL -> {
                    if (amount >= count) {
                        throw new IllegalArgumentException(kind + " amount must be < count");
                    }
                }
            }
        }
    }

    /** 주사위 선택 방식. KH/KL은 유지, DH/DL은 제거를 의미한다. */
    public enum DiceSelectorKind {
        KH,
        KL,
        DH,
        DL
    }

    /**
     * "2d6+1" 같은 기존 단일 표기를 파싱해 DiceSpec으로 반환.
     *
     * @param notation 단일 주사위 표기. 예: d20, 3d6+2, 4D10 - 1
     * @return 주사위 개수, 면 수, 보정치를 담은 DiceSpec
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
     * 여러 주사위 항과 상수 항으로 구성된 표현식을 파싱한다.
     *
     * <p>예: 1D6+2D4+3, 4D6KH2, 3D6KH2+2D4MAX+1</p>
     *
     * @param notation 주사위 표현식 문자열
     * @return 부호가 붙은 항 목록으로 구성된 DiceExpression
     */
    public static DiceExpression parseDiceExpression(String notation) {
        if (notation == null || notation.isBlank()) {
            throw new IllegalArgumentException("notation is blank");
        }

        String source = notation.replaceAll("\\s+", "");
        if (source.isBlank()) {
            throw new IllegalArgumentException("notation is blank");
        }

        List<SignedTerm> terms = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            int sign = 1;
            char current = source.charAt(index);
            if (current == '+' || current == '-') {
                sign = current == '-' ? -1 : 1;
                index++;
                if (index >= source.length()) {
                    throw invalidNotation(notation);
                }
            } else if (!terms.isEmpty()) {
                throw invalidNotation(notation);
            }

            ParseTermResult parsed = parseTerm(source, index, notation);
            terms.add(new SignedTerm(sign, parsed.term()));
            index = parsed.nextIndex();
        }

        return new DiceExpression(terms);
    }

    /**
     * count개의 sides면체 주사위를 굴려 합계를 반환.
     *
     * @param count 굴릴 주사위 개수
     * @param sides 각 주사위의 면 수
     * @param rng 난수 생성기
     * @return 굴림값의 합
     */
    public static int rollDice(int count, int sides, Random rng) {
        if (rng == null) throw new IllegalArgumentException("rng is null");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        if (sides <= 0) throw new IllegalArgumentException("sides must be positive");

        long sum = 0L;
        for (int i = 0; i < count; i++) {
            sum += (long) (rng.nextInt(sides) + 1);
        }
        return safeToInt(sum);
    }

    /**
     * 주사위 표현식을 파싱한 뒤 전달된 난수 생성기로 실제 굴림 결과를 계산한다.
     *
     * @param notation 주사위 표현식 문자열
     * @param rng 난수 생성기. 같은 seed의 Random을 넘기면 같은 결과를 재현할 수 있다.
     * @return 표현식 전체의 굴림 결과
     */
    public static int rollDice(String notation, Random rng) {
        if (rng == null) throw new IllegalArgumentException("rng is null");
        DiceExpression expression = parseDiceExpression(notation);
        long total = 0L;
        for (SignedTerm signedTerm : expression.terms()) {
            total += (long) signedTerm.sign() * (long) rollTerm(signedTerm.term(), rng);
        }
        return safeToInt(total);
    }

    /**
     * ThreadLocalRandom을 사용해 주사위 표현식을 굴린다.
     *
     * @param notation 주사위 표현식 문자열
     * @return 표현식 전체의 굴림 결과
     */
    public static int rollDice(String notation) {
        return rollDice(notation, ThreadLocalRandom.current());
    }

    /**
     * 주사위 표현식의 기대값을 Rational로 계산한다.
     *
     * <p>selector가 없는 일반 합산 주사위와 상수 항만 지원하며, KH/KL/DH/DL/MAX/MIN이 포함되면
     * UnsupportedOperationException을 던진다.</p>
     *
     * @param notation 주사위 표현식 문자열
     * @return 표현식의 기대값
     */
    public static Rational expectedDice(String notation) {
        DiceExpression expression = parseDiceExpression(notation);
        Rational total = Rational.ZERO;
        for (SignedTerm signedTerm : expression.terms()) {
            Rational expected = expectedTerm(signedTerm.term());
            total = signedTerm.sign() == 1 ? total.add(expected) : total.subtract(expected);
        }
        return total;
    }

    /**
     * 주사위 표현식이 가질 수 있는 최소 결과를 계산한다.
     *
     * @param notation 주사위 표현식 문자열
     * @return 가능한 최소값
     */
    public static int minDice(String notation) {
        DiceExpression expression = parseDiceExpression(notation);
        long total = 0L;
        for (SignedTerm signedTerm : expression.terms()) {
            int value = signedTerm.sign() == 1
                    ? minTerm(signedTerm.term())
                    : maxTerm(signedTerm.term());
            total += (long) signedTerm.sign() * (long) value;
        }
        return safeToInt(total);
    }

    /**
     * 주사위 표현식이 가질 수 있는 최대 결과를 계산한다.
     *
     * @param notation 주사위 표현식 문자열
     * @return 가능한 최대값
     */
    public static int maxDice(String notation) {
        DiceExpression expression = parseDiceExpression(notation);
        long total = 0L;
        for (SignedTerm signedTerm : expression.terms()) {
            int value = signedTerm.sign() == 1
                    ? maxTerm(signedTerm.term())
                    : minTerm(signedTerm.term());
            total += (long) signedTerm.sign() * (long) value;
        }
        return safeToInt(total);
    }

    /**
     * 정규화된 표현식 문자열에서 하나의 항을 읽는다.
     *
     * @param source 공백이 제거된 표현식 문자열
     * @param index 항이 시작되는 위치
     * @param originalNotation 에러 메시지에 사용할 원본 표기
     * @return 파싱된 항과 다음 읽기 위치
     */
    private static ParseTermResult parseTerm(String source, int index, String originalNotation) {
        if (source.charAt(index) == 'd' || source.charAt(index) == 'D') {
            return parseDiceTerm(source, index, "", originalNotation);
        }

        if (!Character.isDigit(source.charAt(index))) {
            throw invalidNotation(originalNotation);
        }

        int digitsStart = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        String digits = source.substring(digitsStart, index);

        if (index < source.length() && (source.charAt(index) == 'd' || source.charAt(index) == 'D')) {
            return parseDiceTerm(source, index, digits, originalNotation);
        }

        if (index < source.length() && source.charAt(index) != '+' && source.charAt(index) != '-') {
            throw invalidNotation(originalNotation);
        }

        return new ParseTermResult(new ConstantTerm(Integer.parseInt(digits)), index);
    }

    /**
     * 정규화된 표현식 문자열에서 주사위 항을 읽는다.
     *
     * @param source 공백이 제거된 표현식 문자열
     * @param diceIndex d 또는 D 문자의 위치
     * @param countRaw d 앞에서 읽은 주사위 개수 문자열. 비어 있으면 1로 처리한다.
     * @param originalNotation 에러 메시지에 사용할 원본 표기
     * @return 파싱된 주사위 항과 다음 읽기 위치
     */
    private static ParseTermResult parseDiceTerm(
            String source,
            int diceIndex,
            String countRaw,
            String originalNotation
    ) {
        int count = countRaw == null || countRaw.isBlank() ? 1 : Integer.parseInt(countRaw);
        int index = diceIndex + 1;
        if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
            throw invalidNotation(originalNotation);
        }

        int sidesStart = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        int sides = Integer.parseInt(source.substring(sidesStart, index));

        int selectorStart = index;
        while (index < source.length() && source.charAt(index) != '+' && source.charAt(index) != '-') {
            index++;
        }
        String selectorRaw = source.substring(selectorStart, index);
        DiceSelector selector = parseSelector(selectorRaw, count, originalNotation);

        return new ParseTermResult(new DiceRollTerm(count, sides, selector), index);
    }

    /**
     * 주사위 항 뒤에 붙은 selector 문자열을 파싱한다.
     *
     * @param raw selector 원문. 비어 있으면 selector 없음으로 처리한다.
     * @param count selector가 적용될 주사위 개수
     * @param originalNotation 에러 메시지에 사용할 원본 표기
     * @return 파싱된 DiceSelector 또는 null
     */
    private static DiceSelector parseSelector(String raw, int count, String originalNotation) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String selector = raw.toUpperCase();
        DiceSelector parsed;
        if ("MAX".equals(selector)) {
            parsed = new DiceSelector(DiceSelectorKind.KH, 1);
        } else if ("MIN".equals(selector)) {
            parsed = new DiceSelector(DiceSelectorKind.KL, 1);
        } else if (selector.startsWith("KH") || selector.startsWith("KL")
                || selector.startsWith("DH") || selector.startsWith("DL")) {
            String kindRaw = selector.substring(0, 2);
            String amountRaw = selector.substring(2);
            if (amountRaw.isBlank() || !amountRaw.chars().allMatch(Character::isDigit)) {
                throw invalidNotation(originalNotation);
            }
            parsed = new DiceSelector(DiceSelectorKind.valueOf(kindRaw), Integer.parseInt(amountRaw));
        } else {
            throw invalidNotation(originalNotation);
        }
        parsed.validateFor(count);
        return parsed;
    }

    /**
     * 단일 항의 굴림 결과를 계산한다.
     *
     * @param term 굴릴 항. 주사위 항이면 실제 난수를 사용하고, 상수 항이면 값을 그대로 반환한다.
     * @param rng 난수 생성기
     * @return 항의 굴림 결과
     */
    private static int rollTerm(DiceTerm term, Random rng) {
        if (term instanceof ConstantTerm constant) {
            return constant.value();
        }
        if (term instanceof DiceRollTerm dice) {
            if (dice.selector() == null) {
                return rollDice(dice.count(), dice.sides(), rng);
            }
            List<Integer> rolls = new ArrayList<>();
            for (int i = 0; i < dice.count(); i++) {
                rolls.add(rng.nextInt(dice.sides()) + 1);
            }
            return selectedSum(rolls, dice.selector());
        }
        throw new IllegalArgumentException("unsupported term: " + term);
    }

    /**
     * 주사위 굴림값 목록에 selector를 적용해 합산할 값을 고른다.
     *
     * @param rolls 실제 굴림값 목록
     * @param selector 유지하거나 제거할 값을 정하는 선택 규칙
     * @return selector 적용 후 남은 값들의 합
     */
    private static int selectedSum(List<Integer> rolls, DiceSelector selector) {
        List<Integer> sorted = new ArrayList<>(rolls);
        sorted.sort(Comparator.naturalOrder());

        return switch (selector.kind()) {
            case KH -> sumRange(sorted, sorted.size() - selector.amount(), sorted.size());
            case KL -> sumRange(sorted, 0, selector.amount());
            case DH -> sumRange(sorted, 0, sorted.size() - selector.amount());
            case DL -> sumRange(sorted, selector.amount(), sorted.size());
        };
    }

    /**
     * 정렬된 값 목록의 지정 범위를 합산한다.
     *
     * @param values 합산 대상 값 목록
     * @param fromInclusive 포함 시작 위치
     * @param toExclusive 제외 끝 위치
     * @return 지정 범위의 합
     */
    private static int sumRange(List<Integer> values, int fromInclusive, int toExclusive) {
        long sum = 0L;
        for (int i = fromInclusive; i < toExclusive; i++) {
            sum += values.get(i);
        }
        return safeToInt(sum);
    }

    /**
     * 단일 항의 기대값을 계산한다.
     *
     * @param term 기대값을 구할 항
     * @return 항의 기대값
     */
    private static Rational expectedTerm(DiceTerm term) {
        if (term instanceof ConstantTerm constant) {
            return Rational.of(constant.value());
        }
        if (term instanceof DiceRollTerm dice) {
            if (dice.selector() != null) {
                throw new UnsupportedOperationException("expectedDice does not support dice selectors yet");
            }
            return new Rational((long) dice.count() * ((long) dice.sides() + 1L), 2L);
        }
        throw new IllegalArgumentException("unsupported term: " + term);
    }

    /**
     * 단일 항이 가질 수 있는 최소값을 계산한다.
     *
     * @param term 최소값을 구할 항
     * @return 항의 최소값
     */
    private static int minTerm(DiceTerm term) {
        if (term instanceof ConstantTerm constant) {
            return constant.value();
        }
        if (term instanceof DiceRollTerm dice) {
            int kept = keptDiceCount(dice);
            return safeToInt((long) kept);
        }
        throw new IllegalArgumentException("unsupported term: " + term);
    }

    /**
     * 단일 항이 가질 수 있는 최대값을 계산한다.
     *
     * @param term 최대값을 구할 항
     * @return 항의 최대값
     */
    private static int maxTerm(DiceTerm term) {
        if (term instanceof ConstantTerm constant) {
            return constant.value();
        }
        if (term instanceof DiceRollTerm dice) {
            int kept = keptDiceCount(dice);
            return safeToInt((long) kept * (long) dice.sides());
        }
        throw new IllegalArgumentException("unsupported term: " + term);
    }

    /**
     * selector 적용 후 실제 합산에 남는 주사위 개수를 계산한다.
     *
     * @param dice selector가 적용된 주사위 항
     * @return 합산 대상 주사위 개수
     */
    private static int keptDiceCount(DiceRollTerm dice) {
        if (dice.selector() == null) {
            return dice.count();
        }
        return switch (dice.selector().kind()) {
            case KH, KL -> dice.selector().amount();
            case DH, DL -> dice.count() - dice.selector().amount();
        };
    }

    /**
     * 원본 표기를 포함한 IllegalArgumentException을 생성한다.
     *
     * @param notation 유효하지 않은 원본 주사위 표기
     * @return invalid dice notation 예외
     */
    private static IllegalArgumentException invalidNotation(String notation) {
        return new IllegalArgumentException("invalid dice notation: " + notation);
    }

    /**
     * long 계산 결과를 int로 변환하되, int 범위를 벗어나면 overflow 예외를 던진다.
     *
     * @param v int로 변환할 값
     * @return int 범위 안의 변환 결과
     */
    private static int safeToInt(long v) {
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new ArithmeticException("overflow: " + v);
        }
        return (int) v;
    }

    private record ParseTermResult(DiceTerm term, int nextIndex) {}
}
