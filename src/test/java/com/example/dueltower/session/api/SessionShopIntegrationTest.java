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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionShopIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void shopOpenOnEventNodeReturnsConfiguredOffers() throws Exception {
        Fixture fx = createFixture("shop-open");
        selectEventNode(fx);

        JsonNode shop = getShop(fx);

        assertTrue(shop.path("open").asBoolean());
        assertTrue(shop.path("unavailableReason").isNull());
        assertEquals(fx.code(), shop.path("sessionCode").asText());
        assertTrue(shop.path("gold").asInt() > 0);
        assertOfferIdsPresent(shop.path("offers"), "O-1", "O-8", "O-9", "O-10");
        assertEquals("ITEM", findOffer(shop, "O-1").path("entryType").asText());
        assertEquals("I-1", findOffer(shop, "O-1").path("refId").asText());
        assertEquals("EQUIP", findOffer(shop, "O-8").path("entryType").asText());
        assertEquals("E-1", findOffer(shop, "O-8").path("refId").asText());
        assertEquals("EQUIP", findOffer(shop, "O-9").path("entryType").asText());
        assertEquals("E-2", findOffer(shop, "O-9").path("refId").asText());
        assertEquals(6, findOffer(shop, "O-9").path("loadedAmmo").asInt());
        assertEquals(6, findOffer(shop, "O-9").path("maxLoadedAmmo").asInt());
    }

    @Test
    void shopClosedWhenCurrentNodeIsNotEvent() throws Exception {
        Fixture fx = createFixture("shop-not-event");

        JsonNode shop = getShop(fx);

        assertFalse(shop.path("open").asBoolean());
        assertEquals("이벤트 노드에서만 구매할 수 있습니다.", shop.path("unavailableReason").asText());
        assertOfferIdsPresent(shop.path("offers"), "O-1", "O-8", "O-9", "O-10");
    }

    @Test
    void shopClosedDuringCombat() throws Exception {
        Fixture fx = createFixture("shop-combat");
        markReady(fx.code(), "player1", fx.playerToken());
        JsonNode state = getState(fx);

        JsonNode started = commandAsGm(
                fx.code(),
                fx.gmToken(),
                """
                {
                  "type": "START_COMBAT",
                  "playerId": "player1",
                  "expectedVersion": %d
                }
                """.formatted(state.path("version").asLong())
        );
        assertTrue(started.path("accepted").asBoolean());

        JsonNode shop = getShop(fx);

        assertFalse(shop.path("open").asBoolean());
        assertEquals("전투 중에는 구매할 수 없습니다.", shop.path("unavailableReason").asText());
        assertOfferIdsPresent(shop.path("offers"), "O-1", "O-8", "O-9", "O-10");
    }

    private JsonNode getShop(Fixture fx) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sessions/{code}/shop", fx.code())
                        .header("X-Player-Token", fx.playerToken()))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getState(Fixture fx) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/sessions/{code}/state", fx.code()))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private void assertOfferIdsPresent(JsonNode offers, String... offerIds) {
        assertTrue(offers.isArray());
        for (String offerId : offerIds) {
            findOfferInOffers(offers, offerId);
        }
    }

    private JsonNode findOffer(JsonNode shop, String offerId) {
        return findOfferInOffers(shop.path("offers"), offerId);
    }

    private JsonNode findOfferInOffers(JsonNode offers, String offerId) {
        for (JsonNode offer : offers) {
            if (offerId.equals(offer.path("offerId").asText())) {
                return offer;
            }
        }
        fail("shop offer not found: " + offerId);
        return null;
    }

    private void selectEventNode(Fixture fx) throws Exception {
        JsonNode state = getState(fx);
        String eventChoiceId = findChoiceIdByPhase(state, "EVENT");
        JsonNode selected = commandAsPlayer(
                fx.code(),
                fx.playerToken(),
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, state.path("version").asLong())
        );
        assertTrue(selected.path("accepted").asBoolean());
    }

    private String findChoiceIdByPhase(JsonNode state, String phase) {
        for (JsonNode choice : state.path("run").path("availableChoices")) {
            if (phase.equals(choice.path("phase").asText()) && !choice.path("disabled").asBoolean()) {
                return choice.path("id").asText();
            }
        }
        fail("choice with phase not found: " + phase);
        return null;
    }

    private void markReady(String code, String playerId, String playerToken) throws Exception {
        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", code, playerId)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());
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

    private Fixture createFixture(String suffix) throws Exception {
        String gmId = suffix + "-gm";
        MockHttpSession gmSession = signUpAndLogin(gmId, gmId + "@example.com", "password123");
        CreateSessionResult created = createSession(gmSession, gmId);

        MockHttpSession playerSession = signUpAndLogin("player1", suffix + "-player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, created.code(), "player1");

        return new Fixture(created.code(), created.gmToken(), playerToken);
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
        return new CreateSessionResult(node.path("code").asText(), node.path("gmToken").asText());
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
                  "deckOwnedCardIds": [
                    "oc1","oc2","oc3",
                    "oc4","oc5","oc6",
                    "oc7","oc8","oc9",
                    "oc10","oc11","oc12"
                  ],
                  "exCardId": "EX901"
                }
                """.formatted(playerId);

        MvcResult join = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(join.getResponse().getContentAsString());
        return node.path("playerToken").asText();
    }

    private record Fixture(String code, String gmToken, String playerToken) {}

    private record CreateSessionResult(String code, String gmToken) {}
}
