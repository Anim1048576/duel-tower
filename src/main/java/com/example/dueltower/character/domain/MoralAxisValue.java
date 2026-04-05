package com.example.dueltower.character.domain;

import java.util.Arrays;

public enum MoralAxisValue {
    GOOD("선"),
    NEUTRAL("중용"),
    EVIL("악");

    private final String label;

    MoralAxisValue(String label) {
        this.label = label;
    }

    public static boolean matches(String value) {
        return Arrays.stream(values()).anyMatch(v -> v.label.equals(value));
    }
}
