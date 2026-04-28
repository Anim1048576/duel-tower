package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.domain.CharacterDisposition;
import com.example.dueltower.character.domain.HiddenTraitIds;
import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.dto.CombatStatsDto;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CharacterProfileService {

    private static final Logger log = LoggerFactory.getLogger(CharacterProfileService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CharacterProfileRepository repository;
    private final CharacterCombatStatCalculator combatStatCalculator;
    private final CharacterCardCollectionService cardCollectionService;
    private final CharacterLoadoutService loadoutService;

    public CharacterProfileService(
            CharacterProfileRepository repository,
            CharacterCombatStatCalculator combatStatCalculator,
            CharacterCardCollectionService cardCollectionService,
            CharacterLoadoutService loadoutService
    ) {
        this.repository = repository;
        this.combatStatCalculator = combatStatCalculator;
        this.cardCollectionService = cardCollectionService;
        this.loadoutService = loadoutService;
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
        warnIfLegacyLoadoutInputUsed(req);

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
                .build();

        CharacterProfile saved = repository.save(profile);
        replaceOwnedCards(saved.getId(), req);
        replaceExCard(saved.getId(), req);
        return toResponse(saved);
    }

    @Transactional
    public CharacterProfileResponse update(long id, CharacterProfileRequest req) {
        validateRequired(req);
        warnIfLegacyLoadoutInputUsed(req);

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
        boolean ownedCardsChanged = ownedCardsChanged(id, req);
        if (ownedCardsChanged) {
            loadoutService.clearCurrentSkillDeck(id);
        }
        replaceOwnedCards(id, req);
        replaceExCard(id, req);
        return toResponse(profile);
    }

    @Transactional
    public CharacterProfileResponse applyDeckToCurrentSkillDeck(long characterId, long deckId) {
        CharacterProfile profile = getByIdOrThrow(characterId);
        loadoutService.applyDeckTemplate(characterId, deckId);
        return toResponse(profile);
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(NOT_FOUND, "character not found: " + id);
        }
        loadoutService.deleteLoadout(id);
        cardCollectionService.deleteOwnedCards(id);
        repository.deleteById(id);
    }

    private CharacterProfile getByIdOrThrow(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "character not found: " + id));
    }

    private CharacterProfileResponse toResponse(CharacterProfile profile) {
        CharacterCombatStatCalculator.CombatStats combatStats = combatStatCalculator.calculate(profile);
        Long characterId = profile.getId();
        String exCardId = loadoutService.getExCardId(characterId);
        return new CharacterProfileResponse(
                characterId,
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
                normalizeHiddenTraitIds(profile.getHiddenTraitIds()),
                cardCollectionService.toOwnedCardResponses(characterId),
                loadoutService.getCurrentSkillDeckPreviewCardIds(characterId),
                exCardId,
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

    private boolean ownedCardsChanged(Long characterId, CharacterProfileRequest req) {
        if (req.ownedCardList() != null) {
            // Structured input can serialize differently from the legacy JSON string, so clear safely for this phase.
            return true;
        }
        String ownedCards = req.ownedCards().trim();
        return !ownedCards.equals(cardCollectionService.toOwnedCardsJson(characterId));
    }

    private void replaceOwnedCards(Long characterId, CharacterProfileRequest req) {
        if (req.ownedCardList() != null) {
            // New structured input wins. Legacy ownedCards is ignored when both are present.
            cardCollectionService.replaceOwnedCards(characterId, req.ownedCardList());
            return;
        }
        cardCollectionService.replaceOwnedCardsFromJson(characterId, req.ownedCards().trim());
    }

    private void replaceExCard(Long characterId, CharacterProfileRequest req) {
        if (req.exCardId() != null) {
            // New scalar input wins. Legacy exCard JSON is ignored when both are present.
            // A blank exCardId explicitly clears the equipped EX card.
            String exCardId = req.exCardId().trim();
            if (exCardId.isEmpty()) {
                loadoutService.clearExCard(characterId);
                return;
            }
            loadoutService.replaceExCard(characterId, exCardId);
            return;
        }
        replaceExCardFromJson(characterId, req.exCard());
    }

    private static boolean usesLegacyOwnedCardsInput(CharacterProfileRequest req) {
        return req.ownedCardList() == null;
    }

    private static boolean usesLegacyExCardInput(CharacterProfileRequest req) {
        return req.exCardId() == null;
    }

    private static boolean usesLegacyLoadoutInput(CharacterProfileRequest req) {
        return usesLegacyOwnedCardsInput(req) || usesLegacyExCardInput(req);
    }

    private static void warnIfLegacyLoadoutInputUsed(CharacterProfileRequest req) {
        if (!usesLegacyLoadoutInput(req)) {
            return;
        }
        List<String> fields = new java.util.ArrayList<>(2);
        if (usesLegacyOwnedCardsInput(req)) {
            fields.add("ownedCards");
        }
        if (usesLegacyExCardInput(req)) {
            fields.add("exCard");
        }
        log.warn(
                "Deprecated CharacterProfile request fields used: {}. Use ownedCardList/exCardId instead.",
                String.join(", ", fields)
        );
    }

    private void replaceExCardFromJson(Long characterId, String raw) {
        String exCardId = parseExCardId(raw);
        if (exCardId == null) {
            loadoutService.clearExCard(characterId);
            return;
        }
        loadoutService.replaceExCard(characterId, exCardId);
    }

    private static String parseExCardId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                String value = node.asText("").trim();
                return value.isEmpty() ? null : value;
            }
            String id = node.path("id").asText("").trim();
            return id.isEmpty() ? null : id;
        } catch (Exception e) {
            String value = raw.trim();
            return value.isEmpty() ? null : value;
        }
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
        if (req.ownedCardList() == null) {
            requireText(req.ownedCards(), "ownedCards is required");
        }
        if (req.exCardId() == null) {
            requireText(req.exCard(), "exCard is required");
        }
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
