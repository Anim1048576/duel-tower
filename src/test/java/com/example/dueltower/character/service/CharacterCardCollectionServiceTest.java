package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterOwnedCard;
import com.example.dueltower.character.domain.CharacterOwnedCardModifier;
import com.example.dueltower.character.dto.CharacterOwnedCardResponse;
import com.example.dueltower.character.repository.CharacterOwnedCardModifierRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class CharacterCardCollectionServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private CharacterOwnedCardRepository ownedCardRepository;

    @Mock
    private CharacterOwnedCardModifierRepository ownedCardModifierRepository;

    @InjectMocks
    private CharacterCardCollectionService service;

    @Test
    void replaceOwnedCardsFromJsonAcceptsLegacyStringCardIds() {
        when(ownedCardRepository.findByCharacterId(1L)).thenReturn(List.of());

        service.replaceOwnedCardsFromJson(1L, """
                [" C001 ", "C002"]
                """);

        List<CharacterOwnedCard> saved = capturedSavedCards();
        assertEquals(2, saved.size());
        assertEquals(1L, saved.get(0).getCharacterId());
        assertEquals("C001", saved.get(0).getCardId());
        assertNotNull(saved.get(0).getOwnedCardId());
        assertFalse(saved.get(0).getOwnedCardId().isBlank());
        assertEquals("C002", saved.get(1).getCardId());
        verify(ownedCardModifierRepository).saveAll(List.of());
    }

    @Test
    void replaceOwnedCardsFromJsonAcceptsOwnedCardIds() {
        when(ownedCardRepository.findByCharacterId(1L)).thenReturn(List.of());

        service.replaceOwnedCardsFromJson(1L, """
                [
                  {"ownedCardId": " oc-1 ", "cardId": " C001 "}
                ]
                """);

        List<CharacterOwnedCard> saved = capturedSavedCards();
        assertEquals(1, saved.size());
        assertEquals("oc-1", saved.get(0).getOwnedCardId());
        assertEquals("C001", saved.get(0).getCardId());
    }

    @Test
    void replaceOwnedCardsPreservesLegacyBooleansAsCompatibilityFieldsAndModifiers() {
        when(ownedCardRepository.findByCharacterId(1L)).thenReturn(List.of());

        service.replaceOwnedCards(1L, List.of(new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(),
                true,
                true,
                true,
                null,
                null
        )));

        CharacterOwnedCard savedCard = capturedSavedCards().get(0);
        assertTrue(savedCard.isStrengthened());
        assertTrue(savedCard.isWeakened());
        assertTrue(savedCard.isLockedInDeck());

        List<CharacterOwnedCardModifier> savedModifiers = capturedSavedModifiers();
        assertEquals(List.of(
                CardModifierIds.STRENGTHENED,
                CardModifierIds.WEAKENED,
                CardModifierIds.LOCKED_IN_DECK
        ), savedModifiers.stream().map(CharacterOwnedCardModifier::getModifierId).toList());
    }

    @Test
    void toRuntimeOwnedCardsReturnsOwnedCardModels() {
        givenStoredRows();

        List<OwnedCard> ownedCards = service.toRuntimeOwnedCards(1L);

        assertEquals(1, ownedCards.size());
        OwnedCard ownedCard = ownedCards.get(0);
        assertEquals("oc-1", ownedCard.ownedCardId());
        assertEquals("C001", ownedCard.cardId());
        assertTrue(ownedCard.strengthened());
        assertTrue(ownedCard.lockedInDeck());
    }

    @Test
    void toOwnedCardsJsonReturnsApiCompatibleShape() throws Exception {
        givenStoredRows();

        String json = service.toOwnedCardsJson(1L);

        JsonNode root = JSON.readTree(json);
        assertTrue(root.isArray());
        assertEquals(1, root.size());
        JsonNode card = root.get(0);
        assertEquals("oc-1", card.path("ownedCardId").asText());
        assertEquals("C001", card.path("cardId").asText());
        assertTrue(card.path("strengthened").asBoolean());
        assertFalse(card.path("weakened").asBoolean());
        assertTrue(card.path("lockedInDeck").asBoolean());
        assertEquals(CardModifierIds.STRENGTHENED, card.path("modifiers").get(0).path("modifierId").asText());
        assertEquals(1, card.path("modifiers").get(0).path("value").asInt());
    }

    @Test
    void toOwnedCardResponsesConvertsSessionDtosToCharacterApiResponses() {
        CharacterCardCollectionService spy = spy(service);
        doReturn(List.of(new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 7)),
                true,
                false,
                true,
                false,
                "required-by-current-deck"
        ))).when(spy).toOwnedCardDtos(1L);

        List<CharacterOwnedCardResponse> responses = spy.toOwnedCardResponses(1L);

        assertEquals(1, responses.size());
        CharacterOwnedCardResponse response = responses.get(0);
        assertEquals("oc-1", response.ownedCardId());
        assertEquals("C001", response.cardId());
        assertTrue(response.strengthened());
        assertFalse(response.weakened());
        assertTrue(response.lockedInDeck());
        assertFalse(response.forgettable());
        assertEquals("required-by-current-deck", response.notForgettableReason());
        assertEquals(1, response.modifiers().size());
        assertEquals(CardModifierIds.STRENGTHENED, response.modifiers().get(0).modifierId());
        assertEquals(7, response.modifiers().get(0).value());
    }

    @Test
    void toOwnedCardResponsesConvertsNullableBooleansNullSafely() {
        CharacterCardCollectionService spy = spy(service);
        doReturn(List.of(new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(),
                null,
                null,
                null,
                null,
                null
        ))).when(spy).toOwnedCardDtos(1L);

        CharacterOwnedCardResponse response = spy.toOwnedCardResponses(1L).get(0);

        assertFalse(response.strengthened());
        assertFalse(response.weakened());
        assertFalse(response.lockedInDeck());
        assertTrue(response.forgettable());
        assertNull(response.notForgettableReason());
    }

    @Test
    void replaceOwnedCardsDeletesExistingCardsAndModifiersBeforeSavingReplacement() {
        when(ownedCardRepository.findByCharacterId(1L)).thenReturn(List.of(
                CharacterOwnedCard.builder().ownedCardId("old-1").characterId(1L).cardId("C000").build()
        ));

        service.replaceOwnedCards(1L, List.of(new OwnedCardDto(
                "new-1",
                "C001",
                List.of(new OwnedCardModifierDto(CardModifierIds.STRENGTHENED, 1)),
                false,
                false,
                false,
                true,
                null
        )));

        InOrder inOrder = inOrder(ownedCardModifierRepository, ownedCardRepository);
        inOrder.verify(ownedCardModifierRepository).deleteByOwnedCardIdIn(List.of("old-1"));
        inOrder.verify(ownedCardRepository).deleteByCharacterId(1L);
        inOrder.verify(ownedCardRepository).saveAll(anyList());
        inOrder.verify(ownedCardModifierRepository).saveAll(anyList());
    }

    @Test
    void replaceOwnedCardsRejectsDuplicateOwnedCardIdsBeforeDeletingExistingRows() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.replaceOwnedCards(1L, List.of(
                new OwnedCardDto("dup-1", "C001", List.of(), false, false, false, true, null),
                new OwnedCardDto(" dup-1 ", "C002", List.of(), false, false, false, true, null)
        )));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"ownedCards.ownedCardId must be unique: dup-1\"", ex.getMessage());
        verify(ownedCardRepository, never()).deleteByCharacterId(anyLong());
        verify(ownedCardModifierRepository, never()).deleteByOwnedCardIdIn(anyList());
        verify(ownedCardRepository, never()).saveAll(anyList());
    }

    @Test
    void hasOwnedCardTrimsOwnedCardId() {
        when(ownedCardRepository.existsByCharacterIdAndOwnedCardId(1L, "oc-1")).thenReturn(true);

        assertTrue(service.hasOwnedCard(1L, " oc-1 "));
        assertFalse(service.hasOwnedCard(1L, " "));
    }

    private void givenStoredRows() {
        when(ownedCardRepository.findByCharacterIdOrderByCreateDateAscOwnedCardIdAsc(1L)).thenReturn(List.of(
                CharacterOwnedCard.builder()
                        .ownedCardId("oc-1")
                        .characterId(1L)
                        .cardId("C001")
                        .strengthened(true)
                        .weakened(false)
                        .lockedInDeck(true)
                        .forgettable(true)
                        .build()
        ));
        when(ownedCardModifierRepository.findByOwnedCardIdInOrderByIdAsc(List.of("oc-1"))).thenReturn(List.of(
                CharacterOwnedCardModifier.builder()
                        .ownedCardId("oc-1")
                        .modifierId(CardModifierIds.STRENGTHENED)
                        .value(1)
                        .build(),
                CharacterOwnedCardModifier.builder()
                        .ownedCardId("oc-1")
                        .modifierId(CardModifierIds.LOCKED_IN_DECK)
                        .value(1)
                        .build()
        ));
    }

    @SuppressWarnings("unchecked")
    private List<CharacterOwnedCard> capturedSavedCards() {
        ArgumentCaptor<List<CharacterOwnedCard>> captor = ArgumentCaptor.forClass(List.class);
        verify(ownedCardRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<CharacterOwnedCardModifier> capturedSavedModifiers() {
        ArgumentCaptor<List<CharacterOwnedCardModifier>> captor = ArgumentCaptor.forClass(List.class);
        verify(ownedCardModifierRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
