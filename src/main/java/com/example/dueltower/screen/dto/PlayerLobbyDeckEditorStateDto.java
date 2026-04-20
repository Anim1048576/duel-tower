package com.example.dueltower.screen.dto;

import java.util.List;
import java.util.Map;

public record PlayerLobbyDeckEditorStateDto(
        DeckState deck,
        List<String> globalReasonCodes,
        List<Issue> issues,
        List<DraftEntry> draftEntries,
        List<CardPoolGroup> cardPoolGroups
) {
    public PlayerLobbyDeckEditorStateDto {
        deck = deck == null ? new DeckState(0, 0, 0, false) : deck;
        globalReasonCodes = globalReasonCodes == null ? List.of() : List.copyOf(globalReasonCodes);
        issues = issues == null ? List.of() : List.copyOf(issues);
        draftEntries = draftEntries == null ? List.of() : List.copyOf(draftEntries);
        cardPoolGroups = cardPoolGroups == null ? List.of() : List.copyOf(cardPoolGroups);
    }

    public record DeckState(
            int requiredDeckSize,
            int draftDeckSize,
            int changedCardCount,
            boolean saveAllowed
    ) {}

    public record Issue(
            String level,
            String code,
            Map<String, Object> details
    ) {
        public Issue {
            level = normalize(level);
            code = normalize(code);
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public record DraftEntry(
            String ownedCardId,
            String cardId,
            boolean inSavedDeck,
            boolean lockedInDeck,
            boolean canRemove,
            List<String> reasonCodes
    ) {
        public DraftEntry {
            ownedCardId = normalize(ownedCardId);
            cardId = normalize(cardId);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    public record CardPoolGroup(
            String cardId,
            int currentDeckCount,
            int totalOwnedCount,
            int availableOwnedCount,
            boolean canAdd,
            List<String> reasonCodes,
            List<OwnedCardState> ownedCards
    ) {
        public CardPoolGroup {
            cardId = normalize(cardId);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            ownedCards = ownedCards == null ? List.of() : List.copyOf(ownedCards);
        }
    }

    public record OwnedCardState(
            String ownedCardId,
            String cardId,
            boolean inDraftDeck,
            boolean canAdd,
            List<String> reasonCodes
    ) {
        public OwnedCardState {
            ownedCardId = normalize(ownedCardId);
            cardId = normalize(cardId);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
