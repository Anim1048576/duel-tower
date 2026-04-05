package com.example.dueltower.config;

public record GameRules(
        int deckSize,
        int maxDeckCopies,
        int maxDeckEditChanges,
        int maxPassives,
        int maxOwnedCards,
        int handLimit,
        int fieldLimit,
        int combatStartDrawCount,
        int turnStartBonusDrawHandThreshold,
        int turnStartDrawBelowThreshold,
        int turnStartDrawAtOrAboveThreshold
) {
    public GameRules {
        requirePositive(deckSize, "deckSize");
        requirePositive(maxDeckCopies, "maxDeckCopies");
        requirePositive(maxDeckEditChanges, "maxDeckEditChanges");
        requirePositive(maxPassives, "maxPassives");
        requirePositive(maxOwnedCards, "maxOwnedCards");
        requirePositive(handLimit, "handLimit");
        requirePositive(fieldLimit, "fieldLimit");
        requirePositive(combatStartDrawCount, "combatStartDrawCount");
        requireNonNegative(turnStartBonusDrawHandThreshold, "turnStartBonusDrawHandThreshold");
        requirePositive(turnStartDrawBelowThreshold, "turnStartDrawBelowThreshold");
        requirePositive(turnStartDrawAtOrAboveThreshold, "turnStartDrawAtOrAboveThreshold");
    }

    public static GameRules defaults() {
        return new GameRules(12, 3, 2, 2, 20, 6, 5, 4, 4, 2, 1);
    }

    public int turnStartDrawCount(int handSize) {
        return handSize < turnStartBonusDrawHandThreshold
                ? turnStartDrawBelowThreshold
                : turnStartDrawAtOrAboveThreshold;
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
