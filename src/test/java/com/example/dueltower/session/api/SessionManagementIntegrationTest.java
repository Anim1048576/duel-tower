package com.example.dueltower.session.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.preset.domain.Preset;
import com.example.dueltower.preset.repository.PresetRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private PresetRepository presetRepository;

    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
        presetRepository.deleteAll();
    }

    @Test
    @DisplayName("세션 나가기는 플레이어 토큰이 없으면 실패한다")
    void leaveFailsWithoutPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm").code();
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/leave", code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션 나가기는 성공 시 현재 플레이어를 상태에서 제거한다")
    void leaveSuccessRemovesCurrentPlayerFromState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        String code = createSession(gmSession, "gm").code();
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, code, "player1");

        mockMvc.perform(post("/api/sessions/{code}/leave", code)
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1").doesNotExist());
    }

    @Test
    @DisplayName("강퇴는 유효한 GM 토큰이 필요하다")
    void kickRequiresValidGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/kick", info.code(), "player1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"테스트\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/kick", info.code(), "player1")
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"테스트\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1").doesNotExist());
    }

    @Test
    void gmOnlyEndpointsRejectPlayerTokenAndInvalidGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/kick", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .header("X-GM-Token", "not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/sessions/{code}", info.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리셋은 기본값으로 플레이어를 유지하고 run state를 다시 초기화한다")
    void resetKeepsPlayersByDefaultAndReinitializesRunState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1").exists())
                .andExpect(jsonPath("$.players.player1.deckOwnedCardIds.length()").value(12))
                .andExpect(jsonPath("$.nodeState").value("NON_COMBAT"))
                .andExpect(jsonPath("$.run.floor").value(1));
    }

    @Test
    @DisplayName("리셋은 GM 토큰이 없으면 실패한다")
    void resetFailsWithoutGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("keepPlayers=false 리셋은 플레이어를 비우고 세션 코드는 유지한다")
    void resetWithKeepPlayersFalseClearsPlayersAndKeepsSessionCode() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keepPlayers": false,
                                  "newSeed": 12345
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(info.code()))
                .andExpect(jsonPath("$.players").isEmpty())
                .andExpect(jsonPath("$.seed").exists());
    }

    @Test
    @DisplayName("keepLoadouts=false 리셋은 덱, EX, 패시브를 초기화한다")
    void resetWithKeepLoadoutsFalseResetsDeckExAndPassives() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passiveIds": ["P001", "P002"],
                                  "exCardId": "EX901"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1.passiveIds.length()").value(2));

        mockMvc.perform(post("/api/sessions/{code}/reset", info.code())
                        .header("X-GM-Token", info.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keepPlayers": true,
                                  "keepLoadouts": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1.passiveIds").isEmpty())
                .andExpect(jsonPath("$.players.player1.deckOwnedCardIds.length()").value(12))
                .andExpect(jsonPath("$.players.player1.exCard").exists());
    }

    @Test
    @DisplayName("삭제는 GM 토큰이 없으면 실패한다")
    void deleteFailsWithoutGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");

        mockMvc.perform(delete("/api/sessions/{code}", info.code()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GM 토큰으로 삭제하면 세션을 제거하고 204를 반환한다")
    void deleteWithGmTokenRemovesSessionAndReturns204() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");

        mockMvc.perform(delete("/api/sessions/{code}", info.code())
                        .header("X-GM-Token", info.gmToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sessions/{code}", info.code()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로드아웃 수정은 플레이어 토큰으로 자신의 정보 수정만 허용한다")
    void updateLoadoutAllowsSelfUpdateWithPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("로드아웃 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("중립")
                .oneLiner("로드아웃")
                .story("loadout")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2("P002")
                .ownedCards("[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]")
                .currentSkillDeck(List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"))
                .exCard("{\"id\":\"EX901\"}")
                .build());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": %d
                                }
                                """.formatted(profile.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1.passiveIds[0]").value("P001"));
    }

    @Test
    @DisplayName("로드아웃 수정은 경로의 player ID가 다르면 거부한다")
    void updateLoadoutRejectsDifferentPathPlayerId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, info.code(), "player1");
        joinAsPlayer(player2Session, info.code(), "player2");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player2")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passiveIds": ["P001"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("loadout preview는 본인만 가능하며 응답 echo/signature를 포함하고 세션 상태를 바꾸지 않는다")
    void previewLoadoutAllowsSelfOnlyAndDoesNotMutateState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayerWithOwnedCards(
                playerSession,
                info.code(),
                "player1",
                """
                        [
                          {"ownedCardId":"oc-01","cardId":"C001"},
                          {"ownedCardId":"oc-02","cardId":"C001"},
                          {"ownedCardId":"oc-03","cardId":"C001"},
                          {"ownedCardId":"oc-04","cardId":"C002"},
                          {"ownedCardId":"oc-05","cardId":"C002"},
                          {"ownedCardId":"oc-06","cardId":"C002"},
                          {"ownedCardId":"oc-07","cardId":"C003"},
                          {"ownedCardId":"oc-08","cardId":"C003"},
                          {"ownedCardId":"oc-09","cardId":"C003"},
                          {"ownedCardId":"oc-10","cardId":"C004"},
                          {"ownedCardId":"oc-11","cardId":"C004"},
                          {"ownedCardId":"oc-12","cardId":"C004"},
                          {"ownedCardId":"oc-13","cardId":"C005"}
                        ]
                        """,
                """
                        [
                          "oc-01","oc-02","oc-03",
                          "oc-04","oc-05","oc-06",
                          "oc-07","oc-08","oc-09",
                          "oc-10","oc-11","oc-12"
                        ]
                        """
        );

        MvcResult previewResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/preview", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckOwnedCardIds": [
                                    "oc-01","oc-02","oc-03",
                                    "oc-04","oc-05","oc-06",
                                    "oc-07","oc-08","oc-09",
                                    "oc-10","oc-11","oc-13"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.deckOwnedCardIds[11]").value("oc-13"))
                .andExpect(jsonPath("$.draftSignature").isNotEmpty())
                .andExpect(jsonPath("$.deckEditor.deck.changedCardCount").value(1))
                .andReturn();

        JsonNode previewBody = JSON.readTree(previewResult.getResponse().getContentAsString());
        assertThat(previewBody.path("deckEditor").path("deck").path("saveAllowed").asBoolean()).isTrue();

        MvcResult stateResult = mockMvc.perform(get("/api/sessions/{code}", info.code()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode stateBody = JSON.readTree(stateResult.getResponse().getContentAsString());
        assertThat(stateBody.path("players").path("player1").path("deckOwnedCardIds").get(11).asText()).isEqualTo("oc-12");
    }

    @Test
    @DisplayName("loadout preview는 다른 playerId 경로에 대해 거부된다")
    void previewLoadoutRejectsDifferentPathPlayerId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, info.code(), "player1");
        joinAsPlayer(player2Session, info.code(), "player2");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/preview", info.code(), "player2")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckOwnedCardIds": []
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("loadout preview 응답은 locked 카드, 장수 제한, 교체 제한, 동일 카드 제한을 드러낸다")
    void previewLoadoutReflectsDeckEditorViolations() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayerWithOwnedCards(
                playerSession,
                info.code(),
                "player1",
                """
                        [
                          {"ownedCardId":"oc-01","cardId":"C001","lockedInDeck":true},
                          {"ownedCardId":"oc-02","cardId":"C001"},
                          {"ownedCardId":"oc-03","cardId":"C001"},
                          {"ownedCardId":"oc-04","cardId":"C001"},
                          {"ownedCardId":"oc-05","cardId":"C002"},
                          {"ownedCardId":"oc-06","cardId":"C002"},
                          {"ownedCardId":"oc-07","cardId":"C002"},
                          {"ownedCardId":"oc-08","cardId":"C003"},
                          {"ownedCardId":"oc-09","cardId":"C003"},
                          {"ownedCardId":"oc-10","cardId":"C003"},
                          {"ownedCardId":"oc-11","cardId":"C004"},
                          {"ownedCardId":"oc-12","cardId":"C004"},
                          {"ownedCardId":"oc-13","cardId":"C004"},
                          {"ownedCardId":"oc-14","cardId":"C005"},
                          {"ownedCardId":"oc-15","cardId":"C005"},
                          {"ownedCardId":"oc-16","cardId":"C005"},
                          {"ownedCardId":"oc-17","cardId":"C005"}
                        ]
                        """,
                """
                        [
                          "oc-01","oc-02","oc-03",
                          "oc-05","oc-06","oc-07",
                          "oc-08","oc-09","oc-10",
                          "oc-11","oc-12","oc-13"
                        ]
                        """
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/preview", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckOwnedCardIds": [
                                    "oc-01","oc-02","oc-03",
                                    "oc-05","oc-06","oc-07",
                                    "oc-08","oc-09","oc-10",
                                    "oc-11","oc-12","oc-13"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deckEditor.draftEntries[0].ownedCardId").value("oc-01"))
                .andExpect(jsonPath("$.deckEditor.draftEntries[0].canRemove").value(false))
                .andExpect(jsonPath("$.deckEditor.draftEntries[0].reasonCodes[0]").value("LOCKED_CARD"));

        MvcResult previewResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/preview", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckOwnedCardIds": [
                                    "oc-02","oc-03","oc-04",
                                    "oc-05","oc-06","oc-07",
                                    "oc-08","oc-09","oc-10",
                                    "oc-14","oc-15","oc-16","oc-17"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deckEditor.deck.draftDeckSize").value(13))
                .andExpect(jsonPath("$.deckEditor.deck.saveAllowed").value(false))
                .andReturn();

        JsonNode body = JSON.readTree(previewResult.getResponse().getContentAsString());
        List<String> globalReasonCodes = jsonTextValues(body.path("deckEditor").path("globalReasonCodes"));
        assertThat(globalReasonCodes).contains(
                "INVALID_DECK_SIZE",
                "LOCKED_CARD",
                "REPLACEMENT_LIMIT_REACHED",
                "CARD_LIMIT_REACHED"
        );
        JsonNode c005Group = findCardPoolGroup(body.path("deckEditor").path("cardPoolGroups"), "C005");
        assertThat(c005Group).isNotNull();
        assertThat(c005Group.path("canAdd").asBoolean()).isFalse();
        assertThat(jsonTextValues(c005Group.path("reasonCodes"))).contains("DECK_FULL", "CARD_LIMIT_REACHED");
    }

    @Test
    @DisplayName("로드아웃 수정은 character ID, passive, deck을 검증한다")
    void updateLoadoutValidatesCharacterIdPassiveAndDeck() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": -1
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passiveIds": ["INVALID_PASSIVE"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deckOwnedCardIds": [
                                    "unknown","unknown","unknown","unknown","unknown","unknown",
                                    "unknown","unknown","unknown","unknown","unknown","unknown"
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exCardId": "INVALID_EX"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로드아웃에 프리셋 적용은 플레이어 토큰으로 자신의 프리셋 적용만 허용한다")
    void applyPresetToLoadoutAllowsSelfApplyWithPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("프리셋 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("중립")
                .oneLiner("프리셋")
                .story("preset apply")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2("P002")
                .ownedCards("[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]")
                .currentSkillDeck(List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"))
                .exCard("{\"id\":\"EX901\"}")
                .build());
        Preset preset = presetRepository.save(Preset.create(
                "player1",
                "세션 적용 프리셋",
                profile.getId(),
                List.of(
                        "C001", "C001", "C001",
                        "C002", "C002", "C002",
                        "C003", "C003", "C003",
                        "C004", "C004", "C004"
                ),
                "EX901",
                List.of("P001", "P002")
        ));

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "presetId": %d
                                }
                                """.formatted(preset.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.player1.passiveIds[0]").value("P001"))
                .andExpect(jsonPath("$.players.player1.passiveIds[1]").value("P002"))
                .andExpect(jsonPath("$.players.player1.deckOwnedCardIds.length()").value(12));
    }

    @Test
    @DisplayName("로드아웃에 프리셋 적용은 다른 플레이어의 프리셋이면 거부한다")
    void applyPresetToLoadoutRejectsOtherPlayersPreset() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, info.code(), "player1");
        joinAsPlayer(player2Session, info.code(), "player2");

        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("타인 프리셋")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("중립")
                .oneLiner("타인")
                .story("owner scope")
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
        Preset foreignPreset = presetRepository.save(Preset.create(
                "player2",
                "player2 preset",
                profile.getId(),
                List.of(
                        "C001", "C001", "C001",
                        "C002", "C002", "C002",
                        "C003", "C003", "C003",
                        "C004", "C004", "C004"
                ),
                "EX901",
                List.of("P001")
        ));

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", info.code(), "player1")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "presetId": %d
                                }
                                """.formatted(foreignPreset.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로드아웃에 프리셋 적용은 경로의 player ID가 다르면 거부한다")
    void applyPresetToLoadoutRejectsDifferentPathPlayerId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, info.code(), "player1");
        joinAsPlayer(player2Session, info.code(), "player2");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", info.code(), "player2")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "presetId": 1
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로드아웃에 프리셋 적용은 플레이어 토큰이 필요하다")
    void applyPresetToLoadoutRequiresPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", info.code(), "player1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "presetId": 1
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로드아웃에 프리셋 적용은 preset ID가 필요하다")
    void applyPresetToLoadoutRequiresPresetId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo info = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, info.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", info.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private SessionInfo createSession(MockHttpSession session, String gmId) throws Exception {
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

        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return new SessionInfo(node.path("code").asText(), node.path("gmToken").asText());
    }

    private String joinAsPlayer(MockHttpSession session, String code, String playerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s",
                                  "presetDeckCardIds": [
                                    "C001", "C001", "C001",
                                    "C002", "C002", "C002",
                                    "C003", "C003", "C003",
                                    "C004", "C004", "C004"
                                  ]
                                }
                                """.formatted(playerId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return node.path("playerToken").asText();
    }

    private String joinAsPlayerWithOwnedCards(MockHttpSession session,
                                              String code,
                                              String playerId,
                                              String ownedCardsJson,
                                              String presetDeckOwnedCardIdsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s",
                                  "ownedCards": %s,
                                  "presetDeckOwnedCardIds": %s
                                }
                                """.formatted(playerId, ownedCardsJson, presetDeckOwnedCardIdsJson)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return node.path("playerToken").asText();
    }

    private JsonNode findCardPoolGroup(JsonNode groups, String cardId) {
        for (JsonNode group : groups) {
            if (cardId.equals(group.path("cardId").asText())) {
                return group;
            }
        }
        return null;
    }

    private List<String> jsonTextValues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return List.copyOf(values);
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

    private record SessionInfo(String code, String gmToken) {}
}
