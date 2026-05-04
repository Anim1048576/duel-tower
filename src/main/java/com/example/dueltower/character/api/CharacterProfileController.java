package com.example.dueltower.character.api;

import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.dto.CharacterCombatStatsPreviewRequest;
import com.example.dueltower.character.dto.CharacterCreateOptionsResponse;
import com.example.dueltower.character.dto.CharacterCurrentSkillDeckRequest;
import com.example.dueltower.character.dto.CombatStatsDto;
import com.example.dueltower.character.domain.HiddenTraitIds;
import com.example.dueltower.character.service.CharacterProfileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/content/characters")
public class CharacterProfileController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CURRENT_SKILL_DECK_WRITE_ERROR =
            "currentSkillDeck cannot be written through character create/update; use the dedicated current skill deck API";

    private final CharacterProfileService characterProfileService;

    public CharacterProfileController(CharacterProfileService characterProfileService) {
        this.characterProfileService = characterProfileService;
    }

    @GetMapping
    public List<CharacterProfileResponse> list() {
        return characterProfileService.list();
    }

    @GetMapping("/{id}")
    public CharacterProfileResponse get(@PathVariable long id) {
        return characterProfileService.get(id);
    }

    @GetMapping("/create-options")
    public CharacterCreateOptionsResponse createOptions() {
        return new CharacterCreateOptionsResponse(
                List.of(
                        new CharacterCreateOptionsResponse.Option("MALE", "\uB0A8\uC131", ""),
                        new CharacterCreateOptionsResponse.Option("FEMALE", "\uC5EC\uC131", ""),
                        new CharacterCreateOptionsResponse.Option("OTHER", "\uC131\uBCC4\uBD88\uBA85", "")
                ),
                List.of(
                        new CharacterCreateOptionsResponse.Option("\uC9C8\uC11C", "\uC9C8\uC11C", ""),
                        new CharacterCreateOptionsResponse.Option("\uC911\uB9BD", "\uC911\uB9BD", ""),
                        new CharacterCreateOptionsResponse.Option("\uD63C\uB3C8", "\uD63C\uB3C8", "")
                ),
                List.of(
                        new CharacterCreateOptionsResponse.Option("\uC120", "\uC120", ""),
                        new CharacterCreateOptionsResponse.Option("\uC911\uC6A9", "\uC911\uC6A9", ""),
                        new CharacterCreateOptionsResponse.Option("\uC545", "\uC545", "")
                ),
                List.of(
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.HUMANOID, "\uC778\uAC04\uD615", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.HUMAN, "\uC778\uAC04", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.NON_HUMANOID, "\uBE44\uC778\uAC04\uD615", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.NON_HUMAN, "\uBE44\uC778\uAC04", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.BEASTFOLK, "\uC218\uC778", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.WITCH, "\uB9C8\uB140", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.DEMON, "\uC545\uB9C8", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.SIN, "\uC8C4\uC545", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.HYBRID, "\uD63C\uD608", ""),
                        new CharacterCreateOptionsResponse.Option(HiddenTraitIds.OUTER_ENTITY, "\uC678\uC2E0", "")
                )
        );
    }

    @PostMapping("/combat-stats/preview")
    public CombatStatsDto previewCombatStats(@RequestBody(required = false) CharacterCombatStatsPreviewRequest req) {
        return characterProfileService.previewCombatStats(req);
    }

    @PostMapping
    public CharacterProfileResponse create(@RequestBody(required = false) JsonNode req) {
        return characterProfileService.create(toPublicMutationRequest(req));
    }

    @PutMapping("/{id}")
    public CharacterProfileResponse update(@PathVariable long id, @RequestBody(required = false) JsonNode req) {
        return characterProfileService.update(id, toPublicMutationRequest(req));
    }

    @PostMapping("/{characterId}/current-skill-deck/from-deck/{deckId}")
    public CharacterProfileResponse applyDeckToCurrentSkillDeck(
            @PathVariable long characterId,
            @PathVariable long deckId
    ) {
        return characterProfileService.applyDeckToCurrentSkillDeck(characterId, deckId);
    }

    @PutMapping("/{characterId}/current-skill-deck")
    public CharacterProfileResponse replaceCurrentSkillDeck(
            @PathVariable long characterId,
            @RequestBody(required = false) CharacterCurrentSkillDeckRequest req
    ) {
        return characterProfileService.replaceCurrentSkillDeck(
                characterId,
                req == null ? null : req.ownedCardIds()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        characterProfileService.delete(id);
    }

    private static CharacterProfileRequest toPublicMutationRequest(JsonNode req) {
        if (req == null) {
            return null;
        }
        if (req.has("currentSkillDeck")) {
            throw new ResponseStatusException(BAD_REQUEST, CURRENT_SKILL_DECK_WRITE_ERROR);
        }
        return JSON.convertValue(req, CharacterProfileRequest.class);
    }
}
