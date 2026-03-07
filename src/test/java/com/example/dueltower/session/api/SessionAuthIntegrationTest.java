package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void createSessionRequiresAuthenticatedUserAndUsesSameIdentity() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = signUpAndLogin("tester", "tester@example.com", "password123");

        mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gmId").value("tester"));

        mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"other-user\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handSwapRequiresPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player-a", "player-a@example.com", "password123");
        joinSession(code, playerSession, "player-a");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handSwapBody("player-a", 0L, UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handSwapRejectsWhenTokenDoesNotMatchPlayerId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm2", "gm2@example.com", "password123");
        String code = createSession(gmSession, "gm2");

        MockHttpSession playerASession = signUpAndLogin("player-a2", "player-a2@example.com", "password123");
        joinSession(code, playerASession, "player-a2");

        MockHttpSession playerBSession = signUpAndLogin("player-b2", "player-b2@example.com", "password123");
        String playerBToken = joinSession(code, playerBSession, "player-b2");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handSwapBody("player-a2", 0L, UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void handSwapPassesAuthorizationWhenTokenMatchesPlayerId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm3", "gm3@example.com", "password123");
        String code = createSession(gmSession, "gm3");

        MockHttpSession playerSession = signUpAndLogin("player-a3", "player-a3@example.com", "password123");
        String playerToken = joinSession(code, playerSession, "player-a3");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(handSwapBody("player-a3", 0L, UUID.randomUUID().toString())))
                .andExpect(status().isOk());
    }

    private String createSession(MockHttpSession session, String gmId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gmId": "%s"
                                }
                                """.formatted(gmId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("code").asText();
    }

    private String joinSession(String code, MockHttpSession session, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s"
                                }
                                """.formatted(playerId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("playerToken").asText();
    }

    private static String handSwapBody(String playerId, long expectedVersion, String discardId) {
        return """
                {
                  "type": "HAND_SWAP",
                  "playerId": "%s",
                  "expectedVersion": %d,
                  "discardIds": ["%s"]
                }
                """.formatted(playerId, expectedVersion, discardId);
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
