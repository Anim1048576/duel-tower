package com.example.dueltower.character.domain;

import java.util.Arrays;

public enum OrderAxisValue {
    LAWFUL("질서"),
    NEUTRAL("중립"),
    CHAOTIC("혼돈");

    private final String label;

    OrderAxisValue(String label) {
        this.label = label;
    }

    public static boolean matches(String value) {
        return Arrays.stream(values()).anyMatch(v -> v.label.equals(value));
    }
}
