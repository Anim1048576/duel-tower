package com.example.dueltower.common.util;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseUtility {

    /*
     * =====================================
     * ||          한글 정규화 유틸          ||
     * =====================================
     */

    private static final int BASE = 0xAC00; // 가
    private static final int LAST = 0xD7A3; // 힣

    private static final String[] CHO = {
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };
    private static final String[] JUNG = {
            "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ", "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    };
    private static final String[] JONG = {
            "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ",
            "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    /**
     * "사과" -> "ㅅㅏㄱㅘ", "삭제" -> "ㅅㅏㄱㅈㅔ"
     */
    public static String toKey(String s) {
        if (s == null || s.isBlank()) return "";

        // StringBuilder를 통해 문자열 합치기
        // "내용" + "내용" 문자열 합치기랑 똑같음
        StringBuilder out = new StringBuilder(s.length() * 3);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // 공백 제거
            if (Character.isWhitespace(ch)) continue;

            // 한글 음절을 잘라서 저장
            if (ch >= BASE && ch <= LAST) {
                int code = ch - BASE;
                int l = code / (21 * 28);        // 초성
                int v = (code % (21 * 28)) / 28; // 중성
                int t = code % 28;               // 종성

                out.append(CHO[l]).append(JUNG[v]);
                if (t != 0) out.append(JONG[t]);
                continue;
            }

            // 영문 소문자, 숫자는 유지, 그 외는 버림
            if (Character.isAlphabetic(ch)) out.append(Character.toLowerCase(ch));
            else if (Character.isDigit(ch)) out.append(ch);
        }
        return out.toString();
    }

    /**
     * "사과" -> "ㅅㄱ", "삭제" -> "ㅅㅈ"
     */
    public static String toChosungKey(String s) {
        if (s == null || s.isBlank()) return "";

        // StringBuilder를 통해 문자열 합치기
        // "내용" + "내용" 문자열 합치기랑 똑같음
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (Character.isWhitespace(ch)) continue;

            // 초성 음절을 잘라서 저장
            if (ch >= BASE && ch <= LAST) {
                int code = ch - BASE;
                int l = code / (21 * 28);
                out.append(CHO[l]);
            }
        }
        return out.toString();
    }

    /**
     * 들어온 겹자음을 분해
     */
    public static String jaeumCutter(char ch) {
        switch (ch) {
            case 'ㄳ':
                return "ㄱㅅ";
            case 'ㄵ':
                return "ㄴㅈ";
            case 'ㄶ':
                return "ㄴㅎ";
            case 'ㄺ':
                return "ㄹㄱ";
            case 'ㄻ':
                return "ㄹㅁ";
            case 'ㄼ':
                return "ㄹㅂ";
            case 'ㄽ':
                return "ㄹㅅ";
            case 'ㄾ':
                return "ㄹㅌ";
            case 'ㄿ':
                return "ㄹㅍ";
            case 'ㅀ':
                return "ㄹㅎ";
            case 'ㅄ':
                return "ㅂㅅ";
            default:
                return String.valueOf(ch);
        }
    }

    /**
     * 문자열에서 겹자음을 전부 분해 후 초성만 남김, 나머지는 전부 버림
     */
    public static String jaeumBreaker(String s) {
        if (s == null || s.isBlank()) return "";

        StringBuilder out = new StringBuilder(s.length() * 2);
        for (char ch : s.toCharArray()) {
            if (Character.isWhitespace(ch)) continue;
            for (char c : jaeumCutter(ch).toCharArray()) {
                if (isChosungJamo(c)) out.append(c);  // 초성 19개만 통과
            }
        }
        return out.toString();
    }

    /**
     * 들어온 char값이 초성 자모 배열의 값과 일치하는지 확인
     */
    private static boolean isChosungJamo(char c) {
        for (String s : CHO) { // 초성에 들어가있는지 검사
            if (s.charAt(0) == c) return true;
        }
        return false;
    }

    /**
     * 사용자가 'ㅅㄱ' 같이 초성만 입력했는지 판별
     */
    public static boolean isChosungQuery(String s) {
        if (s == null || s.isBlank()) return false;

        for (char ch : s.toCharArray()) {
            if (Character.isWhitespace(ch)) continue;
            for (char c : jaeumCutter(ch).toCharArray())
                if (!isChosungJamo(c)) return false;
        }
        return true;
    }


    /*
     * =====================================
     * ||          시간 정규화 유틸          ||
     * =====================================
     */

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    public static String formatTimestamp(Timestamp ts) {
        return formatTimestamp(ts, DEFAULT_PATTERN, DEFAULT_ZONE);
    }

    public static String formatTimestamp(Timestamp ts, String pattern) {
        return formatTimestamp(ts, pattern, DEFAULT_ZONE);
    }

    public static String formatTimestamp(Timestamp ts, String pattern, ZoneId zoneId) {
        // null 보정
        if (ts == null) return "null";
        String p = (pattern == null || pattern.isBlank()) ? DEFAULT_PATTERN : pattern;
        ZoneId z = (zoneId == null) ? DEFAULT_ZONE : zoneId;

        // Timestamp -> Instant(절대시간, UTC 기준의 '순간') -> ZonedDateTime(타임존 적용된 달력 시간)
        // - 같은 순간이라도 타임존에 따라 "표시되는 시각"이 달라짐
        ZonedDateTime zdt = ts.toInstant().atZone(z);

        // ZonedDateTime을 원하는 패턴(p)으로 문자열 포맷팅해서 반환
        return zdt.format(DateTimeFormatter.ofPattern(p));
    }


    /*
     * =====================================
     * ||          주사위 표기 유틸          ||
     * =====================================
     */

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


    /*
     * =====================================
     * ||           분수 계산 유틸           ||
     * =====================================
     */

    /**
     * n: 분자
     * d: 분모
     * long 값을 기반으로 동작하므로 큰 값 / 연속 연산에는 적합하지 않음에 주의.
     */
    public static final class Rational implements Comparable<Rational> {

        private final long n; // numerator: 분자
        private final long d; // denominator : 분모

        public Rational(long numerator, long denominator) {
            if (denominator == 0L) throw new ArithmeticException("denominator is 0");

            if (denominator < 0L) {
                numerator *= -1L;
                denominator *= -1L;
            }

            long g = gcd(Math.abs(numerator), denominator);
            this.n = numerator/g;
            this.d = denominator/g;
        }

        /** 유클리드 호제법을 이용한 최대공약수 찾기 메서드 */
        private static long gcd(long a, long b) {
            while (b != 0) {
                long temp = b;
                b = a % b;
                a = temp;
            }
            return a;
        }

        /** 덧셈: a/b + c/d = (a*d + c*b) / (b*d) */
        public Rational add(Rational rational) {
            return new Rational(
                    (this.n * rational.d) + (rational.n * this.d),
                    this.d * rational.d
            );
        }
        public Rational add(long num) {
            return new Rational(
                    this.n + (num * this.d),
                    this.d
            );
        }

        /** 뺄셈: a/b - c/d = (a*d - c*b) / (b*d) */
        public Rational subtract(Rational rational) {
            return new Rational(
                    (this.n * rational.d) - (rational.n * this.d),
                    this.d * rational.d
            );
        }
        public Rational subtract(long num) {
            return new Rational(
                    this.n - (num * this.d),
                    this.d
            );
        }

        /** 곱셈: (a/b) * (c/d) = (a*c) / (b*d) */
        public Rational multiply(Rational rational) {
            return new Rational(
                    this.n * rational.n,
                    this.d * rational.d
            );
        }
        public Rational multiply(long num) {
            return new Rational(
                    this.n * num,
                    this.d
            );
        }

        /** 나눗셈: (a/b) / (c/d) = (a/b) * (d/c) = (a*d) / (b*c) */
        public Rational divide(Rational rational) {
            if (rational.n == 0L) throw new ArithmeticException("divide by zero");
            return new Rational(
                    this.n * rational.d,
                    this.d * rational.n
            );
        }
        public Rational divide(long num) {
            if (num == 0L) throw new ArithmeticException("divide by zero");
            return new Rational(
                    this.n,
                    this.d * num
            );
        }

        /** 부호 반전 */
        public Rational negate() {
            return this.multiply(-1L);
        }

        /** double값으로 반환 */
        public double toDouble() {
            return (double) this.n / (double) this.d;
        }

        public long getNumerator() { return this.n; }
        public long getDenominator() { return this.d; }

        @Override
        public int compareTo(Rational rational) {
            BigInteger left  = BigInteger.valueOf(this.n).multiply(BigInteger.valueOf(rational.d));
            BigInteger right = BigInteger.valueOf(rational.n).multiply(BigInteger.valueOf(this.d));
            return left.compareTo(right);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rational other)) return false;
            return this.n == other.n && this.d == other.d;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(n, d);
        }

        @Override
        public String toString() {
            if (this.n == 0L) return "0";
            return (this.d == 1L) ? Long.toString(this.n) : (this.n + "/" + this.d);
        }
    }
}
