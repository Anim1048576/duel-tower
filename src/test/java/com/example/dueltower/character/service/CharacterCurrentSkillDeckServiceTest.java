package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.deck.service.DeckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class CharacterCurrentSkillDeckServiceTest {

    @Mock
    private CharacterProfileRepository repository;

    @Mock
    private DeckService deckService;

    @InjectMocks
    private CharacterCurrentSkillDeckService service;

    @Test
    @DisplayName("cardId 기반 currentSkillDeck 저장은 profile과 미러 deck을 함께 갱신한다")
    void replaceCurrentSkillDeckFromCardIdsUpdatesProfileAndMirrorDeck() {
        CharacterProfile profile = profile();
        when(repository.save(profile)).thenReturn(profile);

        CharacterProfile saved = service.replaceCurrentSkillDeckFromCardIds(profile, List.of(" C001 ", "C001", "C002"));

        assertIterableEquals(List.of("C001", "C001", "C002"), saved.getCurrentSkillDeck());
        verify(repository).save(profile);
        verify(deckService).upsertCharacterCurrentSkillDeck(7L, List.of("C001", "C001", "C002"));
    }

    @Test
    @DisplayName("cardId based currentSkillDeck write rejects blank cardId input")
    void replaceCurrentSkillDeckFromCardIdsRejectsBlankCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromCardIds(profile(), List.of("C001", "   ")));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("currentSkillDeck cardIds must not contain blank values", ex.getReason());
    }

    @Test
    @DisplayName("ownedCardId 기반 currentSkillDeck 저장은 ownedCardId를 저장하고 cardId로 미러 deck을 갱신한다")
    void replaceCurrentSkillDeckFromOwnedCardIdsStoresOwnedIdsAndMirrorsCardIds() {
        CharacterProfile profile = profile();
        List<OwnedCard> ownedCards = List.of(
                new OwnedCard("oc-1", "C001", List.of()),
                new OwnedCard("oc-2", "C001", List.of()),
                new OwnedCard("oc-3", "C002", List.of())
        );
        when(repository.save(profile)).thenReturn(profile);

        CharacterProfile saved = service.replaceCurrentSkillDeckFromOwnedCardIds(
                profile,
                List.of(" oc-2 ", "oc-1", "oc-3"),
                ownedCards
        );

        assertIterableEquals(List.of("oc-2", "oc-1", "oc-3"), saved.getCurrentSkillDeck());
        verify(repository).save(profile);
        verify(deckService).upsertCharacterCurrentSkillDeck(7L, List.of("C001", "C001", "C002"));
    }

    @Test
    @DisplayName("ownedCardId 기반 저장은 알 수 없는 ownedCardId를 거부한다")
    void replaceCurrentSkillDeckFromOwnedCardIdsRejectsUnknownOwnedCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromOwnedCardIds(
                        profile(),
                        List.of("missing-owned-card"),
                        List.of(new OwnedCard("oc-1", "C001", List.of()))
                ));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("owned card unavailable: missing-owned-card", ex.getReason());
    }

    @Test
    @DisplayName("ownedCardId 기반 저장은 중복 ownedCardId를 거부한다")
    void replaceCurrentSkillDeckFromOwnedCardIdsRejectsDuplicateOwnedCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromOwnedCardIds(
                        profile(),
                        List.of("oc-1", "oc-1"),
                        List.of(new OwnedCard("oc-1", "C001", List.of()))
                ));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("currentSkillDeck ownedCardIds must not contain duplicate values: oc-1", ex.getReason());
    }

    @Test
    @DisplayName("ownedCardId based currentSkillDeck write rejects blank ownedCardId input")
    void replaceCurrentSkillDeckFromOwnedCardIdsRejectsBlankOwnedCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.replaceCurrentSkillDeckFromOwnedCardIds(
                        profile(),
                        List.of("oc-1", "   "),
                        List.of(new OwnedCard("oc-1", "C001", List.of()))
                ));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertEquals("currentSkillDeck ownedCardIds must not contain blank values", ex.getReason());
    }

    @Test
    @DisplayName("deleteCurrentSkillDeckMirror: character current deck 미러 삭제를 DeckService에 위임한다")
    void deleteCurrentSkillDeckMirrorDelegatesToDeckService() {
        service.deleteCurrentSkillDeckMirror(7L);

        verify(deckService).deleteCharacterCurrentSkillDeck(7L);
    }

    @Test
    @DisplayName("clearCurrentSkillDeck: profile currentSkillDeck을 비우고 미러 deck을 삭제한다")
    void clearCurrentSkillDeckClearsProfileAndDeletesMirrorDeck() {
        CharacterProfile profile = profile();
        when(repository.save(profile)).thenReturn(profile);

        CharacterProfile saved = service.clearCurrentSkillDeck(profile);

        assertEquals(null, saved.getCurrentSkillDeck());
        verify(repository).save(profile);
        verify(deckService).deleteCharacterCurrentSkillDeck(7L);
    }

    private static CharacterProfile profile() {
        return CharacterProfile.builder()
                .id(7L)
                .name("name")
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
                .currentSkillDeck(List.of("old"))
                .exCard("{}")
                .build();
    }
}
