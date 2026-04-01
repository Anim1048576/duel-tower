package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.Ids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckLimitPolicyTest {

    @Mock
    private CardService cardService;

    @InjectMocks
    private DeckLimitPolicy policy;

    @Test
    @DisplayName("maxCopiesFor: maxDeckCopies가 null이면 기본값 3을 반환한다")
    void maxCopiesForReturnsDefaultWhenOverrideIsNull() {
        when(cardService.maxDeckCopies(any(Ids.CardDefId.class))).thenReturn(null);

        int maxCopies = policy.maxCopiesFor("normal-card");

        assertEquals(3, maxCopies);
    }

    @Test
    @DisplayName("maxCopiesFor: maxDeckCopies 오버라이드 값이 있으면 그 값을 반환한다")
    void maxCopiesForReturnsOverrideWhenPresent() {
        when(cardService.maxDeckCopies(new Ids.CardDefId("legend-card"))).thenReturn(1);

        int maxCopies = policy.maxCopiesFor("legend-card");

        assertEquals(1, maxCopies);
    }

    @Test
    @DisplayName("validatePlayerDeckExact: 총 12장이고 카드별 제한을 지키면 통과한다")
    void validatePlayerDeckExactAcceptsExactlyTwelveWithinPerCardLimit() {
        when(cardService.maxDeckCopies(any(Ids.CardDefId.class))).thenReturn(null);

        Map<String, Integer> merged = deck(
                "a", 3,
                "b", 3,
                "c", 3,
                "d", 3
        );

        policy.validatePlayerDeckExact(merged);
    }

    @Test
    @DisplayName("validatePlayerDeckExact: 총합이 12보다 작으면 예외를 던진다")
    void validatePlayerDeckExactRejectsTotalBelowTwelve() {
        Map<String, Integer> merged = deck("a", 3, "b", 3, "c", 3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckExact(merged));

        assertTrue(ex.getReason().contains("exactly 12 cards"));
        assertTrue(ex.getReason().contains("got 9"));
    }

    @Test
    @DisplayName("validatePlayerDeckExact: 총합이 12보다 크면 예외를 던진다")
    void validatePlayerDeckExactRejectsTotalAboveTwelve() {
        Map<String, Integer> merged = deck("a", 3, "b", 3, "c", 3, "d", 3, "e", 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckExact(merged));

        assertTrue(ex.getReason().contains("exactly 12 cards"));
        assertTrue(ex.getReason().contains("got 13"));
    }

    @Test
    @DisplayName("validatePlayerDeckUpTo: 총합이 12 미만이면 통과한다")
    void validatePlayerDeckUpToAcceptsBelowTwelve() {
        when(cardService.maxDeckCopies(any(Ids.CardDefId.class))).thenReturn(null);

        Map<String, Integer> merged = deck("a", 3, "b", 3, "c", 2);

        policy.validatePlayerDeckUpTo(merged);
    }

    @Test
    @DisplayName("validatePlayerDeckUpTo: 총합이 12면 통과한다")
    void validatePlayerDeckUpToAcceptsExactlyTwelve() {
        when(cardService.maxDeckCopies(any(Ids.CardDefId.class))).thenReturn(null);

        Map<String, Integer> merged = deck("a", 3, "b", 3, "c", 3, "d", 3);

        policy.validatePlayerDeckUpTo(merged);
    }

    @Test
    @DisplayName("validatePlayerDeckUpTo: 총합이 12를 초과하면 예외를 던진다")
    void validatePlayerDeckUpToRejectsAboveTwelve() {
        Map<String, Integer> merged = deck("a", 3, "b", 3, "c", 3, "d", 3, "e", 1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckUpTo(merged));

        assertTrue(ex.getReason().contains("cannot exceed 12 cards"));
        assertTrue(ex.getReason().contains("got 13"));
    }

    @Test
    @DisplayName("카드별 복사 제한을 넘기면 DeckLimitViolation 예외를 던진다")
    void perCardCopyLimitViolationThrowsDeckLimitViolation() {
        when(cardService.maxDeckCopies(any(Ids.CardDefId.class))).thenReturn(null);

        Map<String, Integer> merged = deck("a", 4, "b", 3, "c", 3, "d", 2);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckExact(merged));

        assertTrue(ex.getReason().contains("max 3 copies per card"));
        assertTrue(ex.getReason().contains("a=4"));
    }

    @Test
    @DisplayName("검증 시 카드별 maxCopies 오버라이드 값을 적용한다")
    void validationRespectsOverrideMaxCopies() {
        when(cardService.maxDeckCopies(new Ids.CardDefId("legend-card"))).thenReturn(1);

        Map<String, Integer> merged = deck(
                "legend-card", 2,
                "normal-a", 3,
                "normal-b", 3,
                "normal-c", 3
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> policy.validatePlayerDeckUpTo(merged));

        assertTrue(ex.getReason().contains("max 1 copies per card"));
        assertTrue(ex.getReason().contains("legend-card=2"));
    }

    private static Map<String, Integer> deck(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
