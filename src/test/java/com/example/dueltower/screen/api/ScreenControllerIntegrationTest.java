package com.example.dueltower.screen.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.member.MemberRepository;
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

    @BeforeEach
    void setUp() {
        deckRepository.deleteAll();
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
    }

    @Test
    void sessionScreenRequiresSessionReadableAuthorization() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm");
        MockHttpSession playerSession = signUpAndLogin("player1", "player1@example.com", "password123");
        String playerToken = joinAsPlayer(playerSession, session.code(), "player1");

        MvcResult unauthorized = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertApiErrorContract(unauthorized, 401);

        MvcResult authorized = mockMvc.perform(get("/api/screens/sessions/{code}/player-lobby", session.code())
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        var body = assertBaseScreenContract(authorized, "PlayerLobby");
        assertThat(body.path("sessionCode").asText()).isEqualTo(session.code());
        assertThat(body.path("policyGroup").asText()).isEqualTo("SESSION_READABLE");
        assertThat(body.path("auth").asText()).isEqualTo("sessionReadable");
        assertThat(body.path("stub").asBoolean()).isTrue();
    }

    @Test
    void sessionScreenAllowsLoginFallbackForRelatedUser() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        SessionInfo session = createSession(gmSession, "gm");

        MvcResult result = mockMvc.perform(get("/api/screens/sessions/{code}/gm-lobby", session.code())
                        .session(gmSession))
                .andExpect(status().isOk())
                .andReturn();

        var body = assertBaseScreenContract(result, "GmLobby");
        assertThat(body.path("routeTemplate").asText()).isEqualTo("/api/screens/sessions/{code}/gm-lobby");
        assertThat(body.path("policyGroup").asText()).isEqualTo("SESSION_READABLE");
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
        assertThat(newEditorBody.path("validation").path("isStale").asBoolean()).isFalse();

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
        assertThat(editorBody.path("validation").path("isStale").asBoolean()).isFalse();

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

        MvcResult result = mockMvc.perform(post("/api/sessions/{code}/join", code)
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
