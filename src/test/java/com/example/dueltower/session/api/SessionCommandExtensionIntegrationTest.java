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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @DisplayName("아이템 사용은 자신의 토큰이면 성공하고 인벤토리를 변경한다")
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
    @DisplayName("아이템 사용은 플레이어 토큰이 없으면 실패한다")
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
    @DisplayName("아이템 사용은 토큰의 플레이어가 일치하지 않으면 실패한다")
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
    @DisplayName("아이템 사용은 item ID가 없으면 실패한다")
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
    @DisplayName("아이템 사용은 알 수 없는 item ID면 거부한다")
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
    @DisplayName("아이템 사용은 수량이 부족하면 거부한다")
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
    @DisplayName("아이템 사용은 해독제 대상이 없으면 거부한다")
    void useItemRejectsAntidoteWhenTargetMissing() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-6",
                  "count": 1,
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertFalse(response.get("accepted").asBoolean());
        assertTrue(hasError(response, "player target required"));
    }

    @Test
    @DisplayName("아이템 사용은 대상이 주어지면 해독제 사용에 성공한다")
    void useItemAntidoteSucceedsWhenTargetProvided() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-6",
                  "count": 1,
                  "targetPlayerIds": ["player1"],
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertTrue(response.get("accepted").asBoolean());
    }

    @Test
    @DisplayName("아이템 사용은 전투가 시작되지 않았으면 거부한다")
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
    @DisplayName("아이템 사용은 연막탄 사용에 성공하고 인벤토리를 소모한다")
    void useItemSmokeBombSucceedsAndConsumesInventory() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);
        long expectedVersion = stateAfterStart.get("version").asLong();
        int beforeCount = findItemCountOrZero(stateAfterStart, "I-4");

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-4",
                  "count": 1,
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(response.get("accepted").asBoolean());
        assertEquals(beforeCount - 1, findItemCountOrZero(response.get("state"), "I-4"));
    }

    @Test
    @DisplayName("아이템 사용은 같은 턴 두 번째 소모품 사용을 거부한다")
    void useItemRejectsSecondConsumableInSameTurn() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode first = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-1",
                  "count": 1,
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertTrue(first.path("accepted").asBoolean());

        JsonNode second = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-4",
                  "count": 1,
                  "expectedVersion": %d
                }
                """.formatted(first.path("state").path("version").asLong())
        );

        assertFalse(second.path("accepted").asBoolean());
        assertTrue(hasError(second, "consumable use limit reached this turn"));
    }

    @Test
    @DisplayName("아이템 사용은 expectedVersion이 일치하지 않으면 거부한다")
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
    @DisplayName("전투 항복은 전투 중이면 성공하고 전투를 종료한다")
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
    @DisplayName("전투 항복은 플레이어 토큰이 없으면 실패한다")
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
    @DisplayName("전투 항복은 토큰의 플레이어가 일치하지 않으면 실패한다")
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
    @DisplayName("전투 항복은 전투 중이 아니면 거부한다")
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
    @DisplayName("전투 항복은 expectedVersion이 일치하지 않으면 거부한다")
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
    @DisplayName("인벤토리 아이템 판매는 인벤토리, 골드, 최근 결과를 변경한다")
    void sellInventoryItemMutatesInventoryGoldAndRecentResults() throws Exception {
        Fixture fx = createFixture();
        JsonNode state = snapshotState(fx);
        long expectedVersion = state.path("version").asLong();
        int beforeGold = state.path("run").path("inventory").path("gold").asInt();
        int beforeCount = findItemCount(state, "I-1");
        int beforeRecentResults = state.path("run").path("recentResults").size();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELL_INVENTORY_ITEM",
                  "playerId": "player1",
                  "itemId": "I-1",
                  "count": 1,
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(response.path("accepted").asBoolean());
        assertEquals(beforeCount - 1, findItemCount(response.path("state"), "I-1"));
        assertTrue(response.path("state").path("run").path("inventory").path("gold").asInt() > beforeGold);
        assertEquals(beforeRecentResults + 1, response.path("state").path("run").path("recentResults").size());
    }

    @Test
    @DisplayName("인벤토리 아이템 판매는 플레이어 토큰이 없으면 실패한다")
    void sellInventoryItemFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();
        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SELL_INVENTORY_ITEM",
                                  "playerId": "player1",
                                  "itemId": "I-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("전투 후퇴는 전투 중이면 성공하고 전투를 종료한다")
    void retreatCombatSucceedsDuringCombatAndClosesCombat() throws Exception {
        Fixture fx = createFixture();
        JsonNode stateAfterStart = startCombatAndReachPlayerMainTurn(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RETREAT_COMBAT",
                  "playerId": "player1",
                  "reason": "too dangerous",
                  "expectedVersion": %d
                }
                """.formatted(stateAfterStart.get("version").asLong())
        );

        assertTrue(response.path("accepted").asBoolean());
        assertTrue(response.path("state").path("combat").isNull());
        assertTrue(response.path("state").path("run").path("resultPending").asBoolean());
    }

    @Test
    @DisplayName("전투 후퇴는 전투 중이 아니면 거부한다")
    void retreatCombatRejectedWhenNotInCombat() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RETREAT_COMBAT",
                  "playerId": "player1",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "retreat is only available during combat"));
    }

    @Test
    @DisplayName("draw rail은 플레이어 인증 커맨드로 여전히 동작한다")
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
    @DisplayName("커맨드 요청은 기존 JSON 필드와의 하위 호환성을 유지한다")
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

    @Test
    @DisplayName("상점 아이템 구매는 이벤트 노드에서 성공하고 골드를 소모한다")
    void buyShopItemSucceedsOnEventNodeAndConsumesGold() throws Exception {
        Fixture fx = createFixture();
        JsonNode initialState = snapshotState(fx);
        String eventChoiceId = findChoiceIdByPhase(initialState, "EVENT");

        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, initialState.get("version").asLong())
        );
        assertTrue(selected.get("accepted").asBoolean());

        long expectedVersion = selected.get("state").get("version").asLong();
        int beforeGold = selected.get("state").path("run").path("inventory").path("gold").asInt();
        int beforeSmokeBomb = findItemCount(selected.get("state"), "I-4");

        JsonNode bought = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-4",
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(bought.get("accepted").asBoolean());
        assertEquals(beforeGold - 50, bought.get("state").path("run").path("inventory").path("gold").asInt());
        assertEquals(beforeSmokeBomb + 1, findItemCount(bought.get("state"), "I-4"));
        assertTrue(bought.get("state").path("run").path("resultPending").asBoolean());
    }

    @Test
    @DisplayName("상점 아이템 구매는 플레이어 토큰이 없으면 실패한다")
    void buyShopItemFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();
        JsonNode initialState = snapshotState(fx);
        String eventChoiceId = findChoiceIdByPhase(initialState, "EVENT");

        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, initialState.get("version").asLong())
        );
        long expectedVersion = selected.path("state").path("version").asLong();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BUY_SHOP_ITEM",
                                  "playerId": "player1",
                                  "offerId": "O-1",
                                  "expectedVersion": %d
                                }
                                """.formatted(expectedVersion)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상점 아이템 구매는 토큰의 플레이어가 일치하지 않으면 실패한다")
    void buyShopItemFailsWhenTokenPlayerMismatch() throws Exception {
        Fixture fx = createFixtureWithSecondPlayer();
        JsonNode initialState = snapshotState(fx);
        String eventChoiceId = findChoiceIdByPhase(initialState, "EVENT");

        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, initialState.get("version").asLong())
        );
        long expectedVersion = selected.path("state").path("version").asLong();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BUY_SHOP_ITEM",
                                  "playerId": "player1",
                                  "offerId": "O-1",
                                  "expectedVersion": %d
                                }
                                """.formatted(expectedVersion)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상점 아이템 구매는 offer ID가 없으면 실패한다")
    void buyShopItemFailsWhenOfferIdMissing() throws Exception {
        Fixture fx = createFixture();
        JsonNode initialState = snapshotState(fx);
        String eventChoiceId = findChoiceIdByPhase(initialState, "EVENT");

        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, initialState.get("version").asLong())
        );

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BUY_SHOP_ITEM",
                                  "playerId": "player1",
                                  "expectedVersion": %d
                                }
                                """.formatted(selected.path("state").path("version").asLong())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상점 아이템 구매는 알 수 없는 offer ID면 거부한다")
    void buyShopItemRejectsWhenOfferIdUnknown() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectEventNode(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-999",
                  "expectedVersion": %d
                }
                """.formatted(selected.path("state").path("version").asLong())
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "offer not found"));
    }

    @Test
    @DisplayName("상점 아이템 구매는 골드가 부족하면 거부한다")
    void buyShopItemRejectsWhenGoldInsufficient() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectEventNode(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-2",
                  "count": 999,
                  "expectedVersion": %d
                }
                """.formatted(selected.path("state").path("version").asLong())
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "not enough gold"));
    }

    @Test
    @DisplayName("상점 아이템 구매는 상점이 없으면 거부한다")
    void buyShopItemRejectsWhenShopNotAvailable() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-1",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "shop is not available now"));
    }

    @Test
    @DisplayName("상점 아이템 구매는 expectedVersion이 일치하지 않으면 거부한다")
    void buyShopItemRejectsWhenExpectedVersionMismatched() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectEventNode(fx);
        long staleVersion = Math.max(0, selected.path("state").path("version").asLong() - 1);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-1",
                  "expectedVersion": %d
                }
                """.formatted(staleVersion)
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "version mismatch"));
    }

    @Test
    @DisplayName("상자 열기는 상자를 소모하고 보상을 추가한다")
    void openChestConsumesChestAndAddsRewards() throws Exception {
        Fixture fx = createFixture();
        JsonNode initialState = snapshotState(fx);
        long expectedVersion = initialState.get("version").asLong();
        int beforeChests = initialState.path("run").path("inventory").path("chests").asInt();
        int beforeGold = initialState.path("run").path("inventory").path("gold").asInt();
        int beforePotion = findItemCount(initialState, "I-1");

        JsonNode opened = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "OPEN_CHEST",
                  "playerId": "player1",
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(opened.get("accepted").asBoolean());
        assertEquals(beforeChests - 1, opened.get("state").path("run").path("inventory").path("chests").asInt());
        assertEquals(beforeGold + 150, opened.get("state").path("run").path("inventory").path("gold").asInt());
        assertEquals(beforePotion + 1, findItemCount(opened.get("state"), "I-1"));
    }

    @Test
    @DisplayName("상자 열기는 플레이어 토큰이 없으면 실패한다")
    void openChestFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OPEN_CHEST",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상자 열기는 토큰의 플레이어가 일치하지 않으면 실패한다")
    void openChestFailsWhenTokenPlayerMismatch() throws Exception {
        Fixture fx = createFixtureWithSecondPlayer();

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "OPEN_CHEST",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상자 열기는 상자가 부족하면 거부한다")
    void openChestRejectsWhenChestInsufficient() throws Exception {
        Fixture fx = createFixture();
        JsonNode initial = snapshotState(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "OPEN_CHEST",
                  "playerId": "player1",
                  "count": 99,
                  "expectedVersion": %d
                }
                """.formatted(initial.path("version").asLong())
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "not enough chests"));
    }

    @Test
    @DisplayName("상자 열기는 노드가 resolving 중이면 거부한다")
    void openChestRejectsWhenNodeResolving() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectJudgementNode(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "OPEN_CHEST",
                  "playerId": "player1",
                  "expectedVersion": %d
                }
                """.formatted(selected.path("state").path("version").asLong())
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "cannot open chest while resolving node"));
    }

    @Test
    @DisplayName("상자 열기는 expectedVersion이 일치하지 않으면 거부한다")
    void openChestRejectsWhenExpectedVersionMismatched() throws Exception {
        Fixture fx = createFixture();
        JsonNode bumped = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "CLEAR_RECENT_RESULTS",
                  "playerId": "player1",
                  "expectedVersion": 0
                }
                """
        );
        assertTrue(bumped.path("accepted").asBoolean());
        long staleVersion = Math.max(0, bumped.path("state").path("version").asLong() - 1);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "OPEN_CHEST",
                  "playerId": "player1",
                  "expectedVersion": %d
                }
                """.formatted(staleVersion)
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "version mismatch"));
    }

    @Test
    @DisplayName("판정 해결은 pending 상태가 필요하며 노드 결과를 해결한다")
    void resolveJudgementRequiresPendingAndResolvesNodeResult() throws Exception {
        Fixture fx = createFixture();
        JsonNode initialState = snapshotState(fx);
        String judgementChoiceId = findChoiceIdByPhase(initialState, "JUDGEMENT");

        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(judgementChoiceId, initialState.get("version").asLong())
        );
        assertTrue(selected.get("accepted").asBoolean());
        assertEquals("JUDGEMENT", selected.path("state").path("players").path("player1").path("pendingDecision").path("type").asText());

        long expectedVersion = selected.get("state").get("version").asLong();
        JsonNode resolved = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RESOLVE_JUDGEMENT",
                  "playerId": "player1",
                  "choiceId": "BODY",
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(resolved.get("accepted").asBoolean());
        boolean resultPending = resolved.get("state").path("run").path("resultPending").asBoolean();
        JsonNode pending = resolved.path("state").path("players").path("player1").path("pendingDecision");
        if (!resultPending) {
            assertEquals("JUDGEMENT", pending.path("type").asText());
            assertTrue(pending.path("candidateIds").isArray());
            assertTrue(pending.path("candidateIds").toString().contains("ACCEPT_MEMORY"));
        }
    }

    @Test
    @DisplayName("판정 해결 실패는 상태 약화 대신 owned card modifier를 적용한다")
    void resolveJudgementFailureAppliesOwnedCardModifierInsteadOfStatusWeakness() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectJudgementNode(fx);
        long expectedVersion = selected.get("state").get("version").asLong();

        JsonNode resolved = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RESOLVE_JUDGEMENT",
                  "playerId": "player1",
                  "choiceId": "BODY",
                  "expectedVersion": %d
                }
                """.formatted(expectedVersion)
        );

        assertTrue(resolved.get("accepted").asBoolean());
        JsonNode playerNode = resolved.path("state").path("players").path("player1");
        JsonNode ownedCards = playerNode.path("ownedCards");
        boolean hasWeakenedOwnedCard = false;
        for (JsonNode ownedCard : ownedCards) {
            JsonNode modifiers = ownedCard.path("modifiers");
            for (JsonNode modifier : modifiers) {
                String modifierId = modifier.path("modifierId").asText();
                if (modifierId.startsWith("WEAKENED_")) {
                    hasWeakenedOwnedCard = true;
                    break;
                }
            }
            if (hasWeakenedOwnedCard) {
                break;
            }
        }
        assertTrue(hasWeakenedOwnedCard);
        assertFalse(playerNode.path("statusValues").has("judgement.weakness.WEAKENED_COST_PLUS_ONE"));
        assertFalse(playerNode.path("statusValues").has("judgement.weakness.WEAKENED_SELF_DAMAGE_10"));
        assertFalse(playerNode.path("statusValues").has("judgement.weakness.WEAKENED_FINAL_HALF"));
        assertFalse(playerNode.path("statusValues").has("judgement.weakness.WEAKENED_RANDOM_ENEMY_ONE"));
        assertFalse(playerNode.path("statusValues").has("judgement.weakness.WEAKENED_DISCARD_ONE_SKILL"));
    }

    @Test
    @DisplayName("판정 해결은 플레이어 토큰이 없으면 실패한다")
    void resolveJudgementFailsWithoutPlayerToken() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectJudgementNode(fx);

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "RESOLVE_JUDGEMENT",
                                  "playerId": "player1",
                                  "choiceId": "BODY",
                                  "expectedVersion": %d
                                }
                                """.formatted(selected.path("state").path("version").asLong())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("판정 해결은 토큰의 플레이어가 일치하지 않으면 실패한다")
    void resolveJudgementFailsWhenTokenPlayerMismatch() throws Exception {
        Fixture fx = createFixtureWithSecondPlayer();
        JsonNode selected = selectJudgementNode(fx);

        mockMvc.perform(post("/api/sessions/{code}/command", fx.code)
                        .header("X-Player-Token", fx.otherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "RESOLVE_JUDGEMENT",
                                  "playerId": "player1",
                                  "choiceId": "BODY",
                                  "expectedVersion": %d
                                }
                                """.formatted(selected.path("state").path("version").asLong())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("판정 해결은 pending decision이 없으면 거부한다")
    void resolveJudgementRejectsWhenNoPendingDecision() throws Exception {
        Fixture fx = createFixture();

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RESOLVE_JUDGEMENT",
                  "playerId": "player1",
                  "choiceId": "BODY",
                  "expectedVersion": 0
                }
                """
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "judgement is not pending"));
    }

    @Test
    @DisplayName("판정 해결은 선택이 유효하지 않으면 거부한다")
    void resolveJudgementRejectsWhenChoiceInvalid() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectJudgementNode(fx);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RESOLVE_JUDGEMENT",
                  "playerId": "player1",
                  "choiceId": "NOPE",
                  "expectedVersion": %d
                }
                """.formatted(selected.path("state").path("version").asLong())
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "invalid judgement choice"));
    }

    @Test
    @DisplayName("판정 해결은 expectedVersion이 일치하지 않으면 거부한다")
    void resolveJudgementRejectsWhenExpectedVersionMismatched() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectJudgementNode(fx);
        long staleVersion = Math.max(0, selected.path("state").path("version").asLong() - 1);

        JsonNode response = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "RESOLVE_JUDGEMENT",
                  "playerId": "player1",
                  "choiceId": "BODY",
                  "expectedVersion": %d
                }
                """.formatted(staleVersion)
        );

        assertFalse(response.path("accepted").asBoolean());
        assertTrue(hasError(response, "version mismatch"));
    }

    @Test
    @DisplayName("카드 사용과 턴 종료 rail은 HTTP 오류가 아니라 거부 응답으로 안정적으로 유지된다")
    void playCardAndEndTurnRailsRemainStableAsRejectedNotHttpError() throws Exception {
        Fixture fx = createFixture();

        JsonNode playCardResponse = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "PLAY_CARD",
                  "playerId": "player1",
                  "cardId": "%s",
                  "expectedVersion": 0
                }
                """.formatted(UUID.randomUUID())
        );
        assertFalse(playCardResponse.path("accepted").asBoolean());
        assertTrue(playCardResponse.path("errors").isArray());
        assertTrue(playCardResponse.path("errors").size() > 0);

        JsonNode endTurnResponse = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "END_TURN",
                  "playerId": "player1",
                  "expectedVersion": 0
                }
                """
        );
        assertFalse(endTurnResponse.path("accepted").asBoolean());
        assertTrue(endTurnResponse.path("errors").isArray());
        assertTrue(endTurnResponse.path("errors").size() > 0);
    }


    @Test
    @DisplayName("장비 오퍼 구매는 equip 엔트리 타입을 저장하고 장착/해제 흐름이 동작한다")
    void buyEquipOfferStoresEquipEntryTypeAndEquipUnequipFlowWorks() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectEventNode(fx);

        JsonNode bought = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-8",
                  "expectedVersion": %d
                }
                """.formatted(selected.get("state").get("version").asLong())
        );
        assertTrue(bought.path("accepted").asBoolean());
        assertTrue(hasInventoryEntryType(bought.path("state"), "E-1", "EQUIP"));
        String inventoryEquipId = findInventoryEquipId(bought.path("state"), "E-1");

        JsonNode equipped = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "EQUIP_EQUIPMENT",
                  "playerId": "player1",
                  "inventoryEquipId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(inventoryEquipId, bought.path("state").get("version").asLong())
        );

        assertTrue(equipped.path("accepted").asBoolean());
        assertEquals(0, countInventoryEntries(equipped.path("state"), "E-1", "EQUIP"));
        assertTrue(hasEquippedItem(equipped.path("state"), "player1", "WEAPON", "E-1"));

        JsonNode unequipped = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "UNEQUIP_EQUIPMENT",
                  "playerId": "player1",
                  "inventoryEquipId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(inventoryEquipId, equipped.path("state").get("version").asLong())
        );

        assertTrue(unequipped.path("accepted").asBoolean());
        assertEquals(1, countInventoryEntries(unequipped.path("state"), "E-1", "EQUIP"));
        assertFalse(hasEquippedItem(unequipped.path("state"), "player1", "WEAPON", "E-1"));
    }

    @Test
    @DisplayName("탄환 묶음 구매는 item 타입을 노출하고 사용은 거부한다")
    void buyBulletBundleExposesItemTypeAndUseIsRejected() throws Exception {
        Fixture fx = createFixture();
        JsonNode selected = selectEventNode(fx);

        JsonNode bought = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "BUY_SHOP_ITEM",
                  "playerId": "player1",
                  "offerId": "O-10",
                  "expectedVersion": %d
                }
                """.formatted(selected.get("state").get("version").asLong())
        );

        assertTrue(bought.path("accepted").asBoolean());
        assertTrue(hasInventoryEntryType(bought.path("state"), "I-8", "ITEM"));

        JsonNode used = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "USE_ITEM",
                  "playerId": "player1",
                  "itemId": "I-8",
                  "expectedVersion": %d
                }
                """.formatted(bought.path("state").get("version").asLong())
        );

        assertFalse(used.path("accepted").asBoolean());
        assertTrue(hasError(used, "combat not started") || hasError(used, "item is not battle usable"));
    }

    private JsonNode startCombatAndReachPlayerMainTurn(Fixture fx) throws Exception {
        markReady(fx.code, "player1", fx.playerToken);
        if (fx.otherPlayerToken != null) {
            markReady(fx.code, "player2", fx.otherPlayerToken);
        }

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

    private JsonNode advanceToPlayerMainTurn(Fixture fx, JsonNode currentState) throws Exception {
        JsonNode state = currentState;
        for (int i = 0; i < 6; i++) {
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

        fail("player turn was not reached after end turn");
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

    private int findItemCountOrZero(JsonNode stateNode, String itemId) {
        JsonNode items = stateNode.path("run").path("inventory").path("items");
        for (JsonNode item : items) {
            if (itemId.equals(item.path("id").asText())) {
                return item.path("count").asInt();
            }
        }
        return 0;
    }


    private boolean hasInventoryEntryType(JsonNode stateNode, String id, String type) {
        JsonNode items = stateNode.path("run").path("inventory").path("items");
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asText()) && type.equals(item.path("entryType").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEquippedItem(JsonNode stateNode, String playerId, String slot, String equipId) {
        JsonNode equippedItems = stateNode.path("players").path(playerId).path("equippedItems");
        for (JsonNode equipped : equippedItems) {
            if (slot.equals(equipped.path("slot").asText()) && equipId.equals(equipped.path("equipId").asText())) {
                return true;
            }
        }
        return false;
    }

    private String findInventoryEquipId(JsonNode stateNode, String equipId) {
        JsonNode items = stateNode.path("run").path("inventory").path("items");
        for (JsonNode item : items) {
            if (equipId.equals(item.path("id").asText()) && "EQUIP".equals(item.path("entryType").asText())) {
                return item.path("inventoryEquipId").asText();
            }
        }
        fail("inventoryEquipId not found for equip: " + equipId);
        return null;
    }

    private int countInventoryEntries(JsonNode stateNode, String id, String entryType) {
        int count = 0;
        JsonNode items = stateNode.path("run").path("inventory").path("items");
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asText()) && entryType.equals(item.path("entryType").asText())) {
                count++;
            }
        }
        return count;
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

    private JsonNode snapshotState(Fixture fx) throws Exception {
        JsonNode probe = commandAsPlayer(
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
        return probe.get("state");
    }

    private String findChoiceIdByPhase(JsonNode stateNode, String phase) {
        JsonNode choices = stateNode.path("run").path("availableChoices");
        for (JsonNode choice : choices) {
            if (phase.equals(choice.path("phase").asText()) && !choice.path("disabled").asBoolean()) {
                return choice.path("id").asText();
            }
        }
        fail("choice with phase not found: " + phase);
        return null;
    }

    private JsonNode selectEventNode(Fixture fx) throws Exception {
        JsonNode initialState = snapshotState(fx);
        String eventChoiceId = findChoiceIdByPhase(initialState, "EVENT");
        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(eventChoiceId, initialState.get("version").asLong())
        );
        assertTrue(selected.path("accepted").asBoolean());
        return selected;
    }

    private JsonNode selectJudgementNode(Fixture fx) throws Exception {
        JsonNode initialState = snapshotState(fx);
        String judgementChoiceId = findChoiceIdByPhase(initialState, "JUDGEMENT");
        JsonNode selected = commandAsPlayer(
                fx.code,
                fx.playerToken,
                """
                {
                  "type": "SELECT_NODE_CHOICE",
                  "playerId": "player1",
                  "choiceId": "%s",
                  "expectedVersion": %d
                }
                """.formatted(judgementChoiceId, initialState.get("version").asLong())
        );
        assertTrue(selected.path("accepted").asBoolean());
        return selected;
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
