package com.example.dueltower.character.api;

import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
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
