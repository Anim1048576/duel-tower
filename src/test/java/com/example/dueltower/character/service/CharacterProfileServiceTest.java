package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterProfileServiceTest {

    private CharacterProfileRepository repository;
    private CharacterProfileService service;

    @BeforeEach
    void setUp() {
        repository = mock(CharacterProfileRepository.class);
        CharacterCombatStatCalculator calculator = new CharacterCombatStatCalculator();
        service = new CharacterProfileService(repository, calculator);
        when(repository.save(any(CharacterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAllowsNullAge() {
        CharacterProfileResponse response = service.create(validRequest(null, "강인함", null, "질서/선"));

        assertNull(response.age());

        ArgumentCaptor<CharacterProfile> captor = ArgumentCaptor.forClass(CharacterProfile.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getAge());
    }

    @Test
    void createAllowsNoTraits() {
        CharacterProfileResponse response = service.create(validRequest(20, null, null, "중립/중용"));

        assertNull(response.trait1());
        assertNull(response.trait2());
    }


    @Test
    void createAllowsNullCurrentSkillDeck() {
        CharacterProfileRequest req = validRequest(20, "강인함", null, "질서/선");
        req = new CharacterProfileRequest(
                req.name(), req.gender(), req.age(), req.wish(), req.disposition(), req.oneLiner(), req.story(),
                req.physical(), req.technique(), req.sense(), req.willpower(),
                req.trait1(), req.trait2(), req.ownedCards(), null, req.exCard()
        );

        CharacterProfileResponse response = service.create(req);

        assertNull(response.currentSkillDeck());
    }

    @Test
    void createRejectsTrait2WithoutTrait1() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(validRequest(20, null, "민첩", "혼돈/악")));

        assertEquals("400 BAD_REQUEST \"trait2 cannot be set when trait1 is empty\"", ex.getMessage());
    }

    @Test
    void createRejectsInvalidDispositionCombination() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(validRequest(20, "강인함", null, "선/질서")));

        assertEquals("400 BAD_REQUEST \"disposition must combine one of [질서, 중립, 혼돈] and one of [선, 중용, 악]\"", ex.getMessage());
    }

    private CharacterProfileRequest validRequest(Integer age, String trait1, String trait2, String disposition) {
        return new CharacterProfileRequest(
                "티그",
                CharacterGender.FEMALE,
                age,
                "소중한 사람을 지키고 싶다",
                disposition,
                "오늘도 전진!",
                "짧은 이야기",
                12,
                8,
                10,
                7,
                trait1,
                trait2,
                "{\"cards\":[\"C001\"]}",
                List.of("D001", "D002"),
                "{\"id\":\"EX001\"}"
        );
    }
}
