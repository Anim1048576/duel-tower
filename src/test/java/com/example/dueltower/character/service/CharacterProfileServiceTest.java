package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterHiddenTrait;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.domain.HiddenTraitIds;
import com.example.dueltower.character.dto.CharacterOwnedCardModifierResponse;
import com.example.dueltower.character.dto.CharacterOwnedCardResponse;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.repository.CharacterHiddenTraitRepository;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;

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
    private CharacterCardCollectionService cardCollectionService;

    @Mock
    private CharacterLoadoutService loadoutService;

    @Mock
    private CharacterHiddenTraitRepository hiddenTraitRepository;

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
        stubProfileSave();
        stubResponseReadModels(List.of(), "{}");

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
        stubProfileSave();
        stubResponseReadModels(List.of(), "{}");

        CharacterProfileRequest req = validRequestWithHiddenTraits(Arrays.asList(
                "  " + HiddenTraitIds.HUMAN + "  ",
                null,
                " ",
                HiddenTraitIds.HUMAN,
                HiddenTraitIds.HYBRID
        ));

        service.create(req);

        ArgumentCaptor<List<CharacterHiddenTrait>> captor = ArgumentCaptor.forClass(List.class);
        verify(hiddenTraitRepository).saveAll(captor.capture());

        assertEquals(List.of(HiddenTraitIds.HUMAN, HiddenTraitIds.HYBRID),
                captor.getValue().stream().map(CharacterHiddenTrait::getHiddenTraitId).toList());
        assertEquals(List.of(1L, 1L),
                captor.getValue().stream().map(CharacterHiddenTrait::getCharacterId).toList());
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
        stubProfileSave();
        stubResponseReadModels(List.of(), "{}");
        CharacterProfileRequest req = validRequestWithTraits("   ", "   ");

        service.create(req);

        ArgumentCaptor<CharacterProfile> captor = ArgumentCaptor.forClass(CharacterProfile.class);
        verify(repository).save(captor.capture());

        CharacterProfile saved = captor.getValue();
        assertNull(saved.getTrait1());
        assertNull(saved.getTrait2());
    }

    @Test
    @DisplayName("create: 필수 프로필 텍스트 필드는 trim 후 저장한다")
    void createTrimsRequiredProfileTextFields() {
        stubProfileSave();
        stubResponseReadModels(List.of(), "{}");
        CharacterProfileRequest req = validRequest(
                "  이름  ",
                "  소원  ",
                "  한줄소개  ",
                "  이야기  ",
                "  질서/선  ",
                List.of()
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
    }

    @Test
    @DisplayName("create: ownedCardList가 없으면 BAD_REQUEST를 던진다")
    void createRejectsMissingOwnedCardList() {
        CharacterProfileRequest req = validStructuredRequest(null, "");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("ownedCardList is required"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: exCardId가 없으면 BAD_REQUEST를 던진다")
    void createRejectsMissingExCardId() {
        CharacterProfileRequest req = validStructuredRequest(List.of(), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(req));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("exCardId is required"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: response includes structured loadout fields")
    void createResponseIncludesStructuredLoadoutFields() {
        stubProfileSave();
        OwnedCardDto ownedCard = new OwnedCardDto(
                "oc-1",
                "C001",
                List.of(new OwnedCardModifierDto("strengthened", 1)),
                false,
                false,
                false,
                true,
                null
        );
        CharacterOwnedCardResponse ownedCardResponse = new CharacterOwnedCardResponse(
                "oc-1",
                "C001",
                List.of(new CharacterOwnedCardModifierResponse("strengthened", 1)),
                false,
                false,
                false,
                true,
                null
        );
        stubResponseReadModels(
                List.of("C001"),
                "{\"id\":\"EX901\"}",
                List.of(ownedCardResponse)
        );

        var response = service.create(validStructuredRequest(List.of(ownedCard), "EX901"));

        assertEquals(1, response.ownedCardList().size());
        CharacterOwnedCardResponse responseOwnedCard = response.ownedCardList().get(0);
        assertEquals("oc-1", responseOwnedCard.ownedCardId());
        assertEquals("C001", responseOwnedCard.cardId());
        assertEquals(1, responseOwnedCard.modifiers().size());
        assertEquals("strengthened", responseOwnedCard.modifiers().get(0).modifierId());
        assertEquals(1, responseOwnedCard.modifiers().get(0).value());
        assertEquals(List.of("C001"), response.currentSkillDeckPreviewCardIds());
        assertEquals("EX901", response.exCardId());
        verify(cardCollectionService).toOwnedCardResponses(1L);
    }

    @Test
    @DisplayName("get: response uses null exCardId when EX is not equipped")
    void getResponseUsesStructuredFieldsWhenExCardIsEmpty() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubResponseReadModels(List.of(), "{}");

        var response = service.get(1L);

        assertTrue(response.ownedCardList().isEmpty());
        assertTrue(response.currentSkillDeckPreviewCardIds().isEmpty());
        assertNull(response.exCardId());
        verify(cardCollectionService).toOwnedCardResponses(1L);
    }

    @Test
    @DisplayName("update: ownedCardList가 제출되면 stale currentSkillDeck와 미러 덱을 비운다")
    void updateClearsCurrentSkillDeckWhenOwnedCardListIsSubmitted() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubResponseReadModels(List.of(), "{}");
        OwnedCardDto ownedCard = new OwnedCardDto("oc-new", "C001", List.of(), false, false, false, true, null);

        var response = service.update(1L, validStructuredRequest(List.of(ownedCard), ""));

        assertTrue(response.currentSkillDeckPreviewCardIds().isEmpty());
        verify(cardCollectionService).replaceOwnedCards(1L, List.of(ownedCard));
        verify(loadoutService).clearCurrentSkillDeck(1L);
        InOrder inOrder = inOrder(loadoutService, cardCollectionService);
        inOrder.verify(loadoutService).clearCurrentSkillDeck(1L);
        inOrder.verify(cardCollectionService).replaceOwnedCards(1L, List.of(ownedCard));
    }

    @Test
    @DisplayName("create: empty ownedCardList is valid without legacy ownedCards")
    void createAcceptsEmptyStructuredOwnedCardListWithoutLegacyOwnedCards() {
        stubProfileSave();
        stubResponseReadModels(List.of(), "{}");

        service.create(validStructuredRequest(List.of(), ""));

        ArgumentCaptor<List<OwnedCardDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardCollectionService).replaceOwnedCards(eq(1L), captor.capture());
        assertTrue(captor.getValue().isEmpty());
        verify(loadoutService).clearExCard(1L);
    }

    @Test
    @DisplayName("update: blank exCardId clears EX")
    void blankExCardIdClearsExCard() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubResponseReadModels(List.of(), "{}");

        service.update(1L, validStructuredRequest(List.of(), "   "));

        verify(loadoutService).clearExCard(1L);
        verify(loadoutService, never()).replaceExCard(anyLong(), anyString());
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: PLAYER 덱 count를 펼쳐 currentSkillDeck에 적용하고 detail 응답을 반환한다")
    void applyDeckToCurrentSkillDeckExpandsCountsAndReturnsCharacterDetail() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        stubResponseReadModels(List.of("C001", "C001", "C002", "C003"), "{}");

        var response = service.applyDeckToCurrentSkillDeck(1L, 10L);

        assertIterableEquals(List.of("C001", "C001", "C002", "C003"), response.currentSkillDeckPreviewCardIds());
        assertTrue(response.ownedCardList().isEmpty());
        assertNull(response.exCardId());
        assertEquals(20, response.combatStats().maxHp());
        verify(loadoutService).applyDeckTemplate(1L, 10L);
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: 캐릭터가 없으면 NOT_FOUND를 반환하고 덱을 조회하지 않는다")
    void applyDeckToCurrentSkillDeckMissingCharacterReturnsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyDeckToCurrentSkillDeck(999L, 10L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
        verify(loadoutService, never()).applyDeckTemplate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("applyDeckToCurrentSkillDeck: 덱 조회/검증 실패를 그대로 전달한다")
    void applyDeckToCurrentSkillDeckPropagatesDeckFailure() {
        CharacterProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        doThrow(new ResponseStatusException(NOT_FOUND, "deck not found: 404"))
                .when(loadoutService)
                .applyDeckTemplate(1L, 404L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.applyDeckToCurrentSkillDeck(1L, 404L));

        assertEquals(NOT_FOUND, ex.getStatusCode());
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

        verify(loadoutService).deleteLoadout(1L);
        verify(cardCollectionService).deleteOwnedCards(1L);
        verify(hiddenTraitRepository).deleteByCharacterId(1L);
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
        return validRequest("name", "wish", "oneLiner", "story", disposition, List.of());
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
                List.of(),
                ""
        );
    }

    private static CharacterProfileRequest validRequestWithHiddenTraits(List<String> hiddenTraitIds) {
        return validRequest("name", "wish", "oneLiner", "story", "질서/선", hiddenTraitIds);
    }

    private static CharacterProfileRequest validRequest(
            String name,
            String wish,
            String oneLiner,
            String story,
            String disposition,
            List<String> hiddenTraitIds
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
                List.of(),
                ""
        );
    }

    private static CharacterProfileRequest validStructuredRequest(List<OwnedCardDto> ownedCardList, String exCardId) {
        return new CharacterProfileRequest(
                "name",
                CharacterGender.MALE,
                20,
                "wish",
                "\uC9C8\uC11C/\uC120",
                "oneLiner",
                "story",
                5,
                5,
                5,
                5,
                "trait1",
                "trait2",
                List.of(),
                ownedCardList,
                exCardId
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
                .build();
    }

    private void stubProfileSave() {
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> {
            CharacterProfile profile = invocation.getArgument(0);
            setId(profile, 1L);
            return profile;
        });
    }

    private void stubResponseReadModels(
            List<String> previewCardIds,
            String exCardJson,
            List<CharacterOwnedCardResponse> ownedCardResponses
    ) {
        when(combatStatCalculator.calculate(any(CharacterProfile.class)))
                .thenReturn(new CharacterCombatStatCalculator.CombatStats(20, 3, 4, 4));
        when(cardCollectionService.toOwnedCardResponses(1L)).thenReturn(ownedCardResponses);
        when(loadoutService.getCurrentSkillDeckPreviewCardIds(1L)).thenReturn(previewCardIds);
        String exCardId = exCardJson.equals("{}") ? null : exCardJson.replace("{\"id\":\"", "").replace("\"}", "");
        when(loadoutService.getExCardId(1L)).thenReturn(exCardId);
        when(hiddenTraitRepository.findByCharacterIdOrderByIdAsc(1L)).thenReturn(List.of());
    }

    private void stubResponseReadModels(List<String> previewCardIds, String exCardJson) {
        stubResponseReadModels(previewCardIds, exCardJson, List.of());
    }

    private static void setId(CharacterProfile profile, Long id) {
        try {
            Field field = CharacterProfile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(profile, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
