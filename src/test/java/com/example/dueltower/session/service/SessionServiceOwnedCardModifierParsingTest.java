package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class SessionServiceOwnedCardModifierParsingTest {

    @Test
    void toOwnedCardModifiersSkipsNullBlankAndDeduplicatesExactDuplicates() throws Exception {
        SessionService service = newSessionService();
        OwnedCardDto dto = new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(
                        null,
                        new OwnedCardModifierDto(null, 2),
                        new OwnedCardModifierDto("   ", 3),
                        new OwnedCardModifierDto("  " + CardModifierIds.STRENGTHENED + "  ", null),
                        new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 0),
                        new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 1),
                        new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 1)
                ),
                false,
                false,
                false,
                null,
                null
        );

        List<OwnedCardModifier> modifiers = invokeToOwnedCardModifiers(service, dto);

        assertEquals(List.of(
                new OwnedCardModifier(CardModifierIds.STRENGTHENED, 0),
                new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1)
        ), modifiers);
    }

    @Test
    void toOwnedCardModifiersMergesLegacyBooleansWithoutDuplicatingSemantics() throws Exception {
        SessionService service = newSessionService();
        OwnedCardDto dto = new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(
                        new OwnedCardModifierDto(" " + CardModifierIds.STRENGTHENED + " ", 1),
                        new OwnedCardModifierDto(CardModifierIds.WEAKENED_COST_PLUS_ONE, 1),
                        new OwnedCardModifierDto(CardModifierIds.LOCKED_IN_DECK, 1)
                ),
                true,
                true,
                true,
                null,
                null
        );

        List<OwnedCardModifier> modifiers = invokeToOwnedCardModifiers(service, dto);

        assertEquals(List.of(
                new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1),
                new OwnedCardModifier(CardModifierIds.WEAKENED_COST_PLUS_ONE, 1),
                new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1)
        ), modifiers);
    }

    @Test
    void parseOwnedCardsStillRejectsBlankCardIdAsUnrecoverableInvalidState() {
        SessionService service = newSessionService();
        OwnedCardDto dto = new OwnedCardDto(
                "oc-1",
                "   ",
                List.of(new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 1)),
                null,
                null,
                null,
                null,
                null
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invokeParseOwnedCards(service, List.of(dto))
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"ownedCards.cardId is required\"", ex.getMessage());
    }

    @Test
    void parseOwnedCardsJsonNormalizesRecoverableLegacyEntries() {
        SessionService service = newSessionService();

        List<OwnedCardDto> parsed = invokeParseOwnedCardsJson(service, """
                [
                  " C001 ",
                  {
                    "cardId": " C002 ",
                    "strengthened": true,
                    "weakened": false,
                    "lockedInDeck": true
                  }
                ]
                """);

        assertEquals(2, parsed.size());

        OwnedCardDto legacyTextual = parsed.get(0);
        assertNull(legacyTextual.ownedCardId());
        assertEquals("C001", legacyTextual.cardId());
        assertEquals(List.of(), legacyTextual.modifiers());
        assertFalse(legacyTextual.strengthened());
        assertFalse(legacyTextual.weakened());
        assertFalse(legacyTextual.lockedInDeck());

        OwnedCardDto legacyObject = parsed.get(1);
        assertNull(legacyObject.ownedCardId());
        assertEquals("C002", legacyObject.cardId());
        assertEquals(List.of(), legacyObject.modifiers());
        assertTrue(legacyObject.strengthened());
        assertFalse(legacyObject.weakened());
        assertTrue(legacyObject.lockedInDeck());
    }

    @Test
    void parseOwnedCardsJsonFailsOnUnrecoverablePersistedEntry() {
        SessionService service = newSessionService();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invokeParseOwnedCardsJson(service, """
                        [
                          {"ownedCardId": "oc-1", "cardId": "   "}
                        ]
                        """)
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"invalid persisted ownedCards payload: entry[0] has missing cardId\"", ex.getMessage());
    }

    private static SessionService newSessionService() {
        return new SessionService(null, null, null, null, null, null, null, Duration.ofMinutes(30), Duration.ofMinutes(5));
    }

    @SuppressWarnings("unchecked")
    private static List<OwnedCardModifier> invokeToOwnedCardModifiers(SessionService service, OwnedCardDto dto) throws Exception {
        Method method = SessionService.class.getDeclaredMethod("toOwnedCardModifiers", OwnedCardDto.class);
        method.setAccessible(true);
        return (List<OwnedCardModifier>) method.invoke(service, dto);
    }

    @SuppressWarnings("unchecked")
    private static List<OwnedCard> invokeParseOwnedCards(SessionService service, List<OwnedCardDto> dtos) {
        try {
            Method method = SessionService.class.getDeclaredMethod("parseOwnedCards", List.class);
            method.setAccessible(true);
            return (List<OwnedCard>) method.invoke(service, dtos);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<OwnedCardDto> invokeParseOwnedCardsJson(SessionService service, String raw) {
        try {
            Method method = SessionService.class.getDeclaredMethod("parseOwnedCardsJson", String.class);
            method.setAccessible(true);
            return (List<OwnedCardDto>) method.invoke(service, raw);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
