package com.example.dueltower.session.api;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @Test
    void commandReturns400WhenExpectedVersionIsNull() throws Exception {
        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DRAW",
                                  "playerId": "p1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("expectedVersion is required"));
    }


    @Test
    void commandReturns400WhenSummonIdIsMissingForUseSummonAction() throws Exception {
        SessionRuntime runtime = new SessionRuntime(
                "TESTCODE",
                "gm",
                "gm-token",
                new GameState(new Ids.SessionId(UUID.randomUUID()), 1L),
                new EngineContext(Map.of(), Map.of())
        );
        given(sessionService.get("TESTCODE")).willReturn(runtime);

        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "USE_SUMMON_ACTION",
                                  "playerId": "p1",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("summonId is required"));
    }

    @Test
    void commandExposesVersionMismatchErrorFromEngine() throws Exception {
        SessionRuntime runtime = new SessionRuntime(
                "TESTCODE",
                "gm",
                "gm-token",
                new GameState(new Ids.SessionId(UUID.randomUUID()), 1L),
                new EngineContext(Map.of(), Map.of())
        );
        given(sessionService.get("TESTCODE")).willReturn(runtime);

        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DRAW",
                                  "playerId": "p1",
                                  "expectedVersion": 999,
                                  "commandId": "f5f85e98-50e3-4f5a-a0c1-f4f8b1108d95"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.errors[0]").value("version mismatch"));
    }
    @Test
    void commandRejectsWhenActorDoesNotMatchRequestedPlayer() throws Exception {
        SessionRuntime runtime = new SessionRuntime(
                "TESTCODE",
                "gm",
                "gm-token",
                new GameState(new Ids.SessionId(UUID.randomUUID()), 1L),
                new EngineContext(Map.of(), Map.of())
        );
        given(sessionService.get("TESTCODE")).willReturn(runtime);
        given(sessionService.resolvePlayerIdByToken("TESTCODE", "token-p2")).willReturn("p2");

        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .header("X-Player-Token", "token-p2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DRAW",
                                  "playerId": "p1",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(status().reason("playerId mismatch"));
    }

    @Test
    void updateDeckRejectsWhenTokenOwnerDiffersFromPathPlayerId() throws Exception {
        given(sessionService.resolvePlayerIdByToken("TESTCODE", "token-p2")).willReturn("p2");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", "TESTCODE", "p1")
                        .header("X-Player-Token", "token-p2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckCardIds": [
                                    "C001", "C001", "C001",
                                    "C002", "C002", "C002",
                                    "C003", "C003", "C003",
                                    "C004", "C004", "C004"
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(status().reason("players may only edit their own deck"));
    }



    @Test
    void commandReturns401WhenPlayerTokenIsMissingForPlayerCommand() throws Exception {
        SessionRuntime runtime = new SessionRuntime(
                "TESTCODE",
                "gm",
                "gm-token",
                new GameState(new Ids.SessionId(UUID.randomUUID()), 1L),
                new EngineContext(Map.of(), Map.of())
        );
        given(sessionService.get("TESTCODE")).willReturn(runtime);

        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DRAW",
                                  "playerId": "p1",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("player authorization required"));
    }

    @Test
    void commandReturns401WhenGmTokenIsMissingForStartCombat() throws Exception {
        SessionRuntime runtime = new SessionRuntime(
                "TESTCODE",
                "gm",
                "gm-token",
                new GameState(new Ids.SessionId(UUID.randomUUID()), 1L),
                new EngineContext(Map.of(), Map.of())
        );
        given(sessionService.get("TESTCODE")).willReturn(runtime);

        mockMvc.perform(post("/api/sessions/{code}/command", "TESTCODE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "p1",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("gm authorization required"));
    }

}
