package com.example.dueltower.session.api;

import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.session.service.SessionService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionDeckRuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void nonCombatStateAllowsDeckEdit() throws Exception {
        MockHttpSession session = signUpAndLogin("player1", "player1@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player1", ownedCardsWithC005());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C005","C005"
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void combatStateBlocksDeckEditWithExplicitError() throws Exception {
        MockHttpSession session = signUpAndLogin("player2", "player2@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player2", ownedCardsWithC005());

        sessionService.withSessionLock(code, rt -> {
            rt.state().nodeState(NodeState.COMBAT);
            return null;
        });

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player2")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C005","C005"
                                """)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("deck edit unavailable during combat"));
    }

    @Test
    void curseStateBlocksDeckEditWithExplicitError() throws Exception {
        MockHttpSession session = signUpAndLogin("player3", "player3@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player3", ownedCardsWithC005());

        sessionService.withSessionLock(code, rt -> {
            rt.state().nodeState(NodeState.CURSE);
            return null;
        });

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player3")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C005","C005"
                                """)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("deck edit unavailable during curse"));
    }

    @Test
    void deckEditAllowsAtMostTwoCardChanges() throws Exception {
        MockHttpSession session = signUpAndLogin("player4", "player4@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player4", ownedCardsWithC005());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player4")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C005","C005"
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void deckEditFailsWhenMoreThanTwoCardsChanged() throws Exception {
        MockHttpSession session = signUpAndLogin("player5", "player5@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player5", ownedCardsWithC005());

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player5")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C005","C005","C005"
                                """)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("deck edit invalid: at most 2 cards"));
    }

    @Test
    void multisetDifferenceCountsDuplicateQuantityChanges() throws Exception {
        MockHttpSession session = signUpAndLogin("player6", "player6@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "player6",
                  "ownedCards": [
                    {"cardId":"C001","weakened":false},
                    {"cardId":"C001","weakened":false},
                    {"cardId":"C001","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C005","weakened":false},
                    {"cardId":"C005","weakened":false},
                    {"cardId":"C005","weakened":false},
                    {"cardId":"C006","weakened":false},
                    {"cardId":"C006","weakened":false},
                    {"cardId":"C006","weakened":false}
                  ],
                  "presetDeckCardIds": [
                    "C001","C001",
                    "C002","C002",
                    "C003","C003",
                    "C004","C004",
                    "C005","C005",
                    "C006","C006"
                  ]
                }
                """;

        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andReturn();
        String playerToken = extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player6")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002",
                                "C003","C003",
                                "C004","C004",
                                "C005","C005",
                                "C006","C006"
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void lockedInDeckCardCannotBeRemovedOnDeckEdit() throws Exception {
        MockHttpSession session = signUpAndLogin("player7", "player7@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "player7",
                  "ownedCards": [
                    {"cardId":"C001","weakened":true,"lockedInDeck":true},
                    {"cardId":"C001","weakened":false},
                    {"cardId":"C001","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C002","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C003","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C004","weakened":false},
                    {"cardId":"C005","weakened":false}
                  ],
                  "presetDeckCardIds": [
                    "C001","C001","C001",
                    "C002","C002","C002",
                    "C003","C003","C003",
                    "C004","C004","C004"
                  ]
                }
                """;

        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andReturn();

        String playerToken = extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");

        MvcResult updateResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player7")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C004","C004",
                                "C005"
                                """)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(updateResult.getResponse().getErrorMessage().contains("locked-in-deck card must remain in deck"));
    }

    @Test
    void joinAllowsOwnedCardsUpToTwentyAndExposesForgettingFlags() throws Exception {
        MockHttpSession session = signUpAndLogin("player8", "player8@example.com", "password123");
        String code = createSession(session);

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBodyWithOwnedCards("player8", twentyOwnedCardsJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.players.player8.ownedCardCount").value(20))
                .andExpect(jsonPath("$.state.players.player8.maxOwnedCardCount").value(20))
                .andExpect(jsonPath("$.state.players.player8.forgettingRequired").value(false));
    }

    @Test
    void overTwentyOwnedCardsSetsForgettingRequiredAndBlocksDeckEdit() throws Exception {
        MockHttpSession session = signUpAndLogin("player9", "player9@example.com", "password123");
        String code = createSession(session);

        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBodyWithOwnedCards("player9", twentyOneOwnedCardsJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.players.player9.ownedCardCount").value(21))
                .andExpect(jsonPath("$.state.players.player9.maxOwnedCardCount").value(20))
                .andExpect(jsonPath("$.state.players.player9.forgettingRequired").value(true))
                .andReturn();

        String playerToken = extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");

        MvcResult updateResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player9")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","C004","C004"
                                """)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(updateResult.getResponse().getErrorMessage().contains("forgetting required"));

        mockMvc.perform(get("/api/sessions/{code}", code)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player9.ownedCardCount").value(21))
                .andExpect(jsonPath("$.players.player9.maxOwnedCardCount").value(20))
                .andExpect(jsonPath("$.players.player9.forgettingRequired").value(true));
    }

    @Test
    void normalCardCanBeForgotten() throws Exception {
        MockHttpSession session = signUpAndLogin("player10", "player10@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player10", twentyOneOwnedCardsJson());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player10")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player10.ownedCardCount").value(20))
                .andExpect(jsonPath("$.players.player10.forgettingRequired").value(false));
    }

    @Test
    void strengthenedCardCannotBeForgotten() throws Exception {
        MockHttpSession session = signUpAndLogin("player11", "player11@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player11", """
                {"cardId":"C001","strengthened":true,"weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C005","weakened":false}
                """);

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player11")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("cannot forget strengthened card"));
    }

    @Test
    void weakenedCardCannotBeForgotten() throws Exception {
        MockHttpSession session = signUpAndLogin("player12", "player12@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player12", """
                {"cardId":"C001","weakened":true},
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C005","weakened":false}
                """);

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player12")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("cannot forget weakened card"));
    }

    @Test
    void lockedCardCannotBeForgotten() throws Exception {
        MockHttpSession session = signUpAndLogin("player13", "player13@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player13", """
                {"cardId":"C001","weakened":false,"lockedInDeck":true},
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C005","weakened":false}
                """);

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player13")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("cannot forget locked-in-deck card"));
    }

    @Test
    void cardRequiredByDeckCannotBeForgottenToKeepConsistency() throws Exception {
        MockHttpSession session = signUpAndLogin("player14", "player14@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player14", """
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false}
                """);

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player14")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("required by current deck"));
    }

    @Test
    void forgettingRequiredWithoutForgettableCardReturnsValidationError() throws Exception {
        MockHttpSession session = signUpAndLogin("player15", "player15@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player15", twentyOneLockedOwnedCardsJson());

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player15")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownedCardIndex": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("no forgettable cards"));
    }



    private String joinBodyWithOwnedCards(String playerId, String ownedCardsJson) {
        return """
                {
                  "playerId": "%s",
                  "ownedCards": [
                    %s
                  ],
                  "presetDeckCardIds": [
                    "C001","C001","C001",
                    "C002","C002","C002",
                    "C003","C003","C003",
                    "C004","C004","C004"
                  ]
                }
                """.formatted(playerId, ownedCardsJson);
    }

    private String twentyOwnedCardsJson() {
        return repeatCardJson("C001", 5)
                + ",\n" + repeatCardJson("C002", 5)
                + ",\n" + repeatCardJson("C003", 5)
                + ",\n" + repeatCardJson("C004", 5);
    }


    private String twentyOneLockedOwnedCardsJson() {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < 3; i++) cards.add("{\"cardId\":\"C001\",\"weakened\":false,\"lockedInDeck\":true}");
        for (int i = 0; i < 3; i++) cards.add("{\"cardId\":\"C002\",\"weakened\":false,\"lockedInDeck\":true}");
        for (int i = 0; i < 3; i++) cards.add("{\"cardId\":\"C003\",\"weakened\":false,\"lockedInDeck\":true}");
        for (int i = 0; i < 3; i++) cards.add("{\"cardId\":\"C004\",\"weakened\":false,\"lockedInDeck\":true}");
        for (int i = 0; i < 9; i++) cards.add("{\"cardId\":\"C005\",\"weakened\":false,\"lockedInDeck\":true}");
        return String.join(",\n", cards);
    }

    private String twentyOneOwnedCardsJson() {
        return twentyOwnedCardsJson() + ",\n{\"cardId\":\"C005\",\"weakened\":false}";
    }

    private String repeatCardJson(String cardId, int count) {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add("{\"cardId\":\"" + cardId + "\",\"weakened\":false}");
        }
        return String.join(",\n", cards);
    }

    private String createSession(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonStringValue(result.getResponse().getContentAsString(), "code");
    }

    private String joinWithOwnedCards(String code, MockHttpSession session, String playerId, String ownedCardsJson) throws Exception {
        String joinBody = """
                {
                  "playerId": "%s",
                  "ownedCards": [
                    %s
                  ],
                  "presetDeckCardIds": [
                    "C001","C001","C001",
                    "C002","C002","C002",
                    "C003","C003","C003",
                    "C004","C004","C004"
                  ]
                }
                """.formatted(playerId, ownedCardsJson);

        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andReturn();

        return extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");
    }

    private String ownedCardsWithC005() {
        return """
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C001","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C002","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C003","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C004","weakened":false},
                {"cardId":"C005","weakened":false},
                {"cardId":"C005","weakened":false},
                {"cardId":"C005","weakened":false}
                """;
    }

    private String deckUpdateBody(String cardsJson) {
        return """
                {
                  "deckCardIds": [
                    %s
                  ]
                }
                """.formatted(cardsJson);
    }

    private String extractJsonStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), "JSON field not found: " + key);
        return matcher.group(1);
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
}
