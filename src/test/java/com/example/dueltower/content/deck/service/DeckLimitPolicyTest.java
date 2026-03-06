package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeckLimitPolicyTest {

    private CardService cardService;
    private DeckLimitPolicy policy;

    @BeforeEach
    void setUp() {
        cardService = mock(CardService.class);
        policy = new DeckLimitPolicy(cardService);
    }

    @Test
    void usesDefaultLimitWhenNoOverride() {
        when(cardService.maxDeckCopies(new Ids.CardDefId("C001"))).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckExact(Map.of(
                        "C001", 4,
                        "C002", 8
                )));

        assertEquals("400 BAD_REQUEST \"player deck: max 3 copies per card (C001=4)\"", ex.getMessage());
    }

    @Test
    void allowsCardSpecificOverride() {
        when(cardService.maxDeckCopies(new Ids.CardDefId("C001"))).thenReturn(5);
        when(cardService.maxDeckCopies(new Ids.CardDefId("C002"))).thenReturn(null);
        when(cardService.maxDeckCopies(new Ids.CardDefId("C003"))).thenReturn(null);
        when(cardService.maxDeckCopies(new Ids.CardDefId("C004"))).thenReturn(null);

        assertDoesNotThrow(() -> policy.validatePlayerDeckExact(Map.of(
                "C001", 5,
                "C002", 3,
                "C003", 3,
                "C004", 1
        )));
    }

    @Test
    void addValidationUsesSameCopyRule() {
        when(cardService.maxDeckCopies(new Ids.CardDefId("C001"))).thenReturn(4);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckUpTo(Map.of("C001", 5)));

        assertEquals("400 BAD_REQUEST \"player deck: max 4 copies per card (C001=5)\"", ex.getMessage());
    }
}
