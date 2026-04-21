package com.example.dueltower.character.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private DeckRepository deckRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
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
                .andExpect(jsonPath("$.currentSkillDeck.length()").value(12))
                .andExpect(jsonPath("$.currentSkillDeck[0]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeck[1]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeck[2]").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeck[3]").value("C002"))
                .andExpect(jsonPath("$.currentSkillDeck[11]").value("C004"))
                .andExpect(jsonPath("$.ownedCards").value("[]"))
                .andExpect(jsonPath("$.exCard").value("{}"))
                .andExpect(jsonPath("$.combatStats.maxHp").exists());
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
                .currentSkillDeck(currentSkillDeck)
                .exCard("{}")
                .build();
        return characterProfileRepository.save(profile);
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
