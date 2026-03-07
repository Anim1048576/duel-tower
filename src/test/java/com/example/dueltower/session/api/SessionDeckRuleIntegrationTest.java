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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionDeckRuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void weakenedCardCanBeIncludedInDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("player1", "player1@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "player1",
                  "ownedCards": [
                    {"cardId":"C001","weakened":true},
                    {"cardId":"C001","weakened":true},
                    {"cardId":"C001","weakened":true},
                    {"cardId":"C002","weakened":true},
                    {"cardId":"C002","weakened":true},
                    {"cardId":"C002","weakened":true},
                    {"cardId":"C003","weakened":true},
                    {"cardId":"C003","weakened":true},
                    {"cardId":"C003","weakened":true},
                    {"cardId":"C004","weakened":true},
                    {"cardId":"C004","weakened":true},
                    {"cardId":"C004","weakened":true}
                  ],
                  "presetDeckCardIds": [
                    "C001","C001","C001",
                    "C002","C002","C002",
                    "C003","C003","C003",
                    "C004","C004","C004"
                  ]
                }
                """;

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk());
    }

    @Test
    void lockedInDeckCardCannotBeRemovedOnDeckEdit() throws Exception {
        MockHttpSession session = signUpAndLogin("player2", "player2@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "player2",
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
                    {"cardId":"C005","weakened":false},
                    {"cardId":"C005","weakened":false},
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

        MvcResult updateResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player2")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckCardIds": [
                                    "C002","C002","C002",
                                    "C003","C003","C003",
                                    "C004","C004","C004",
                                    "C005","C005","C005"
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        String errorMessage = updateResult.getResponse().getErrorMessage();
        org.junit.jupiter.api.Assertions.assertNotNull(errorMessage);
        org.junit.jupiter.api.Assertions.assertTrue(errorMessage.contains("locked-in-deck card must remain in deck"));
    }

    @Test
    void weakenedButUnlockedCardCanBeRemovedFromDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("player3", "player3@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "player3",
                  "ownedCards": [
                    {"cardId":"C001","weakened":true,"lockedInDeck":false},
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

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player3")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckCardIds": [
                                    "C002","C002","C002",
                                    "C003","C003","C003",
                                    "C004","C004","C004",
                                    "C005","C005","C005"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void normalDeckEditStillWorks() throws Exception {
        MockHttpSession session = signUpAndLogin("player4", "player4@example.com", "password123");
        String code = createSession(session);

        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "player4"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String playerToken = extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player4")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckCardIds": [
                                    "C001","C001","C001",
                                    "C002","C002","C002",
                                    "C003","C003","C003",
                                    "C004","C004","C004"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
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

    private String extractJsonStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "JSON field not found: " + key);
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
