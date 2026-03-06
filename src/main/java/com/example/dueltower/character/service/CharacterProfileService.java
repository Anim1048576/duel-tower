package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.dto.CombatStatsDto;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CharacterProfileService {

    private final CharacterProfileRepository repository;
    private final CharacterCombatStatCalculator combatStatCalculator;

    public CharacterProfileService(
            CharacterProfileRepository repository,
            CharacterCombatStatCalculator combatStatCalculator
    ) {
        this.repository = repository;
        this.combatStatCalculator = combatStatCalculator;
    }

    @Transactional(readOnly = true)
    public List<CharacterProfileResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CharacterProfileResponse get(long id) {
        return toResponse(getByIdOrThrow(id));
    }

    @Transactional
    public CharacterProfileResponse create(CharacterProfileRequest req) {
        validateRequired(req);

        CharacterProfile profile = CharacterProfile.builder()
                .name(req.name().trim())
                .gender(req.gender())
                .age(req.age())
                .wish(req.wish().trim())
                .disposition(req.disposition().trim())
                .oneLiner(req.oneLiner().trim())
                .story(req.story().trim())
                .physical(req.physical())
                .technique(req.technique())
                .sense(req.sense())
                .willpower(req.willpower())
                .trait1(req.trait1().trim())
                .trait2(req.trait2().trim())
                .ownedCards(req.ownedCards().trim())
                .currentSkillDeck(req.currentSkillDeck().trim())
                .exCard(req.exCard().trim())
                .build();

        return toResponse(repository.save(profile));
    }

    @Transactional
    public CharacterProfileResponse update(long id, CharacterProfileRequest req) {
        validateRequired(req);

        CharacterProfile profile = getByIdOrThrow(id);
        profile.setName(req.name().trim());
        profile.setGender(req.gender());
        profile.setAge(req.age());
        profile.setWish(req.wish().trim());
        profile.setDisposition(req.disposition().trim());
        profile.setOneLiner(req.oneLiner().trim());
        profile.setStory(req.story().trim());
        profile.setPhysical(req.physical());
        profile.setTechnique(req.technique());
        profile.setSense(req.sense());
        profile.setWillpower(req.willpower());
        profile.setTrait1(req.trait1().trim());
        profile.setTrait2(req.trait2().trim());
        profile.setOwnedCards(req.ownedCards().trim());
        profile.setCurrentSkillDeck(req.currentSkillDeck().trim());
        profile.setExCard(req.exCard().trim());
        return toResponse(profile);
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "character not found: " + id);
        }
        repository.deleteById(id);
    }

    private CharacterProfile getByIdOrThrow(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "character not found: " + id));
    }

    private CharacterProfileResponse toResponse(CharacterProfile profile) {
        CharacterCombatStatCalculator.CombatStats combatStats = combatStatCalculator.calculate(profile);
        return new CharacterProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getGender(),
                profile.getAge(),
                profile.getWish(),
                profile.getDisposition(),
                profile.getOneLiner(),
                profile.getStory(),
                profile.getPhysical(),
                profile.getTechnique(),
                profile.getSense(),
                profile.getWillpower(),
                profile.getTrait1(),
                profile.getTrait2(),
                profile.getOwnedCards(),
                profile.getCurrentSkillDeck(),
                profile.getExCard(),
                new CombatStatsDto(
                        combatStats.maxHp(),
                        combatStats.maxAp(),
                        combatStats.attackPower(),
                        combatStats.healPower()
                ),
                profile.getCreateDate(),
                profile.getUpdateDate()
        );
    }

    private static void validateRequired(CharacterProfileRequest req) {
        if (req == null) {
            throw new ResponseStatusException(BAD_REQUEST, "request body is required");
        }
        requireText(req.name(), "name is required");
        if (req.gender() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "gender is required");
        }
        requireNumber(req.age(), "age is required");
        requireText(req.wish(), "wish is required");
        requireText(req.disposition(), "disposition is required");
        requireText(req.oneLiner(), "oneLiner is required");
        requireText(req.story(), "story is required");
        requireNumber(req.physical(), "physical is required");
        requireNumber(req.technique(), "technique is required");
        requireNumber(req.sense(), "sense is required");
        requireNumber(req.willpower(), "willpower is required");
        requireText(req.trait1(), "trait1 is required");
        requireText(req.trait2(), "trait2 is required");
        requireText(req.ownedCards(), "ownedCards is required");
        requireText(req.currentSkillDeck(), "currentSkillDeck is required");
        requireText(req.exCard(), "exCard is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }

    private static void requireNumber(Integer value, String message) {
        if (value == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }
}
