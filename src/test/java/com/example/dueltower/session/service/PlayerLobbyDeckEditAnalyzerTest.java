package com.example.dueltower.session.service;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerLobbyDeckEditAnalyzerTest {

    private final PlayerLobbyDeckEditAnalyzer analyzer = new PlayerLobbyDeckEditAnalyzer();
    private final GameRules gameRules = GameRules.defaults();

    @Test
    void analyzesDeckSizeBelowAndAboveLimit() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004"),
                owned("oc-13", "C005")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var under = analyze(ownedCards, savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11"));
        var over = analyze(ownedCards, savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12", "oc-13"));

        assertThat(under.deck().draftDeckSize()).isEqualTo(11);
        assertThat(under.saveAllowed()).isFalse();
        assertThat(issueCodes(under)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.INVALID_DECK_SIZE);

        assertThat(over.deck().draftDeckSize()).isEqualTo(13);
        assertThat(over.saveAllowed()).isFalse();
        assertThat(issueCodes(over)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.INVALID_DECK_SIZE);
    }

    @Test
    void analyzesSameCardCopyLimit() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"), owned("oc-04", "C001"),
                owned("oc-05", "C002"), owned("oc-06", "C002"), owned("oc-07", "C002"),
                owned("oc-08", "C003"), owned("oc-09", "C003"), owned("oc-10", "C003"),
                owned("oc-11", "C004"), owned("oc-12", "C004")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12", "oc-04");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12")
        );

        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.CARD_LIMIT_REACHED);
        assertThat(group(analysis, "C001").currentDeckCount()).isEqualTo(4);
        assertThat(group(analysis, "C001").addable()).isFalse();
        assertThat(group(analysis, "C001").blockedReasons()).contains(
                PlayerLobbyDeckEditAnalysis.IssueCode.DECK_FULL,
                PlayerLobbyDeckEditAnalysis.IssueCode.CARD_LIMIT_REACHED
        );
    }

    @Test
    void analyzesReplacementLimit() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004"),
                owned("oc-13", "C005"), owned("oc-14", "C005"), owned("oc-15", "C005")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-13", "oc-14", "oc-15")
        );

        assertThat(analysis.deck().changedCardCount()).isEqualTo(3);
        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED);
        assertThat(entry(analysis, "oc-01").removable()).isFalse();
        assertThat(entry(analysis, "oc-01").blockedReasons()).contains(PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED);
    }

    @Test
    void analyzesLockedCardRemoval() {
        List<OwnedCard> ownedCards = ownedCards(
                ownedLocked("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004"),
                owned("oc-13", "C005")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12", "oc-13")
        );

        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.LOCKED_CARD);
        assertThat(entry(analysis, "oc-02").removable()).isTrue();
    }

    @Test
    void analyzesNotOwnedOwnedCardId() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "missing-owned-card")
        );

        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.NOT_OWNED);
        assertThat(analysis.saveAllowed()).isFalse();
    }

    @Test
    void analyzesAlreadyInDeckWhenOwnedCardIdIsDuplicated() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-01", "oc-01", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12")
        );

        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.ALREADY_IN_DECK);
        assertThat(analysis.saveAllowed()).isFalse();
    }

    @Test
    void analyzesNormalAddAndRemoveAvailability() {
        List<OwnedCard> ownedCards = ownedCards(
                owned("oc-01", "C001"), owned("oc-02", "C001"), owned("oc-03", "C001"),
                owned("oc-04", "C002"), owned("oc-05", "C002"), owned("oc-06", "C002"),
                owned("oc-07", "C003"), owned("oc-08", "C003"), owned("oc-09", "C003"),
                owned("oc-10", "C004"), owned("oc-11", "C004"), owned("oc-12", "C004"),
                owned("oc-13", "C005"), owned("oc-14", "C005")
        );
        List<String> savedDeck = deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11", "oc-12");

        var analysis = analyze(
                ownedCards,
                savedDeck,
                deck("oc-01", "oc-02", "oc-03", "oc-04", "oc-05", "oc-06", "oc-07", "oc-08", "oc-09", "oc-10", "oc-11")
        );

        assertThat(issueCodes(analysis)).contains(PlayerLobbyDeckEditAnalysis.IssueCode.INVALID_DECK_SIZE);
        assertThat(entry(analysis, "oc-01").removable()).isTrue();
        assertThat(group(analysis, "C005").addable()).isTrue();
        assertThat(group(analysis, "C005").availableOwnedCount()).isEqualTo(2);
        assertThat(group(analysis, "C005").nextOwnedCardId()).isEqualTo("oc-13");
    }

    private PlayerLobbyDeckEditAnalysis analyze(List<OwnedCard> ownedCards, List<String> savedDeckOwnedCardIds, List<String> draftDeckOwnedCardIds) {
        return analyzer.analyze(new PlayerLobbyDeckEditAnalyzer.Request(
                ownedCards,
                savedDeckOwnedCardIds,
                draftDeckOwnedCardIds,
                gameRules,
                Map.of(),
                maxCopiesByCardId(ownedCards)
        ));
    }

    private Map<String, Integer> maxCopiesByCardId(List<OwnedCard> ownedCards) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.putIfAbsent(ownedCard.cardId(), gameRules.maxDeckCopies());
        }
        return out;
    }

    private List<PlayerLobbyDeckEditAnalysis.IssueCode> issueCodes(PlayerLobbyDeckEditAnalysis analysis) {
        return analysis.globalIssues().stream()
                .map(PlayerLobbyDeckEditAnalysis.Issue::code)
                .toList();
    }

    private PlayerLobbyDeckEditAnalysis.DeckEntryAnalysis entry(PlayerLobbyDeckEditAnalysis analysis, String ownedCardId) {
        return analysis.deckEntries().stream()
                .filter(entry -> entry.ownedCardId().equals(ownedCardId))
                .findFirst()
                .orElseThrow();
    }

    private PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis group(PlayerLobbyDeckEditAnalysis analysis, String cardId) {
        return analysis.cardPoolGroups().stream()
                .filter(group -> group.cardId().equals(cardId))
                .findFirst()
                .orElseThrow();
    }

    private static List<OwnedCard> ownedCards(OwnedCard... ownedCards) {
        return List.of(ownedCards);
    }

    private static OwnedCard owned(String ownedCardId, String cardId) {
        return new OwnedCard(ownedCardId, cardId, List.of());
    }

    private static OwnedCard ownedLocked(String ownedCardId, String cardId) {
        return new OwnedCard(ownedCardId, cardId, List.of(new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1)));
    }

    private static List<String> deck(String... ownedCardIds) {
        return List.of(ownedCardIds);
    }
}
