package com.example.dueltower.character.service;

import com.example.dueltower.content.card.model.OwnedCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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
        assertIterableEquals(List.of("C001", "C001", "C002"), preview.cardIds());
        assertEquals(2, preview.cardCounts().get("C001"));
        assertEquals(1, preview.cardCounts().get("C002"));
    }

    private static List<OwnedCard> ownedCards() {
        return List.of(
                new OwnedCard("oc-1", "C001", List.of()),
                new OwnedCard("oc-2", "C001", List.of()),
                new OwnedCard("oc-3", "C002", List.of())
        );
    }
}
