package com.example.dueltower.screen.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.preset.domain.Preset;
import com.example.dueltower.preset.repository.PresetRepository;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionRequest;
import com.example.dueltower.screen.support.ScreenApiContractTestSupport;
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
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreenControllerIntegrationTest extends ScreenApiContractTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private PresetRepository presetRepository;

    @BeforeEach
    void setUp() {
        presetRepository.deleteAll();
        deckRepository.deleteAll();
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
    }

    @Test
    void playerLobbyScreenRequiresPlayerLobbyAccessAndReturnsResolvedScreenModel() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "player1", characterId);
        createPreset("player1", characterId);

        MvcResult unauthorized = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertApiErrorContract(unauthorized, 401);

        MvcResult forbiddenForGm = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .session(gmSession))
                .andExpect(status().isForbidden())
                .andReturn();
        assertApiErrorContract(forbiddenForGm, 403);

        MvcResult authorized = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = assertBaseScreenContract(authorized, "PlayerLobby");
        assertThat(body.path("sessionCode").asText()).isEqualTo(session.code());
        assertThat(body.path("version").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(body.path("routeTemplate").asText()).isEqualTo("/api/screens/sessions/{code}/player-lobby");
        assertThat(body.path("policyGroup").asText()).isEqualTo("SESSION_READABLE");
        assertThat(body.path("auth").asText()).isEqualTo("sessionReadable");
        assertThat(body.path("participantSlots")).hasSize(1);
        assertThat(body.path("participantSlots").get(0).path("slot").asText()).isEqualTo("P1");
        assertThat(body.path("participantSlots").get(0).path("name").asText()).contains("player1");
        assertThat(body.path("participantSlots").get(0).path("state").asText()).isEqualTo("You / Joined");
        assertThat(body.path("participantSlots").get(0).path("tone").asText()).isEqualTo("accent");
        assertThat(body.path("participantSlots").get(0).path("note").asText()).contains("Deck");
        assertThat(body.path("me").path("playerId").asText()).isEqualTo("player1");
        assertThat(body.path("me").path("ready").asBoolean()).isFalse();
        assertThat(body.path("me").path("loadout").path("characterId").asLong()).isEqualTo(characterId);
        assertThat(body.path("me").path("loadout").path("characterLabel").asText()).contains("#" + characterId);
        assertThat(body.path("me").path("loadout").path("exCardId").asText()).isEqualTo("EX901");
        assertThat(body.path("me").path("loadout").path("deckOwnedCardIds")).isNotEmpty();
        assertThat(body.path("me").path("draft").path("characterId").asLong()).isEqualTo(characterId);
        assertThat(body.path("me").path("draftFlags").path("dirty").asBoolean()).isFalse();
        assertThat(body.path("me").path("draftFlags").path("deckEditingLocked").asBoolean()).isFalse();
        assertThat(body.path("me").path("draftFlags").path("requiredFieldsMissing").asBoolean()).isFalse();
        assertThat(body.path("references").path("characterOptions")).isNotEmpty();
        assertThat(body.path("references").path("exCardOptions")).isNotEmpty();
        assertThat(body.path("references").path("passiveOptions")).isNotEmpty();
        assertThat(body.path("references").path("ownedCardOptions")).isNotEmpty();
        assertThat(body.path("references").path("characterOptions").get(0).path("label").asText()).isNotBlank();
        assertThat(body.path("references").path("characterOptions").get(0).path("tags").isArray()).isTrue();
        assertThat(body.path("references").path("ownedCardOptions").get(0).path("ownedCardId").asText()).isNotBlank();
        assertThat(body.path("references").path("ownedCardOptions").get(0).path("label").asText()).isNotBlank();
        assertThat(body.path("references").path("ownedCardOptions").get(0).path("subtitle").asText()).isNotBlank();
        assertThat(body.path("references").path("ownedCardOptions").get(0).path("tags").isArray()).isTrue();
        assertThat(body.path("presets").path("items")).hasSize(1);
        assertThat(body.path("presets").path("selectedId").asLong()).isPositive();
        assertThat(body.path("presets").path("items").get(0).path("label").asText()).isNotBlank();
        assertThat(body.path("presets").path("items").get(0).path("subtitle").asText()).isNotBlank();
        assertThat(body.path("presets").path("preview").path("characterLabel").asText()).contains("#" + characterId);
        assertThat(body.path("presets").path("preview").path("exLabel").asText()).contains("EX901");
        assertThat(body.path("presets").path("preview").path("deckItems")).hasSize(2);
        assertThat(body.path("presets").path("preview").path("passiveItems")).hasSize(1);
        assertThat(body.path("presets").path("preview").path("deckItems").get(0).path("label").asText()).contains("C001");
        assertThat(body.path("presets").path("preview").path("deckItems").get(0).path("tags").isArray()).isTrue();
        assertThat(body.path("presets").path("preview").path("passiveItems").get(0).path("label").asText()).contains("Tig001_Passive");

        JsonNode toggleReadyAction = findAction(body, "playerLobby.toggleReady");
        JsonNode leaveAction = findAction(body, "playerLobby.leave");
        JsonNode saveLoadoutAction = findAction(body, "playerLobby.saveLoadout");
        JsonNode applyPresetAction = findAction(body, "playerLobby.applyPreset");
        assertThat(body.path("possibleActions")).hasSize(4);
        assertActionContract(toggleReadyAction);
        assertActionContract(leaveAction);
        assertActionContract(saveLoadoutAction);
        assertActionContract(applyPresetAction);
        assertThat(toggleReadyAction.path("label").asText()).isEqualTo("Mark ready");
        assertThat(toggleReadyAction.path("auth").asText()).isEqualTo("playerToken");
        assertThat(toggleReadyAction.path("payloadTemplate").path("ready").asBoolean()).isTrue();
        assertThat(saveLoadoutAction.path("payloadTemplate").path("characterId").asLong()).isEqualTo(characterId);
        assertThat(applyPresetAction.path("enabled").asBoolean()).isTrue();
        assertThat(applyPresetAction.path("payloadTemplate").path("presetId").asLong()).isPositive();
    }

    @Test
    void sessionScreenAllowsLoginFallbackForRelatedUser() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("gm-fallback-p1", "gm-fallback-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-fallback-p1");
        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "gm-fallback-p1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .session(gmSession))
                .andExpect(status().isOk())
                .andReturn();

        var body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("routeTemplate").asText()).isEqualTo("/api/screens/sessions/{code}/gm-lobby");
        assertThat(body.path("policyGroup").asText()).isEqualTo("SESSION_READABLE");
        JsonNode startCombatAction = findAction(body, "gmLobby.startCombat");
        assertThat(startCombatAction.path("enabled").asBoolean()).isTrue();
        assertThat(startCombatAction.path("auth").asText()).isEqualTo("loginCookie");
        assertThat(startCombatAction.path("href").asText()).isEqualTo("/api/screens/sessions/" + session.code() + "/gm-lobby/start-combat");
    }

    @Test
    void playerLobbyScreenExposesMissingCharacterStateAndDisablesPresetApplyWhenArchiveIsEmpty() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-missing", "gm-missing@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-missing");
        MockHttpSession playerSession = signUpAndLogin("player-missing", "player-missing@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "player-missing", null);

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "PlayerLobby");
        assertThat(body.path("me").path("loadout").path("characterId").isNull()).isTrue();
        assertThat(body.path("me").path("draftFlags").path("dirty").asBoolean()).isFalse();
        assertThat(body.path("me").path("draftFlags").path("deckEditingLocked").asBoolean()).isTrue();
        assertThat(body.path("me").path("draftFlags").path("requiredFieldsMissing").asBoolean()).isTrue();
        assertThat(body.path("references").path("ownedCardOptions")).isNotEmpty();
        assertThat(body.path("presets").path("items")).hasSize(0);
        assertThat(body.path("presets").path("selectedId").isNull()).isTrue();
        assertThat(body.path("presets").path("preview").isNull()).isTrue();

        JsonNode applyPresetAction = findAction(body, "playerLobby.applyPreset");
        assertDisabledActionContract(applyPresetAction);
        assertThat(applyPresetAction.path("disabledReason").path("code").asText()).isEqualTo("PRESET_REQUIRED");
        assertThat(applyPresetAction.path("payloadTemplate").path("presetId").asLong()).isZero();
    }

    @Test
    void playerLobbyScreenUpdatesToggleReadyActionAfterReadyStateChanges() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-ready", "gm-ready@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-ready");
        MockHttpSession playerSession = signUpAndLogin("player-ready", "player-ready@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "player-ready", characterId);

        MvcResult beforeReady = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeReadyBody = assertBaseScreenContract(beforeReady, "PlayerLobby");
        assertThat(findAction(beforeReadyBody, "playerLobby.toggleReady").path("label").asText()).isEqualTo("Mark ready");

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "player-ready")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult afterReady = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode afterReadyBody = assertBaseScreenContract(afterReady, "PlayerLobby");
        assertThat(afterReadyBody.path("me").path("ready").asBoolean()).isTrue();
        assertThat(afterReadyBody.path("participantSlots").get(0).path("state").asText()).isEqualTo("You / Ready");
        assertThat(findAction(afterReadyBody, "playerLobby.toggleReady").path("label").asText()).isEqualTo("Mark not ready");
        assertThat(findAction(afterReadyBody, "playerLobby.toggleReady").path("payloadTemplate").path("ready").asBoolean()).isFalse();
    }

    @Test
    void gmLobbyScreenWithGmTokenReturnsCuratedParticipantCardsAndEnabledActions() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-screen", "gm-screen@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-screen");
        MockHttpSession player1Session = signUpAndLogin("gm-screen-p1", "gm-screen-p1@example.com", "password123");
        MockHttpSession player2Session = signUpAndLogin("gm-screen-p2", "gm-screen-p2@example.com", "password123");
        long characterId = createCharacter();
        String player1Token = joinAsPlayer(player1Session, session.code(), "gm-screen-p1", characterId);
        joinAsPlayer(player2Session, session.code(), "gm-screen-p2", characterId);

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "gm-screen-p1")
                        .header("X-Player-Token", player1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .header("X-GM-Token", session.gmToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("sessionCode").asText()).isEqualTo(session.code());
        assertThat(body.path("version").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(body.path("routeTemplate").asText()).isEqualTo("/api/screens/sessions/{code}/gm-lobby");
        assertThat(body.path("policyGroup").asText()).isEqualTo("SESSION_READABLE");
        assertThat(body.path("auth").asText()).isEqualTo("sessionReadable");
        assertThat(body.path("participantCards")).hasSize(2);
        assertThat(body.path("participantCards").get(0).path("slot").asText()).isEqualTo("P1");
        assertThat(body.path("participantCards").get(0).path("playerId").asText()).isEqualTo("gm-screen-p1");
        assertThat(body.path("participantCards").get(0).path("name").asText()).isEqualTo("gm-screen-p1");
        assertThat(body.path("participantCards").get(0).path("readyLabel").asText()).isEqualTo("Ready");
        assertThat(body.path("participantCards").get(0).path("readyTone").asText()).isEqualTo("success");
        assertThat(body.path("participantCards").get(0).path("characterSummary").asText()).contains("#" + characterId);
        assertThat(body.path("participantCards").get(0).path("exSummary").asText()).contains("EX901");
        assertThat(body.path("participantCards").get(0).path("passiveSummary").asText()).isNotBlank();
        assertThat(body.path("participantCards").get(0).path("deckSummary").asText()).isNotBlank();
        assertThat(body.path("participantCards").get(0).path("detailTags").isArray()).isTrue();
        assertThat(body.path("participantCards").get(0).path("detailTags")).isNotEmpty();
        assertThat(body.path("participantCards").get(0).path("detailTags").get(0).path("label").asText()).isNotBlank();
        assertThat(body.path("participantCards").get(0).path("detailTags").get(0).path("tone").asText()).isNotBlank();
        assertThat(body.path("startCombat").path("recommendedStartPlayerId").asText()).isEqualTo("gm-screen-p1");
        assertThat(body.path("startCombat").path("blockedReason").isNull()).isTrue();
        assertThat(body.path("startCombat").path("selectableStartPlayers")).hasSize(2);
        assertThat(body.path("startCombat").path("selectableStartPlayers").get(0).path("playerId").asText()).isEqualTo("gm-screen-p1");
        assertThat(body.path("startCombat").path("selectableStartPlayers").get(0).path("slot").asText()).isEqualTo("P1");
        assertThat(body.path("startCombat").path("selectableStartPlayers").get(0).path("label").asText()).contains("ready");
        assertThat(body.path("startCombat").path("selectableStartPlayers").get(0).path("ready").asBoolean()).isTrue();

        JsonNode kickAction = findAction(body, "gmLobby.kick");
        JsonNode resetAction = findAction(body, "gmLobby.reset");
        JsonNode startCombatAction = findAction(body, "gmLobby.startCombat");
        assertThat(body.path("possibleActions")).hasSize(3);
        assertActionContract(kickAction);
        assertActionContract(resetAction);
        assertActionContract(startCombatAction);
        assertThat(kickAction.path("enabled").asBoolean()).isTrue();
        assertThat(resetAction.path("enabled").asBoolean()).isTrue();
        assertThat(startCombatAction.path("enabled").asBoolean()).isTrue();
        assertThat(startCombatAction.path("auth").asText()).isEqualTo("gmToken");
        assertThat(startCombatAction.path("href").asText()).isEqualTo("/api/screens/sessions/" + session.code() + "/gm-lobby/start-combat");
        assertThat(startCombatAction.path("payloadTemplate").path("expectedVersion").asLong()).isEqualTo(body.path("version").asLong());
        assertThat(startCombatAction.path("payloadTemplate").path("playerId").asText()).isEqualTo("gm-screen-p1");
    }

    @Test
    void gmLobbyScreenBlocksStartWhenNoParticipantsJoined() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-empty", "gm-empty@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-empty");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .header("X-GM-Token", session.gmToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("participantCards")).hasSize(0);
        assertThat(body.path("startCombat").path("recommendedStartPlayerId").isNull()).isTrue();
        assertThat(body.path("startCombat").path("selectableStartPlayers")).hasSize(0);
        assertThat(body.path("startCombat").path("blockedReason").path("code").asText()).isEqualTo("PARTICIPANT_REQUIRED");

        JsonNode kickAction = findAction(body, "gmLobby.kick");
        JsonNode resetAction = findAction(body, "gmLobby.reset");
        JsonNode startCombatAction = findAction(body, "gmLobby.startCombat");
        assertDisabledActionContract(kickAction);
        assertThat(kickAction.path("disabledReason").path("code").asText()).isEqualTo("PLAYER_REQUIRED");
        assertThat(resetAction.path("enabled").asBoolean()).isTrue();
        assertDisabledActionContract(startCombatAction);
        assertThat(startCombatAction.path("disabledReason").path("code").asText()).isEqualTo("PARTICIPANT_REQUIRED");
    }

    @Test
    void gmLobbyScreenBlocksStartWhenNoParticipantIsReady() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-unready", "gm-unready@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-unready");
        MockHttpSession playerSession = signUpAndLogin("gm-unready-p1", "gm-unready-p1@example.com", "password123");
        joinAsPlayer(playerSession, session.code(), "gm-unready-p1");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .header("X-GM-Token", session.gmToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("participantCards")).hasSize(1);
        assertThat(body.path("participantCards").get(0).path("playerId").asText()).isEqualTo("gm-unready-p1");
        assertThat(body.path("participantCards").get(0).path("readyLabel").asText()).isEqualTo("Not ready");
        assertThat(body.path("startCombat").path("recommendedStartPlayerId").isNull()).isTrue();
        assertThat(body.path("startCombat").path("blockedReason").path("code").asText()).isEqualTo("READY_PARTICIPANT_REQUIRED");
        assertThat(body.path("startCombat").path("selectableStartPlayers")).hasSize(1);
        assertThat(body.path("startCombat").path("selectableStartPlayers").get(0).path("ready").asBoolean()).isFalse();

        JsonNode startCombatAction = findAction(body, "gmLobby.startCombat");
        assertDisabledActionContract(startCombatAction);
        assertThat(startCombatAction.path("disabledReason").path("code").asText()).isEqualTo("READY_PARTICIPANT_REQUIRED");
    }

    @Test
    void gmLobbyScreenAllowsReadAccessButDisablesGmActionsWithoutGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-readonly", "gm-readonly@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-readonly");
        MockHttpSession playerSession = signUpAndLogin("gm-readonly-p1", "gm-readonly-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-readonly-p1");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("participantCards")).hasSize(1);

        JsonNode kickAction = findAction(body, "gmLobby.kick");
        JsonNode resetAction = findAction(body, "gmLobby.reset");
        JsonNode startCombatAction = findAction(body, "gmLobby.startCombat");
        assertDisabledActionContract(kickAction);
        assertDisabledActionContract(resetAction);
        assertDisabledActionContract(startCombatAction);
        assertThat(kickAction.path("disabledReason").path("code").asText()).isEqualTo("GM_TOKEN_REQUIRED");
        assertThat(resetAction.path("disabledReason").path("code").asText()).isEqualTo("GM_TOKEN_REQUIRED");
        assertThat(startCombatAction.path("disabledReason").path("code").asText()).isEqualTo("GM_ACCESS_REQUIRED");
    }

    @Test
    void gmLobbyStartCombatActionRestoresGmAccessAndStartsCombat() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-action", "gm-action@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-action");
        MockHttpSession playerSession = signUpAndLogin("gm-action-p1", "gm-action-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-action-p1");

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "gm-action-p1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new GmLobbyStartCombatActionRequest(null, "gm-action-p1"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("STARTED");
        assertThat(body.path("gmAccessRestored").asBoolean()).isTrue();
        assertThat(body.path("restoredGmToken").asText()).isEqualTo(session.gmToken());
        assertThat(body.path("retryUsed").asBoolean()).isFalse();
        assertThat(body.path("nextRoute").asText()).isEqualTo("/sessions/" + session.code() + "/combat");
        assertThat(body.path("combatEntryHint").asText()).isEqualTo("navigate");
        assertThat(body.path("disabledReason").isNull()).isTrue();
        assertThat(body.path("latestScreen").isNull()).isTrue();
    }

    @Test
    void gmLobbyStartCombatActionFailsWhenGmAccessCannotBeRestored() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-action-fail", "gm-action-fail@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-action-fail");
        MockHttpSession playerSession = signUpAndLogin("gm-action-fail-p1", "gm-action-fail-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-action-fail-p1");

        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "gm-action-fail-p1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("outcome").asText()).isEqualTo("GM_ACCESS_REQUIRED");
        assertThat(body.path("disabledReason").path("code").asText()).isEqualTo("GM_ACCESS_RESTORE_FAILED");
        assertThat(body.path("nextRoute").isNull()).isTrue();
        assertThat(body.path("latestScreen").path("screenKey").asText()).isEqualTo("GmLobby");
        assertThat(body.path("latestScreen").path("startCombat").path("blockedReason").path("code").asText())
                .isEqualTo("READY_PARTICIPANT_REQUIRED");
        assertThat(findAction(body.path("latestScreen"), "gmLobby.startCombat").path("disabledReason").path("code").asText())
                .isEqualTo("GM_ACCESS_REQUIRED");
    }

    @Test
    void gmLobbyStartCombatActionRetriesOnceOnVersionMismatch() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-action-retry", "gm-action-retry@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-action-retry");
        MockHttpSession playerSession = signUpAndLogin("gm-action-retry-p1", "gm-action-retry-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-action-retry-p1");

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "gm-action-retry-p1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .header("X-GM-Token", session.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": -1,
                                  "playerId": "gm-action-retry-p1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("STARTED");
        assertThat(body.path("retryUsed").asBoolean()).isTrue();
        assertThat(body.path("nextRoute").asText()).isEqualTo("/sessions/" + session.code() + "/combat");
    }

    @Test
    void gmLobbyStartCombatActionTreatsAlreadyStartedCombatAsTransition() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-action-active", "gm-action-active@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-action-active");
        MockHttpSession playerSession = signUpAndLogin("gm-action-active-p1", "gm-action-active-p1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "gm-action-active-p1");

        mockMvc.perform(put("/api/sessions/{code}/players/{playerId}/ready", session.code(), "gm-action-active-p1")
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ready": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .header("X-GM-Token", session.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "gm-action-active-p1"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .header("X-GM-Token", session.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "gm-action-active-p1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("ALREADY_ACTIVE");
        assertThat(body.path("nextRoute").asText()).isEqualTo("/sessions/" + session.code() + "/combat");
    }

    @Test
    void gmLobbyStartCombatActionReturnsBlockedStateWhenLobbyCannotStart() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-action-blocked", "gm-action-blocked@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-action-blocked");
        MockHttpSession playerSession = signUpAndLogin("gm-action-blocked-p1", "gm-action-blocked-p1@example.com", "password123");
        joinAsPlayer(playerSession, session.code(), "gm-action-blocked-p1");

        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/gm-lobby/start-combat", session.code())
                        .header("X-GM-Token", session.gmToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "gm-action-blocked-p1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readJson(result);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("outcome").asText()).isEqualTo("BLOCKED");
        assertThat(body.path("disabledReason").path("code").asText()).isEqualTo("READY_PARTICIPANT_REQUIRED");
        assertThat(body.path("latestScreen").path("screenKey").asText()).isEqualTo("GmLobby");
        assertThat(body.path("latestScreen").path("participantCards")).hasSize(1);
        assertThat(body.path("latestScreen").path("startCombat").path("blockedReason").path("code").asText())
                .isEqualTo("READY_PARTICIPANT_REQUIRED");
        assertThat(findAction(body.path("latestScreen"), "gmLobby.startCombat").path("enabled").asBoolean()).isFalse();
    }

    @Test
    void deckEditorScreenRequiresLogin() throws Exception {
        Deck deck = createDeck("screen-deck", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        MvcResult unauthorized = mockMvc.perform(get("/api/screens/decks/{id}/editor", deck.getId()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertApiErrorContract(unauthorized, 401);
    }

    @Test
    void newDeckEditorScreenReturnsCreateModeDraftValidationAndAction() throws Exception {
        MockHttpSession session = signUpAndLogin("deck-user", "deck-user@example.com", "password123");

        MvcResult newEditor = mockMvc.perform(get("/api/screens/decks/new/editor")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        var newEditorBody = assertBaseScreenContract(newEditor, "DeckEditor");
        assertThat(newEditorBody.path("mode").asText()).isEqualTo("create");
        assertThat(newEditorBody.path("policyGroup").asText()).isEqualTo("AUTHENTICATED_WEB");
        assertThat(newEditorBody.path("auth").asText()).isEqualTo("loginCookie");
        assertThat(newEditorBody.path("deckId").isNull()).isTrue();
        assertThat(newEditorBody.path("draft").path("name").asText()).isEmpty();
        assertThat(newEditorBody.path("draft").path("type").asText()).isEqualTo("PLAYER");
        assertThat(newEditorBody.path("draft").path("cards")).hasSize(0);
        assertThat(newEditorBody.path("derived").path("title").asText()).isEqualTo("New deck");
        assertThat(newEditorBody.path("derived").path("deckTypeLabel").asText()).isEqualTo("Player");
        assertThat(newEditorBody.path("derived").path("totalCards").asInt()).isEqualTo(0);
        assertThat(newEditorBody.path("derived").path("dirty").asBoolean()).isFalse();
        assertThat(newEditorBody.path("validation").path("valid").asBoolean()).isFalse();
        assertThat(newEditorBody.path("validation").path("normalizedTotalCards").asInt()).isEqualTo(0);
        assertThat(newEditorBody.path("validation").path("issues")).isNotEmpty();
        assertThat(newEditorBody.path("validation").path("isStale").isMissingNode()).isTrue();
        assertThat(newEditorBody.path("validation").path("validatedDraftSignature").asText())
                .isEqualTo("type=PLAYER;cards=");
        assertThat(newEditorBody.path("validation").path("validatedAt").asText()).isNotBlank();

        JsonNode createAction = findAction(newEditorBody, "deckEditor.create");
        assertThat(newEditorBody.path("possibleActions")).hasSize(1);
        assertActionContract(createAction);
        assertThat(createAction.path("enabled").asBoolean()).isTrue();
        assertThat(createAction.path("href").asText()).isEqualTo("/api/content/decks");
        assertThat(createAction.path("method").asText()).isEqualTo("POST");
        assertThat(createAction.path("payloadTemplate").path("name").asText()).isEmpty();
        assertThat(createAction.path("payloadTemplate").path("type").asText()).isEqualTo("PLAYER");
        assertThat(createAction.path("payloadTemplate").path("cards")).hasSize(0);
    }

    @Test
    void existingDeckEditorScreenReturnsEditModeDraftValidationAndActions() throws Exception {
        Deck deck = createDeck("screen-deck", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));
        MockHttpSession session = signUpAndLogin("deck-user-edit", "deck-user-edit@example.com", "password123");

        MvcResult editor = mockMvc.perform(get("/api/screens/decks/{id}/editor", deck.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        var editorBody = assertBaseScreenContract(editor, "DeckEditor");
        assertThat(editorBody.path("deckId").asLong()).isEqualTo(deck.getId());
        assertThat(editorBody.path("mode").asText()).isEqualTo("edit");
        assertThat(editorBody.path("auth").asText()).isEqualTo("loginCookie");
        assertThat(editorBody.path("draft").path("name").asText()).isEqualTo("screen-deck");
        assertThat(editorBody.path("draft").path("type").asText()).isEqualTo("PLAYER");
        assertThat(editorBody.path("draft").path("cards")).hasSize(4);
        assertThat(editorBody.path("draft").path("cards").get(0).path("key").asText()).isEqualTo("deck-card-1");
        assertThat(editorBody.path("draft").path("cards").get(0).path("count").asInt()).isEqualTo(3);
        assertThat(editorBody.path("draft").path("cards").get(0).path("position").asInt()).isEqualTo(1);
        assertThat(editorBody.path("draft").path("cards").get(3).path("position").asInt()).isEqualTo(4);
        assertThat(StreamSupport.stream(editorBody.path("draft").path("cards").spliterator(), false)
                .map(card -> card.path("cardId").asText()))
                .containsExactlyInAnyOrder("C001", "C002", "C003", "C004");
        assertThat(editorBody.path("derived").path("title").asText()).isEqualTo("screen-deck");
        assertThat(editorBody.path("derived").path("deckTypeLabel").asText()).isEqualTo("Player");
        assertThat(editorBody.path("derived").path("totalCards").asInt()).isEqualTo(12);
        assertThat(editorBody.path("derived").path("dirty").asBoolean()).isFalse();
        assertThat(editorBody.path("validation").path("valid").asBoolean()).isTrue();
        assertThat(editorBody.path("validation").path("normalizedTotalCards").asInt()).isEqualTo(12);
        assertThat(editorBody.path("validation").path("issues")).hasSize(0);
        assertThat(editorBody.path("validation").path("isStale").isMissingNode()).isTrue();
        String expectedValidatedSignature = "type=%s;cards=%s".formatted(
                editorBody.path("draft").path("type").asText(),
                StreamSupport.stream(editorBody.path("draft").path("cards").spliterator(), false)
                        .map(card -> card.path("cardId").asText() + ":" + card.path("count").asInt())
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("")
        );
        assertThat(editorBody.path("validation").path("validatedDraftSignature").asText())
                .isEqualTo(expectedValidatedSignature);
        assertThat(editorBody.path("validation").path("validatedAt").asText()).isNotBlank();

        JsonNode validateAction = findAction(editorBody, "deckEditor.validate");
        JsonNode saveAction = findAction(editorBody, "deckEditor.save");
        JsonNode deleteAction = findAction(editorBody, "deckEditor.delete");
        assertThat(editorBody.path("possibleActions")).hasSize(3);
        assertActionContract(validateAction);
        assertActionContract(saveAction);
        assertActionContract(deleteAction);
        assertThat(validateAction.path("href").asText()).isEqualTo("/api/content/decks/" + deck.getId() + "/validate");
        assertThat(validateAction.path("method").asText()).isEqualTo("POST");
        assertThat(validateAction.path("payloadTemplate").path("type").asText()).isEqualTo("PLAYER");
        assertThat(validateAction.path("payloadTemplate").path("cards")).hasSize(4);
        assertThat(saveAction.path("href").asText()).isEqualTo("/api/content/decks/" + deck.getId());
        assertThat(saveAction.path("method").asText()).isEqualTo("PUT");
        assertThat(saveAction.path("payloadTemplate").path("name").asText()).isEqualTo("screen-deck");
        assertThat(saveAction.path("payloadTemplate").path("cards")).hasSize(4);
        assertThat(deleteAction.path("href").asText()).isEqualTo("/api/content/decks/" + deck.getId());
        assertThat(deleteAction.path("method").asText()).isEqualTo("DELETE");
        assertThat(deleteAction.path("payloadTemplate").isNull()).isTrue();
    }

    @Test
    void newPresetEditorScreenReturnsCreateModeDraftResolvedPreviewAndCreateAction() throws Exception {
        MockHttpSession session = signUpAndLogin("preset-user", "preset-user@example.com", "password123");

        MvcResult newEditor = mockMvc.perform(get("/api/screens/presets/new/editor")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(newEditor, "PresetEditor");
        assertThat(body.path("mode").asText()).isEqualTo("create");
        assertThat(body.path("policyGroup").asText()).isEqualTo("AUTHENTICATED_WEB");
        assertThat(body.path("auth").asText()).isEqualTo("loginCookie");
        assertThat(body.path("presetId").isNull()).isTrue();
        assertThat(body.path("draft").path("name").asText()).isEmpty();
        assertThat(body.path("draft").path("characterId").isNull()).isTrue();
        assertThat(body.path("draft").path("deckCardIds")).hasSize(0);
        assertThat(body.path("draft").path("exCardId").asText()).isEmpty();
        assertThat(body.path("draft").path("passiveIds")).hasSize(0);
        assertThat(body.path("resolved").path("characterLabel").asText()).isEqualTo("No character selected");
        assertThat(body.path("resolved").path("exLabel").asText()).isEqualTo("No EX card selected");
        assertThat(body.path("resolved").path("deckItems")).hasSize(0);
        assertThat(body.path("resolved").path("passiveItems")).hasSize(0);
        assertThat(body.path("derived").path("dirty").asBoolean()).isFalse();
        assertThat(body.path("derived").path("createdAtLabel").asText()).isEqualTo("Available after create");
        assertThat(body.path("derived").path("updatedAtLabel").asText()).isEqualTo("Available after create");

        assertThat(body.path("possibleActions")).hasSize(1);
        JsonNode createAction = findAction(body, "presetEditor.create");
        assertActionContract(createAction);
        assertThat(createAction.path("href").asText()).isEqualTo("/api/me/presets");
        assertThat(createAction.path("method").asText()).isEqualTo("POST");
        assertThat(createAction.path("payloadTemplate").path("name").asText()).isEmpty();
        assertThat(createAction.path("payloadTemplate").path("characterId").asLong()).isZero();
        assertThat(createAction.path("payloadTemplate").path("deckCardIds")).hasSize(0);
        assertThat(createAction.path("payloadTemplate").path("exCardId").asText()).isEmpty();
        assertThat(createAction.path("payloadTemplate").path("passiveIds")).hasSize(0);
    }

    @Test
    void existingPresetEditorScreenReturnsEditModeDraftResolvedPreviewAndModeSpecificActions() throws Exception {
        long characterId = createCharacter();
        MockHttpSession session = signUpAndLogin("preset-user-edit", "preset-user-edit@example.com", "password123");
        Preset preset = createPreset("preset-user-edit", characterId);

        MvcResult editor = mockMvc.perform(get("/api/screens/presets/{id}/editor", preset.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(editor, "PresetEditor");
        assertThat(body.path("presetId").asLong()).isEqualTo(preset.getId());
        assertThat(body.path("mode").asText()).isEqualTo("edit");
        assertThat(body.path("draft").path("name").asText()).isEqualTo("screen-preset");
        assertThat(body.path("draft").path("characterId").asLong()).isEqualTo(characterId);
        assertThat(body.path("draft").path("deckCardIds")).hasSize(2);
        assertThat(body.path("draft").path("deckCardIds").get(0).asText()).isEqualTo("C001");
        assertThat(body.path("draft").path("exCardId").asText()).isEqualTo("EX901");
        assertThat(body.path("draft").path("passiveIds")).hasSize(1);
        assertThat(body.path("resolved").path("characterLabel").asText()).contains("#" + characterId);
        assertThat(body.path("resolved").path("deckItems")).hasSize(2);
        assertThat(body.path("resolved").path("deckItems").get(0).path("label").asText()).contains("C001");
        assertThat(body.path("resolved").path("deckItems").get(0).path("tags").isArray()).isTrue();
        assertThat(body.path("resolved").path("passiveItems")).hasSize(1);
        assertThat(body.path("resolved").path("passiveItems").get(0).path("label").asText()).contains("Tig001_Passive");
        assertThat(body.path("derived").path("dirty").asBoolean()).isFalse();
        assertThat(body.path("derived").path("createdAtLabel").asText()).isNotBlank();
        assertThat(body.path("derived").path("updatedAtLabel").asText()).isNotBlank();

        assertThat(body.path("possibleActions")).hasSize(3);
        JsonNode saveAction = findAction(body, "presetEditor.save");
        JsonNode cloneAction = findAction(body, "presetEditor.clone");
        JsonNode deleteAction = findAction(body, "presetEditor.delete");
        assertActionContract(saveAction);
        assertActionContract(cloneAction);
        assertActionContract(deleteAction);
        assertThat(saveAction.path("href").asText()).isEqualTo("/api/me/presets/" + preset.getId());
        assertThat(saveAction.path("method").asText()).isEqualTo("PUT");
        assertThat(saveAction.path("payloadTemplate").path("name").asText()).isEqualTo("screen-preset");
        assertThat(saveAction.path("payloadTemplate").path("characterId").asLong()).isEqualTo(characterId);
        assertThat(saveAction.path("payloadTemplate").path("deckCardIds")).hasSize(2);
        assertThat(cloneAction.path("href").asText()).isEqualTo("/api/me/presets/" + preset.getId() + "/clone");
        assertThat(cloneAction.path("method").asText()).isEqualTo("POST");
        assertThat(cloneAction.path("payloadTemplate").isNull()).isTrue();
        assertThat(deleteAction.path("href").asText()).isEqualTo("/api/me/presets/" + preset.getId());
        assertThat(deleteAction.path("method").asText()).isEqualTo("DELETE");
        assertThat(deleteAction.path("payloadTemplate").isNull()).isTrue();
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
        long characterId = createCharacter();
        return joinAsPlayer(session, code, playerId, characterId);
    }

    private String joinAsPlayer(MockHttpSession session, String code, String playerId, Long characterId) throws Exception {
        String characterPayload = characterId == null ? "null" : characterId.toString();

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerId": "%s",
                                  "characterId": %s
                                }
                                """.formatted(playerId, characterPayload)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        return node.path("playerToken").asText();
    }

    private long createCharacter() {
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("Screen Test Character")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("test")
                .disposition("neutral")
                .oneLiner("screen")
                .story("screen")
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
        return profile.getId();
    }

    private Deck createDeck(String name, DeckType type, Map<String, Integer> cards) {
        Deck deck = Deck.create(name, type);
        deck.syncCards(cards);
        return deckRepository.save(deck);
    }

    private Preset createPreset(String ownerUsername, long characterId) {
        Preset preset = Preset.create(
                ownerUsername,
                "screen-preset",
                characterId,
                List.of("C001", "C002"),
                "EX901",
                List.of("Tig001_Passive")
        );
        return presetRepository.save(preset);
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
