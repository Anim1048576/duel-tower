package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.domain.CharacterDisposition;
import com.example.dueltower.character.domain.HiddenTraitIds;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.dto.CombatStatsDto;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.deck.service.DeckService;
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
    private final DeckService deckService;
    private final CharacterCurrentSkillDeckService currentSkillDeckService;

    public CharacterProfileService(
            CharacterProfileRepository repository,
            CharacterCombatStatCalculator combatStatCalculator,
            DeckService deckService,
            CharacterCurrentSkillDeckService currentSkillDeckService
    ) {
        this.repository = repository;
        this.combatStatCalculator = combatStatCalculator;
        this.deckService = deckService;
        this.currentSkillDeckService = currentSkillDeckService;
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
                .hiddenTraitIds(normalizeHiddenTraitIds(req.hiddenTraitIds()))
                .ownedCards(req.ownedCards().trim())
                .currentSkillDeck(null)
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
        profile.setHiddenTraitIds(normalizeHiddenTraitIds(req.hiddenTraitIds()));
        profile.setOwnedCards(req.ownedCards().trim());
        profile.setExCard(req.exCard().trim());
        return toResponse(profile);
    }

    @Transactional
    public CharacterProfileResponse applyDeckToCurrentSkillDeck(long characterId, long deckId) {
        CharacterProfile profile = getByIdOrThrow(characterId);
        List<String> currentSkillDeck = deckService.expandPlayerDeckCardIdsForCurrentSkillDeck(deckId);
        CharacterProfile saved = currentSkillDeckService.replaceCurrentSkillDeckFromCardIds(profile, currentSkillDeck);
        return toResponse(saved);
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
        validateHiddenTraits(req.hiddenTraitIds());
        requireText(req.ownedCards(), "ownedCards is required");
        requireText(req.exCard(), "exCard is required");
    }


    private static void validateDisposition(String disposition) {
        requireText(disposition, "disposition is required");
        if (!CharacterDisposition.hasAxisFormat(disposition)) {
            throw new ResponseStatusException(BAD_REQUEST, "disposition must be in the format axis1/axis2 (e.g. 질서/선)");
        }
        if (!CharacterDisposition.isValid(disposition)) {
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


    private static void validateHiddenTraits(List<String> hiddenTraitIds) {
        List<String> normalizedIds = normalizeHiddenTraitIds(hiddenTraitIds);

        for (String id : normalizedIds) {
            if (!HiddenTraitIds.isSupported(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown hiddenTraitId: " + id);
            }
        }

        if (normalizedIds.contains(HiddenTraitIds.SIN) && !normalizedIds.contains(HiddenTraitIds.DEMON)) {
            throw new ResponseStatusException(BAD_REQUEST, "죄악은 악마 없이 단독으로 설정할 수 없습니다");
        }

        if (normalizedIds.contains(HiddenTraitIds.HUMAN)
                && normalizedIds.contains(HiddenTraitIds.NON_HUMAN)
                && !normalizedIds.contains(HiddenTraitIds.HYBRID)) {
            throw new ResponseStatusException(BAD_REQUEST, "인간과 비인간을 동시에 가지려면 혼혈이 필요합니다");
        }
    }

    private static List<String> normalizeHiddenTraitIds(List<String> hiddenTraitIds) {
        if (hiddenTraitIds == null) {
            return List.of();
        }
        return hiddenTraitIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
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
