package com.example.dueltower.session.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SessionLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
    }

    @Test
    void eventsCanBeReadByParticipantWithDefaultQuery() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .header("X-Player-Token", fixture.playerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(fixture.code()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.fromVersion").isNumber())
                .andExpect(jsonPath("$.toVersion").isNumber());
    }

    @Test
    void eventsAfterVersionAndLimitAreApplied() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 1);
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 2);

        MvcResult filtered = mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .header("X-Player-Token", fixture.playerToken())
                        .param("afterVersion", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        JsonNode filteredBody = JSON.readTree(filtered.getResponse().getContentAsString());
        for (JsonNode item : filteredBody.path("items")) {
            assertTrue(item.path("version").asLong() > 1L);
        }
    }

    @Test
    void eventsCanBeReadByGmAndLimitIsCapped() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        MvcResult result = mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .header("X-GM-Token", fixture.gmToken())
                        .param("limit", "9999"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("items").size() <= 200);
    }

    @Test
    void eventsCanBeReadByAuthenticatedGmWithoutTokenHeader() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .session(fixture.gmSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void logsCanBeReadByParticipantWithDefaultQuery() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .header("X-Player-Token", fixture.playerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(fixture.code()))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].message").isString())
                .andExpect(jsonPath("$.items[0].type").value("LOG_APPENDED"))
                .andExpect(jsonPath("$.items[0].timestamp").isString())
                .andExpect(jsonPath("$.items[0].cursor").isNumber());
    }

    @Test
    void logsBeforeAndLimitAreApplied() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 1);
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 2);

        MvcResult firstPage = mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .header("X-Player-Token", fixture.playerToken())
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextBefore").isNumber())
                .andReturn();

        JsonNode firstBody = JSON.readTree(firstPage.getResponse().getContentAsString());
        long before = firstBody.path("nextBefore").asLong();

        MvcResult secondPage = mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .header("X-Player-Token", fixture.playerToken())
                        .param("before", String.valueOf(before))
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondBody = JSON.readTree(secondPage.getResponse().getContentAsString());
        List<Long> cursors = new ArrayList<>();
        for (JsonNode item : secondBody.path("items")) {
            cursors.add(item.path("cursor").asLong());
        }
        assertTrue(cursors.stream().allMatch(cursor -> cursor < before));
    }

    @Test
    void logsCanBeReadByGm() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .header("X-GM-Token", fixture.gmToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void logsCanBeReadByAuthenticatedParticipantWithoutTokenHeader() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .session(fixture.playerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void eventsLogsReadableAccessDecision(CapturedOutput output) throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        commandClearRecentResults(fixture.code(), fixture.playerToken(), 0);

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .session(fixture.gmSession()))
                .andExpect(status().isOk());

        assertTrue(output.getOut().contains("session read granted"));
        assertTrue(output.getOut().contains("endpoint=GET /api/sessions/{code}/events"));
        assertTrue(output.getOut().contains("source=AUTHENTICATED_GM"));
        assertTrue(output.getOut().contains("code=" + fixture.code()));
        assertTrue(output.getOut().contains("username=gm"));
    }

    @Test
    void eventsAndLogsReturnEmptyItemsWhenHistoryDoesNotExist() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .header("X-GM-Token", fixture.gmToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .header("X-GM-Token", fixture.gmToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void eventsAndLogsFailWithoutAuthorization() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventsAndLogsFailForNonParticipant() throws Exception {
        SessionFixture fixture = createFixture("gm", "player1");
        MockHttpSession otherSession = signUpAndLogin("other", "other@example.com", "password123");

        mockMvc.perform(get("/api/sessions/{code}/events", fixture.code())
                        .session(otherSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/sessions/{code}/logs", fixture.code())
                        .session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventsAndLogsFailWhenSessionCodeDoesNotExist() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");

        mockMvc.perform(get("/api/sessions/{code}/events", "NOSESSION")
                        .session(gmSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/sessions/{code}/logs", "NOSESSION")
                        .session(gmSession))
                .andExpect(status().isNotFound());
    }

    private SessionFixture createFixture(String gmUsername, String playerUsername) throws Exception {
        MockHttpSession gmSession = signUpAndLogin(gmUsername, gmUsername + "@example.com", "password123");
        CreateSessionResult create = createSession(gmSession, gmUsername);

        MockHttpSession playerSession = signUpAndLogin(playerUsername, playerUsername + "@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, create.code(), playerUsername);

        return new SessionFixture(create.code(), create.gmToken(), playerToken, gmSession, playerSession);
    }

    private void commandClearRecentResults(String code, String playerToken, long expectedVersion) throws Exception {
        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CLEAR_RECENT_RESULTS",
                                  "playerId": "player1",
                                  "expectedVersion": %d
                                }
                                """.formatted(expectedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
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
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"" + gmId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = JSON.readTree(result.getResponse().getContentAsString());
        return new CreateSessionResult(json.get("code").asText(), json.get("gmToken").asText());
    }

    private String joinAsPlayer(MockHttpSession session, String code, String playerId) throws Exception {
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("로그 테스트 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("log controller test")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2(null)
                .ownedCards("[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]")
                .currentSkillDeck(List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"))
                .exCard("{\"id\":\"EX901\"}")
                .build());

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s",
                                  "characterId": %d
                                }
                                """.formatted(playerId, profile.getId())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = JSON.readTree(result.getResponse().getContentAsString());
        return json.get("playerToken").asText();
    }

    private record SessionFixture(String code,
                                  String gmToken,
                                  String playerToken,
                                  MockHttpSession gmSession,
                                  MockHttpSession playerSession) {}

    private record CreateSessionResult(String code, String gmToken) {}
}
