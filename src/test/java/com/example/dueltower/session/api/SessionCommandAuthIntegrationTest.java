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
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerControlType;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.session.service.SessionLifecycleService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.dueltower.support.CharacterLoadoutTestFixtures.seedLoadout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCommandAuthIntegrationTest {

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

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        characterCurrentSkillDeckEntryRepository.deleteAll();
        characterExLoadoutRepository.deleteAll();
        characterOwnedCardModifierRepository.deleteAll();
        characterOwnedCardRepository.deleteAll();
        characterProfileRepository.deleteAll();
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
    void playerAuthCommandRequiresBodyPlayerIdToMatchTokenOwner() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, code, "player1");
        joinAsPlayer(player2Session, code, "player2");

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "END_TURN",
                                  "playerId": "player2",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void gmControlledNpcIsAddedAsSessionParticipant() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm");

        mockMvc.perform(post("/api/sessions/{code}/gm-npcs", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(info.code()))
                .andExpect(jsonPath("$.npcPlayerId").value("gm-npc-1"));

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "gm-npc-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.state.players.gm-npc-1.controlType").value("GM_CONTROLLED_NPC"))
                .andExpect(jsonPath("$.state.players.gm-npc-1.controllerPlayerId").value("gm"))
                .andExpect(jsonPath("$.state.combat.turnOrder").isArray());
    }

    @Test
    void gmControlledNpcWithCharacterCopiesLifeStats() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-npc-stats", "gm-npc-stats@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm-npc-stats");
        long characterId = createCharacterWithStatsAndLoadout("GM NPC Stats", 3, 4, 5, 6);

        mockMvc.perform(post("/api/sessions/{code}/gm-npcs", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": %d
                                }
                                """.formatted(characterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npcPlayerId").value("gm-npc-1"));

        sessionLifecycleService.withLockedSession(info.code(), rt -> {
            PlayerState npc = rt.state().player(new PlayerId("gm-npc-1"));
            assertNotNull(npc);
            assertEquals(3, npc.body());
            assertEquals(4, npc.skill());
            assertEquals(5, npc.sense());
            assertEquals(6, npc.will());
            assertEquals(npc.maxHp(), npc.hp());
            assertEquals(npc.maxAp(), npc.ap());
            assertTrue(npc.ready());
            assertEquals(PlayerControlType.GM_CONTROLLED_NPC, npc.controlType());
            assertEquals("gm-npc-stats", npc.controllerPlayerId().value());
            return null;
        });
    }

    @Test
    void characterBasedPlayerAttackUsesCopiedStatsAndDamagesEnemy() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-gm", "combat-gm@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "combat-gm");
        long characterId = createCharacterWithStatsAndLoadout("Combat Stats", 3, 4, 5, 6);
        MockHttpSession playerSession = signUpAndLogin("combat-player", "combat-player@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "combat-player", characterId);
        markReady(info.code(), "combat-player", playerToken);

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "combat-player",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));

        CombatPlaySetup setup = prepareAttackCardInHand(info.code(), "combat-player");

        MvcResult playResult = mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PLAY_CARD",
                                  "playerId": "combat-player",
                                  "cardId": "%s",
                                  "targets": [
                                    {"enemyId": "%s"}
                                  ],
                                  "expectedVersion": %d
                                }
                                """.formatted(setup.cardId(), setup.enemyId(), setup.expectedVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andReturn();

        JsonNode playBody = JSON.readTree(playResult.getResponse().getContentAsString());
        assertTrue(hasDamageLog(playBody));

        sessionLifecycleService.withLockedSession(info.code(), rt -> {
            PlayerState player = rt.state().player(new PlayerId("combat-player"));
            assertNotNull(player);
            assertEquals(3, player.body());
            assertEquals(4, player.skill());
            assertEquals(5, player.sense());
            assertEquals(6, player.will());
            assertEquals(setup.apBefore() - 1, player.ap());
            assertTrue(player.hand().stream().noneMatch(id -> id.value().toString().equals(setup.cardId())));
            assertTrue(player.grave().stream().anyMatch(id -> id.value().toString().equals(setup.cardId())));
            assertEquals(setup.enemyHpBefore() - setup.attackPower(),
                    rt.state().enemy(new com.example.dueltower.engine.model.Ids.EnemyId(setup.enemyId())).hp());
            return null;
        });
    }

    @Test
    void gmControllerCanIssueNpcPlayerCommand() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm");
        String gmPlayerToken = joinAsPlayer(gmSession, info.code(), "gm");

        mockMvc.perform(post("/api/sessions/{code}/gm-npcs", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-Player-Token", gmPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CLEAR_RECENT_RESULTS",
                                  "playerId": "gm-npc-1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void otherPlayerCannotIssueNpcPlayerCommand() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player2", "player2@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player2");

        mockMvc.perform(post("/api/sessions/{code}/gm-npcs", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CLEAR_RECENT_RESULTS",
                                  "playerId": "gm-npc-1",
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

        markReady(info.code(), "player1", playerToken);

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void rawStartCombatRejectsUnreadyParticipants() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-raw-unready", "gm-raw-unready@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm-raw-unready");
        MockHttpSession playerSession = signUpAndLogin("raw-unready-p1", "raw-unready-p1@example.com", "password123");
        joinAsPlayer(playerSession, info.code(), "raw-unready-p1");

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "raw-unready-p1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.errors[0]").value(containsString("required players are ready")))
                .andExpect(jsonPath("$.errorDetails[0].code").value("READY_PARTICIPANT_REQUIRED"));
    }

    @Test
    void rawStartCombatRejectsInvalidDecks() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-raw-invalid-deck", "gm-raw-invalid-deck@example.com", "password123");
        SessionInfo info = createSessionInfo(gmSession, "gm-raw-invalid-deck");
        MockHttpSession playerSession = signUpAndLogin("raw-invalid-deck-p1", "raw-invalid-deck-p1@example.com", "password123");
        long characterId = createCharacterWithEmptySkillDeck();
        String playerToken = joinAsPlayer(playerSession, info.code(), "raw-invalid-deck-p1", characterId);
        markReady(info.code(), "raw-invalid-deck-p1", playerToken);

        mockMvc.perform(post("/api/sessions/{code}/command", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "raw-invalid-deck-p1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.errors[0]").value(containsString("decks are invalid")))
                .andExpect(jsonPath("$.errorDetails[0].code").value("DECK_INVALID"));
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
                  "deckOwnedCardIds": [
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

    private String joinAsPlayer(MockHttpSession session, String code, String playerId, long characterId) throws Exception {
        MvcResult join = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s",
                                  "characterId": %d
                                }
                                """.formatted(playerId, characterId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(join.getResponse().getContentAsString());
        return node.get("playerToken").asText();
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

    private CombatPlaySetup prepareAttackCardInHand(String code, String playerId) {
        return sessionLifecycleService.withLockedSession(code, rt -> {
            PlayerId pid = new PlayerId(playerId);
            PlayerState player = rt.state().player(pid);
            assertNotNull(player);
            assertNotNull(rt.state().combat());

            rt.state().combat().turnOrder().clear();
            rt.state().combat().turnOrder().add(TargetRef.ofPlayer(pid));
            rt.state().combat().currentTurnIndex(0);
            rt.state().combat().phase(CombatPhase.MAIN);

            CardInstId attackCard = player.hand().stream()
                    .filter(id -> isCardDef(rt.state().card(id), "C001"))
                    .findFirst()
                    .orElse(null);
            if (attackCard == null) {
                attackCard = player.deck().stream()
                        .filter(id -> isCardDef(rt.state().card(id), "C001"))
                        .findFirst()
                        .orElseThrow();
                ZoneOps.moveToZoneOrVanishIfToken(rt.state(), rt.ctx(), player, attackCard, Zone.HAND, new ArrayList<>());
            }

            var enemy = rt.state().enemies().entrySet().stream().findFirst().orElseThrow();
            return new CombatPlaySetup(
                    attackCard.value().toString(),
                    enemy.getKey().value(),
                    enemy.getValue().hp(),
                    player.attackPower(),
                    player.ap(),
                    rt.state().version()
            );
        });
    }

    private boolean isCardDef(CardInstance card, String cardDefId) {
        return card != null && card.defId() != null && cardDefId.equals(card.defId().value());
    }

    private boolean hasDamageLog(JsonNode body) {
        JsonNode events = body.path("events");
        if (!events.isArray()) {
            return false;
        }
        for (JsonNode event : events) {
            JsonNode payload = event.path("payload");
            if ("COMBAT_LOG_APPENDED".equals(event.path("type").asText())
                    && "combat.damage".equals(payload.path("type").asText())) {
                return true;
            }
            if ("LOG_APPENDED".equals(event.path("type").asText())
                    && payload.path("line").asText("").contains("deals")) {
                return true;
            }
        }
        return false;
    }

    private long createCharacterWithStatsAndLoadout(String name,
                                                    int physical,
                                                    int technique,
                                                    int sense,
                                                    int willpower) {
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name(name)
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("test")
                .disposition("neutral")
                .oneLiner("stats")
                .story("stats")
                .physical(physical)
                .technique(technique)
                .sense(sense)
                .willpower(willpower)
                .trait1("P001")
                .trait2("P002")
                .build());
        seedLoadout(
                characterCardCollectionService,
                characterLoadoutService,
                profile.getId(),
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901"
        );
        return profile.getId();
    }

    private long createCharacterWithEmptySkillDeck() {
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("Raw Command Empty Deck Character")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("test")
                .disposition("neutral")
                .oneLiner("raw command")
                .story("raw command")
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
                List.of(),
                "EX901"
        );
        return profile.getId();
    }

    private record CombatPlaySetup(
            String cardId,
            String enemyId,
            int enemyHpBefore,
            int attackPower,
            int apBefore,
            long expectedVersion
    ) {}

    private record SessionInfo(String code, String gmToken) {}
}
