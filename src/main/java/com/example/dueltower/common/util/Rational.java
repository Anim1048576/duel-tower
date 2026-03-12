package com.example.dueltower.common.util;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 불변 분수 값 객체.
 *
 * <p>특징:
 * <ul>
 *   <li>항상 기약분수 + 양수 분모 형태로 정규화된다.</li>
 *   <li>내부 저장은 long 이지만, 연산 중 overflow는 BigInteger 기반으로 감지한다.</li>
 *   <li>곱셈/나눗셈은 교차 약분, 덧셈/뺄셈은 분모 최대공약수를 먼저 반영해 불필요하게 큰 수 생성을 줄인다.</li>
 * </ul>
 */
public final class Rational implements Comparable<Rational> {

    private static final BigInteger BI_LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    public static final Rational ZERO = new Rational(0L, 1L, true);
    public static final Rational ONE = new Rational(1L, 1L, true);

    private final long n; // numerator
    private final long d; // denominator (always > 0)

    public Rational(long numerator, long denominator) {
        long[] normalized = normalize(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        this.n = normalized[0];
        this.d = normalized[1];
    }

    private Rational(long numerator, long denominator, boolean normalized) {
        this.n = numerator;
        this.d = denominator;
    }

    public static Rational of(long numerator, long denominator) {
        if (numerator == 0L) {
            if (denominator == 0L) {
                throw new ArithmeticException("denominator is 0");
            }
            return ZERO;
        }
        if (numerator == 1L && denominator == 1L) {
            return ONE;
        }
        return new Rational(numerator, denominator);
    }

    public static Rational of(long wholeNumber) {
        return (wholeNumber == 0L) ? ZERO : (wholeNumber == 1L ? ONE : new Rational(wholeNumber, 1L));
    }

    public static Rational min(Rational a, Rational b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static Rational min(Rational first, Rational... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");

        Rational result = first;
        for (int i = 0; i < rest.length; i++) {
            Rational value = Objects.requireNonNull(rest[i], "rest[" + i + "]");
            if (value.compareTo(result) < 0) {
                result = value;
            }
        }
        return result;
    }

    public static Rational max(Rational a, Rational b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static Rational max(Rational first, Rational... rest) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(rest, "rest");

        Rational result = first;
        for (int i = 0; i < rest.length; i++) {
            Rational value = Objects.requireNonNull(rest[i], "rest[" + i + "]");
            if (value.compareTo(result) > 0) {
                result = value;
            }
        }
        return result;
    }

    public Rational min(Rational other) {
        return min(this, other);
    }

    public Rational max(Rational other) {
        return max(this, other);
    }

    private static long[] normalize(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("denominator is 0");
        }

        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }

        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);

