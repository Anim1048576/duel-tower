package com.example.dueltower.character.service;

import com.example.dueltower.content.card.model.OwnedCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class CharacterCurrentSkillDeckReadServiceTest {

    private final CharacterCurrentSkillDeckReadService service = new CharacterCurrentSkillDeckReadService();

    @Test
    @DisplayName("resolve to cardIds keeps stored cardId based currentSkillDeck")
    void resolveToCardIdsKeepsCardIdStoredDeck() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of(" C001 ", "C001", "C002"),
                ownedCards()
        );

        assertIterableEquals(List.of("C001", "C001", "C002"), resolved);
    }

    @Test
    @DisplayName("resolve to cardIds converts stored ownedCardIds through ownedCards")
    void resolveToCardIdsConvertsOwnedCardIdStoredDeck() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of(" oc-2 ", "oc-1", "oc-3"),
                ownedCards()
        );

        assertIterableEquals(List.of("C001", "C001", "C002"), resolved);
    }

    @Test
    @DisplayName("resolve to cardIds drops stale ownedCardId-looking entries instead of exposing them as cardIds")
    void resolveToCardIdsDropsStaleOwnedCardIdLookingEntries() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of("oc-2", "oc-stale", "oc-3"),
                ownedCards()
        );

        assertIterableEquals(List.of("C001", "C002"), resolved);
    }

    @Test
    @DisplayName("resolve to cardIds returns empty when every stored ownedCardId-looking entry is stale")
    void resolveToCardIdsDropsEveryStaleOwnedCardIdLookingEntry() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of("oc-stale-1", "oc-stale-2"),
                ownedCards()
        );

        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("resolve to cardIds drops stale uuid ownedCardId-looking entries")
    void resolveToCardIdsDropsStaleUuidOwnedCardIdLookingEntries() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of("oc-2", "123e4567-e89b-12d3-a456-426614174000", "oc-3"),
                ownedCards()
        );

        assertIterableEquals(List.of("C001", "C002"), resolved);
    }

    @Test
    @DisplayName("resolve to cardIds does not expose ownedCardId-looking raw entries when ownedCards are missing")
    void resolveToCardIdsDropsOwnedCardIdLookingEntriesWhenOwnedCardsAreMissing() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of("oc-stale-1", "oc-stale-2"),
                List.of()
        );

        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("resolve to cardIds keeps unresolved non-owned entries as cardId based stored deck")
    void resolveToCardIdsKeepsUnresolvedNonOwnedEntriesAsCardIds() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToCardIds(
                List.of("C001", "UNKNOWN_CARD", "C002"),
                ownedCards()
        );

        assertIterableEquals(List.of("C001", "UNKNOWN_CARD", "C002"), resolved);
    }

    @Test
    @DisplayName("resolve to cardIds returns empty list for null or blank stored deck")
    void resolveToCardIdsReturnsEmptyListForNullOrBlankStoredDeck() {
        assertTrue(service.resolveStoredCurrentSkillDeckToCardIds(null, ownedCards()).isEmpty());
        assertTrue(service.resolveStoredCurrentSkillDeckToCardIds(List.of(" ", "\t"), ownedCards()).isEmpty());
    }

    @Test
    @DisplayName("resolve to ownedCardIds converts stored cardIds through ownedCards")
    void resolveToOwnedCardIdsConvertsCardIdStoredDeck() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToOwnedCardIds(
                List.of("C001", "C001", "C002"),
                ownedCards()
        );

        assertIterableEquals(List.of("oc-1", "oc-2", "oc-3"), resolved);
    }

    @Test
    @DisplayName("resolve to ownedCardIds keeps stored ownedCardId based currentSkillDeck")
    void resolveToOwnedCardIdsKeepsOwnedCardIdStoredDeck() {
        List<String> resolved = service.resolveStoredCurrentSkillDeckToOwnedCardIds(
                List.of(" oc-2 ", "oc-1", "oc-3"),
                ownedCards()
        );

        assertIterableEquals(List.of("oc-2", "oc-1", "oc-3"), resolved);
    }

    @Test
    @DisplayName("resolve to ownedCardIds requires ownedCards")
    void resolveToOwnedCardIdsRequiresOwnedCards() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveStoredCurrentSkillDeckToOwnedCardIds(List.of("C001"), List.of()));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("ownedCards is required to resolve currentSkillDeck to ownedCardIds", ex.getReason());
    }

    @Test
    @DisplayName("resolve to ownedCardIds rejects stale ownedCardId when ownedCards do not contain it")
    void resolveToOwnedCardIdsRejectsStaleOwnedCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveStoredCurrentSkillDeckToOwnedCardIds(List.of("stale-owned-card"), ownedCards()));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("owned card unavailable: stale-owned-card", ex.getReason());
    }

    @Test
    @DisplayName("resolve to ownedCardIds rejects cardId stored deck when ownedCards are insufficient")
    void resolveToOwnedCardIdsRejectsCardIdStoredDeckWhenOwnedCardsAreInsufficient() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveStoredCurrentSkillDeckToOwnedCardIds(List.of("C001", "C001", "C001"), ownedCards()));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("owned card unavailable: C001", ex.getReason());
    }

    @Test
    @DisplayName("preview resolves display cardIds and card counts")
    void previewResolvesCardIdsAndCounts() {
        var preview = service.previewStoredCurrentSkillDeck(
                List.of("oc-2", "oc-1", "oc-3"),
                """
                        [
                          {"ownedCardId":"oc-1","cardId":"C001"},
                          {"ownedCardId":"oc-2","cardId":"C001"},
                          {"ownedCardId":"oc-3","cardId":"C002"}
                        ]
                        """
        );

        assertEquals(3, preview.totalCards());
        assertEquals(0, preview.unresolvedEntryCount());
        assertIterableEquals(List.of("C001", "C001", "C002"), preview.cardIds());
        assertEquals(2, preview.cardCounts().get("C001"));
        assertEquals(1, preview.cardCounts().get("C002"));
    }

    @Test
    @DisplayName("preview returns empty stable result for null or empty stored deck")
    void previewReturnsEmptyStableResultForNullOrEmptyStoredDeck() {
        var nullPreview = service.previewStoredCurrentSkillDeck(null, "[]");
        var emptyPreview = service.previewStoredCurrentSkillDeck(List.of(), "[]");

        assertTrue(nullPreview.cardIds().isEmpty());
        assertEquals(0, nullPreview.totalCards());
        assertEquals(0, nullPreview.unresolvedEntryCount());
        assertTrue(!nullPreview.hasUnresolvedEntries());
        assertTrue(emptyPreview.cardIds().isEmpty());
        assertEquals(0, emptyPreview.totalCards());
        assertEquals(0, emptyPreview.unresolvedEntryCount());
        assertTrue(!emptyPreview.hasUnresolvedEntries());
    }

    @Test
    @DisplayName("preview drops stale ownedCardId entries and reports unresolved count")
    void previewDropsStaleOwnedCardIdsAndReportsUnresolvedCount() {
        var preview = service.previewStoredCurrentSkillDeck(
                List.of("oc-2", "oc-stale", "oc-3"),
                """
                        [
                          {"ownedCardId":"oc-2","cardId":"C001"},
                          {"ownedCardId":"oc-3","cardId":"C002"}
                        ]
                        """
        );

        assertEquals(2, preview.totalCards());
        assertEquals(1, preview.unresolvedEntryCount());
        assertTrue(preview.hasUnresolvedEntries());
        assertIterableEquals(List.of("C001", "C002"), preview.cardIds());
        assertTrue(!preview.cardCounts().containsKey("oc-stale"));
    }

    @Test
    @DisplayName("preview reports unresolved count even when every stored ownedCardId is stale")
    void previewReportsUnresolvedCountWhenEveryOwnedCardIdIsStale() {
        var preview = service.previewStoredCurrentSkillDeck(
                List.of("oc-stale-1", "oc-stale-2"),
                """
                        [
                          {"ownedCardId":"oc-1","cardId":"C001"}
                        ]
                        """
        );

        assertEquals(0, preview.totalCards());
        assertEquals(2, preview.unresolvedEntryCount());
        assertTrue(preview.hasUnresolvedEntries());
        assertTrue(preview.cardIds().isEmpty());
    }

    @Test
    @DisplayName("preview does not expose stale uuid ownedCardId-looking entries as cardIds")
    void previewDropsStaleUuidOwnedCardIdLookingEntries() {
        var preview = service.previewStoredCurrentSkillDeck(
                List.of("oc-2", "123e4567-e89b-12d3-a456-426614174000", "oc-3"),
                """
                        [
                          {"ownedCardId":"oc-2","cardId":"C001"},
                          {"ownedCardId":"oc-3","cardId":"C002"}
                        ]
                        """
        );

        assertEquals(2, preview.totalCards());
        assertEquals(1, preview.unresolvedEntryCount());
        assertTrue(preview.hasUnresolvedEntries());
        assertIterableEquals(List.of("C001", "C002"), preview.cardIds());
        assertTrue(!preview.cardIds().contains("123e4567-e89b-12d3-a456-426614174000"));
    }

    private static List<OwnedCard> ownedCards() {
        return List.of(
                new OwnedCard("oc-1", "C001", List.of()),
                new OwnedCard("oc-2", "C001", List.of()),
                new OwnedCard("oc-3", "C002", List.of())
        );
    }
}
