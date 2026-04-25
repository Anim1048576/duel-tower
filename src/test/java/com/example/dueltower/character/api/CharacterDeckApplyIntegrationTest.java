package com.example.dueltower.character.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterCurrentSkillDeckEntry;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterCurrentSkillDeckEntryRepository;
import com.example.dueltower.character.repository.CharacterExLoadoutRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardModifierRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardRepository;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.character.service.CharacterCardCollectionService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacterDeckApplyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private CharacterOwnedCardRepository characterOwnedCardRepository;

    @Autowired
    private CharacterOwnedCardModifierRepository characterOwnedCardModifierRepository;

    @Autowired
    private CharacterCurrentSkillDeckEntryRepository currentSkillDeckEntryRepository;

    @Autowired
    private CharacterExLoadoutRepository characterExLoadoutRepository;

    @Autowired
    private CharacterCardCollectionService cardCollectionService;

    @Autowired
    private DeckRepository deckRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        currentSkillDeckEntryRepository.deleteAll();
        characterExLoadoutRepository.deleteAll();
        characterOwnedCardModifierRepository.deleteAll();
        characterOwnedCardRepository.deleteAll();
        characterProfileRepository.deleteAll();
        deckRepository.deleteAll();
    }

    @Test
    @DisplayName("저장된 PLAYER 덱을 캐릭터 currentSkillDeck에 펼쳐 적용하고 detail 응답을 반환한다")
    void applyPlayerDeckToCharacterCurrentSkillDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckPlayer");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));
        Deck deck = createDeck("player-apply", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "C004", 3
        ));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), deck.getId())
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(character.getId()))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds.length()").value(12))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[0]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[1]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[2]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[3]").value("C002"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[11]").value("C004"))
                .andExpect(jsonPath("$.ownedCards").isString())
                .andExpect(jsonPath("$.exCard").value("{}"))
                .andExpect(jsonPath("$.combatStats.maxHp").exists());

        CharacterProfile reloaded = characterProfileRepository.findById(character.getId()).orElseThrow();
        assertEquals(null, reloaded.getCurrentSkillDeck());
        assertIterableEquals(List.of(
                "oc-c001-1", "oc-c001-2", "oc-c001-3",
                "oc-c002-1", "oc-c002-2", "oc-c002-3",
                "oc-c003-1", "oc-c003-2", "oc-c003-3",
                "oc-c004-1", "oc-c004-2", "oc-c004-3"
        ), currentSkillDeckEntryRepository.findByCharacterIdOrderByPositionAsc(character.getId()).stream()
                .map(CharacterCurrentSkillDeckEntry::getOwnedCardId)
                .toList());
        assertTrue(deckRepository.findFirstByTypeAndName(
                DeckType.PLAYER,
                "character:" + character.getId() + ":currentSkillDeck"
        ).isEmpty());

        mockMvc.perform(get("/api/content/characters/{id}", character.getId())
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(character.getId()))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds.length()").value(12))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[0]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[11]").value("C004"));
    }

    @Test
    @DisplayName("저장된 PLAYER 덱을 재적용하면 currentSkillDeck 미러 덱에서 이전 카드가 제거된다")
    void reapplyingPlayerDeckReplacesMirrorDeckWithoutStaleCards() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckReplaceMirror");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));
        Deck firstDeck = createDeck("player-apply-first", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "Tig001_Card", 3
        ));
        Deck secondDeck = createDeck("player-apply-second", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "C004", 3
        ));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), firstDeck.getId())
                        .session(session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), secondDeck.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[11]").value("C004"));

        List<String> savedOwnedCardIds = currentSkillDeckEntryRepository.findByCharacterIdOrderByPositionAsc(character.getId()).stream()
                .map(CharacterCurrentSkillDeckEntry::getOwnedCardId)
                .toList();
        assertFalse(savedOwnedCardIds.stream().anyMatch(id -> id.contains("tig")));
        assertTrue(savedOwnedCardIds.contains("oc-c004-3"));
        assertTrue(deckRepository.findFirstByTypeAndName(
                DeckType.PLAYER,
                "character:" + character.getId() + ":currentSkillDeck"
        ).isEmpty());
    }

    @Test
    @DisplayName("캐릭터 삭제 시 currentSkillDeck 미러 덱도 삭제된다")
    void deletingCharacterRemovesCurrentSkillDeckMirrorDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckDeleteMirror");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));
        Deck deck = createDeck("player-apply-delete", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "C004", 3
        ));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), deck.getId())
                        .session(session))
                .andExpect(status().isOk());

        assertFalse(currentSkillDeckEntryRepository.findByCharacterId(character.getId()).isEmpty());

        mockMvc.perform(delete("/api/content/characters/{id}", character.getId())
                        .session(session))
                .andExpect(status().isOk());

        assertTrue(characterProfileRepository.findById(character.getId()).isEmpty());
        assertTrue(currentSkillDeckEntryRepository.findByCharacterId(character.getId()).isEmpty());
        assertTrue(characterOwnedCardRepository.findByCharacterId(character.getId()).isEmpty());
    }

    @Test
    @DisplayName("ownedCards 변경 저장 시 stale currentSkillDeck entry를 비운다")
    void updatingOwnedCardsClearsCurrentSkillDeckEntries() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckOwnedCardsChanged");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));
        Deck deck = createDeck("player-apply-owned-cards-change", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "C004", 3
        ));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), deck.getId())
                        .session(session))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/content/characters/{id}", character.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "test-character",
                                  "gender": "MALE",
                                  "age": 20,
                                  "wish": "wish",
                                  "disposition": "질서/선",
                                  "oneLiner": "oneLiner",
                                  "story": "story",
                                  "physical": 5,
                                  "technique": 5,
                                  "sense": 5,
                                  "willpower": 5,
                                  "trait1": "trait1",
                                  "trait2": "trait2",
                                  "hiddenTraitIds": [],
                                  "ownedCards": "[{\\"cardId\\":\\"C001\\"}]",
                                  "exCard": "{}"
                                }
                                """))
                .andExpect(status().isOk());

        CharacterProfile reloaded = characterProfileRepository.findById(character.getId()).orElseThrow();
        assertEquals(null, reloaded.getCurrentSkillDeck());
        assertTrue(currentSkillDeckEntryRepository.findByCharacterId(character.getId()).isEmpty());
    }

    @Test
    @DisplayName("detail 조회는 ownedCardId로 저장된 currentSkillDeck의 preview를 cardId로 반환한다")
    void characterDetailPreviewResolvesOwnedCardIdStoredCurrentSkillDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("detailPreviewOwnedIds");
        CharacterProfile character = createCharacter(
                """
                        [
                          {"ownedCardId":"oc-1","cardId":"C001"},
                          {"ownedCardId":"oc-2","cardId":"C001"},
                          {"ownedCardId":"oc-3","cardId":"C002"}
                        ]
                        """,
                List.of("oc-2", "oc-1", "oc-3")
        );

        mockMvc.perform(get("/api/content/characters/{id}", character.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[0]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[1]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[2]").value("C002"));
    }

    @Test
    @DisplayName("detail/list 응답은 stale ownedCardId를 preview에서 제외하고 raw currentSkillDeck을 노출하지 않는다")
    void characterReadResponsesDropStaleOwnedCardIdsAndDoNotExposeRawCurrentSkillDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("detailPreviewStaleOwnedIds");
        CharacterProfile character = createCharacter(
                """
                        [
                          {"ownedCardId":"oc-1","cardId":"C001"},
                          {"ownedCardId":"oc-2","cardId":"C002"}
                        ]
                        """,
                List.of("oc-1", "oc-stale", "oc-2")
        );

        mockMvc.perform(get("/api/content/characters/{id}", character.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds.length()").value(2))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[0]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds[1]").value("C002"));

        String listJson = mockMvc.perform(get("/api/content/characters")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$[0].currentSkillDeckPreviewCardIds.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(listJson.contains("\"currentSkillDeck\":"));
        assertFalse(listJson.contains("oc-stale"));
    }

    @Test
    @DisplayName("덱 적용 API는 없는 캐릭터에 NOT_FOUND를 반환한다")
    void applyDeckMissingCharacterReturnsNotFound() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckMissingCharacter");
        Deck deck = createDeck("player-apply", DeckType.PLAYER, orderedCards(
                "C001", 3,
                "C002", 3,
                "C003", 3,
                "C004", 3
        ));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        999999, deck.getId())
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("덱 적용 API는 없는 덱에 NOT_FOUND를 반환한다")
    void applyDeckMissingDeckReturnsNotFound() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckMissingDeck");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), 999999)
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("덱 적용 API는 PLAYER가 아닌 덱을 거부한다")
    void applyDeckRejectsNonPlayerDeck() throws Exception {
        MockHttpSession session = signUpAndLogin("applyDeckEnemy");
        CharacterProfile character = createCharacter(List.of("OLD_CARD"));
        Deck deck = createDeck("enemy-apply", DeckType.ENEMY, orderedCards("C001", 1));

        mockMvc.perform(post("/api/content/characters/{characterId}/current-skill-deck/from-deck/{deckId}",
                        character.getId(), deck.getId())
                        .session(session))
                .andExpect(status().isBadRequest());
    }

    private CharacterProfile createCharacter(List<String> currentSkillDeck) {
        return createCharacter(defaultOwnedCardsJson(), currentSkillDeck);
    }

    private CharacterProfile createCharacter(String ownedCards, List<String> currentSkillDeck) {
        CharacterProfile profile = CharacterProfile.builder()
                .name("test-character")
                .gender(CharacterGender.MALE)
                .age(20)
                .wish("wish")
                .disposition("test/test")
                .oneLiner("oneLiner")
                .story("story")
                .physical(5)
                .technique(5)
                .sense(5)
                .willpower(5)
                .trait1("trait1")
                .trait2("trait2")
                .hiddenTraitIds(List.of())
                .ownedCards("[]")
                .currentSkillDeck(null)
                .exCard("{}")
                .build();
        CharacterProfile saved = characterProfileRepository.save(profile);
        cardCollectionService.replaceOwnedCardsFromJson(saved.getId(), ownedCards);
        insertCurrentSkillDeckEntries(saved.getId(), currentSkillDeck);
        return saved;
    }

    private void insertCurrentSkillDeckEntries(Long characterId, List<String> currentSkillDeck) {
        if (currentSkillDeck == null || currentSkillDeck.isEmpty()) {
            return;
        }
        List<CharacterCurrentSkillDeckEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < currentSkillDeck.size(); i++) {
            entries.add(CharacterCurrentSkillDeckEntry.builder()
                    .characterId(characterId)
                    .ownedCardId(currentSkillDeck.get(i))
                    .position(i)
                    .build());
        }
        currentSkillDeckEntryRepository.saveAll(entries);
    }

    private String defaultOwnedCardsJson() {
        return """
                [
                  {"ownedCardId":"oc-c001-1","cardId":"C001"},
                  {"ownedCardId":"oc-c001-2","cardId":"C001"},
                  {"ownedCardId":"oc-c001-3","cardId":"C001"},
                  {"ownedCardId":"oc-c002-1","cardId":"C002"},
                  {"ownedCardId":"oc-c002-2","cardId":"C002"},
                  {"ownedCardId":"oc-c002-3","cardId":"C002"},
                  {"ownedCardId":"oc-c003-1","cardId":"C003"},
                  {"ownedCardId":"oc-c003-2","cardId":"C003"},
                  {"ownedCardId":"oc-c003-3","cardId":"C003"},
                  {"ownedCardId":"oc-c004-1","cardId":"C004"},
                  {"ownedCardId":"oc-c004-2","cardId":"C004"},
                  {"ownedCardId":"oc-c004-3","cardId":"C004"},
                  {"ownedCardId":"oc-tig-1","cardId":"Tig001_Card"},
                  {"ownedCardId":"oc-tig-2","cardId":"Tig001_Card"},
                  {"ownedCardId":"oc-tig-3","cardId":"Tig001_Card"}
                ]
                """;
    }

    private Deck createDeck(String name, DeckType type, Map<String, Integer> cards) {
        Deck deck = Deck.create(name, type);
        deck.syncCards(cards);
        return deckRepository.save(deck);
    }

    private Map<String, Integer> orderedCards(Object... pairs) {
        Map<String, Integer> cards = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            cards.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return cards;
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
