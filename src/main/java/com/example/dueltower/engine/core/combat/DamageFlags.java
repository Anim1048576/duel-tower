package com.example.dueltower.engine.core.combat;

public record DamageFlags(
        boolean ignoreEvasion,
        boolean ignoreShield,
        boolean ignoreBarrier
) {
    public static final DamageFlags NONE = new DamageFlags(false, false, false);

    public DamageFlags or(DamageFlags other) {
        if (other == null) return this;
        return new DamageFlags(
                this.ignoreEvasion || other.ignoreEvasion,
                this.ignoreShield || other.ignoreShield,
                this.ignoreBarrier || other.ignoreBarrier
        );
    }
}
