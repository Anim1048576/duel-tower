package com.example.dueltower.session.api;

import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
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

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void nonCombatStateAllowsDeckEdit() throws Exception {
        MockHttpSession session = signUpAndLogin("player1", "player1@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player1", ownedCardsWithTig001_Card());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isOk());
    }




    @Test
    void joinAcceptsPresetDeckOwnedCardIdsCanonical() throws Exception {
        MockHttpSession session = signUpAndLogin("playerJoinOwned", "playerJoinOwned@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "playerJoinOwned",
                  "ownedCards": [
                    {"ownedCardId":"oc1","cardId":"C001"},
                    {"ownedCardId":"oc2","cardId":"C001","modifiers":[{"modifierId":"WEAKENED","value":1}]},
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
                    "oc2","oc1","oc3",
                    "oc4","oc5","oc6",
                    "oc7","oc8","oc9",
                    "oc10","oc11","oc12"
                  ]
                }
                """;

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.players.playerJoinOwned.deckOwnedCardIds[0]").value("oc2"))
                .andExpect(jsonPath("$.state.players.playerJoinOwned.deckOwnedCardIds[1]").value("oc1"))
                .andExpect(jsonPath("$.state.players.playerJoinOwned.deckOwnedCardIds[2]").value("oc3"));

        sessionService.withSessionLock(code, rt -> {
            PlayerState ps = rt.state().player(new com.example.dueltower.engine.model.Ids.PlayerId("playerJoinOwned"));
            assertNotNull(ps);

            List<String> c001SourceOwnedIds = ps.deck().stream()
                    .map(rt.state()::card)
                    .filter(ci -> ci != null && "C001".equals(ci.defId().value()))
                    .map(CardInstance::sourceOwnedCardId)
                    .sorted()
                    .toList();
            assertEquals(List.of("oc1", "oc2", "oc3"), c001SourceOwnedIds);
            return null;
        });
    }

    @Test
    void updateDeckAcceptsDeckOwnedCardIds() throws Exception {
        MockHttpSession session = signUpAndLogin("playerOwned", "playerOwned@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "playerOwned", ownedCardsWithExplicitIds());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "playerOwned")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBodyOwned("""
                                "oc1","oc2","oc3","oc4","oc5","oc6",
                                "oc7","oc8","oc9","oc10","oc13","oc14"
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.playerOwned.deckOwnedCardIds[10]").value("oc13"))
                .andExpect(jsonPath("$.players.playerOwned.deckOwnedCardIds[11]").value("oc14"));
    }

    @Test
    void updateDeckLegacyDeckCardIdsStillWorks() throws Exception {
        MockHttpSession session = signUpAndLogin("playerLegacy", "playerLegacy@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "playerLegacy", ownedCardsWithTig001_Card());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "playerLegacy")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void forgettingNonDeckOwnedCopyIsAllowed() throws Exception {
        MockHttpSession session = signUpAndLogin("playerForgetOk", "playerForgetOk@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "playerForgetOk", ownedCardsWithExplicitIds());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "playerForgetOk")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownedCardIndex":12}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void forgettingExactDeckOwnedCopyIsBlocked() throws Exception {
        MockHttpSession session = signUpAndLogin("playerForgetBlocked", "playerForgetBlocked@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "playerForgetBlocked", ownedCardsWithExplicitIds());

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "playerForgetBlocked")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownedCardIndex":0}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("required by current deck"));
    }

    @Test
    void deckEditPersistsToCharacterCurrentSkillDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("playerPersist", "playerPersist@example.com", "password123");

        String characterId = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트 캐릭터",
                                  "gender": "OTHER",
                                  "age": 20,
                                  "wish": "소원",
                                  "disposition": "질서/선",
                                  "oneLiner": "한마디",
                                  "story": "설명",
                                  "physical": 5,
                                  "technique": 5,
                                  "sense": 5,
                                  "willpower": 5,
                                  "trait1": null,
                                  "trait2": null,
                                  "ownedCards": "[{\\\"cardId\\\":\\\"C001\\\"},{\\\"cardId\\\":\\\"C001\\\"},{\\\"cardId\\\":\\\"C001\\\"},{\\\"cardId\\\":\\\"C002\\\"},{\\\"cardId\\\":\\\"C002\\\"},{\\\"cardId\\\":\\\"C002\\\"},{\\\"cardId\\\":\\\"C003\\\"},{\\\"cardId\\\":\\\"C003\\\"},{\\\"cardId\\\":\\\"C003\\\"},{\\\"cardId\\\":\\\"C004\\\"},{\\\"cardId\\\":\\\"C004\\\"},{\\\"cardId\\\":\\\"C004\\\"},{\\\"cardId\\\":\\\"Tig001_Card\\\"},{\\\"cardId\\\":\\\"Tig001_Card\\\"}]",
                                  "currentSkillDeck": ["C001","C001","C001","C002","C002","C002","C003","C003","C003","C004","C004","Tig001_Card"],
                                  "exCard": "{\\\"id\\\":\\\"EX901\\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String characterIdValue = extractJsonStringOrNumberValue(characterId, "id");

        String code = createSession(session);
        MvcResult joinResult = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "playerPersist",
                                  "characterId": %s
                                }
                                """.formatted(characterIdValue)))
                .andExpect(status().isOk())
                .andReturn();
        String playerToken = extractJsonStringValue(joinResult.getResponse().getContentAsString(), "playerToken");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "playerPersist")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/characters/{id}", characterIdValue)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSkillDeck[0]").isString())
                .andExpect(jsonPath("$.currentSkillDeck[11]").isString());

        String decksJson = mockMvc.perform(get("/api/content/decks")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(decksJson.contains("\"name\":\"character:" + characterIdValue + ":currentSkillDeck\""));
        assertTrue(decksJson.contains("\"totalCards\":12"));
        assertTrue(decksJson.contains("\"cardId\":\"Tig001_Card\",\"count\":2"));
    }

    @Test
    void combatStateBlocksDeckEditWithExplicitError() throws Exception {
        MockHttpSession session = signUpAndLogin("player2", "player2@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player2", ownedCardsWithTig001_Card());

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
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("deck edit unavailable during combat"));
    }

    @Test
    void curseStateBlocksDeckEditWithExplicitError() throws Exception {
        MockHttpSession session = signUpAndLogin("player3", "player3@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player3", ownedCardsWithTig001_Card());

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
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(result.getResponse().getErrorMessage().contains("deck edit unavailable during curse"));
    }

    @Test
    void deckEditAllowsAtMostTwoCardChanges() throws Exception {
        MockHttpSession session = signUpAndLogin("player4", "player4@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player4", ownedCardsWithTig001_Card());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player4")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "C004","Tig001_Card","Tig001_Card"
                                """)))
                .andExpect(status().isOk());
    }

    @Test
    void deckEditFailsWhenMoreThanTwoCardsChanged() throws Exception {
        MockHttpSession session = signUpAndLogin("player5", "player5@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player5", ownedCardsWithTig001_Card());

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player5")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBody("""
                                "C001","C001","C001",
                                "C002","C002","C002",
                                "C003","C003","C003",
                                "Tig001_Card","Tig001_Card","Tig001_Card"
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
                    {"cardId":"Tig001_Card","weakened":false},
                    {"cardId":"Tig001_Card","weakened":false},
                    {"cardId":"Tig001_Card","weakened":false},
                    {"cardId":"C006","weakened":false},
                    {"cardId":"C006","weakened":false},
                    {"cardId":"C006","weakened":false}
                  ],
                  "presetDeckCardIds": [
                    "C001","C001",
                    "C002","C002",
                    "C003","C003",
                    "C004","C004",
                    "Tig001_Card","Tig001_Card",
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
                                "Tig001_Card","Tig001_Card",
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
                {"cardId":"Tig001_Card","weakened":false}
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

        String joinResponse = joinResult.getResponse().getContentAsString();
        String playerToken = extractJsonStringValue(joinResponse, "playerToken");
        String lockedOwnedCardId = extractLockedOwnedCardId(joinResponse, "player7");
        List<String> currentDeckOwnedCardIds = extractJsonArrayValues(joinResponse, "deckOwnedCardIds");
        List<String> allOwnedCardIds = extractJsonArrayValues(joinResponse, "ownedCardId");

        List<String> updateDeckOwnedCardIds = new ArrayList<>(currentDeckOwnedCardIds);

        boolean removed = updateDeckOwnedCardIds.remove(lockedOwnedCardId);
        assertTrue(removed, "locked owned card was not present in current deck");

        String replacementOwnedCardId = allOwnedCardIds.stream()
                .filter(ownedCardId -> !ownedCardId.equals(lockedOwnedCardId))
                .filter(ownedCardId -> !updateDeckOwnedCardIds.contains(ownedCardId))
                .findFirst()
                .orElseThrow();

        assertNotEquals(lockedOwnedCardId, replacementOwnedCardId);

        updateDeckOwnedCardIds.add(replacementOwnedCardId);

        assertFalse(updateDeckOwnedCardIds.contains(lockedOwnedCardId));

        MvcResult updateResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/deck", code, "player7")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deckUpdateBodyOwned(jsonArrayValues(updateDeckOwnedCardIds))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(updateResult.getResponse().getErrorMessage().contains(lockedOwnedCardId));
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
                .andExpect(jsonPath("$.state.players.player8.ownedCards[0].ownedCardId").isNotEmpty())
                .andExpect(jsonPath("$.state.players.player8.ownedCards[0].modifiers").isArray())
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
                {"cardId":"Tig001_Card","weakened":false}
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

        mockMvc.perform(get("/api/sessions/{code}", code)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player11.ownedCards[0].strengthened").value(true))
                .andExpect(jsonPath("$.players.player11.ownedCards[0].modifiers[0].modifierId").value("STRENGTHENED"));
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
                {"cardId":"Tig001_Card","weakened":false}
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

        mockMvc.perform(get("/api/sessions/{code}", code)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player12.ownedCards[0].weakened").value(true))
                .andExpect(jsonPath("$.players.player12.ownedCards[0].modifiers[0].modifierId").value("WEAKENED"));
    }

    @Test
    void concreteWeakenedModifierCardCannotBeForgotten() throws Exception {
        MockHttpSession session = signUpAndLogin("player12b", "player12b@example.com", "password123");
        String code = createSession(session);
        String playerToken = joinWithOwnedCards(code, session, "player12b", """
                {"ownedCardId":"oc-w1","cardId":"C001","modifiers":[{"modifierId":"WEAKENED_COST_PLUS_ONE","value":1}]},
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
                {"cardId":"Tig001_Card","weakened":false}
                """);

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/forget", code, "player12b")
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

        mockMvc.perform(get("/api/sessions/{code}", code)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player12b.ownedCards[0].weakened").value(true))
                .andExpect(jsonPath("$.players.player12b.ownedCards[0].modifiers[0].modifierId").value("WEAKENED_COST_PLUS_ONE"));
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
                {"cardId":"Tig001_Card","weakened":false}
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

        mockMvc.perform(get("/api/sessions/{code}", code)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player13.ownedCards[0].lockedInDeck").value(true))
                .andExpect(jsonPath("$.players.player13.ownedCards[0].modifiers[0].modifierId").value("LOCKED_IN_DECK"));
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



    @Test
    void loadDeckAssignsOwnedSlotsDeterministicallyAndPreservesRuntimeMetadata() throws Exception {
        MockHttpSession session = signUpAndLogin("playerMeta1", "playerMeta1@example.com", "password123");
        String code = createSession(session);

        String joinBody = """
                {
                  "playerId": "playerMeta1",
                  "ownedCards": [
                    {"ownedCardId":"oc-strength","cardId":"C001","modifiers":[{"modifierId":"STRENGTHENED","value":1}]},
                    {"ownedCardId":"oc-plain","cardId":"C001","modifiers":[]},
                    {"ownedCardId":"oc-c002-1","cardId":"C002"},
                    {"ownedCardId":"oc-c002-2","cardId":"C002"},
                    {"ownedCardId":"oc-c002-3","cardId":"C002"},
                    {"ownedCardId":"oc-c003-1","cardId":"C003"},
                    {"ownedCardId":"oc-c003-2","cardId":"C003"},
                    {"ownedCardId":"oc-c003-3","cardId":"C003"},
                    {"ownedCardId":"oc-c004-1","cardId":"C004"},
                    {"ownedCardId":"oc-c004-2","cardId":"C004"},
                    {"ownedCardId":"oc-c004-3","cardId":"C004"},
                    {"ownedCardId":"oc-c001-3","cardId":"C001"}
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

        sessionService.withSessionLock(code, rt -> {
            PlayerState ps = rt.state().player(new com.example.dueltower.engine.model.Ids.PlayerId("playerMeta1"));
            assertNotNull(ps);

            List<CardInstance> c001Cards = ps.deck().stream()
                    .map(id -> rt.state().cardInstances().get(id))
                    .filter(ci -> ci != null && "C001".equals(ci.defId().value()))
                    .toList();
            assertEquals(3, c001Cards.size());

            List<String> sourceIds = c001Cards.stream().map(CardInstance::sourceOwnedCardId).sorted().toList();
            assertEquals(List.of("oc-c001-3", "oc-plain", "oc-strength"), sourceIds);
            long strengthenedCount = c001Cards.stream().filter(ci -> ci.hasModifier("STRENGTHENED")).count();
            assertEquals(1, strengthenedCount);

            CardInstance exCard = rt.state().cardInstances().get(ps.exCard());
            assertNotNull(exCard);
            assertNull(exCard.sourceOwnedCardId());
            assertTrue(exCard.modifiers().isEmpty());
            return null;
        });
    }

    @Test
    void sessionStateCardsExposeSourceOwnedCardIdAndModifiers() throws Exception {
        MockHttpSession session = signUpAndLogin("playerMeta2", "playerMeta2@example.com", "password123");
        String code = createSession(session);

        mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "playerMeta2",
                                  "ownedCards": [
                                    {"ownedCardId":"meta-1","cardId":"C001","modifiers":[{"modifierId":"WEAKENED","value":1}]},
                                    {"ownedCardId":"meta-2","cardId":"C001"},
                                    {"cardId":"C001"},
                                    {"cardId":"C002"},{"cardId":"C002"},{"cardId":"C002"},
                                    {"cardId":"C003"},{"cardId":"C003"},{"cardId":"C003"},
                                    {"cardId":"C004"},{"cardId":"C004"},{"cardId":"C004"}
                                  ],
                                  "presetDeckCardIds": [
                                    "C001","C001","C001",
                                    "C002","C002","C002",
                                    "C003","C003","C003",
                                    "C004","C004","C004"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        String inspectedCardId = sessionService.withSessionLock(code, rt -> {
            PlayerState ps = rt.state().player(new com.example.dueltower.engine.model.Ids.PlayerId("playerMeta2"));
            assertNotNull(ps);
            return ps.deck().stream()
                    .map(id -> rt.state().cardInstances().get(id))
                    .filter(ci -> ci != null && ci.hasModifier("WEAKENED"))
                    .findFirst()
                    .map(ci -> ci.instanceId().value().toString())
                    .orElseThrow();
        });

        mockMvc.perform(get("/api/sessions/{code}", code).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards['%s'].sourceOwnedCardId".formatted(inspectedCardId)).value("meta-1"))
                .andExpect(jsonPath("$.cards['%s'].modifiers[0].modifierId".formatted(inspectedCardId)).value("WEAKENED"));
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
        for (int i = 0; i < 9; i++) cards.add("{\"cardId\":\"Tig001_Card\",\"weakened\":false,\"lockedInDeck\":true}");
        return String.join(",\n", cards);
    }

    private String twentyOneOwnedCardsJson() {
        return twentyOwnedCardsJson() + ",\n{\"cardId\":\"Tig001_Card\",\"weakened\":false}";
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

    private String ownedCardsWithExplicitIds() {
        return """
                {"ownedCardId":"oc1","cardId":"C001","weakened":false},
                {"ownedCardId":"oc2","cardId":"C001","weakened":false},
                {"ownedCardId":"oc3","cardId":"C001","weakened":false},
                {"ownedCardId":"oc4","cardId":"C002","weakened":false},
                {"ownedCardId":"oc5","cardId":"C002","weakened":false},
                {"ownedCardId":"oc6","cardId":"C002","weakened":false},
                {"ownedCardId":"oc7","cardId":"C003","weakened":false},
                {"ownedCardId":"oc8","cardId":"C003","weakened":false},
                {"ownedCardId":"oc9","cardId":"C003","weakened":false},
                {"ownedCardId":"oc10","cardId":"C004","weakened":false},
                {"ownedCardId":"oc11","cardId":"C004","weakened":false},
                {"ownedCardId":"oc12","cardId":"C004","weakened":false},
                {"ownedCardId":"oc13","cardId":"Tig001_Card","weakened":false},
                {"ownedCardId":"oc14","cardId":"Tig001_Card","weakened":false}
                """;
    }

    private String ownedCardsWithTig001_Card() {
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
                {"cardId":"Tig001_Card","weakened":false},
                {"cardId":"Tig001_Card","weakened":false},
                {"cardId":"Tig001_Card","weakened":false}
                """;
    }

    private String deckUpdateBodyOwned(String cardsJson) {
        return """
                {
                  "deckOwnedCardIds": [
                    %s
                  ]
                }
                """.formatted(cardsJson);
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

    private String jsonArrayValues(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private List<String> extractJsonArrayValues(String json, String key) {
        Pattern arrayPattern = Pattern.compile("\"" + Pattern.quote(key) + "\":\\[(.*?)]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);
        if (arrayMatcher.find()) {
            return extractJsonStringTokens(arrayMatcher.group(1));
        }

        Pattern valuePattern = Pattern.compile("\"" + Pattern.quote(key) + "\":\"([^\"]+)\"");
        Matcher valueMatcher = valuePattern.matcher(json);
        List<String> values = new ArrayList<>();
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }

    private List<String> extractJsonStringTokens(String jsonFragment) {
        Pattern tokenPattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = tokenPattern.matcher(jsonFragment);
        List<String> out = new ArrayList<>();
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    private String extractLockedOwnedCardId(String json, String playerId) throws Exception {
        JsonNode root = JSON.readTree(json);
        JsonNode ownedCards = root.path("state").path("players").path(playerId).path("ownedCards");

        assertTrue(ownedCards.isArray(), "ownedCards array not found in response for playerId=" + playerId);

        for (JsonNode card : ownedCards) {
            if (card.path("lockedInDeck").asBoolean(false)) {
                String ownedCardId = card.path("ownedCardId").asText("").trim();
                assertFalse(ownedCardId.isEmpty(), "locked owned card id is blank");
                return ownedCardId;
            }
        }

        fail("locked owned card id not found in response for playerId=" + playerId);
        return null;
    }

    private String extractJsonStringOrNumberValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"([^\"]*)\"|(\\d+))");
        Matcher matcher = pattern.matcher(json);
        assertTrue(matcher.find(), "JSON field not found: " + key);
        String stringValue = matcher.group(1);
        return stringValue != null ? stringValue : matcher.group(2);
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
