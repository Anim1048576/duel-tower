package com.example.dueltower.session.service;

import java.util.List;
import java.util.Map;

public record PlayerLobbyDeckEditAnalysis(
        DeckState deck,
        List<Issue> globalIssues,
        List<DeckEntryAnalysis> deckEntries,
        List<CardPoolGroupAnalysis> cardPoolGroups
) {
    public PlayerLobbyDeckEditAnalysis {
        deck = deck == null ? new DeckState(0, 0, 0, false) : deck;
        globalIssues = globalIssues == null ? List.of() : List.copyOf(globalIssues);
        deckEntries = deckEntries == null ? List.of() : List.copyOf(deckEntries);
        cardPoolGroups = cardPoolGroups == null ? List.of() : List.copyOf(cardPoolGroups);
    }

    public boolean saveAllowed() {
        return deck.saveAllowed();
    }

    public record DeckState(
            int requiredDeckSize,
            int draftDeckSize,
            int changedCardCount,
            boolean saveAllowed
    ) {}

    public record Issue(
            IssueLevel level,
            IssueCode code,
            Map<String, Object> details
    ) {
        public Issue {
            level = level == null ? IssueLevel.ERROR : level;
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public record DeckEntryAnalysis(
            String ownedCardId,
            String cardId,
            boolean inSavedDeck,
            boolean lockedInDeck,
            boolean removable,
            List<IssueCode> blockedReasons
    ) {
        public DeckEntryAnalysis {
            ownedCardId = normalize(ownedCardId);
            cardId = normalize(cardId);
            blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        }
    }

    public record CardPoolGroupAnalysis(
            String cardId,
            int currentDeckCount,
            int totalOwnedCount,
            int availableOwnedCount,
            boolean addable,
            List<IssueCode> blockedReasons
    ) {
        public CardPoolGroupAnalysis {
            cardId = normalize(cardId);
            blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        }
    }

    public enum IssueLevel {
        WARNING,
        ERROR
    }

    public enum IssueCode {
        DECK_FULL,
        INVALID_DECK_SIZE,
        CARD_LIMIT_REACHED,
        REPLACEMENT_LIMIT_REACHED,
        LOCKED_CARD,
        NOT_OWNED,
        ALREADY_IN_DECK
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
