package com.example.dueltower.character.api;

import com.example.dueltower.character.dto.CharacterProfileRequest;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.service.CharacterProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/content/characters")
public class CharacterProfileController {

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
    public CharacterProfileResponse create(@RequestBody(required = false) CharacterProfileRequest req) {
        return characterProfileService.create(req);
    }

    @PutMapping("/{id}")
    public CharacterProfileResponse update(@PathVariable long id, @RequestBody(required = false) CharacterProfileRequest req) {
        return characterProfileService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        characterProfileService.delete(id);
    }
}
