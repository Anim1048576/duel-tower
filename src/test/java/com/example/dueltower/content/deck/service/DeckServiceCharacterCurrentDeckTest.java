package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.repository.DeckRepository;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class DeckServiceCharacterCurrentDeckTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardService cardService;

    @Mock
    private DeckLimitPolicy deckLimitPolicy;

    @InjectMocks
    private DeckService service;

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: characterId가 0 이하이면 거부한다")
    void rejectsNonPositiveCharacterId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertCharacterCurrentSkillDeck(0, List.of("c1")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: deckCardIds가 null이면 거부한다")
    void rejectsNullDeckCardIds() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertCharacterCurrentSkillDeck(1L, null));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: deckCardIds 내부 blank 값을 거부한다")
    void rejectsBlankValuesInsideDeckCardIds() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertCharacterCurrentSkillDeck(1L, List.of("card-1", "   ")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: 알 수 없는 cardId를 거부한다")
    void rejectsUnknownCardId() {
        when(cardService.asMap()).thenReturn(cardMap("known"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.upsertCharacterCurrentSkillDeck(1L, List.of("known", "unknown")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: 병합된 카드 매수로 validatePlayerDeckExact를 호출한다")
    void callsValidatePlayerDeckExactWithMergedCounts() {
        when(cardService.asMap()).thenReturn(cardMap("a", "b"));

        service.upsertCharacterCurrentSkillDeck(7L, List.of("a", "a", "b"));

        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(deckLimitPolicy).validatePlayerDeckExact(captor.capture());
        assertEquals(2, captor.getValue().get("a"));
        assertEquals(1, captor.getValue().get("b"));
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: 기존 덱이 없으면 파생 이름의 PLAYER 덱을 생성한다")
    void createsNewPlayerDeckWithDerivedNameWhenMissing() {
        when(cardService.asMap()).thenReturn(cardMap(
                "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12"
        ));
        when(deckRepository.findFirstByTypeAndName(DeckType.PLAYER, "character:3:currentSkillDeck"))
                .thenReturn(Optional.empty());

        service.upsertCharacterCurrentSkillDeck(3L, List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12"));

        ArgumentCaptor<Deck> captor = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(captor.capture());
        Deck saved = captor.getValue();
        assertEquals(DeckType.PLAYER, saved.getType());
        assertEquals("character:3:currentSkillDeck", saved.getName());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: 기존 덱이 있으면 같은 파생 이름으로 업데이트한다")
    void updatesExistingDeckUsingSameDerivedName() {
        when(cardService.asMap()).thenReturn(cardMap("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12"));
        Deck existing = Deck.create("legacy-name", DeckType.ENEMY);
        existing.syncCards(Map.of("c1", 12));
        when(deckRepository.findFirstByTypeAndName(DeckType.PLAYER, "character:9:currentSkillDeck"))
                .thenReturn(Optional.of(existing));

        service.upsertCharacterCurrentSkillDeck(9L, List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12"));

        ArgumentCaptor<Deck> captor = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(captor.capture());
        Deck saved = captor.getValue();
        assertEquals(DeckType.PLAYER, saved.getType());
        assertEquals("character:9:currentSkillDeck", saved.getName());
    }

    @Test
    @DisplayName("upsertCharacterCurrentSkillDeck: 동기화된 정확히 12장 구성으로 저장한다")
    void savesSyncedExact12CardDeckContent() {
        when(cardService.asMap()).thenReturn(cardMap("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"));
        when(deckRepository.findFirstByTypeAndName(any(), any())).thenReturn(Optional.empty());

        service.upsertCharacterCurrentSkillDeck(5L, List.of("a", "a", "b", "b", "c", "d", "e", "f", "g", "h", "i", "j"));

        ArgumentCaptor<Deck> captor = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(captor.capture());
        Deck saved = captor.getValue();

        int total = saved.getCards().stream().mapToInt(card -> card.getCount()).sum();
        assertEquals(12, total);
        assertEquals(2, saved.getCards().stream().filter(card -> card.getCardId().equals("a")).findFirst().orElseThrow().getCount());
        assertEquals(2, saved.getCards().stream().filter(card -> card.getCardId().equals("b")).findFirst().orElseThrow().getCount());
    }

    @Test
    @DisplayName("deleteCharacterCurrentSkillDeck: 파생 currentSkillDeck 덱이 있으면 삭제한다")
    void deleteCharacterCurrentSkillDeckDeletesMirrorDeckWhenPresent() {
        Deck existing = Deck.create("character:4:currentSkillDeck", DeckType.PLAYER);
        when(deckRepository.findFirstByTypeAndName(DeckType.PLAYER, "character:4:currentSkillDeck"))
                .thenReturn(Optional.of(existing));

        service.deleteCharacterCurrentSkillDeck(4L);

        verify(deckRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteCharacterCurrentSkillDeck: 파생 currentSkillDeck 덱이 없어도 성공한다")
    void deleteCharacterCurrentSkillDeckIgnoresMissingMirrorDeck() {
        when(deckRepository.findFirstByTypeAndName(DeckType.PLAYER, "character:4:currentSkillDeck"))
                .thenReturn(Optional.empty());

        service.deleteCharacterCurrentSkillDeck(4L);

        verify(deckRepository, never()).delete(any());
    }

    private static Map<Ids.CardDefId, CardDefinition> cardMap(String... ids) {
        Map<Ids.CardDefId, CardDefinition> map = new HashMap<>();
        for (String id : ids) {
            map.put(new Ids.CardDefId(id), org.mockito.Mockito.mock(CardDefinition.class));
        }
        return map;
    }
}
