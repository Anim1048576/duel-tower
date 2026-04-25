package com.example.dueltower.content.deck.api;

import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.member.MemberRepository;
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

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    @DisplayName("카드 교체는 전체 덱을 교체하고 덱 응답을 반환한다")
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
    @DisplayName("카드 교체는 카드 추가 엔드포인트와 충돌 없이 동작한다")
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
    @DisplayName("카드 교체는 인증이 필요하다")
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
    @DisplayName("카드 교체는 덱이 없으면 실패한다")
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
    @DisplayName("카드 교체는 알 수 없는 카드 ID면 실패한다")
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
    @DisplayName("카드 교체는 count가 양수가 아니면 실패한다")
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
    @DisplayName("카드 교체는 중복 카드 ID를 합산한다")
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
    @DisplayName("카드 제거는 요청한 수량만큼 차감한다")
    void removeCardsShouldSubtractRequestedCopies() throws Exception {
        MockHttpSession session = signUpAndLogin("deckRemove1");
        Deck deck = createDeck("deck-remove", DeckType.ENEMY, Map.of("C001", 3, "C002", 2));

        mockMvc.perform(post("/api/content/decks/{id}/cards/remove", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.cards.length()").value(2));
    }

    @Test
    @DisplayName("카드 제거는 덱에 카드가 없으면 실패한다")
    void removeCardsShouldFailWhenCardMissingInDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("deckRemove2");
        Deck deck = createDeck("deck-remove-missing", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(post("/api/content/decks/{id}/cards/remove", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C002","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("카드 제거는 count가 양수가 아니면 실패한다")
    void removeCardsShouldFailWhenCountIsNotPositive() throws Exception {
        MockHttpSession session = signUpAndLogin("deckRemove3");
        Deck deck = createDeck("deck-remove-bad-count", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(post("/api/content/decks/{id}/cards/remove", deck.getId())
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
    @DisplayName("덱 검증은 유효한 플레이어 후보에 대해 valid=true를 반환한다")
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
    @DisplayName("덱 검증은 카드 교체와 동일하게 인증이 필요하다")
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
    @DisplayName("덱 검증은 덱이 없으면 실패한다")
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
    @DisplayName("덱 검증은 총 카드 수 제한 위반 사유를 반환한다")
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
    @DisplayName("덱 검증은 복제 수 제한 위반 사유를 반환한다")
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
    @DisplayName("덱 검증은 EX 규칙 위반 사유를 반환한다")
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
    @DisplayName("덱 검증은 알 수 없는 카드 ID 사유를 반환한다")
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
    @DisplayName("덱 검증은 요청 본문이 없으면 현재 덱 구성을 사용한다")
    void validateWithoutRequestBodyShouldUseCurrentDeckComposition() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidateCurrent");
        Deck deck = createDeck("deck-validate-current", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    @DisplayName("덱 검증은 중복 카드 ID 병합 정책을 일관되게 적용한다")
    void validateShouldApplyDuplicateCardIdMergePolicyConsistently() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidateDupMerge");
        Deck deck = createDeck("deck-validate-dup-merge", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":2},
                                    {"cardId":"C001","count":2},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("COPY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.normalizedTotalCards").value(12));
    }

    @Test
    @DisplayName("덱 검증은 요청 type을 draft type override로 반영한다")
    void validateShouldUseRequestedTypeOverrideWhenPresent() throws Exception {
        MockHttpSession session = signUpAndLogin("deckValidateTypeOverride");
        Deck deck = createDeck("deck-validate-type-override", DeckType.PLAYER, Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(post("/api/content/decks/{id}/validate", deck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "ENEMY",
                                  "cards": [
                                    {"cardId":"C001","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0))
                .andExpect(jsonPath("$.normalizedTotalCards").value(1));
    }

    @Test
    @DisplayName("공개 덱 조회 엔드포인트는 계속 접근 가능해야 한다")
    void publicGetDeckEndpointsShouldRemainAccessible() throws Exception {
        Deck deck = createDeck("deck-public", DeckType.ENEMY, Map.of("C001", 1));

        mockMvc.perform(get("/api/content/decks"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/decks/{id}", deck.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deck.getId()));
    }

    @Test
    @DisplayName("기존 덱 PUT은 여전히 동작한다")
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

    @Test
    @DisplayName("character currentSkillDeck 미러 덱은 public deck API로 변경할 수 없다")
    void reservedCurrentSkillDeckRejectsPublicDeckMutations() throws Exception {
        MockHttpSession session = signUpAndLogin("deckReservedMirror");

        mockMvc.perform(post("/api/content/decks")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "character:7:currentSkillDeck",
                                  "type": "PLAYER",
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));

        Deck mirrorDeck = createDeck("character:7:currentSkillDeck", DeckType.PLAYER,
                Map.of("C001", 3, "C002", 3, "C003", 3, "C004", 3));

        mockMvc.perform(put("/api/content/decks/{id}", mirrorDeck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "renamed-deck",
                                  "type": "PLAYER",
                                  "cards": [
                                    {"cardId":"C001","count":3},
                                    {"cardId":"C002","count":3},
                                    {"cardId":"C003","count":3},
                                    {"cardId":"C004","count":3}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));

        mockMvc.perform(post("/api/content/decks/{id}/cards/add", mirrorDeck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));

        mockMvc.perform(put("/api/content/decks/{id}/cards", mirrorDeck.getId())
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
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));

        mockMvc.perform(post("/api/content/decks/{id}/cards/remove", mirrorDeck.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cards": [
                                    {"cardId":"C001","count":1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));

        mockMvc.perform(delete("/api/content/decks/{id}", mirrorDeck.getId())
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reserved current skill deck")));
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
