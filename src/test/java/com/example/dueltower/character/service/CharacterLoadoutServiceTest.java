package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterCurrentSkillDeckEntry;
import com.example.dueltower.character.domain.CharacterExLoadout;
import com.example.dueltower.character.repository.CharacterCurrentSkillDeckEntryRepository;
import com.example.dueltower.character.repository.CharacterExLoadoutRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.deck.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class CharacterLoadoutServiceTest {

    @Mock
    private CharacterCurrentSkillDeckEntryRepository currentSkillDeckEntryRepository;

    @Mock
    private CharacterExLoadoutRepository exLoadoutRepository;

    @Mock
    private CharacterCardCollectionService cardCollectionService;

    @Mock
    private DeckService deckService;

    @InjectMocks
    private CharacterLoadoutService service;

    @Test
    void replaceCurrentSkillDeckFromOwnedCardIdsSavesAndReadsInOrder() {
        when(cardCollectionService.hasOwnedCard(1L, "oc-1")).thenReturn(true);
        when(cardCollectionService.hasOwnedCard(1L, "oc-2")).thenReturn(true);
        when(currentSkillDeckEntryRepository.findByCharacterIdOrderByPositionAsc(1L)).thenReturn(List.of(
                entry("oc-1", 0),
                entry("oc-2", 1)
        ));

        service.replaceCurrentSkillDeckFromOwnedCardIds(1L, List.of(" oc-1 ", "oc-2"));

        List<CharacterCurrentSkillDeckEntry> saved = capturedSavedEntries();
        assertEquals(List.of("oc-1", "oc-2"), saved.stream().map(CharacterCurrentSkillDeckEntry::getOwnedCardId).toList());
        assertEquals(List.of(0, 1), saved.stream().map(CharacterCurrentSkillDeckEntry::getPosition).toList());
        assertEquals(List.of("oc-1", "oc-2"), service.getCurrentSkillDeckOwnedCardIds(1L));
    }

    @Test
    void replaceCurrentSkillDeckRejectsDuplicateOwnedCardIds() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromOwnedCardIds(1L, List.of("oc-1", " oc-1 ")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"currentSkillDeck ownedCardIds must not contain duplicate values: oc-1\"", ex.getMessage());
        verify(currentSkillDeckEntryRepository, never()).deleteByCharacterId(anyLong());
        verify(currentSkillDeckEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceCurrentSkillDeckRejectsUnavailableOwnedCardId() {
        when(cardCollectionService.hasOwnedCard(1L, "oc-missing")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromOwnedCardIds(1L, List.of("oc-missing")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"owned card unavailable: oc-missing\"", ex.getMessage());
        verify(currentSkillDeckEntryRepository, never()).deleteByCharacterId(anyLong());
        verify(currentSkillDeckEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void getCurrentSkillDeckPreviewCardIdsReturnsCardIds() {
        when(currentSkillDeckEntryRepository.findByCharacterIdOrderByPositionAsc(1L)).thenReturn(List.of(
                entry("oc-2", 0),
                entry("oc-1", 1)
        ));
        when(cardCollectionService.ownedCardMap(1L)).thenReturn(
                java.util.Map.of(
                        "oc-1", new OwnedCard("oc-1", "C001", List.of()),
                        "oc-2", new OwnedCard("oc-2", "C002", List.of())
                )
        );

        assertEquals(List.of("C002", "C001"), service.getCurrentSkillDeckPreviewCardIds(1L));
    }

    @Test
    void replaceExCardSavesAndGetExCardIdReads() {
        when(exLoadoutRepository.findById(1L)).thenReturn(Optional.empty(), Optional.of(
                CharacterExLoadout.builder().characterId(1L).exCardId("EX001").build()
        ));

        service.replaceExCard(1L, " EX001 ");

        ArgumentCaptor<CharacterExLoadout> captor = ArgumentCaptor.forClass(CharacterExLoadout.class);
        verify(exLoadoutRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getCharacterId());
        assertEquals("EX001", captor.getValue().getExCardId());
        assertEquals("EX001", service.getExCardId(1L));
    }

    @Test
    void applyDeckTemplateMapsCardIdsToOwnedCardIdsByFirstUnusedCopy() {
        when(deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(10L)).thenReturn(List.of("C001", "C001", "C002"));
        when(cardCollectionService.toRuntimeOwnedCards(1L)).thenReturn(List.of(
                new OwnedCard("oc-a", "C001", List.of()),
                new OwnedCard("oc-b", "C002", List.of()),
                new OwnedCard("oc-c", "C001", List.of())
        ));
        when(cardCollectionService.hasOwnedCard(1L, "oc-a")).thenReturn(true);
        when(cardCollectionService.hasOwnedCard(1L, "oc-c")).thenReturn(true);
        when(cardCollectionService.hasOwnedCard(1L, "oc-b")).thenReturn(true);

        service.applyDeckTemplate(1L, 10L);

        List<CharacterCurrentSkillDeckEntry> saved = capturedSavedEntries();
        assertEquals(List.of("oc-a", "oc-c", "oc-b"), saved.stream().map(CharacterCurrentSkillDeckEntry::getOwnedCardId).toList());
        assertEquals(List.of(0, 1, 2), saved.stream().map(CharacterCurrentSkillDeckEntry::getPosition).toList());
    }

    @Test
    void applyDeckTemplateFailsWhenOwnedCardsAreInsufficient() {
        when(deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(10L)).thenReturn(List.of("C001", "C001"));
        when(cardCollectionService.toRuntimeOwnedCards(1L)).thenReturn(List.of(
                new OwnedCard("oc-a", "C001", List.of())
        ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyDeckTemplate(1L, 10L));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"owned card unavailable: C001\"", ex.getMessage());
        verify(currentSkillDeckEntryRepository, never()).deleteByCharacterId(anyLong());
        verify(currentSkillDeckEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void clearCurrentSkillDeckDeletesEntries() {
        service.clearCurrentSkillDeck(1L);

        verify(currentSkillDeckEntryRepository).deleteByCharacterId(1L);
    }

    private static CharacterCurrentSkillDeckEntry entry(String ownedCardId, int position) {
        return CharacterCurrentSkillDeckEntry.builder()
                .characterId(1L)
                .ownedCardId(ownedCardId)
                .position(position)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<CharacterCurrentSkillDeckEntry> capturedSavedEntries() {
        ArgumentCaptor<List<CharacterCurrentSkillDeckEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(currentSkillDeckEntryRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
