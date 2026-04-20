package com.example.dueltower.screen.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.preset.domain.Preset;
import com.example.dueltower.preset.repository.PresetRepository;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionRequest;
import com.example.dueltower.screen.support.ScreenApiContractTestSupport;
import com.example.dueltower.session.service.SessionLifecycleService;
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

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

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
        assertThat(body.path("deckEditor").path("deck").path("requiredDeckSize").asInt()).isEqualTo(12);
        assertThat(body.path("deckEditor").path("deck").path("draftDeckSize").asInt()).isEqualTo(body.path("me").path("loadout").path("deckCount").asInt());
        assertThat(body.path("deckEditor").path("deck").path("saveAllowed").asBoolean()).isTrue();
        assertThat(body.path("deckEditor").path("draftEntries")).isNotEmpty();
        assertThat(body.path("deckEditor").path("cardPoolGroups")).isNotEmpty();
        assertThat(body.path("deckEditor").path("issues").isArray()).isTrue();
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
    void playerLobbyScreenIncludesDeckEditorReasonsForLockedAndInvalidDeckState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-deck-editor", "gm-deck-editor@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-deck-editor");
        MockHttpSession playerSession = signUpAndLogin("player-deck-editor", "player-deck-editor@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "player-deck-editor", characterId);

        sessionLifecycleService.withLockedSession(session.code(), rt -> {
            var player = rt.state().player(new Ids.PlayerId("player-deck-editor"));
            List<com.example.dueltower.content.card.model.OwnedCard> ownedCards = new java.util.ArrayList<>(player.ownedCards());
            ownedCards.set(0, ownedCards.get(0).withLockInDeck(true));
            player.ownedCards(ownedCards);
            player.deckOwnedCardIds(List.copyOf(player.deckOwnedCardIds().subList(0, player.deckOwnedCardIds().size() - 1)));
            return null;
        });

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "PlayerLobby");
        assertThat(body.path("deckEditor").path("deck").path("draftDeckSize").asInt()).isEqualTo(11);
        assertThat(body.path("deckEditor").path("deck").path("saveAllowed").asBoolean()).isFalse();
        assertThat(StreamSupport.stream(body.path("deckEditor").path("globalReasonCodes").spliterator(), false)
                .map(JsonNode::asText))
                .contains("INVALID_DECK_SIZE");
        assertThat(StreamSupport.stream(body.path("deckEditor").path("issues").spliterator(), false)
                .map(issue -> issue.path("code").asText()))
                .contains("INVALID_DECK_SIZE");
        JsonNode lockedEntry = StreamSupport.stream(body.path("deckEditor").path("draftEntries").spliterator(), false)
                .filter(entry -> entry.path("lockedInDeck").asBoolean())
                .findFirst()
                .orElseThrow();
        assertThat(lockedEntry.path("canRemove").asBoolean()).isFalse();
        assertThat(StreamSupport.stream(lockedEntry.path("reasonCodes").spliterator(), false)
                .map(JsonNode::asText))
                .contains("LOCKED_CARD");
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
    void combatScreenReturnsComposedReadModelEnvelope() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-gm", "combat-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-p1", "combat-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-p1", characterId);
        markPlayerReady(session.code(), "combat-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-p1");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "Combat");
        assertCombatScreenContract(body);
        assertThat(body.path("sessionCode").asText()).isEqualTo(session.code());
        assertThat(body.path("version").asLong()).isGreaterThan(0L);
        assertThat(body.path("changed").asBoolean()).isTrue();
        assertThat(body.path("uiNotices")).isNotEmpty();
        JsonNode drawAction = findAction(body, "combat.draw");
        assertActionContract(drawAction);
        assertThat(drawAction.path("enabled").asBoolean()).isTrue();
        assertThat(drawAction.path("metadata").path("kind").asText()).isEqualTo("simple");
    }

    @Test
    void combatScreenIncludesRequiredStatusActorsZonesAndSidebarFields() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-fields-gm", "combat-fields-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-fields-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-fields-p1", "combat-fields-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-fields-p1", characterId);
        markPlayerReady(session.code(), "combat-fields-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-fields-p1");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "Combat");
        assertCombatScreenContract(body);
        assertThat(body.path("status").path("round").isInt()).isTrue();
        assertThat(body.path("status").path("phase").asText()).isNotBlank();
        assertThat(body.path("status").path("currentActor").path("label").asText()).isNotBlank();
        assertThat(body.path("status").path("turnOrderSummary").asText()).isNotBlank();
        assertThat(body.path("status").path("battlefieldSummary").asText()).contains("players");
        assertThat(body.path("status").path("runSummary").asText()).isNotBlank();
        assertThat(body.path("access").path("role").asText()).isEqualTo("player");
        assertThat(body.path("access").path("runtimePlayerId").asText()).isEqualTo("combat-fields-p1");
        assertThat(body.path("access").path("expectedVersion").asLong()).isEqualTo(body.path("version").asLong());
        assertThat(body.path("access").path("guards").path("canClearRecentResultsCommand").isBoolean()).isTrue();
        assertThat(body.path("actors").path("players")).isNotEmpty();
        assertThat(body.path("actors").path("players").get(0).path("playerId").asText()).isEqualTo("combat-fields-p1");
        assertThat(body.path("actors").path("players").get(0).path("metrics")).isNotEmpty();
        assertThat(body.path("actors").path("enemies").isArray()).isTrue();
        assertThat(body.path("actors").path("summons").isArray()).isTrue();
        assertThat(body.path("zones").path("visiblePlayerId").asText()).isEqualTo("combat-fields-p1");
        assertThat(body.path("zones").path("hand").isArray()).isTrue();
        assertThat(body.path("zones").path("field").isArray()).isTrue();
        assertThat(body.path("zones").path("grave").isArray()).isTrue();
        assertThat(body.path("zones").path("excluded").isArray()).isTrue();
        assertThat(body.path("sidebar").path("events").isArray()).isTrue();
        assertThat(body.path("sidebar").path("logs").isArray()).isTrue();
        assertThat(body.path("sidebar").path("recentResults").isArray()).isTrue();
        JsonNode resolvePendingAction = findAction(body, "combat.resolvePending");
        assertDisabledActionContract(resolvePendingAction);
        assertThat(resolvePendingAction.path("disabledReason").path("code").asText()).isEqualTo("PENDING_DECISION_REQUIRED");
        assertThat(resolvePendingAction.path("metadata").path("kind").asText()).isEqualTo("pendingDecision");
        assertThat(resolvePendingAction.path("metadata").path("supported").asBoolean()).isFalse();
    }

    @Test
    void combatScreenMarksChangedFalseWhenAfterVersionMatchesCurrentVersion() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-changed-gm", "combat-changed-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-changed-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-changed-p1", "combat-changed-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-changed-p1", characterId);
        markPlayerReady(session.code(), "combat-changed-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-changed-p1");

        JsonNode initialBody = assertBaseScreenContract(
                mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                                .header("X-Player-Token", playerToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "Combat"
        );
        assertCombatScreenContract(initialBody);

        long currentVersion = initialBody.path("version").asLong();

        MvcResult unchanged = mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                        .header("X-Player-Token", playerToken)
                        .param("afterVersion", String.valueOf(currentVersion)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode unchangedBody = assertBaseScreenContract(unchanged, "Combat");
        assertCombatScreenContract(unchangedBody);
        assertThat(unchangedBody.path("version").asLong()).isEqualTo(currentVersion);
        assertThat(unchangedBody.path("changed").asBoolean()).isFalse();
    }

    @Test
    void combatScreenResolvesCardItemsForVisibleZones() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-card-gm", "combat-card-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-card-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-card-p1", "combat-card-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-card-p1", characterId);
        markPlayerReady(session.code(), "combat-card-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-card-p1");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "Combat");
        assertCombatScreenContract(body);
        assertThat(body.path("zones").path("hand")).isNotEmpty();

        JsonNode card = body.path("zones").path("hand").get(0);
        assertCombatCardContract(card);
        assertThat(card.path("defId").asText()).isNotBlank();
        assertThat(card.path("title").asText()).isNotBlank();
        assertThat(card.path("title").asText()).isNotEqualTo(card.path("defId").asText());
        assertThat(card.path("subtitle").asText()).isNotBlank();
        assertThat(card.path("unresolved").asBoolean()).isFalse();
        assertThat(card.path("tags")).isNotEmpty();
        assertThat(card.path("meta").asText()).contains("Instance");

        JsonNode playCardAction = findAction(body, "combat.playCard");
        JsonNode useExAction = findAction(body, "combat.useEx");
        assertActionContract(playCardAction);
        assertActionContract(useExAction);
        assertThat(playCardAction.path("metadata").path("kind").asText()).isEqualTo("playCard");
        assertThat(playCardAction.path("metadata").path("sourceOptions")).isNotEmpty();
        JsonNode hostileSourceOption = StreamSupport.stream(
                        playCardAction.path("metadata").path("sourceOptions").spliterator(),
                        false
                )
                .filter(option -> "HOSTILE".equals(option.path("requirementView").path("boardObjectRequirement").path("relation").asText(null)))
                .findFirst()
                .orElse(playCardAction.path("metadata").path("sourceOptions").get(0));
        assertThat(hostileSourceOption.path("requirementView").path("targetSummary").asText()).isNotBlank();
        assertThat(hostileSourceOption.path("requirementView").path("boardObjectRequirement").path("relation").asText())
                .isEqualTo("HOSTILE");
        assertThat(hostileSourceOption.path("requirementView").path("targetRule").path("requiredSelection").asBoolean())
                .isFalse();
        assertThat(hostileSourceOption.path("requirementView").has("discardRequirement")).isTrue();
        assertThat(useExAction.path("metadata").path("kind").asText()).isEqualTo("useEx");
        assertThat(useExAction.path("metadata").path("requirementView").path("targetSummary").asText()).isNotBlank();
    }

    @Test
    void combatScreenShowsUnsupportedPendingDecisionMetadataAndDisabledReason() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-pending-gm", "combat-pending-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-pending-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-pending-p1", "combat-pending-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-pending-p1", characterId);
        markPlayerReady(session.code(), "combat-pending-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-pending-p1");

        sessionLifecycleService.withLockedSession(session.code(), rt -> {
            rt.state()
                    .player(new Ids.PlayerId("combat-pending-p1"))
                    .pendingDecision(new PendingDecision.JudgementChoice("manual test", List.of("BODY", "WILL")));
            return null;
        });

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/combat", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = assertBaseScreenContract(result, "Combat");
        assertCombatScreenContract(body);
        JsonNode resolvePendingAction = findAction(body, "combat.resolvePending");
        assertDisabledActionContract(resolvePendingAction);
        assertThat(resolvePendingAction.path("disabledReason").path("code").asText()).isEqualTo("PENDING_DECISION_UNSUPPORTED");
        assertThat(resolvePendingAction.path("metadata").path("pendingDecisionType").asText()).isEqualTo("JUDGEMENT");
        assertThat(resolvePendingAction.path("metadata").path("supported").asBoolean()).isFalse();
        assertThat(resolvePendingAction.path("metadata").path("unsupportedReason").asText()).contains("not supported");
        assertThat(resolvePendingAction.path("metadata").path("schema").path("type").asText()).isEqualTo("JUDGEMENT");
    }

    @Test
    void combatDrawActionSucceedsAndReturnsLatestScreen() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-draw-gm", "combat-draw-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-draw-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-draw-p1", "combat-draw-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-draw-p1", characterId);
        markPlayerReady(session.code(), "combat-draw-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-draw-p1");

        JsonNode currentScreen = getCombatScreen(session.code(), playerToken);
        long beforeVersion = currentScreen.path("version").asLong();

        JsonNode body = executeCombatAction(session.code(), "combat.draw", playerToken, "{}");
        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(body.path("message").asText()).isEqualTo("Draw completed.");
        assertThat(body.path("disabledReason").isNull()).isTrue();
        assertThat(body.path("latestVersion").asLong()).isGreaterThan(beforeVersion);
        assertThat(body.path("resultSummary").path("actionId").asText()).isEqualTo("combat.draw");
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("DRAW");
        assertThat(body.path("serverNotices")).isNotEmpty();
        assertThat(body.path("latestScreen").path("version").asLong()).isEqualTo(body.path("latestVersion").asLong());
    }

    @Test
    void combatUseExActionSucceedsAndReturnsLatestScreen() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-ex-gm", "combat-ex-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-ex-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-ex-p1", "combat-ex-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-ex-p1", characterId);
        markPlayerReady(session.code(), "combat-ex-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-ex-p1");

        JsonNode currentScreen = getCombatScreen(session.code(), playerToken);
        long beforeVersion = currentScreen.path("version").asLong();

        JsonNode body = executeCombatAction(
                session.code(),
                "combat.useEx",
                playerToken,
                """
                {
                  "targets": [
                    {
                      "playerId": "combat-ex-p1"
                    }
                  ]
                }
                """
        );

        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(body.path("resultSummary").path("actionId").asText()).isEqualTo("combat.useEx");
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("USE_EX");
        assertThat(body.path("latestVersion").asLong()).isGreaterThan(beforeVersion);
    }

    @Test
    void combatPlayCardActionReturnsStructuredFailureWhenEngineRejects() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-play-fail-gm", "combat-play-fail-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-play-fail-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-play-fail-p1", "combat-play-fail-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-play-fail-p1", characterId);
        markPlayerReady(session.code(), "combat-play-fail-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-play-fail-p1");

        JsonNode body = executeCombatAction(
                session.code(),
                "combat.playCard",
                playerToken,
                """
                {
                  "cardId": "00000000-0000-0000-0000-000000000000"
                }
                """
        );

        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("outcome").asText()).isEqualTo("FAILED");
        assertDisabledReasonContract(body.path("disabledReason"));
        assertThat(body.path("resultSummary").path("actionId").asText()).isEqualTo("combat.playCard");
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("PLAY_CARD");
        assertThat(body.path("latestVersion").asLong()).isEqualTo(body.path("latestScreen").path("version").asLong());
    }

    @Test
    void combatResolvePendingActionSucceedsForSupportedDecision() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-pending-ok-gm", "combat-pending-ok-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-pending-ok-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-pending-ok-p1", "combat-pending-ok-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-pending-ok-p1", characterId);
        markPlayerReady(session.code(), "combat-pending-ok-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-pending-ok-p1");

        String discardId = sessionLifecycleService.withLockedSession(session.code(), rt -> {
            var player = rt.state().player(new Ids.PlayerId("combat-pending-ok-p1"));
            int discardLimit = Math.max(0, player.hand().size() - 1);
            player.pendingDecision(new PendingDecision.DiscardToHandLimit("manual test", discardLimit));
            return player.hand().get(0).value().toString();
        });

        JsonNode body = executeCombatAction(
                session.code(),
                "combat.resolvePending",
                playerToken,
                """
                {
                  "discardIds": ["%s"]
                }
                """.formatted(discardId)
        );

        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("outcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("DISCARD_TO_HAND_LIMIT");
        assertThat(body.path("latestScreen").path("access").path("guards").path("hasPendingDecision").asBoolean()).isFalse();
    }

    @Test
    void combatClearRecentResultsActionSucceedsAndClearsSidebarEntries() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-results-gm", "combat-results-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-results-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-results-p1", "combat-results-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-results-p1", characterId);
        markPlayerReady(session.code(), "combat-results-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-results-p1");

        sessionLifecycleService.withLockedSession(session.code(), rt -> {
            rt.state().runState().appendRecentResult(
                    "TEST",
                    "Synthetic result",
                    "Clear me",
                    "Inserted by screen contract test",
                    "screen-test"
            );
            return null;
        });

        JsonNode before = getCombatScreen(session.code(), playerToken);
        assertThat(before.path("sidebar").path("recentResults")).isNotEmpty();

        JsonNode body = executeCombatAction(session.code(), "combat.clearRecentResults", playerToken, "{}");
        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("resultSummary").path("commandType").asText()).isEqualTo("CLEAR_RECENT_RESULTS");
        assertThat(body.path("latestScreen").path("sidebar").path("recentResults")).isEmpty();
    }

    @Test
    void combatActionReturnsCurrentScreenWhenBlockedByScreenMetadata() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("combat-blocked-gm", "combat-blocked-gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "combat-blocked-gm");
        MockHttpSession playerSession = signUpAndLogin("combat-blocked-p1", "combat-blocked-p1@example.com", "password123");
        long characterId = createCharacter();
        String playerToken = joinAsPlayer(playerSession, session.code(), "combat-blocked-p1", characterId);
        markPlayerReady(session.code(), "combat-blocked-p1", playerToken);
        startCombat(session.code(), session.gmToken(), "combat-blocked-p1");

        JsonNode currentScreen = getCombatScreen(session.code(), playerToken);

        JsonNode body = executeCombatAction(session.code(), "combat.resolvePending", playerToken, "{}");
        assertCombatActionResponseContract(body);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("outcome").asText()).isEqualTo("BLOCKED");
        assertDisabledReasonContract(body.path("disabledReason"));
        assertThat(body.path("disabledReason").path("code").asText()).isEqualTo("PENDING_DECISION_REQUIRED");
        assertThat(body.path("latestVersion").asLong()).isEqualTo(currentScreen.path("version").asLong());
        assertThat(body.path("latestScreen").path("version").asLong()).isEqualTo(currentScreen.path("version").asLong());
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

    @Test
    void combatScreenExposesBoardObjectRequirementMetadataForTig901Ex() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm-tig901", "gm-tig901@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm-tig901");
        MockHttpSession playerSession = signUpAndLogin("player-tig901", "player-tig901@example.com", "password123");
        long characterId = createCharacter("Tig901_EX");
        String playerToken = joinAsPlayer(playerSession, session.code(), "player-tig901", characterId);

        markPlayerReady(session.code(), "player-tig901", playerToken);
        startCombat(session.code(), session.gmToken(), "player-tig901");

        JsonNode body = getCombatScreen(session.code(), playerToken);
        JsonNode useExAction = findAction(body, "combat.useEx");
        JsonNode requirementView = useExAction.path("metadata").path("requirementView");
        int hostileSummonCount = 0;
        for (JsonNode summon : body.path("actors").path("summons")) {
            if (!"player-tig901".equals(summon.path("owner").asText())) {
                hostileSummonCount++;
            }
        }
        int expectedCandidateCount = body.path("actors").path("enemies").size() + hostileSummonCount;
        List<Integer> expectedAllowedCounts = expectedCandidateCount <= 0
                ? List.of()
                : expectedCandidateCount == 1
                ? List.of(1)
                : List.of(1, 2);

        assertThat(requirementView.path("boardObjectSummary").asText()).contains("hostile");
        assertThat(requirementView.path("boardObjectRequirement").path("minSelections").asInt()).isEqualTo(1);
        assertThat(requirementView.path("boardObjectRequirement").path("maxSelections").asInt()).isEqualTo(2);
        assertThat(requirementView.path("boardObjectRequirement").path("relation").asText()).isEqualTo("HOSTILE");
        assertThat(requirementView.path("boardObjectRequirement").path("kinds")).hasSize(2);
        assertThat(requirementView.path("boardObjectSelectionHints").path("candidateCount").asInt()).isEqualTo(expectedCandidateCount);
        assertThat(StreamSupport.stream(
                requirementView.path("boardObjectSelectionHints").path("allowedCounts").spliterator(),
                false
        ).map(JsonNode::asInt).toList()).isEqualTo(expectedAllowedCounts);
        assertThat(requirementView.path("boardObjectSelectionHints").path("skipCountChoice").asBoolean())
                .isEqualTo(expectedAllowedCounts.size() <= 1);
        assertThat(requirementView.path("targetRule").path("requiredSelection").asBoolean()).isFalse();
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

    private void markPlayerReady(String code, String playerId, String playerToken) throws Exception {
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

    private void startCombat(String code, String gmToken, String playerId) throws Exception {
        long expectedVersion = readJson(mockMvc.perform(get("/api/sessions/{code}", code))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("version")
                .asLong();

        mockMvc.perform(post("/api/sessions/{code}/command", code)
                        .header("X-GM-Token", gmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "START_COMBAT",
                                  "expectedVersion": %d,
                                  "playerId": "%s"
                                }
                                """.formatted(expectedVersion, playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    private JsonNode getCombatScreen(String code, String playerToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/combat", code)
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();
        return assertBaseScreenContract(result, "Combat");
    }

    private JsonNode executeCombatAction(String code,
                                         String actionId,
                                         String playerToken,
                                         String payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/screens/sessions/{code}/combat/actions/{actionId}", code, actionId)
                        .header("X-Player-Token", playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private long createCharacter() {
        return createCharacter("EX901");
    }

    private long createCharacter(String exCardId) {
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
                .exCard("{\"id\":\"" + exCardId + "\"}")
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
