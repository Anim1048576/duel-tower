package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("플레이어 인증 커맨드는 플레이어 토큰이 없으면 401을 반환한다")
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
    void playerAuthCommandWithInvalidPlayerTokenReturns401() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", "not-a-valid-token")
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
    @DisplayName("플레이어 인증 커맨드는 다른 플레이어의 토큰이면 403을 반환한다")
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
    @DisplayName("아이템 사용은 플레이어 토큰이 없으면 401을 반환한다")
    void useItemWithoutPlayerTokenReturns401() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
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
    @DisplayName("전투 시작은 유효한 GM 토큰이 없으면 401을 반환한다")
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
    void startCombatRequiresGmTokenAndAllowsValidGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-GM-Token", info.gmToken())
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

    @Test
    @DisplayName("GM 전용 커맨드는 유효한 GM 토큰이 없으면 401을 반환한다")
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
    @DisplayName("알 수 없는 command type이면 400을 반환한다")
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
    @DisplayName("잘못된 command ID UUID면 400과 사유를 반환한다")
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
    @DisplayName("카드 사용은 잘못된 card ID UUID면 400을 반환한다")
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
    @DisplayName("소환 액션 사용은 잘못된 summon ID UUID면 400을 반환한다")
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
    @DisplayName("Search pick과 resolve search pick은 unknown command type 경로를 타지 않는다")
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
    @DisplayName("카드 사용은 기존/신규 target payload 형식을 모두 허용한다")
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
        return createSessionInfo(session, gmId).code();
    }

    private SessionInfo createSessionInfo(MockHttpSession session, String gmId) throws Exception {
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
        return new SessionInfo(node.get("code").asText(), node.get("gmToken").asText());
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

    private record SessionInfo(String code, String gmToken) {}
}
