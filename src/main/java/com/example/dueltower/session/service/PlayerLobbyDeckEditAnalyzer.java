package com.example.dueltower.session.service;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.engine.model.CardDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PlayerLobbyDeckEditAnalyzer {

    public PlayerLobbyDeckEditAnalysis analyze(Request request) {
        Objects.requireNonNull(request, "request is required");

        Map<String, OwnedCard> ownedById = ownedCardMap(request.ownedCards());
        Set<String> savedDeckOwnedCardIds = new LinkedHashSet<>(request.savedDeckOwnedCardIds());

        Set<String> seenDraftOwnedCardIds = new LinkedHashSet<>();
        Set<String> draftDeckOwnedCardIds = new LinkedHashSet<>();
        Map<String, Integer> draftCardCounts = new LinkedHashMap<>();
        List<String> duplicateOwnedCardIds = new ArrayList<>();
        List<String> unavailableOwnedCardIds = new ArrayList<>();
        List<String> normalizedDraftOwnedCardIds = new ArrayList<>();

        for (String rawOwnedCardId : request.draftDeckOwnedCardIds()) {
            String ownedCardId = normalize(rawOwnedCardId);
            normalizedDraftOwnedCardIds.add(ownedCardId);

            if (!seenDraftOwnedCardIds.add(ownedCardId)) {
                duplicateOwnedCardIds.add(ownedCardId);
            }
            if (!ownedCardId.isBlank()) {
                draftDeckOwnedCardIds.add(ownedCardId);
            }

            OwnedCard ownedCard = ownedById.get(ownedCardId);
            if (ownedCard == null) {
                unavailableOwnedCardIds.add(ownedCardId);
                continue;
            }
            draftCardCounts.merge(ownedCard.cardId(), 1, Integer::sum);
        }

        int changedCardCount = calculateChangedCardCount(savedDeckOwnedCardIds, draftDeckOwnedCardIds);
        Set<String> requiredLockedOwnedCardIds = requiredLockedOwnedCardIds(savedDeckOwnedCardIds, request.ownedCards());

        List<PlayerLobbyDeckEditAnalysis.Issue> globalIssues = new ArrayList<>();
        if (request.draftDeckOwnedCardIds().size() != request.gameRules().deckSize()) {
            globalIssues.add(issue(
                    PlayerLobbyDeckEditAnalysis.IssueCode.INVALID_DECK_SIZE,
                    "requiredDeckSize", request.gameRules().deckSize(),
                    "actualDeckSize", request.draftDeckOwnedCardIds().size()
            ));
        }
        for (String duplicateOwnedCardId : duplicateOwnedCardIds) {
            globalIssues.add(issue(
                    PlayerLobbyDeckEditAnalysis.IssueCode.ALREADY_IN_DECK,
                    "ownedCardId", duplicateOwnedCardId
            ));
        }
        for (String unavailableOwnedCardId : unavailableOwnedCardIds) {
            globalIssues.add(issue(
                    PlayerLobbyDeckEditAnalysis.IssueCode.NOT_OWNED,
                    "ownedCardId", unavailableOwnedCardId
            ));
        }
        for (Map.Entry<String, Integer> entry : draftCardCounts.entrySet()) {
            int maxCopies = request.maxDeckCopiesFor(entry.getKey());
            if (entry.getValue() > maxCopies) {
                globalIssues.add(issue(
                        PlayerLobbyDeckEditAnalysis.IssueCode.CARD_LIMIT_REACHED,
                        "cardId", entry.getKey(),
                        "maxCopies", maxCopies,
                        "actualCopies", entry.getValue()
                ));
            }
        }
        if (changedCardCount > request.gameRules().maxDeckEditChanges()) {
            globalIssues.add(issue(
                    PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED,
                    "maxChangedCards", request.gameRules().maxDeckEditChanges(),
                    "actualChangedCards", changedCardCount
            ));
        }
        for (String lockedOwnedCardId : requiredLockedOwnedCardIds) {
            if (!draftDeckOwnedCardIds.contains(lockedOwnedCardId)) {
                globalIssues.add(issue(
                        PlayerLobbyDeckEditAnalysis.IssueCode.LOCKED_CARD,
                        "ownedCardId", lockedOwnedCardId
                ));
            }
        }

        List<PlayerLobbyDeckEditAnalysis.DeckEntryAnalysis> deckEntries = new ArrayList<>();
        for (String draftOwnedCardId : normalizedDraftOwnedCardIds) {
            OwnedCard ownedCard = ownedById.get(draftOwnedCardId);
            boolean inSavedDeck = savedDeckOwnedCardIds.contains(draftOwnedCardId);
            boolean lockedInDeck = ownedCard != null && ownedCard.lockedInDeck() && inSavedDeck;

            List<PlayerLobbyDeckEditAnalysis.IssueCode> blockedReasons = new ArrayList<>();
            if (lockedInDeck) {
                blockedReasons.add(PlayerLobbyDeckEditAnalysis.IssueCode.LOCKED_CARD);
            }
            if (inSavedDeck && changedCardCount + 1 > request.gameRules().maxDeckEditChanges()) {
                blockedReasons.add(PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED);
            }

            deckEntries.add(new PlayerLobbyDeckEditAnalysis.DeckEntryAnalysis(
                    draftOwnedCardId,
                    ownedCard == null ? "" : ownedCard.cardId(),
                    inSavedDeck,
                    lockedInDeck,
                    blockedReasons.isEmpty(),
                    distinct(blockedReasons)
            ));
        }

        List<PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis> cardPoolGroups = new ArrayList<>();
        for (Map.Entry<String, List<OwnedCard>> entry : ownedCardGroups(request.ownedCards()).entrySet()) {
            String cardId = entry.getKey();
            List<OwnedCard> ownedGroup = entry.getValue();
            int totalOwnedCount = ownedGroup.size();
            int currentDeckCount = draftCardCounts.getOrDefault(cardId, 0);
            int availableOwnedCount = 0;
            for (OwnedCard ownedCard : ownedGroup) {
                if (draftDeckOwnedCardIds.contains(ownedCard.ownedCardId())) {
                    continue;
                }
                availableOwnedCount++;
            }

            List<PlayerLobbyDeckEditAnalysis.IssueCode> blockedReasons = new ArrayList<>();
            if (request.draftDeckOwnedCardIds().size() >= request.gameRules().deckSize()) {
                blockedReasons.add(PlayerLobbyDeckEditAnalysis.IssueCode.DECK_FULL);
            }
            if (currentDeckCount >= request.maxDeckCopiesFor(cardId)) {
                blockedReasons.add(PlayerLobbyDeckEditAnalysis.IssueCode.CARD_LIMIT_REACHED);
            }
            if (availableOwnedCount == 0) {
                blockedReasons.add(PlayerLobbyDeckEditAnalysis.IssueCode.ALREADY_IN_DECK);
            }

            cardPoolGroups.add(new PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis(
                    cardId,
                    currentDeckCount,
                    totalOwnedCount,
                    availableOwnedCount,
                    blockedReasons.isEmpty(),
                    distinct(blockedReasons)
            ));
        }

        return new PlayerLobbyDeckEditAnalysis(
                new PlayerLobbyDeckEditAnalysis.DeckState(
                        request.gameRules().deckSize(),
                        request.draftDeckOwnedCardIds().size(),
                        changedCardCount,
                        globalIssues.isEmpty()
                ),
                globalIssues,
                deckEntries,
                cardPoolGroups
        );
    }

    private PlayerLobbyDeckEditAnalysis.Issue issue(PlayerLobbyDeckEditAnalysis.IssueCode code, Object... detailPairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < detailPairs.length; index += 2) {
            details.put((String) detailPairs[index], detailPairs[index + 1]);
        }
        return new PlayerLobbyDeckEditAnalysis.Issue(
                PlayerLobbyDeckEditAnalysis.IssueLevel.ERROR,
                code,
                details
        );
    }

    private Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return out;
    }

    private Map<String, List<OwnedCard>> ownedCardGroups(List<OwnedCard> ownedCards) {
        Map<String, List<OwnedCard>> groups = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            groups.computeIfAbsent(ownedCard.cardId(), ignored -> new ArrayList<>()).add(ownedCard);
        }
        return groups;
    }

    private int calculateChangedCardCount(Set<String> savedDeckOwnedCardIds, Set<String> draftDeckOwnedCardIds) {
        int changedCardCount = 0;
        for (String savedDeckOwnedCardId : savedDeckOwnedCardIds) {
            if (!draftDeckOwnedCardIds.contains(savedDeckOwnedCardId)) {
                changedCardCount++;
            }
        }
        return changedCardCount;
    }

    private Set<String> requiredLockedOwnedCardIds(Set<String> savedDeckOwnedCardIds, List<OwnedCard> ownedCards) {
        Set<String> out = new LinkedHashSet<>();
        for (OwnedCard ownedCard : ownedCards) {
            if (ownedCard.lockedInDeck() && savedDeckOwnedCardIds.contains(ownedCard.ownedCardId())) {
                out.add(ownedCard.ownedCardId());
            }
        }
        return out;
    }

    private List<PlayerLobbyDeckEditAnalysis.IssueCode> distinct(List<PlayerLobbyDeckEditAnalysis.IssueCode> codes) {
        return List.copyOf(new LinkedHashSet<>(codes));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record Request(
            List<OwnedCard> ownedCards,
            List<String> savedDeckOwnedCardIds,
            List<String> draftDeckOwnedCardIds,
            GameRules gameRules,
            Map<String, CardDefinition> cardDefinitionsById,
            Map<String, Integer> maxDeckCopiesByCardId
    ) {
        public Request {
            ownedCards = ownedCards == null ? List.of() : List.copyOf(ownedCards);
            savedDeckOwnedCardIds = savedDeckOwnedCardIds == null ? List.of() : List.copyOf(savedDeckOwnedCardIds);
            draftDeckOwnedCardIds = draftDeckOwnedCardIds == null ? List.of() : List.copyOf(draftDeckOwnedCardIds);
            Objects.requireNonNull(gameRules, "gameRules is required");
            cardDefinitionsById = cardDefinitionsById == null ? Map.of() : Map.copyOf(cardDefinitionsById);
            maxDeckCopiesByCardId = maxDeckCopiesByCardId == null ? Map.of() : Map.copyOf(maxDeckCopiesByCardId);
        }

        public int maxDeckCopiesFor(String cardId) {
            return maxDeckCopiesByCardId.getOrDefault(cardId, gameRules.maxDeckCopies());
        }
    }
}
