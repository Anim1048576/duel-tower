package com.example.dueltower.character.domain;

public final class CharacterDisposition {

    private CharacterDisposition() {
    }

    public static boolean isValid(String rawDisposition) {
        if (rawDisposition == null) {
            return false;
        }

        if (!hasAxisFormat(rawDisposition)) {
            return false;
        }

        String[] parts = rawDisposition.trim().split("/");
        String orderAxis = parts[0].trim();
        String moralAxis = parts[1].trim();
        return OrderAxisValue.matches(orderAxis) && MoralAxisValue.matches(moralAxis);
    }

    public static boolean hasAxisFormat(String rawDisposition) {
        if (rawDisposition == null) {
            return false;
        }
        String[] parts = rawDisposition.trim().split("/");
        return parts.length == 2;
    }
}
