package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCommandExtensionIntegrationTest {

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
    void useItemSucceedsWithOwnTokenAndMutatesInventory() throws Exception {
        Fixture fx = createFixture();

        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);
        long expectedVersion = stateAfterStart.get("version").asLong();
        int beforeCount = findItemCount(stateAfterStart, "I-1");

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-1",
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(response.get("accepted").asBoolean());
        assertTrue(response.get("events").isArray());
        assertTrue(response.get("events").size() > 0);
        assertEquals(expectedVersion + 1, response.get("state").get("version").asLong());
        assertEquals(beforeCount - 1, findItemCount(response.get("state"), "I-1"));
    }

    @Test
    void useItemFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "USE_ITEM",
                                  "playerId": "player1",
                                  "itemId": "I-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void useItemFailsWhenTokenPlayerMismatch() throws Exception {
        Fixture fx = createFixtureWithSecondPlayer();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "USE_ITEM",
                                  "playerId": "player1",
                                  "itemId": "I-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void useItemFailsWhenItemIdMissing() throws Exception {
        Fixture fx = createFixture();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "USE_ITEM",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void useItemRejectsWhenItemIdUnknown() throws Exception {
        Fixture fx = createFixture();
        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-999",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "combat not started") || hasError(response, "item not found"));
    }

    @Test
    void useItemRejectsWhenCountInsufficient() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-2",
                  "count": 99,
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "not enough item count"));
    }

    @Test
    void useItemRejectsWhenCombatNotStarted() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-1",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "combat not started"));
    }

    @Test
    void useItemRejectsWhenExpectedVersionMismatched() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);
        long staleVersion = Math.max(0, stateAfterStart.get("version").asLong() - 1);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-1",
                  "expectedVersion": %d
                }
                """.formatted(staleVersion)
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "version mismatch"));
    }

    @Test
    void surrenderCombatSucceedsDuringCombatAndClosesCombat() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SURRENDER_COMBAT",
                  "playerId": "player1",
                  "reason": "test",
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertTrue(response.get("accepted").asBoolean());
        JsonNode state = response.get("state");
        assertTrue(state.get("combat").isNull());
        assertTrue(state.get("run").get("resultPending").asBoolean());
        assertTrue(state.get("run").get("recentResults").isArray());
        assertTrue(state.get("run").get("recentResults").size() > 0);
    }

    @Test
    void surrenderCombatFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SURRENDER_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void surrenderCombatFailsWhenTokenPlayerMismatch() throws Exception {
        Fixture fx = createFixtureWithSecondPlayer();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SURRENDER_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void surrenderCombatRejectedWhenNotInCombat() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SURRENDER_COMBAT",
                  "playerId": "player1",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "surrender is only available during combat"));
    }

    @Test
    void surrenderCombatRejectedWhenExpectedVersionMismatched() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);
        long staleVersion = Math.max(0, stateAfterStart.get("version").asLong() - 1);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SURRENDER_COMBAT",
                  "playerId": "player1",
                  "expectedVersion": %d
                }
                """.formatted(staleVersion)
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "version mismatch"));
    }

    @Test
    void drawRailStillWorksAsPlayerAuthCommand() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "DRAW",
                  "playerId": "player1",
                  "count": 1,
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "combat not started"));
    }

    @Test
    void commandRequestBackwardCompatibilityForExistingJsonFields() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SEARCH_PICK",
                  "playerId": "player1",
                  "selectedIds": [],
                  "expectedVersion": 0
                }
                """
        );

        assertNotNull(response);
        assertTrue(response.has("accepted"));
        assertTrue(response.has("state"));
    }

    private JsonNode startCombatAndReachPlayerMainTurn(Fixture fx) throws Exception {
        JsonNode start = commandAsGm(
                fx.code,
                fx.gmToken,
                """
                {
                  "type": "START_COMBAT",
                  "playerId": "player1",
                  "expectedVersion": 0
                }
                """
        );
        assertTrue(start.get("accepted").asBoolean());

        JsonNode state = start.get("state");
        for (int i = 0; i < 4; i++) {
            String actor = state.path("combat").path("currentTurnPlayer").asText("");
            if (actor.startsWith("P:player1")) {
                return state;
            }
            if (!actor.startsWith("E:")) {
                break;
            }

            String enemyId = actor.substring(2);
            JsonNode enemyEnd = commandAsGm(
                    fx.code,
                    fx.gmToken,
                    """
                    {
                      "type": "ENEMY_END_TURN",
                      "enemyId": "%s",
                      "expectedVersion": %d
                    }
                    """.formatted(enemyId, state.get("version").asLong())
            );
            assertTrue(enemyEnd.get("accepted").asBoolean());
            state = enemyEnd.get("state");
        }

        fail("player turn was not reached for test setup");
        return state;
    }

    private boolean hasError(JsonNode response, String expected) {
        JsonNode errors = response.get("errors");
        if (errors == null || !errors.isArray()) {
            return false;
        }
        for (JsonNode error : errors) {
            if (error.asText().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private int findItemCount(JsonNode stateNode, String itemId) {
        JsonNode items = stateNode.path("run").path("inventory").path("items");
        for (JsonNode item : items) {
            if (itemId.equals(item.path("id").asText())) {
                return item.path("count").asInt();
            }
        }
        throw new IllegalStateException("item not found: " + itemId);
    }

    private JsonNode commandAsPlayer(String code, String playerToken, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode commandAsGm(String code, String gmToken, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-GM-Token", gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private Fixture createFixture() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        CreateSessionResult created = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, created.code(), "player1");

        return new Fixture(created.code(), created.gmToken(), playerToken, null);
    }

    private Fixture createFixtureWithSecondPlayer() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        CreateSessionResult created = createSession(gmSession, "gm");

        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(player1Session, created.code(), "player1");

        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String otherToken = joinAsPlayer(player2Session, created.code(), "player2");

        return new Fixture(created.code(), created.gmToken(), playerToken, otherToken);
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

    private CreateSessionResult createSession(MockHttpSession session, String gmId) throws Exception {
        MvcResult create = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gmId": "%s"
                                }
                                """.formatted(gmId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(create.getResponse().getContentAsString());
        return new CreateSessionResult(node.get("code").asText(), node.get("gmToken").asText());
    }

    private String joinAsPlayer(MockHttpSession session, String code, String playerId) throws Exception {
        String joinBody = """
                {
                  "playerId": "%s",
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
                  ],
                  "presetExCardId": "EX901"
                }
                """.formatted(playerId);

        MvcResult join = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(join.getResponse().getContentAsString());
        return node.get("playerToken").asText();
    }

    private record Fixture(String code, String gmToken, String playerToken, String otherPlayerToken) {}

    private record CreateSessionResult(String code, String gmToken) {}
}
