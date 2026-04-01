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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCommandAuthIntegrationTest {

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
    void playerAuthCommandWithoutPlayerTokenReturns401() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "DRAW",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void playerAuthCommandWithDifferentPlayersTokenReturns403() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        joinAsPlayer(player1Session, code, "player1");
        String otherPlayerToken = joinAsPlayer(player2Session, code, "player2");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "END_TURN",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void startCombatWithoutValidGmTokenReturns401() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gmOnlyCommandWithoutValidGmTokenReturns401() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "ENEMY_END_TURN",
                                  "enemyId": "enemy-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCommandTypeReturns400() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "NOT_A_REAL_COMMAND",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCommandIdUuidReturns400WithReason() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "NOT_A_REAL_COMMAND",
                                  "commandId": "not-a-uuid",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        Exception ex = result.getResolvedException();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("invalid commandId uuid"));
    }

    @Test
    void playCardWithInvalidCardIdUuidReturns400() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PLAY_CARD",
                                  "playerId": "player1",
                                  "cardId": "invalid-card-id",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void useSummonActionWithInvalidSummonIdUuidReturns400() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "USE_SUMMON_ACTION",
                                  "playerId": "player1",
                                  "summonId": "invalid-summon-id",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchPickAndResolveSearchPickAvoidUnknownCommandTypePath() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SEARCH_PICK",
                                  "playerId": "player1",
                                  "selectedIds": [],
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "RESOLVE_SEARCH_PICK",
                                  "playerId": "player1",
                                  "selectedIds": [],
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void playCardAcceptsLegacyAndNewTargetPayloadShapes() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, code, "player1");
        String anyCardId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PLAY_CARD",
                                  "playerId": "player1",
                                  "cardId": "%s",
                                  "targetPlayerIds": ["player1"],
                                  "targetEnemyIds": ["enemy-1"],
                                  "expectedVersion": 0
                                }
                                """.formatted(anyCardId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PLAY_CARD",
                                  "playerId": "player1",
                                  "cardId": "%s",
                                  "targets": [
                                    {"playerId": "player1"},
                                    {"enemyId": "enemy-1"}
                                  ],
                                  "expectedVersion": 0
                                }
                                """.formatted(anyCardId)))
                .andExpect(status().isOk());
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

    private String createSession(MockHttpSession session, String gmId) throws Exception {
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
        return node.get("code").asText();
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
                  ]
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
}
