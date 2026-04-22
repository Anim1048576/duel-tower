package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.domain.HiddenTraitIds;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.service.DeckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class CharacterProfileServiceTest {

    @Mock
    private CharacterProfileRepository repository;

    @Mock
    private CharacterCombatStatCalculator combatStatCalculator;

    @Mock
    private DeckService deckService;

    @Mock
    private CharacterCurrentSkillDeckService currentSkillDeckService;

    @InjectMocks
    private CharacterProfileService service;

    @Test
    @DisplayName("create: null 요청 본문이면 BAD_REQUEST를 던진다")
    void createRejectsNullRequestBody() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(null));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: disposition 유효 조합(질서/선, 중립/중용, 혼돈/악)을 허용한다")
    void dispositionAcceptsValidValues() {
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        service.create(validRequestWithDisposition("질서/선"));
        service.create(validRequestWithDisposition("중립/중용"));
        service.create(validRequestWithDisposition("혼돈/악"));

        verify(repository, times(3)).save(any(CharacterProfile.class));
    }

    @Test
    @DisplayName("create: disposition에 슬래시가 없으면 BAD_REQUEST를 던진다")
    void dispositionRejectsInvalidFormatWithoutSlash() {
        CharacterProfileRequest req = validRequestWithDisposition("질서선");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: disposition 축 값이 유효하지 않으면 BAD_REQUEST를 던진다")
    void dispositionRejectsInvalidAxisValues() {
        CharacterProfileRequest req = validRequestWithDisposition("선/질서");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: trait1이 비어있을 때 trait2를 설정하면 BAD_REQUEST를 던진다")
    void trait2CannotBeSetWhenTrait1IsEmpty() {
        CharacterProfileRequest req = validRequestWithTraits("   ", "부지런함");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: hiddenTraitIds 공백/null/중복은 정규화한다")
    void hiddenTraitIdsAreNormalized() {
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        CharacterProfileRequest req = validRequestWithHiddenTraits(Arrays.asList(
                "  " + HiddenTraitIds.HUMAN + "  ",
                null,
                " ",
                HiddenTraitIds.HUMAN,
                HiddenTraitIds.HYBRID
        ));

        service.create(req);

        ArgumentCaptor<CharacterProfile> captor = ArgumentCaptor.forClass(CharacterProfile.class);
        verify(repository).save(captor.capture());

        CharacterProfile saved = captor.getValue();
        assertIterableEquals(List.of(HiddenTraitIds.HUMAN, HiddenTraitIds.HYBRID), saved.getHiddenTraitIds());
    }

    @Test
    @DisplayName("create: 미등록 hiddenTraitId가 오면 BAD_REQUEST를 던진다")
    void rejectUnknownHiddenTraitId() {
        CharacterProfileRequest req = validRequestWithHiddenTraits(List.of("UNKNOWN"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: 죄악은 악마 없이 단독으로 설정할 수 없다")
    void sinRequiresDemon() {
        CharacterProfileRequest req = validRequestWithHiddenTraits(List.of(HiddenTraitIds.SIN));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: 인간과 비인간을 동시에 가지려면 혼혈이 필요하다")
    void humanAndNonHumanRequireHybrid() {
        CharacterProfileRequest req = validRequestWithHiddenTraits(List.of(HiddenTraitIds.HUMAN, HiddenTraitIds.NON_HUMAN));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: trait1/trait2 공백 문자열은 null로 정규화한다")
    void optionalTextNormalizationBlankTraitsBecomeNull() {
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));
        CharacterProfileRequest req = validRequestWithTraits("   ", "   ");

        service.create(req);

        ArgumentCaptor<CharacterProfile> captor = ArgumentCaptor.forClass(CharacterProfile.class);
        verify(repository).save(captor.capture());

        CharacterProfile saved = captor.getValue();
        assertNull(saved.getTrait1());
        assertNull(saved.getTrait2());
    }

    @Test
    @DisplayName("create: 필수 텍스트 필드는 trim 후 저장한다")
    void createTrimsRequiredTextFields() {
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));
        CharacterProfileRequest req = validRequest(
                "  이름  ",
                "  소원  ",
                "  한줄소개  ",
                "  이야기  ",
                "  질서/선  ",
                List.of(),
                "  [\"card-1\"]  ",
                "  {\"id\":\"ex-1\"}  "
        );

        service.create(req);

        ArgumentCaptor<CharacterProfile> captor = ArgumentCaptor.forClass(CharacterProfile.class);
        verify(repository).save(captor.capture());

        CharacterProfile saved = captor.getValue();
        assertEquals("이름", saved.getName());
        assertEquals("소원", saved.getWish());
        assertEquals("한줄소개", saved.getOneLiner());
        assertEquals("이야기", saved.getStory());
        assertEquals("질서/선", saved.getDisposition());
        assertEquals("[\"card-1\"]", saved.getOwnedCards());
        assertEquals("{\"id\":\"ex-1\"}", saved.getExCard());
        assertNull(saved.getCurrentSkillDeck());
    }

    @Test
    @DisplayName("update: profile 필드 저장 시 기존 currentSkillDeck를 유지한다")
    void updateKeepsExistingCurrentSkillDeckWhenSavingProfileFields() {
        CharacterProfile existing = existingProfile();
        existing.setCurrentSkillDeck(List.of("old-1", "old-2"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        service.update(1L, validRequestWithDisposition(existing.getDisposition()));

        assertIterableEquals(List.of("old-1", "old-2"), existing.getCurrentSkillDeck());
    }

    @Test
    @DisplayName("update: public profile 저장 경로는 currentSkillDeck를 변경하지 않는다")
    void updateDoesNotWriteCurrentSkillDeckThroughProfileSave() {
        CharacterProfile existing = existingProfile();
        existing.setCurrentSkillDeck(List.of("old"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        service.update(1L, validRequestWithDisposition(existing.getDisposition()));

        assertIterableEquals(List.of("old"), existing.getCurrentSkillDeck());
    }

    @Test
    @DisplayName("update: ownedCards가 변경되면 stale currentSkillDeck와 미러 덱을 비운다")
    void updateClearsCurrentSkillDeckWhenOwnedCardsChanges() {
        CharacterProfile existing = existingProfile();
        existing.setOwnedCards("[\"old-card\"]");
        existing.setCurrentSkillDeck(List.of("old"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(currentSkillDeckService.clearCurrentSkillDeck(existing)).thenAnswer(invocation -> {
            existing.setCurrentSkillDeck(null);
            return existing;
        });
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        var response = service.update(1L, validRequest(
                "name",
                "wish",
                "oneLiner",
                "story",
                existing.getDisposition(),
                List.of(),
                "[\"new-card\"]",
                "{}"
        ));

        assertNull(existing.getCurrentSkillDeck());
        assertNull(response.currentSkillDeck());
        verify(currentSkillDeckService).clearCurrentSkillDeck(existing);
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: PLAYER 덱 count를 펼쳐 currentSkillDeck에 적용하고 detail 응답을 반환한다")
    void applyDeckToCurrentSkillDeckExpandsCountsAndReturnsCharacterDetail() {
        CharacterProfile existing = existingProfile();
        existing.setCurrentSkillDeck(List.of("old"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(10L))
                .thenReturn(List.of("C001", "C001", "C002", "C003"));
        when(currentSkillDeckService.replaceCurrentSkillDeckFromCardIds(existing, List.of("C001", "C001", "C002", "C003")))
                .thenAnswer(invocation -> {
                    existing.setCurrentSkillDeck(invocation.getArgument(1));
                    return existing;
                });
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));

        var response = service.applyDeckToCurrentSkillDeck(1L, 10L);

        assertIterableEquals(List.of("C001", "C001", "C002", "C003"), existing.getCurrentSkillDeck());
        assertIterableEquals(List.of("C001", "C001", "C002", "C003"), response.currentSkillDeck());
        assertEquals(existing.getOwnedCards(), response.ownedCards());
        assertEquals(existing.getExCard(), response.exCard());
        assertEquals(20, response.combatStats().maxHp());
        verify(currentSkillDeckService).replaceCurrentSkillDeckFromCardIds(existing, List.of("C001", "C001", "C002", "C003"));
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: 캐릭터가 없으면 NOT_FOUND를 반환하고 덱을 조회하지 않는다")
    void applyDeckToCurrentSkillDeckMissingCharacterReturnsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyDeckToCurrentSkillDeck(999L, 10L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
        verify(deckService, never()).expandPlayerDeckCardIdsForCurrentSkillDeck(anyLong());
        verify(currentSkillDeckService, never()).replaceCurrentSkillDeckFromCardIds(any(), any());
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: 덱 조회/검증 실패를 그대로 전달한다")
    void applyDeckToCurrentSkillDeckPropagatesDeckFailure() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(404L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "deck not found: 404"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyDeckToCurrentSkillDeck(1L, 404L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
        verify(currentSkillDeckService, never()).replaceCurrentSkillDeckFromCardIds(any(), any());
    }

    @Test
    @DisplayName("delete: 없는 캐릭터를 삭제하면 NOT_FOUND를 던진다")
    void deleteNonexistentCharacterReturnsNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(999L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("delete: 캐릭터 삭제 시 currentSkillDeck 미러 덱도 삭제한다")
    void deleteRemovesCurrentSkillDeckMirror() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(currentSkillDeckService).deleteCurrentSkillDeckMirror(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("get: 없는 캐릭터를 조회하면 NOT_FOUND를 던진다")
    void getNonexistentCharacterReturnsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(999L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
    }

    private static CharacterProfileRequest validRequestWithDisposition(String disposition) {
        return validRequest(
                "name",
                "wish",
                "oneLiner",
                "story",
                disposition,
                List.of(),
                "[]",
                "{}"
        );
    }

    private static CharacterProfileRequest validRequestWithTraits(String trait1, String trait2) {
        return new CharacterProfileRequest(
                "name",
                CharacterGender.MALE,
                20,
                "wish",
                "질서/선",
                "oneLiner",
                "story",
                5,
                5,
                5,
                5,
                trait1,
                trait2,
                List.of(),
                "[]",
                "{}"
        );
    }

    private static CharacterProfileRequest validRequestWithHiddenTraits(List<String> hiddenTraitIds) {
        return validRequest(
                "name",
                "wish",
                "oneLiner",
                "story",
                "질서/선",
                hiddenTraitIds,
                "[]",
                "{}"
        );
    }

    private static CharacterProfileRequest validRequest(
            String name,
            String wish,
            String oneLiner,
            String story,
            String disposition,
            List<String> hiddenTraitIds,
            String ownedCards,
            String exCard
    ) {
        return new CharacterProfileRequest(
                name,
                CharacterGender.MALE,
                20,
                wish,
                disposition,
                oneLiner,
                story,
                5,
                5,
                5,
                5,
                "trait1",
                "trait2",
                hiddenTraitIds,
                ownedCards,
                exCard
        );
    }

    private static CharacterProfile existingProfile() {
        return CharacterProfile.builder()
                .id(1L)
                .name("name")
                .gender(CharacterGender.MALE)
                .age(20)
                .wish("wish")
                .disposition("질서/선")
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
                .currentSkillDeck(List.of("deck-1"))
                .exCard("{}")
                .build();
    }
}
