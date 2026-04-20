package com.example.dueltower.character.domain;

import java.util.Set;

public final class HiddenTraitIds {

    public static final String HUMANOID = "HT001";
    public static final String HUMAN = "HT002";
    public static final String NON_HUMANOID = "HT003";
    public static final String NON_HUMAN = "HT004";
    public static final String BEASTFOLK = "HT005";
    public static final String WITCH = "HT006";
    public static final String DEMON = "HT007";
    public static final String SIN = "HT008";
    public static final String HYBRID = "HT009";
    public static final String OUTER_ENTITY = "HT010";

    private static final Set<String> ALL = Set.of(
            HUMANOID,
            HUMAN,
            NON_HUMANOID,
            NON_HUMAN,
            BEASTFOLK,
            WITCH,
            DEMON,
            SIN,
            HYBRID,
            OUTER_ENTITY
    );

    private HiddenTraitIds() {
    }

    public static boolean isSupported(String id) {
        return ALL.contains(id);
    }

    public static Set<String> all() {
        return ALL;
    }
}
