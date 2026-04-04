package com.example.dueltower.preset.api;

import com.example.dueltower.preset.dto.CreatePresetRequest;
import com.example.dueltower.preset.dto.PresetResponse;
import com.example.dueltower.preset.dto.UpdatePresetRequest;
import com.example.dueltower.preset.service.PresetService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/me/presets")
public class PresetController {

    private final PresetService presetService;

    public PresetController(PresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping
    public List<PresetResponse> list(Authentication authentication) {
        return presetService.listMine(requireAuthenticatedUsername(authentication));
    }

    @GetMapping("/{presetId}")
    public PresetResponse get(@PathVariable long presetId, Authentication authentication) {
        return presetService.getMine(requireAuthenticatedUsername(authentication), presetId);
    }

    @PostMapping
    public PresetResponse create(@RequestBody(required = false) CreatePresetRequest req,
                                 Authentication authentication) {
        return presetService.create(requireAuthenticatedUsername(authentication), req);
    }

    @PutMapping("/{presetId}")
    public PresetResponse update(@PathVariable long presetId,
                                 @RequestBody(required = false) UpdatePresetRequest req,
                                 Authentication authentication) {
        return presetService.update(requireAuthenticatedUsername(authentication), presetId, req);
    }

    @PostMapping("/{presetId}/clone")
    public PresetResponse clonePreset(@PathVariable long presetId, Authentication authentication) {
        return presetService.cloneMine(requireAuthenticatedUsername(authentication), presetId);
    }

    @DeleteMapping("/{presetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long presetId, Authentication authentication) {
        presetService.delete(requireAuthenticatedUsername(authentication), presetId);
    }

    private static String requireAuthenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(UNAUTHORIZED, "login required");
        }
        return authentication.getName();
    }
}
