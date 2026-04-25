package com.example.dueltower.session.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.preset.domain.Preset;
import com.example.dueltower.preset.repository.PresetRepository;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionPresetApplyIntegrationTest {

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
        presetRepository.deleteAll();
        characterProfileRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void applyPresetToLoadoutSucceedsForSelfAndReflectsState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        CharacterProfile profile = createCharacterProfile(
                "세션 프리셋 적용 캐릭터",
                "P001",
                "P002",
                "[\"Tig001_Card\",\"Tig001_Card\",\"Tig001_Card\",\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\"]",
                List.of("Tig001_Card", "Tig001_Card", "Tig001_Card", "C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003"),
                "{\"id\":\"Tig901_EX\"}"
        );
        long presetId = createPreset(
                "player1",
                "적용 프리셋",
                profile.getId(),
                List.of("Tig001_Card", "Tig001_Card", "Tig001_Card", "C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003"),
                "Tig901_EX",
                List.of("P001", "P002")
        );

        MvcResult applyResult = mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(sessionInfo.code()))
                .andExpect(jsonPath("$.players.player1.playerId").value("player1"))
                .andExpect(jsonPath("$.players.player1.passiveIds[0]").value("P001"))
                .andExpect(jsonPath("$.players.player1.passiveIds[1]").value("P002"))
                .andExpect(jsonPath("$.players.player1.ownedCards[0].cardId").value("Tig001_Card"))
                .andExpect(jsonPath("$.players.player1.deckOwnedCardIds.length()").value(12))
                .andReturn();

        JsonNode response = JSON.readTree(applyResult.getResponse().getContentAsString());
        String exCardInstanceId = response.path("players").path("player1").path("exCard").asText();
        String exCardDefId = response.path("cards").path(exCardInstanceId).path("defId").asText();
        org.junit.jupiter.api.Assertions.assertEquals("Tig901_EX", exCardDefId);
    }

    @Test
    void applyPresetToLoadoutFailsWithoutPlayerToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applyPresetToLoadoutFailsWhenPathPlayerDiffersFromTokenOwner() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, sessionInfo.code(), "player1");
        joinAsPlayer(player2Session, sessionInfo.code(), "player2");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player2")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyPresetToLoadoutFailsForForeignOwnerPreset() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession player1Session = signUpAndLogin("player1", "player1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("player2", "player2@example.com", "password123");
        String player1Token = joinAsPlayer(player1Session, sessionInfo.code(), "player1");
        joinAsPlayer(player2Session, sessionInfo.code(), "player2");

        CharacterProfile profile = createCharacterProfile(
                "타인 프리셋 캐릭터",
                "P001",
                null,
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "{\"id\":\"EX901\"}"
        );
        long foreignPresetId = createPreset(
                "player2",
                "player2 preset",
                profile.getId(),
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901",
                List.of("P001")
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(foreignPresetId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyPresetToLoadoutFailsForMissingPresetId() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(999_999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyPresetToLoadoutFailsWhenPresetCharacterIsInvalid() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        long presetId = createPreset(
                "player1",
                "invalid character preset",
                999_999L,
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901",
                List.of("P001")
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyPresetToLoadoutFailsWhenPresetExCardIsInvalid() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        CharacterProfile profile = createCharacterProfile(
                "ex invalid 캐릭터",
                "P001",
                null,
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "{\"id\":\"EX901\"}"
        );
        long presetId = createPreset(
                "player1",
                "invalid ex preset",
                profile.getId(),
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "UNKNOWN_EX",
                List.of("P001")
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyPresetToLoadoutFailsWhenPresetPassiveIdsAreInvalid() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        CharacterProfile profile = createCharacterProfile(
                "passive invalid 캐릭터",
                "P001",
                null,
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "{\"id\":\"EX901\"}"
        );
        long presetId = createPreset(
                "player1",
                "invalid passive preset",
                profile.getId(),
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901",
                List.of("INVALID_PASSIVE")
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyPresetToLoadoutFailsWhenPresetDeckCardIdsAreInvalid() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        CharacterProfile profile = createCharacterProfile(
                "deck invalid 캐릭터",
                "P001",
                null,
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "{\"id\":\"EX901\"}"
        );
        long presetId = createPreset(
                "player1",
                "invalid deck preset",
                profile.getId(),
                List.of("UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD", "UNKNOWN_CARD"),
                "EX901",
                List.of("P001")
        );

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyPresetToLoadoutFailsWhenSessionStateDisallowsLoadoutEdit() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo sessionInfo = createSession(gmSession, "gm");

        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, sessionInfo.code(), "player1");

        CharacterProfile profile = createCharacterProfile(
                "전투중 수정 금지 캐릭터",
                "P001",
                null,
                "[\"C001\",\"C001\",\"C001\",\"C002\",\"C002\",\"C002\",\"C003\",\"C003\",\"C003\",\"C004\",\"C004\",\"C004\"]",
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "{\"id\":\"EX901\"}"
        );
        long presetId = createPreset(
                "player1",
                "combat lock preset",
                profile.getId(),
                List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004"),
                "EX901",
                List.of("P001")
        );

        markReady(sessionInfo.code(), "player1", playerToken);

        mockMvc.perform(post("/api/sessions/{code}/command", sessionInfo.code())
                        .header("X-GM-Token", sessionInfo.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "playerId": "player1",
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{code}/players/{playerId}/loadout/from-preset", sessionInfo.code(), "player1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(presetId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DECK_EDIT_FORBIDDEN"));
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

    private CharacterProfile createCharacterProfile(
            String name,
            String trait1,
            String trait2,
            String ownedCards,
            List<String> currentSkillDeck,
            String exCard
    ) {
        return characterProfileRepository.save(CharacterProfile.builder()
                .name(name)
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("중립")
                .oneLiner("테스트")
                .story("테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1(trait1)
                .trait2(trait2)
                .ownedCards(ownedCards)
                .currentSkillDeck(currentSkillDeck)
                .exCard(exCard)
                .build());
    }

    private long createPreset(
            String ownerUsername,
            String name,
            long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
        return presetRepository.save(Preset.create(ownerUsername, name, characterId, deckCardIds, exCardId, passiveIds)).getId();
    }

    private String applyBody(long presetId) {
        return """
                {
                  "presetId": %d
                }
                """.formatted(presetId);
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

        return (MockHttpSession) session;
    }

    private record SessionInfo(String code, String gmToken) {
    }
}
