package com.example.dueltower.common.util;

import java.math.BigInteger;

/**
 * 분수 계산 유틸
 * numerator: 분자
 * denominator: 분모
 * long 값을 기반으로 동작하므로 큰 값 / 연속 연산에는 적합하지 않음에 주의.
 */
public final class Rational implements Comparable<Rational> {

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
