package com.example.dueltower.session.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        CardService cardService = mock(CardService.class);
        StatusService statusService = mock(StatusService.class);
        KeywordService keywordService = mock(KeywordService.class);
        PassiveService passiveService = mock(PassiveService.class);

        when(cardService.asMap()).thenReturn(Map.of());
        when(cardService.effectsMap()).thenReturn(Map.of());
        when(statusService.defsMap()).thenReturn(Map.of());
        when(statusService.effectsMap()).thenReturn(Map.of());
        when(keywordService.defsMap()).thenReturn(Map.of());
        when(keywordService.effectsMap()).thenReturn(Map.of());
        when(passiveService.effectsMap()).thenReturn(Map.of());
        when(passiveService.defsMap()).thenReturn(Map.of(
                "P001", new PassiveDefinition("P001", "P1", 100, ""),
                "P002", new PassiveDefinition("P002", "P2", 100, "")
        ));

        sessionService = new SessionService(
                cardService,
                statusService,
                keywordService,
                passiveService,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void joinRejectsTooManyPassives() {
        String code = sessionService.createSession("gm").code();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("P001", "P002", "P003")));

        assertEquals("400 BAD_REQUEST \"passiveIds allows 0 to 2 items.\"", ex.getMessage());
    }

    @Test
    void joinRejectsPassiveIdFormat() {
        String code = sessionService.createSession("gm").code();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("bad")));

        assertEquals("400 BAD_REQUEST \"Invalid passiveId format: bad (expected P###, e.g. P001).\"", ex.getMessage());
    }

    @Test
    void joinRejectsDuplicatePassiveIds() {
        String code = sessionService.createSession("gm").code();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("P001", "P001")));

        assertEquals("400 BAD_REQUEST \"Duplicate passiveId is not allowed: P001\"", ex.getMessage());
    }

    @Test
    void joinRejectsUnknownPassiveId() {
        String code = sessionService.createSession("gm").code();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("P099")));

        assertEquals("400 BAD_REQUEST \"Unknown passiveId: P099. Select a passive from the available list.\"", ex.getMessage());
    }

    @Test
    void joinDoesNotAllowPassiveChangeAfterInitialJoinEvenInLobbyOrCombat() {
        String code = sessionService.createSession("gm").code();
        sessionService.join(code, "p1", List.of("P001"));

        ResponseStatusException lobbyEx = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("P002")));
        assertEquals("400 BAD_REQUEST \"Passives are fixed at first join and cannot be changed later. Leave passiveIds empty or resend the same values.\"", lobbyEx.getMessage());

        sessionService.withSessionLock(code, rt -> {
            rt.state().combat(new CombatState());
            return null;
        });

        ResponseStatusException combatEx = assertThrows(ResponseStatusException.class,
                () -> sessionService.join(code, "p1", List.of("P002")));
        assertEquals("400 BAD_REQUEST \"Passives are fixed at first join and cannot be changed later. Leave passiveIds empty or resend the same values.\"", combatEx.getMessage());
    }
}
