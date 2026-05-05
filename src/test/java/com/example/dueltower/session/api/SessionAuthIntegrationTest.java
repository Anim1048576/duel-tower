package com.example.dueltower.session.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterCurrentSkillDeckEntryRepository;
import com.example.dueltower.character.repository.CharacterExLoadoutRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardModifierRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardRepository;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.character.service.CharacterCardCollectionService;
import com.example.dueltower.character.service.CharacterLoadoutService;
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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.dueltower.support.CharacterLoadoutTestFixtures.seedLoadout;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private CharacterCurrentSkillDeckEntryRepository characterCurrentSkillDeckEntryRepository;

    @Autowired
    private CharacterExLoadoutRepository characterExLoadoutRepository;

    @Autowired
    private CharacterOwnedCardModifierRepository characterOwnedCardModifierRepository;

    @Autowired
    private CharacterOwnedCardRepository characterOwnedCardRepository;

    @Autowired
    private CharacterCardCollectionService characterCardCollectionService;

    @Autowired
    private CharacterLoadoutService characterLoadoutService;

    @BeforeEach
    void setUp() {
        characterCurrentSkillDeckEntryRepository.deleteAll();
        characterExLoadoutRepository.deleteAll();
        characterOwnedCardModifierRepository.deleteAll();
        characterOwnedCardRepository.deleteAll();
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
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
    void joinSessionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/sessions/ABCD/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"tester\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicStateEndpointsAllowAnonymousAccess() throws Exception {
        MockHttpSession session = signUpAndLogin("tester", "tester@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"tester\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        mockMvc.perform(get("/api/sessions/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(code));

        mockMvc.perform(get("/api/sessions/{code}/state", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(code));
    }

    @Test
    void joinSessionAcceptsCharacterIdAndUsesServerCharacterData() throws Exception {
        MockHttpSession session = signUpAndLogin("tester", "tester@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"tester\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("테스트 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("join 테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2(null)
                .build());
        seedLoadout(
                characterCardCollectionService,
                characterLoadoutService,
                profile.getId(),
                """
                        [
                          {"ownedCardId":"join-oc-c001-1","cardId":"C001"},
                          {"ownedCardId":"join-oc-c001-2","cardId":"C001"},
                          {"ownedCardId":"join-oc-c001-3","cardId":"C001"},
                          {"ownedCardId":"join-oc-c002-1","cardId":"C002"},
                          {"ownedCardId":"join-oc-c002-2","cardId":"C002"},
                          {"ownedCardId":"join-oc-c002-3","cardId":"C002"},
                          {"ownedCardId":"join-oc-c003-1","cardId":"C003"},
                          {"ownedCardId":"join-oc-c003-2","cardId":"C003"},
                          {"ownedCardId":"join-oc-c003-3","cardId":"C003"},
                          {"ownedCardId":"join-oc-c004-1","cardId":"C004"},
                          {"ownedCardId":"join-oc-c004-2","cardId":"C004"},
                          {"ownedCardId":"join-oc-c004-3","cardId":"C004"}
                        ]
                        """,
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901"
        );

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "playerId": "tester",
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerToken").isString())
                .andExpect(jsonPath("$.state.players.tester.passiveIds[0]").value("P001"))
                .andExpect(jsonPath("$.state.players.tester.ownedCards[0].cardId").value("C001"))
                .andExpect(jsonPath("$.state.players.tester.ownedCards[0].ownedCardId").isNotEmpty());
    }


    @Test
    void joinSessionAcceptsNonPFormatPassiveIdsWhenDefinedInContent() throws Exception {
        MockHttpSession session = signUpAndLogin("tester", "tester@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"tester\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("TIG 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("join tig passive 테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("Tig001_Passive")
                .trait2(null)
                .build());
        seedLoadout(
                characterCardCollectionService,
                characterLoadoutService,
                profile.getId(),
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901"
        );

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "tester",
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.players.tester.passiveIds[0]").value("Tig001_Passive"));
    }

    @Test
    void joinSessionAllowsCharacterWithEmptySkillDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("tester", "tester@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"tester\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("빈 덱 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("join empty deck 테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2(null)
                .build());
        seedLoadout(
                characterCardCollectionService,
                characterLoadoutService,
                profile.getId(),
                "[\"C001\",\"C001\"]",
                List.of(),
                "EX901"
        );

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "tester",
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.players.tester.deck.length()").value(0));

        mockMvc.perform(get("/api/sessions/{code}/state", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.tester.deck.length()").value(0));
    }

    @Test
    void readyEndpointRequiresPlayerTokenAndOnlyAllowsSelfUpdate() throws Exception {
        MockHttpSession alice = signUpAndLogin("alice", "alice@example.com", "password123");
        MockHttpSession bob = signUpAndLogin("bob", "bob@example.com", "password123");
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("레디 테스트 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("ready 테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2(null)
                .build());
        seedLoadout(
                characterCardCollectionService,
                characterLoadoutService,
                profile.getId(),
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901"
        );

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"alice\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        String aliceToken = extractJsonStringValue(mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "alice",
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "playerToken");

        String bobToken = extractJsonStringValue(mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "bob",
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "playerToken");

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, "alice")
                        .header("X-Player-Token", "not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, "alice")
                        .header("X-Player-Token", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, "alice")
                        .header("X-Player-Token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.alice.ready").value(true))
                .andExpect(jsonPath("$.players.bob.ready").value(false));
    }

    @Test
    void leaveEndpointRequiresOwnValidPlayerToken() throws Exception {
        MockHttpSession alice = signUpAndLogin("alice", "alice@example.com", "password123");
        MockHttpSession bob = signUpAndLogin("bob", "bob@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"alice\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        String aliceToken = extractJsonStringValue(mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "alice",
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
                                  "deckOwnedCardIds": [
                                    "oc1","oc2","oc3",
                                    "oc4","oc5","oc6",
                                    "oc7","oc8","oc9",
                                    "oc10","oc11","oc12"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "playerToken");

        String bobToken = extractJsonStringValue(mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "bob",
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
                                  "deckOwnedCardIds": [
                                    "oc1","oc2","oc3",
                                    "oc4","oc5","oc6",
                                    "oc7","oc8","oc9",
                                    "oc10","oc11","oc12"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "playerToken");

        mockMvc.perform(post("/api/sessions/{code}/leave", code))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/leave", code)
                        .header("X-Player-Token", "not-a-valid-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, "alice")
                        .header("X-Player-Token", bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sessions/{code}/leave", code)
                        .header("X-Player-Token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.alice").doesNotExist())
                .andExpect(jsonPath("$.players.bob").exists());
    }

    @Test
    void gmOnlyEndpointsRequireValidGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");
        String gmToken = extractJsonStringValue(createResult.getResponse().getContentAsString(), "gmToken");

        mockMvc.perform(post("/api/sessions/{code}/reset", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/reset", code)
                        .header("X-GM-Token", "not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/reset", code)
                        .header("X-GM-Token", gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(code));
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

    private String extractJsonStringValue(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("missing json string key: " + key + " in " + json);
        }
        return m.group(1);
    }
}
