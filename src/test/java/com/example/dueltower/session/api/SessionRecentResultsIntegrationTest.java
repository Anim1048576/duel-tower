package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SessionRecentResultsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void recentResultsWithoutParticipantAuthReturns401() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recentResultsAllowsPlayerToken() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk());
    }

    @Test
    void recentResultsAllowsGmToken() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-GM-Token", fixture.gmToken))
                .andExpect(status().isOk());
    }

    @Test
    void recentResultsWithInvalidTokenReturns401() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-Player-Token", "not-a-valid-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-GM-Token", "not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recentResultsRejectsInvalidPlayerTokenEvenForLoggedInParticipant() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-Player-Token", "not-a-valid-token")
                        .session(fixture.playerSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recentResultsAllowsAuthenticatedParticipantOrGmWithoutTokenHeader() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .session(fixture.gmSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .session(fixture.playerSession))
                .andExpect(status().isOk());
    }

    @Test
    void recentResultsLogsReadableAccessDecision(CapturedOutput output) throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk());

        assertTrue(output.getOut().contains("session read granted"));
        assertTrue(output.getOut().contains("endpoint=GET /api/sessions/{code}/recent-results"));
        assertTrue(output.getOut().contains("source=PLAYER_TOKEN"));
        assertTrue(output.getOut().contains("code=" + fixture.code));
        assertTrue(output.getOut().contains("playerId=player1"));
    }

    @Test
    void recentResultsReturns403ForAuthenticatedNonParticipant() throws Exception {
        SessionFixture fixture = createFixture();
        MockHttpSession otherSession = signUpAndLogin("other", "other@example.com", "password123");

        mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void runAliasEndpointsRequireParticipantOrGmAuth() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/run", fixture.code))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sessions/{code}/inventory", fixture.code))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sessions/{code}/results", fixture.code))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sessions/{code}/choices", fixture.code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runAliasEndpointsReturnPartialRunSlices() throws Exception {
        SessionFixture fixture = createFixture();

        mockMvc.perform(get("/api/sessions/{code}/run", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inventory").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.floor").exists());

        mockMvc.perform(get("/api/sessions/{code}/inventory", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inventory.items").isArray());

        mockMvc.perform(get("/api/sessions/{code}/results", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.recentResults").isArray())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.availableChoices").doesNotExist());

        mockMvc.perform(get("/api/sessions/{code}/choices", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.availableChoices").isArray())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inventory").doesNotExist());
    }

    @Test
    void runAliasEndpointsAllowGmTokenAndAuthenticatedParticipant() throws Exception {
        SessionFixture fixture = createFixture();
        String[] endpoints = {"/run", "/inventory", "/results", "/choices"};

        for (String endpoint : endpoints) {
            mockMvc.perform(get("/api/sessions/{code}" + endpoint, fixture.code)
                            .header("X-GM-Token", fixture.gmToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/sessions/{code}" + endpoint, fixture.code)
                            .session(fixture.playerSession))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void clearRecentResultsWithPlayerTokenResetsResultPendingAndRecentResults() throws Exception {
        SessionFixture fixture = createFixture();
        startCombat(fixture);
        surrenderCombat(fixture, 1);

        JsonNode before = getRecentResults(fixture);
        assertTrue(before.get("resultPending").asBoolean());
        assertTrue(before.get("recentResults").isArray());
        assertFalse(before.get("recentResults").isEmpty());

        long version = before.get("version").asLong();
        clearRecentResults(fixture, version);

        JsonNode after = getRecentResults(fixture);
        assertFalse(after.get("resultPending").asBoolean());
        assertTrue(after.get("recentResults").isEmpty());
    }

    private SessionFixture createFixture() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        MvcResult create = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gmId": "gm"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createNode = JSON.readTree(create.getResponse().getContentAsString());

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        MvcResult join = mockMvc.perform(post("/api/sessions/{code}/join", createNode.get("code").asText())
                        .session(playerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "player1",
                                  "ownedCards": [
                                    {"ownedCardId":"oc1","cardId":"C001"},
                                    {"ownedCardId":"oc2","cardId":"C001"},
                                    {"ownedCardId":"oc3","cardId":"C001"},
                                    {"ownedCardId":"oc4","cardId":"C002"},
                                    {"ownedCardId":"oc5","cardId":"C002"},
                                    {"ownedCardId":"oc6","cardId":"C002"},
                                    {"ownedCardId":"oc7","cardId":"C003"},
                                    {"ownedCardId":"oc8","cardId":"C003"},
                                    {"ownedCardId":"oc9","cardId":"C003"},
                                    {"ownedCardId":"oc10","cardId":"C004"},
                                    {"ownedCardId":"oc11","cardId":"C004"},
                                    {"ownedCardId":"oc12","cardId":"C004"}
                                  ],
                                  "presetDeckOwnedCardIds": [
                                    "oc1","oc2","oc3",
                                    "oc4","oc5","oc6",
                                    "oc7","oc8","oc9",
                                    "oc10","oc11","oc12"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode joinNode = JSON.readTree(join.getResponse().getContentAsString());

        return new SessionFixture(
                createNode.get("code").asText(),
                createNode.get("gmToken").asText(),
                joinNode.get("playerToken").asText(),
                gmSession,
                playerSession
        );
    }

    private void startCombat(SessionFixture fixture) throws Exception {
        mockMvc.perform(post("/api/sessions/{code}/command", fixture.code)
                        .header("X-GM-Token", fixture.gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void surrenderCombat(SessionFixture fixture, long expectedVersion) throws Exception {
        mockMvc.perform(post("/api/sessions/{code}/command", fixture.code)
                        .header("X-Player-Token", fixture.playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SURRENDER_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": %d
                                }
                                """.formatted(expectedVersion)))
                .andExpect(status().isOk());
    }

    private void clearRecentResults(SessionFixture fixture, long expectedVersion) throws Exception {
        mockMvc.perform(post("/api/sessions/{code}/command", fixture.code)
                        .header("X-Player-Token", fixture.playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CLEAR_RECENT_RESULTS",
                                  "playerId": "player1",
                                  "expectedVersion": %d
                                }
                                """.formatted(expectedVersion)))
                .andExpect(status().isOk());
    }

    private JsonNode getRecentResults(SessionFixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sessions/{code}/recent-results", fixture.code)
                        .header("X-Player-Token", fixture.playerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        assertNotNull(node);
        return node;
    }

    private MockHttpSession signUpAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, password)))
                .andExpect(status().isOk());

        HttpSession session = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private record SessionFixture(
            String code,
            String gmToken,
            String playerToken,
            MockHttpSession gmSession,
            MockHttpSession playerSession
    ) {}
}
