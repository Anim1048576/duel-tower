package com.example.dueltower.content.deck.api;

import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeckControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeckRepository deckRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        deckRepository.deleteAll();
    }

    @Test
    void replaceCardsSuccessShouldReplaceWholeDeckAndReturnDeckResponse() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace1");
        Deck deck = createDeck("deck-replace", DeckType.ENEMY, Map.of("C001", 1, "C002", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C003","count":2},
                                    {"cardId":"C004","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deck.getId()))
                .andExpect(jsonPath("$.name").value("deck-replace"))
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.cards.length()").value(2))
                .andExpect(jsonPath("$.cards[0].cardId").value("C003"))
                .andExpect(jsonPath("$.cards[0].count").value(2))
                .andExpect(jsonPath("$.cards[1].cardId").value("C004"))
                .andExpect(jsonPath("$.cards[1].count").value(1));
    }

    @Test
    void replaceCardsShouldWorkWithoutConflictWithCardsAddEndpoint() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace2");
        Deck deck = createDeck("deck-no-conflict", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C002","count":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(2))
                .andExpect(jsonPath("$.cards[0].cardId").value("C002"))
                .andExpect(jsonPath("$.cards[0].count").value(2));

        mockMvc.perform(post("/api/content/decks/{id}/cards/add", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C002","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].cardId").value("C002"))
                .andExpect(jsonPath("$.cards[0].count").value(3));
    }

    @Test
    void replaceCardsShouldRequireAuthentication() throws Exception {
        Deck deck = createDeck("deck-auth", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replaceCardsShouldFailForMissingDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace3");

        mockMvc.perform(put("/api/content/decks/{id}/cards", 999999)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceCardsShouldFailForUnknownCardId() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace4");
        Deck deck = createDeck("deck-unknown-card", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"UNKNOWN_CARD","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceCardsShouldFailForNonPositiveCount() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace5");
        Deck deck = createDeck("deck-bad-count", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":0}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceCardsShouldSumDuplicateCardIds() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReplace6");
        Deck deck = createDeck("deck-dup", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}/cards", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C003","count":2},
                                    {"cardId":"C003","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(5))
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].cardId").value("C003"))
                .andExpect(jsonPath("$.cards[0].count").value(5));
    }

    @Test
    void validateShouldReturnValidTrueForValidPlayerCandidate() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidate1");
        Deck deck = createDeck("deck-player", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues.length()").value(0))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    void validateShouldRequireAuthenticationLikeReplace() throws Exception {
        Deck deck = createDeck("deck-validate-auth", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validateShouldFailForMissingDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidate2");

        mockMvc.perform(post("/api/content/decks/{id}/validate", 999999)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void validateShouldReturnReasonForTotalCardLimitViolation() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidate3");
        Deck deck = createDeck("deck-validate-reasons", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":4}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues.length()").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("TOTAL_CARDS_INVALID"))
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("exactly 12")))
                .andExpect(jsonPath("$.normalizedTotalCards").value(13));
    }

    @Test
    void validateShouldReturnReasonForCopyLimitViolation() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidateCopy");
        Deck deck = createDeck("deck-validate-copy", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":4},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues.length()").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("COPY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("copies per card")))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    void validateShouldReturnReasonForExRuleViolation() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidateEx");
        Deck deck = createDeck("deck-validate-ex", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"EX901","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues[0].code").value("EX_NOT_ALLOWED"))
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("EX card is not allowed")))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    void validateShouldReturnReasonsForUnknownCardId() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidate4");
        Deck deck = createDeck("deck-validate-unknown", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"UNKNOWN_CARD","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues.length()").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("UNKNOWN_CARD_ID"))
                .andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.containsString("unknown cardId")))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    void publicGetDeckEndpointsShouldRemainAccessible() throws Exception {
        Deck deck = createDeck("deck-public", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(get("/api/content/decks"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/decks/{id}", deck.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deck.getId()));
    }

    @Test
    void existingDeckPutShouldStillWork() throws Exception {
        MockHttpSession session = signUpAndLogin("deckUpdate1");
        Deck deck = createDeck("deck-update", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(put("/api/content/decks/{id}", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "deck-update-renamed",
                                  "type": "ENEMY",
                                  "cards": [
                                    {"cardId":"C002","count":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("deck-update-renamed"))
                .andExpect(jsonPath("$.totalCards").value(2))
                .andExpect(jsonPath("$.cards[0].cardId").value("C002"));
    }

    private Deck createDeck(String name, DeckType type, Map<String, Integer> cards) {
        Deck deck = Deck.create(name, type);
        deck.syncCards(cards);
        return deckRepository.save(deck);
    }

    private MockHttpSession signUpAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());

        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
