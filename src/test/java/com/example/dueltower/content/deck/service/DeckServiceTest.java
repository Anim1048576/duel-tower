package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.domain.Deck;
import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.AddDeckCardsRequest;
import com.example.dueltower.content.deck.dto.CreateDeckRequest;
import com.example.dueltower.content.deck.dto.DeckCardSpec;
import com.example.dueltower.content.deck.dto.DeckResponse;
import com.example.dueltower.content.deck.dto.UpdateDeckRequest;
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
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardService cardService;

    @Mock
    private DeckLimitPolicy deckLimitPolicy;

    @InjectMocks
    private DeckService service;

    @Test
    @DisplayName("create: PLAYER에서 이름이 null/blank면 기본값 player-deck을 사용한다")
    void createDefaultsPlayerNameWhenNameIsNullOrBlank() {
        when(cardService.asMap()).thenReturn(cardMap("card-1", "card-2", "card-3", "card-4"));
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeckResponse nullName = service.create(new CreateDeckRequest(null, DeckType.PLAYER, cards12()));
        DeckResponse blankName = service.create(new CreateDeckRequest("   ", DeckType.PLAYER, cards12()));

        assertEquals("player-deck", nullName.name());
        assertEquals("player-deck", blankName.name());
    }

    @Test
    @DisplayName("create: ENEMY에서 이름이 null/blank면 기본값 enemy-deck을 사용한다")
    void createDefaultsEnemyNameWhenNameIsNullOrBlank() {
        when(cardService.asMap()).thenReturn(cardMap("enemy-card"));
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeckResponse nullName = service.create(new CreateDeckRequest(null, DeckType.ENEMY, List.of(new DeckCardSpec("enemy-card", 1))));
        DeckResponse blankName = service.create(new CreateDeckRequest("   ", DeckType.ENEMY, List.of(new DeckCardSpec("enemy-card", 1))));

        assertEquals("enemy-deck", nullName.name());
        assertEquals("enemy-deck", blankName.name());
    }

    @Test
    @DisplayName("get: 존재하지 않는 덱이면 NOT_FOUND를 던진다")
    void getNonexistentDeckReturnsNotFound() {
        when(deckRepository.findWithCardsById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(99L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("delete: 존재하지 않는 덱이면 NOT_FOUND를 던진다")
    void deleteNonexistentDeckReturnsNotFound() {
        when(deckRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(99L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("update: 이름/타입/카드 구성을 정규화해서 변경한다")
    void updateChangesNameTypeAndCardsUsingNormalizedInput() {
        when(cardService.asMap()).thenReturn(cardMap("card-a", "card-b"));
        Deck deck = Deck.create("old-name", DeckType.PLAYER);
        deck.syncCards(Map.of("card-a", 12));
        when(deckRepository.findWithCardsById(7L)).thenReturn(Optional.of(deck));

        DeckResponse updated = service.update(7L, new UpdateDeckRequest(
                "  updated-name  ",
                DeckType.ENEMY,
                List.of(
                        new DeckCardSpec("  card-a ", 2),
                        new DeckCardSpec("card-b", 3)
                )
        ));

        assertEquals("updated-name", updated.name());
        assertEquals(DeckType.ENEMY, updated.type());
        assertEquals(5, updated.totalCards());
        assertEquals(2, updated.cards().size());
    }

    @Test
    @DisplayName("addCards: 기존 카드 매수를 대체하지 않고 누적한다")
    void addCardsAccumulatesCounts() {
        when(cardService.asMap()).thenReturn(cardMap("card-a"));
        Deck deck = Deck.create("deck", DeckType.ENEMY);
        deck.syncCards(Map.of("card-a", 2));
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        DeckResponse response = service.addCards(1L, new AddDeckCardsRequest(List.of(new DeckCardSpec("card-a", 3))));

        assertEquals(5, response.totalCards());
        assertEquals(5, response.cards().get(0).count());
    }

    @Test
    @DisplayName("addCards: 존재하지 않는 카드가 있으면 상태 변경 전에 예외를 던진다")
    void addCardsValidatesCardExistenceBeforeMutation() {
        when(cardService.asMap()).thenReturn(cardMap("known"));
        Deck deck = Deck.create("deck", DeckType.ENEMY);
        deck.syncCards(Map.of("known", 2));
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addCards(1L, new AddDeckCardsRequest(List.of(new DeckCardSpec("unknown", 1)))));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals(2, deck.getCards().get(0).getCount());
    }

    @Test
    @DisplayName("addCards: PLAYER 덱은 누적된 카드 맵으로 validatePlayerDeckUpTo를 호출한다")
    void addCardsPlayerCallsValidatePlayerDeckUpToWithMergedCounts() {
        when(cardService.asMap()).thenReturn(cardMap("card-a", "card-b"));
        Deck deck = Deck.create("deck", DeckType.PLAYER);
        deck.syncCards(Map.of("card-a", 2));
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        service.addCards(1L, new AddDeckCardsRequest(List.of(
                new DeckCardSpec("card-a", 1),
                new DeckCardSpec("card-b", 4)
        )));

        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(deckLimitPolicy).validatePlayerDeckUpTo(captor.capture());
        assertEquals(3, captor.getValue().get("card-a"));
        assertEquals(4, captor.getValue().get("card-b"));
    }

    @Test
    @DisplayName("addCards: ENEMY 덱은 player deck limit 검증을 호출하지 않는다")
    void addCardsEnemyDoesNotValidatePlayerDeckLimit() {
        when(cardService.asMap()).thenReturn(cardMap("enemy-card"));
        Deck deck = Deck.create("enemy", DeckType.ENEMY);
        deck.syncCards(Map.of("enemy-card", 1));
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        service.addCards(1L, new AddDeckCardsRequest(List.of(new DeckCardSpec("enemy-card", 2))));

        verify(deckLimitPolicy, never()).validatePlayerDeckUpTo(any());
    }

    @Test
    @DisplayName("addCards: blank cardId를 거부한다")
    void addCardsRejectsBlankCardId() {
        Deck deck = Deck.create("deck", DeckType.ENEMY);
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addCards(1L, new AddDeckCardsRequest(List.of(new DeckCardSpec("   ", 1)))));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("addCards: count가 0 이하이면 거부한다")
    void addCardsRejectsCountLessOrEqualZero() {
        Deck deck = Deck.create("deck", DeckType.ENEMY);
        when(deckRepository.findWithCardsById(1L)).thenReturn(Optional.of(deck));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addCards(1L, new AddDeckCardsRequest(List.of(new DeckCardSpec("card-a", 0)))));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
    }

    private static List<DeckCardSpec> cards12() {
        return List.of(
                new DeckCardSpec("card-1", 3),
                new DeckCardSpec("card-2", 3),
                new DeckCardSpec("card-3", 3),
                new DeckCardSpec("card-4", 3)
        );
    }

    private static Map<Ids.CardDefId, CardDefinition> cardMap(String... ids) {
        Map<Ids.CardDefId, CardDefinition> map = new HashMap<>();
        for (String id : ids) {
            map.put(new Ids.CardDefId(id), org.mockito.Mockito.mock(CardDefinition.class));
        }
        return map;
    }
}