        long normalizedN = toLongExact(numerator, "numerator overflow");
        long normalizedD = toLongExact(denominator, "denominator overflow");
        return new long[]{normalizedN, normalizedD};
    }

    private static Rational fromBigInteger(BigInteger numerator, BigInteger denominator) {
        long[] normalized = normalize(numerator, denominator);
        if (normalized[0] == 0L) {
            return ZERO;
        }
        if (normalized[0] == 1L && normalized[1] == 1L) {
            return ONE;
        }
        return new Rational(normalized[0], normalized[1], true);
    }

    private static long toLongExact(BigInteger value, String message) {
        if (value.compareTo(BI_LONG_MIN) < 0 || value.compareTo(BI_LONG_MAX) > 0) {
            throw new ArithmeticException(message + ": " + value);
        }
        return value.longValue();
    }

    private static BigInteger bi(long value) {
        return BigInteger.valueOf(value);
    }

    private static long gcdPositive(long a, long b) {
        if (a < 0L || b < 0L) {
            throw new IllegalArgumentException("gcdPositive requires non-negative inputs");
        }
        while (b != 0L) {
            long tmp = b;
            b = a % b;
            a = tmp;
        }
        return a;
    }

    /**
     * gcd(|signed|, positive).
     * positive 는 반드시 0보다 커야 한다.
     * Long.MIN_VALUE 도 안전하게 처리하기 위해 mod 기반으로 축약한다.
     */
    private static long gcdAbsWithPositive(long signed, long positive) {
        if (positive <= 0L) {
            throw new IllegalArgumentException("positive must be > 0");
        }
        long mod = signed % positive;
        if (mod < 0L) {
            mod = -mod;
        }
        return gcdPositive(mod, positive);
    }

    /**
     * 덧셈 최적화:
     * a/b + c/d = (a*(d/g) + c*(b/g)) / ((b/g)*d), where g = gcd(b, d)
     */
    public Rational add(Rational other) {
        Objects.requireNonNull(other, "other");

        if (this.n == 0L) return other;
        if (other.n == 0L) return this;

        long g = gcdPositive(this.d, other.d);
        long leftMul = other.d / g;
        long rightMul = this.d / g;

        BigInteger numerator = bi(this.n).multiply(bi(leftMul))
                .add(bi(other.n).multiply(bi(rightMul)));
        BigInteger denominator = bi(rightMul).multiply(bi(other.d));
        return fromBigInteger(numerator, denominator);
    }

    public Rational add(long value) {
        if (value == 0L) return this;
        BigInteger numerator = bi(this.n).add(bi(value).multiply(bi(this.d)));
        return fromBigInteger(numerator, bi(this.d));
    }

    /**
     * 뺄셈 최적화:
     * a/b - c/d = (a*(d/g) - c*(b/g)) / ((b/g)*d), where g = gcd(b, d)
     */
    public Rational subtract(Rational other) {
        Objects.requireNonNull(other, "other");

        if (other.n == 0L) return this;
        if (this.n == 0L) return other.negate();

        long g = gcdPositive(this.d, other.d);
        long leftMul = other.d / g;
        long rightMul = this.d / g;

        BigInteger numerator = bi(this.n).multiply(bi(leftMul))
                .subtract(bi(other.n).multiply(bi(rightMul)));
        BigInteger denominator = bi(rightMul).multiply(bi(other.d));
        return fromBigInteger(numerator, denominator);
    }

    public Rational subtract(long value) {
        if (value == 0L) return this;
        BigInteger numerator = bi(this.n).subtract(bi(value).multiply(bi(this.d)));
        return fromBigInteger(numerator, bi(this.d));
    }

    /**
     * 곱셈 최적화:
     * (a/b) * (c/d) 에서 a 와 d, c 와 b 를 먼저 교차 약분한다.
     */
    public Rational multiply(Rational other) {
        Objects.requireNonNull(other, "other");

        if (this.n == 0L || other.n == 0L) return ZERO;
        if (this == ONE) return other;
        if (other == ONE) return this;

        long g1 = gcdAbsWithPositive(this.n, other.d);
        long g2 = gcdAbsWithPositive(other.n, this.d);

        long leftN = this.n / g1;
        long rightD = other.d / g1;
        long rightN = other.n / g2;
        long leftD = this.d / g2;

        BigInteger numerator = bi(leftN).multiply(bi(rightN));
        BigInteger denominator = bi(leftD).multiply(bi(rightD));
        return fromBigInteger(numerator, denominator);
    }

    public Rational multiply(long value) {
        if (value == 0L || this.n == 0L) return ZERO;
        if (value == 1L) return this;

        BigInteger g = bi(this.d).gcd(bi(value).abs());
        BigInteger reducedValue = bi(value).divide(g);
        BigInteger reducedDenominator = bi(this.d).divide(g);

        BigInteger numerator = bi(this.n).multiply(reducedValue);
        return fromBigInteger(numerator, reducedDenominator);
    }

    /**
     * 나눗셈 최적화:
     * (a/b) / (c/d) = (a*d) / (b*c) 에서 a 와 c, d 와 b 를 먼저 약분한다.
     */
    public Rational divide(Rational other) {
        Objects.requireNonNull(other, "other");
        if (other.n == 0L) {
            throw new ArithmeticException("divide by zero");
        }
        if (this.n == 0L) {
            return ZERO;
        }
        if (other.equals(ONE)) {
            return this;
        }

        BigInteger a = bi(this.n);
        BigInteger b = bi(this.d);
        BigInteger c = bi(other.n);
        BigInteger d = bi(other.d);

        BigInteger g1 = a.abs().gcd(c.abs());
        if (!BigInteger.ONE.equals(g1)) {
            a = a.divide(g1);
            c = c.divide(g1);
        }

        BigInteger g2 = b.gcd(d);
        if (!BigInteger.ONE.equals(g2)) {
            b = b.divide(g2);
            d = d.divide(g2);
        }

        return fromBigInteger(a.multiply(d), b.multiply(c));
    }

    public Rational divide(long value) {
        if (value == 0L) {
            throw new ArithmeticException("divide by zero");
        }
        if (this.n == 0L) {
            return ZERO;
        }
        if (value == 1L) {
            return this;
        }

        BigInteger numerator = bi(this.n);
        BigInteger denominator = bi(this.d);
        BigInteger divisor = bi(value);

        BigInteger g = numerator.abs().gcd(divisor.abs());
        if (!BigInteger.ONE.equals(g)) {
            numerator = numerator.divide(g);
            divisor = divisor.divide(g);
        }

        return fromBigInteger(numerator, denominator.multiply(divisor));
    }

    public Rational reciprocal() {
        if (this.n == 0L) {
            throw new ArithmeticException("divide by zero");
        }
        return new Rational(this.d, this.n);
    }

    public Rational negate() {
        return fromBigInteger(bi(this.n).negate(), bi(this.d));
    }

    public Rational abs() {
        return this.n >= 0L ? this : this.negate();
    }

    public boolean isZero() {
        return this.n == 0L;
    }

    public boolean isInteger() {
        return this.d == 1L;
    }

    public int signum() {
        return Long.compare(this.n, 0L);
    }

    public long toLongExact() {
        if (this.d != 1L) {
            throw new ArithmeticException("not an integer: " + this);
        }
        return this.n;
    }

    public double toDouble() {
        return (double) this.n / (double) this.d;
    }

    public long getNumerator() {
        return this.n;
    }

    public long getDenominator() {
        return this.d;
    }

    @Override
    public int compareTo(Rational other) {
        Objects.requireNonNull(other, "other");
        BigInteger left = bi(this.n).multiply(bi(other.d));
        BigInteger right = bi(other.n).multiply(bi(this.d));
        return left.compareTo(right);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Rational other)) return false;
        return this.n == other.n && this.d == other.d;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.n, this.d);
    }

    @Override
    public String toString() {
        if (this.n == 0L) {
            return "0";
        }
        return (this.d == 1L) ? Long.toString(this.n) : (this.n + "/" + this.d);
    }
}
