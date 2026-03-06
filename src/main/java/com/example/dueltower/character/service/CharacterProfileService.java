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
                .trait1(normalizeOptionalText(req.trait1()))
                .trait2(normalizeOptionalText(req.trait2()))
                .ownedCards(req.ownedCards().trim())
                .currentSkillDeck(normalizeCurrentSkillDeck(req.currentSkillDeck()))
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
        profile.setTrait1(normalizeOptionalText(req.trait1()));
        profile.setTrait2(normalizeOptionalText(req.trait2()));
        profile.setOwnedCards(req.ownedCards().trim());
        profile.setCurrentSkillDeck(normalizeCurrentSkillDeck(req.currentSkillDeck()));
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
        requireText(req.wish(), "wish is required");
        validateDisposition(req.disposition());
        requireText(req.oneLiner(), "oneLiner is required");
        requireText(req.story(), "story is required");
        requireNumber(req.physical(), "physical is required");
        requireNumber(req.technique(), "technique is required");
        requireNumber(req.sense(), "sense is required");
        requireNumber(req.willpower(), "willpower is required");
        validateTraits(req.trait1(), req.trait2());
        requireText(req.ownedCards(), "ownedCards is required");
        requireText(req.exCard(), "exCard is required");
    }


    private static void validateDisposition(String disposition) {
        requireText(disposition, "disposition is required");
        String[] parts = disposition.trim().split("/");
        if (parts.length != 2) {
            throw new ResponseStatusException(BAD_REQUEST, "disposition must be in the format axis1/axis2 (e.g. 질서/선)");
        }

        String lawChaos = parts[0].trim();
        String goodEvil = parts[1].trim();

        if (!(lawChaos.equals("질서") || lawChaos.equals("중립") || lawChaos.equals("혼돈"))
                || !(goodEvil.equals("선") || goodEvil.equals("중용") || goodEvil.equals("악"))) {
            throw new ResponseStatusException(BAD_REQUEST, "disposition must combine one of [질서, 중립, 혼돈] and one of [선, 중용, 악]");
        }
    }

    private static void validateTraits(String trait1, String trait2) {
        String normalizedTrait1 = normalizeOptionalText(trait1);
        String normalizedTrait2 = normalizeOptionalText(trait2);

        if (normalizedTrait1 == null && normalizedTrait2 != null) {
            throw new ResponseStatusException(BAD_REQUEST, "trait2 cannot be set when trait1 is empty");
        }
    }


    private static List<String> normalizeCurrentSkillDeck(List<String> deckPresetIds) {
        if (deckPresetIds == null) {
            return null;
        }

        return deckPresetIds.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
